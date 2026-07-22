package com.huawei.clouds.openrewrite.feignjackson;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FeignJackson13ApiMigrationTest implements RewriteTest {
    private static final String DECODER_DEFAULT =
            "Feign Jackson 12+ maps HTTP 404/204 to Util.emptyValueOf(type), while 10/11 did not; also revalidate empty bodies, response charset, unknown properties, Optional/collection defaults, and Jackson 2.18 coercion";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateFeignJackson13Apis())
                .parser(JavaParser.fromJavaVersion().classpath("feign-core", "feign-jackson", "jackson-databind",
                        "jackson-core", "jackson-annotations"));
    }

    @Test
    void renamesDecode404OnJacksonDecoderBuilderChain() {
        // Wiring form reduced from Apache James at 75a3c1e7a4ae0656ffc8558c5853aba00a4f9009.
        rewriteRun(java(
                """
                import feign.Feign;
                import feign.jackson.JacksonDecoder;
                interface Api {}
                class Factory {
                    Api create() {
                        return Feign.builder().decoder(new JacksonDecoder()).decode404()
                            .target(Api.class, "https://example.test");
                    }
                }
                """,
                """
                import feign.Feign;
                import feign.jackson.JacksonDecoder;
                interface Api {}
                class Factory {
                    Api create() {
                        return Feign.builder().decoder(new JacksonDecoder()).dismiss404()
                            .target(Api.class, "https://example.test");
                    }
                }
                """));
    }

    @Test
    void preservesEncoderDecoderOrderAndOtherBuilderCalls() {
        rewriteRun(java(
                """
                import feign.Feign;
                import feign.jackson.JacksonDecoder;
                import feign.jackson.JacksonEncoder;
                class Wiring {
                    Object builder() {
                        return Feign.builder().encoder(new JacksonEncoder()).decode404()
                            .decoder(new JacksonDecoder()).logLevel(feign.Logger.Level.BASIC);
                    }
                }
                """,
                """
                import feign.Feign;
                import feign.jackson.JacksonDecoder;
                import feign.jackson.JacksonEncoder;
                class Wiring {
                    Object builder() {
                        return Feign.builder().encoder(new JacksonEncoder()).dismiss404()
                            .decoder(new JacksonDecoder()).logLevel(feign.Logger.Level.BASIC);
                    }
                }
                """));
    }

    @Test
    void sameNamedApplicationBuilderIsNoop() {
        rewriteRun(java("class LocalBuilder { LocalBuilder decode404() { return this; } void use() { decode404(); } }"));
    }

    @Test
    void targetSpellingIsNoop() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                """
                package feign;
                public class Feign {
                    public static Builder builder() { return new Builder(); }
                    public static class Builder { public Builder dismiss404() { return this; } }
                }
                """)), java("""
                import feign.Feign;
                class Current { Object builder() { return Feign.builder().dismiss404(); } }
                """));
    }

    @Test
    void generatedAndInstalledTreesAreNoop() {
        rewriteRun(
                java("""
                        import feign.Feign;
                        class Generated { Object builder() { return Feign.builder().decode404(); } }
                        """, source -> source.path("generatedClients/Generated.java")),
                java("""
                        import feign.Feign;
                        class Installed { Object builder() { return Feign.builder().decode404(); } }
                        """, source -> source.path("installation/cache/Installed.java")));
    }

    @Test
    void installLeafFilenameIsMigrated() {
        rewriteRun(java(
                """
                import feign.Feign;
                class install { Object builder() { return Feign.builder().decode404(); } }
                """,
                """
                import feign.Feign;
                class install { Object builder() { return Feign.builder().dismiss404(); } }
                """, source -> source.path("install.java")));
    }

    @Test
    void deterministicRenameIsTwoCycleIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                import feign.Feign;
                class Client { Object builder() { return Feign.builder().decode404(); } }
                """,
                """
                import feign.Feign;
                class Client { Object builder() { return Feign.builder().dismiss404(); } }
                """));
    }

    @Test
    void recommendedRecipeRunsAutoThenMarksBehaviorBoundary() {
        rewriteRun(spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feignjackson").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.feignjackson.MigrateFeignJacksonTo13_6")),
                java(
                        """
                        import feign.Feign;
                        import feign.jackson.JacksonDecoder;
                        class Client { Object builder() { return Feign.builder().decoder(new JacksonDecoder()).decode404(); } }
                        """,
                        """
                        import feign.Feign;
                        import feign.jackson.JacksonDecoder;
                        class Client { Object builder() { return Feign.builder().decoder(/*~~(%s)~~>*/new JacksonDecoder()).dismiss404(); } }
                        """.formatted(DECODER_DEFAULT)));
    }

    @Test
    void lowLevelRecipeNeverTouchesSource() {
        rewriteRun(spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feignjackson").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.feignjackson.UpgradeFeignJacksonTo13_6")),
                java("""
                        import feign.Feign;
                        import feign.jackson.JacksonDecoder;
                        class Client { Object builder() { return Feign.builder().decoder(new JacksonDecoder()).decode404(); } }
                        """));
    }
}
