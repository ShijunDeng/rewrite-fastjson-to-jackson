package com.huawei.clouds.openrewrite.feigncore;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class Feign13SourceMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateFeign13DeterministicApis())
                .parser(JavaParser.fromJavaVersion().classpath("feign-core"));
    }

    @Test
    void correctsContractMethodFromWorkbookTenSource() {
        rewriteRun(java(
                """
                import feign.Contract;
                class ClientContract {
                    Object inspect(Class<?> api) {
                        return new Contract.Default().parseAndValidatateMetadata(api);
                    }
                }
                """,
                """
                import feign.Contract;
                class ClientContract {
                    Object inspect(Class<?> api) {
                        return new Contract.Default().parseAndValidateMetadata(api);
                    }
                }
                """));
    }

    @Test
    void migratesDeprecatedDecode404NameWithoutChangingChain() {
        rewriteRun(java(
                """
                import feign.Feign;
                interface Api {}
                class ClientFactory {
                    Api create() {
                        return Feign.builder().decode404().target(Api.class, "https://example.test");
                    }
                }
                """,
                """
                import feign.Feign;
                interface Api {}
                class ClientFactory {
                    Api create() {
                        return Feign.builder().dismiss404().target(Api.class, "https://example.test");
                    }
                }
                """));
    }

    @Test
    void preservesEpochMillisecondsForRetryAfterRead() {
        rewriteRun(java(
                """
                import feign.RetryableException;
                class Backoff {
                    long epoch(RetryableException failure) {
                        return failure.retryAfter().getTime();
                    }
                }
                """,
                """
                import feign.RetryableException;
                class Backoff {
                    long epoch(RetryableException failure) {
                        return failure.retryAfter();
                    }
                }
                """));
    }

    @Test
    void migratesAllDeterministicCallsTogether() {
        rewriteRun(java(
                """
                import feign.Contract;
                import feign.Feign;
                import feign.RetryableException;
                class LegacyFeign {
                    void inspect(Contract.Default contract, Class<?> api, RetryableException failure) {
                        contract.parseAndValidatateMetadata(api);
                        Feign.builder().decode404();
                        long next = failure.retryAfter().getTime();
                    }
                }
                """,
                """
                import feign.Contract;
                import feign.Feign;
                import feign.RetryableException;
                class LegacyFeign {
                    void inspect(Contract.Default contract, Class<?> api, RetryableException failure) {
                        contract.parseAndValidateMetadata(api);
                        Feign.builder().dismiss404();
                        long next = failure.retryAfter();
                    }
                }
                """));
    }

    @Test
    void sameNamedApplicationApisAreNoop() {
        rewriteRun(java(
                """
                import java.util.Date;
                class LocalApi {
                    Object parseAndValidatateMetadata(Class<?> type) { return type; }
                    LocalApi decode404() { return this; }
                    Date retryAfter() { return new Date(); }
                    long use() { return retryAfter().getTime(); }
                }
                """));
    }

    @Test
    void ambiguousDateUseRemainsForMarkerRecipe() {
        rewriteRun(java(
                """
                import feign.RetryableException;
                import java.util.Date;
                class Backoff {
                    Date retryDate(RetryableException failure) {
                        return failure.retryAfter();
                    }
                }
                """));
    }

    @Test
    void generatedAndInstallTreesAreNoop() {
        rewriteRun(
                java("""
                        import feign.Feign;
                        class GeneratedClient { Object make() { return Feign.builder().decode404(); } }
                        """, source -> source.path("generatedSources/GeneratedClient.java")),
                java("""
                        import feign.RetryableException;
                        class InstalledClient { long read(RetryableException e) { return e.retryAfter().getTime(); } }
                        """, source -> source.path("installation/cache/InstalledClient.java")));
    }

    @Test
    void deterministicMigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                import feign.Feign;
                import feign.RetryableException;
                class IdempotentClient {
                    long read(RetryableException e) {
                        Feign.builder().decode404();
                        return e.retryAfter().getTime();
                    }
                }
                """,
                """
                import feign.Feign;
                import feign.RetryableException;
                class IdempotentClient {
                    long read(RetryableException e) {
                        Feign.builder().dismiss404();
                        return e.retryAfter();
                    }
                }
                """));
    }

    @Test
    void recommendedRecipeRunsAutoBeforeSourceAudit() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feigncore").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.feigncore.MigrateFeignCoreTo13_6")),
                java(
                        """
                        import feign.Feign;
                        import feign.RetryableException;
                        class RecommendedClient {
                            long read(RetryableException e) {
                                Feign.builder().decode404();
                                return e.retryAfter().getTime();
                            }
                        }
                        """,
                        """
                        import feign.Feign;
                        import feign.RetryableException;
                        class RecommendedClient {
                            long read(RetryableException e) {
                                Feign.builder().dismiss404();
                                return e.retryAfter();
                            }
                        }
                        """));
    }

    @Test
    void lowLevelUpgradeRecipeDoesNotMutateSource() {
        rewriteRun(
                spec -> spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.feigncore").build()
                        .activateRecipes("com.huawei.clouds.openrewrite.feigncore.UpgradeFeignCoreTo13_6")),
                java("""
                        import feign.Feign;
                        class UpgradeOnly { Object builder() { return Feign.builder().decode404(); } }
                        """));
    }
}
