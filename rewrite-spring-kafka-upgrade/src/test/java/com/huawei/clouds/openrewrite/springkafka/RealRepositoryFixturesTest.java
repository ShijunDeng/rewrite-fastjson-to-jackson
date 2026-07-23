package com.huawei.clouds.openrewrite.springkafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class RealRepositoryFixturesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
                        .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springkafka",
                                              "org.openrewrite.java.spring.kafka")
                        .build()
                        .activateRecipes("com.huawei.clouds.openrewrite.springkafka.MigrateSpringKafkaTo3_3_15"))
                .parser(SpringKafkaTestSupport.parser());
    }

    @Test
    void alexeiErrorHandlerFixtureIsAutomaticallyMigratedAndMarked() {
        rewriteRun(java(fixture("alexei-error-handler.java"), source -> source.path("RealErrorHandlerFixture.java")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("DefaultErrorHandler"), printed);
                    assertFalse(printed.contains("SeekToCurrentErrorHandler"), printed);
                    assertTrue(printed.contains(FindSpringKafka3315SourceRisks.DEFAULT_ERROR_HANDLER), printed);
                })));
    }

    @Test
    void kkDddFutureBridgeFixtureIsAutomaticallyMigrated() {
        rewriteRun(java(fixture("kk-ddd-future-bridge.java"), source -> source.path("RealFutureBridgeFixture.java")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertFalse(printed.contains("KafkaOperations2"), printed);
                    assertFalse(printed.contains("usingCompletableFuture"), printed);
                    assertTrue(printed.contains("KafkaOperations<String, Object>"), printed);
                })));
    }

    @Test
    void baeldungJsonFixtureExposesSerializerTrustAndMapperReview() {
        rewriteRun(java(fixture("baeldung-json.java"), source -> source.path("RealJsonFixture.java")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringKafka3315SourceRisks.JSON),
                                after.printAll()))));
    }

    @Test
    void baeldungRetryTopicFixtureExposesRetryDltAndListenerReview() {
        rewriteRun(java(fixture("baeldung-retry-topic.java"), source -> source.path("RealRetryTopicFixture.java")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindSpringKafka3315SourceRisks.RETRY_DLT), printed);
                    assertTrue(printed.contains(FindSpringKafka3315SourceRisks.LISTENER), printed);
                })));
    }

    @Test
    void dsyerTransactionFixtureExposesTransactionAndRollbackReview() {
        rewriteRun(java(fixture("dsyer-transaction.java"), source -> source.path("RealTransactionFixture.java")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains(FindSpringKafka3315SourceRisks.TRANSACTION), printed);
                    assertTrue(printed.contains(FindSpringKafka3315SourceRisks.RETRY_DLT), printed);
                })));
    }

    private static String fixture(String name) {
        String path = "/fixtures/real/" + name;
        try (InputStream stream = RealRepositoryFixturesTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load " + path, exception);
        }
    }
}
