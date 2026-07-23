package com.huawei.clouds.openrewrite.nettycodechttp2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class NettyCodecHttp2RecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.nettycodechttp2.MigrateNettyCodecHttp2To4_1_136";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECOMMENDED))
                .parser(JavaParser.fromJavaVersion().dependsOn(NettyCodecHttp2TestApi.sources()));
    }

    @Test
    void publicRecipeHasSafetyOrderedStages() {
        assertEquals(List.of(
                        "com.huawei.clouds.openrewrite.nettycodechttp2.FindNettyCodecHttp2BuildRisks",
                        "com.huawei.clouds.openrewrite.nettycodechttp2.UpgradeNettyCodecHttp2To4_1_136",
                        "com.huawei.clouds.openrewrite.nettycodechttp2.MigrateDeprecatedHttp2Constructors",
                        "com.huawei.clouds.openrewrite.nettycodechttp2.FindNettyCodecHttp2SourceRisks"),
                environment().activateRecipes(RECOMMENDED).getRecipeList().stream()
                        .map(recipe -> recipe.getName()).toList());
    }

    @Test
    void publicRecipeDescriptorIsDiscoverableFromRuntimeClasspath() {
        assertEquals(RECOMMENDED, environment().activateRecipes(RECOMMENDED).getName());
    }

    @Test
    void recommendedRecipeUpgradesApproved41Source() {
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.pom("4.1.100.Final"), source ->
                source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>4.1.136.Final</version>"), printed);
                    assertFalse(printed.contains(FindNettyCodecHttp2BuildRisks.OUTSIDE), printed);
                })));
    }

    @ParameterizedTest(name = "never downgrades {0}")
    @MethodSource("targetConflicts")
    void recommendedRecipeNeverDowngradesHigherBranches(String version, String exactMarker) {
        rewriteRun(xml(UpgradeNettyCodecHttp2DependencyTest.pom(version), source ->
                source.path(version + "/pom.xml").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>" + version + "</version>"), printed);
                    assertFalse(printed.contains("<version>4.1.136.Final</version>"), printed);
                    assertTrue(printed.contains(exactMarker), printed);
                })));
    }

    static Stream<Arguments> targetConflicts() {
        return Stream.of(
                Arguments.of("4.1.137.Final",
                        FindNettyCodecHttp2BuildRisks.targetConflictMessage("4.1.137.Final")),
                Arguments.of("4.2.0.Final",
                        FindNettyCodecHttp2BuildRisks.targetConflictMessage("4.2.0.Final")),
                Arguments.of("4.2.10.Final", FindNettyCodecHttp2BuildRisks.TARGET_CONFLICT_4_2_10),
                Arguments.of("4.2.12.Final", FindNettyCodecHttp2BuildRisks.TARGET_CONFLICT_4_2_12),
                Arguments.of("5.0.0.Alpha1",
                        FindNettyCodecHttp2BuildRisks.targetConflictMessage("5.0.0.Alpha1")));
    }

    @Test
    void constructorAutoMigrationComposesWithDecompressionRiskMarker() {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.*;
                class Pipeline {
                    Object listener(Http2Connection connection, Http2FrameListener delegate) {
                        return new DelegatingDecompressorFrameListener(connection, delegate);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(
                            "new DelegatingDecompressorFrameListener(connection, delegate, 0)"), printed);
                    assertTrue(printed.contains(FindNettyCodecHttp2SourceRisks.DECOMPRESSION), printed);
                })));
    }

    @Test
    void unsafeValidationChoiceIsMarkedButNotInventedAway() {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.DefaultHttp2Headers;
                class Pipeline {
                    Object headers(boolean validate) {
                        return new DefaultHttp2Headers(validate);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("new DefaultHttp2Headers(validate)"), printed);
                    assertTrue(printed.contains(FindNettyCodecHttp2SourceRisks.HEADER_VALIDATION), printed);
                })));
    }

    @Test
    void targetConflictAndSourceMarkersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeNettyCodecHttp2DependencyTest.pom("4.2.12.Final"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> assertEquals(1,
                                occurrences(after.printAll(),
                                        FindNettyCodecHttp2BuildRisks.TARGET_CONFLICT_4_2_12)))),
                java("""
                        import io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder;
                        class Pipeline {
                            Object encoder() { return new DefaultHttp2HeadersEncoder(); }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> assertEquals(1,
                        occurrences(after.printAll(), FindNettyCodecHttp2SourceRisks.ENCODER_RESOURCE)))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath(
                "com.huawei.clouds.openrewrite.nettycodechttp2").build();
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
