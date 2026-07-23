package com.huawei.clouds.openrewrite.nettycodechttp;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class NettyCodecHttpRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.nettycodechttp.MigrateNettyCodecHttpTo4_1_136";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECOMMENDED))
                .parser(JavaParser.fromJavaVersion().dependsOn(NettyCodecHttpTestApi.riskSources()));
    }

    @Test
    void publicRecipeHasSafetyOrderedStages() {
        assertEquals(List.of(
                        "com.huawei.clouds.openrewrite.nettycodechttp.FindNettyCodecHttp41136BuildRisks",
                        "com.huawei.clouds.openrewrite.nettycodechttp.UpgradeNettyCodecHttpTo4_1_136",
                        "com.huawei.clouds.openrewrite.nettycodechttp.MigrateValidatedHttpDecoderConstructors",
                        "com.huawei.clouds.openrewrite.nettycodechttp.FindNettyCodecHttp41136SourceRisks"),
                environment().activateRecipes(RECOMMENDED).getRecipeList().stream()
                        .map(recipe -> recipe.getName()).toList());
    }

    @Test
    void recipeDescriptorIsDiscoverableFromRuntimeClasspath() {
        assertEquals(RECOMMENDED, environment().activateRecipes(RECOMMENDED).getName());
    }

    @Test
    void recommendedRecipeUpgrades41ButNeverDowngrades42() {
        rewriteRun(
                xml(UpgradeNettyCodecHttpDependencyTest.pom("4.1.100.Final"), source ->
                        source.path("upgrade/pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>4.1.136.Final</version>"), printed);
                        })),
                xml(UpgradeNettyCodecHttpDependencyTest.pom("4.2.10.Final"), source ->
                        source.path("no-downgrade/pom.xml").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>4.2.10.Final</version>"), printed);
                            assertTrue(printed.contains(FindNettyCodecHttp41136BuildRisks.NO_DOWNGRADE), printed);
                        })));
    }

    @Test
    void deterministicConstructorMigrationComposesWithParserRiskMarker() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.HttpRequestDecoder;
                class ServerPipeline { Object decoder(){ return new HttpRequestDecoder(4096, 8192, 16384, true); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("new HttpDecoderConfig().setMaxInitialLineLength(4096)"), printed);
            assertTrue(printed.contains(FindNettyCodecHttp41136SourceRisks.PARSING), printed);
        })));
    }

    @Test
    void unsafeValidationChoiceIsMarkedAndLeftUntouched() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.HttpServerCodec;
                class ServerPipeline { Object decoder(){ return new HttpServerCodec(4096, 8192, 16384, false); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("new HttpServerCodec(4096, 8192, 16384, false)"), printed);
            assertTrue(printed.contains(FindNettyCodecHttp41136SourceRisks.HEADER_VALIDATION), printed);
        })));
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeNettyCodecHttpDependencyTest.pom("4.2.10.Final"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> assertEquals(1,
                                occurrences(after.printAll(), FindNettyCodecHttp41136BuildRisks.NO_DOWNGRADE)))),
                java("""
                        import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
                        class C { Object x(Object request){ return new HttpPostRequestDecoder(request); } }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> assertEquals(1,
                        occurrences(after.printAll(), FindNettyCodecHttp41136SourceRisks.MULTIPART)))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.nettycodechttp").build();
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
