package com.huawei.clouds.openrewrite.springkafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;

class SpringKafkaRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.springkafka.MigrateSpringKafkaTo3_3_15";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECOMMENDED))
                .parser(SpringKafkaTestSupport.parser());
    }

    @Test
    void publicRecipeHasExplicitUpgradeAutoAndThreeRiskStages() {
        assertEquals(List.of(
                        "com.huawei.clouds.openrewrite.springkafka.UpgradeSpringKafkaTo3_3_15",
                        "com.huawei.clouds.openrewrite.springkafka.MigrateDeterministicSpringKafka3Java",
                        "com.huawei.clouds.openrewrite.springkafka.FindSpringKafka3_3_15BuildRisks",
                        "com.huawei.clouds.openrewrite.springkafka.FindSpringKafka3_3_15SourceRisks",
                        "com.huawei.clouds.openrewrite.springkafka.FindSpringKafka3_3_15ConfigurationRisks"),
                environment().activateRecipes(RECOMMENDED).getRecipeList().stream()
                        .map(recipe -> recipe.getName()).toList());
    }

    @Test
    void approvedDependencyAndOfficialErrorHandlerMigrationComposeWithSemanticMarker() {
        rewriteRun(spec -> spec.parser(SpringKafkaTestSupport.legacyErrorHandlerParser()),
                xml(UpgradeSpringKafkaDependencyTest.pom("2.9.5"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>3.3.15</version>"), printed);
                            assertFalse(printed.contains(FindSpringKafka3315BuildRisks.OUTSIDE), printed);
                        })),
                java("""
                        import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
                        import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
                        class KafkaErrors {
                            SeekToCurrentErrorHandler handler = new SeekToCurrentErrorHandler();
                            void configure(AbstractKafkaListenerContainerFactory factory) {
                                factory.setErrorHandler(handler);
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("DefaultErrorHandler"), printed);
                    assertTrue(printed.contains("setCommonErrorHandler(handler)"), printed);
                    assertTrue(printed.contains(FindSpringKafka3315SourceRisks.DEFAULT_ERROR_HANDLER), printed);
                    assertTrue(printed.contains(FindSpringKafka3315SourceRisks.CONTAINER), printed);
                })));
    }

    @Test
    void higherVersionIsNeverDowngradedAndReceivesOnlyTheExactConflictLabel() {
        rewriteRun(xml(UpgradeSpringKafkaDependencyTest.pom("4.0.0"), source -> source.path("pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>4.0.0</version>"), printed);
                    assertFalse(printed.contains("<version>3.3.15</version>"), printed);
                    assertEquals(1, occurrences(printed, SpringKafkaUpgradeSupport.TARGET_CONFLICT), printed);
                })));
    }

    @Test
    void futureAutoMigrationStillLeavesAVisibleBehaviorReview() {
        rewriteRun(java("""
                import org.springframework.kafka.core.KafkaOperations;
                import org.springframework.kafka.support.SendResult;
                import org.springframework.util.concurrent.ListenableFuture;
                import org.springframework.util.concurrent.ListenableFutureCallback;
                class Producer {
                    void send(KafkaOperations<String, String> operations) {
                        ListenableFuture<SendResult<String, String>> future =
                                operations.send("orders", "id", "payload");
                        future.addCallback(new ListenableFutureCallback<>() {
                            public void onSuccess(SendResult<String, String> result) {}
                            public void onFailure(Throwable ex) {}
                        });
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("CompletableFuture<SendResult<String, String>>"), printed);
            assertTrue(printed.contains("whenComplete"), printed);
            assertTrue(printed.contains(FindSpringKafka3315SourceRisks.FUTURE), printed);
        })));
    }

    @Test
    void strictDependencyAndConfigurationMarkersComposeInOneRun() {
        rewriteRun(
                xml(UpgradeSpringKafkaDependencyTest.pom("2.8.11"),
                        UpgradeSpringKafkaDependencyTest.pom("3.3.15"), source -> source.path("pom.xml")),
                properties("""
                        spring.kafka.consumer.properties.spring.json.trusted.packages=*
                        spring.kafka.listener.eos-mode=ALPHA
                        spring.kafka.retry.topic.delay=1000
                        spring.kafka.listener.observation-enabled=true
                        """, source -> source.path("application.properties")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindSpringKafka3315ConfigurationRisks.JSON), printed);
                            assertTrue(printed.contains(FindSpringKafka3315ConfigurationRisks.TRANSACTION), printed);
                            assertTrue(printed.contains(FindSpringKafka3315ConfigurationRisks.RETRY_DLT), printed);
                            assertTrue(printed.contains(FindSpringKafka3315ConfigurationRisks.OBSERVATION), printed);
                        })));
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeSpringKafkaDependencyTest.pom("2.9.5"),
                        UpgradeSpringKafkaDependencyTest.pom("3.3.15"), source -> source.path("pom.xml")),
                java("""
                        import org.springframework.kafka.core.KafkaOperations;
                        import org.springframework.kafka.core.KafkaOperations2;
                        class Producer {
                            KafkaOperations2<String, String> bridge(KafkaOperations<String, String> operations) {
                                return operations.usingCompletableFuture();
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertFalse(printed.contains("KafkaOperations2"), printed);
                    assertFalse(printed.contains("usingCompletableFuture"), printed);
                })),
                properties("spring.kafka.listener.eos-mode=ALPHA\n",
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(),
                                        FindSpringKafka3315ConfigurationRisks.TRANSACTION)))));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springkafka",
                                      "org.openrewrite.java.spring.kafka")
                .build();
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
