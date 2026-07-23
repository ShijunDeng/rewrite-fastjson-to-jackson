package com.huawei.clouds.openrewrite.springkafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SpringKafkaOfficialMigrationTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springkafka.MigrateDeterministicSpringKafka3Java";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE))
                .parser(SpringKafkaTestSupport.parser());
    }

    @Test
    void reusesAllApplicableOfficialSpringKafkaComponentsButNotTheirBroadDependencyUpgrade() {
        List<String> names = environment().activateRecipes(RECIPE).getRecipeList().stream()
                .map(recipe -> recipe.getName()).toList();
        assertTrue(names.contains("org.openrewrite.java.spring.kafka.KafkaOperationsSendReturnType"), names.toString());
        assertTrue(names.contains("org.openrewrite.java.spring.kafka.KafkaTestUtilsDuration"), names.toString());
        assertTrue(names.contains("org.openrewrite.java.spring.kafka.RemoveUsingCompletableFuture"), names.toString());
        assertTrue(names.contains("org.openrewrite.java.spring.kafka.UpgradeSpringKafka_2_8_ErrorHandlers"),
                names.toString());
        assertFalse(names.contains("org.openrewrite.java.dependencies.UpgradeDependencyVersion"), names.toString());
        assertFalse(names.contains("org.openrewrite.java.spring.kafka.UpgradeSpringKafka_3_0"), names.toString());
    }

    @Test
    void migratesSeekToCurrentErrorHandlerUsingTheOfficialRecipe() {
        rewriteRun(spec -> spec.parser(SpringKafkaTestSupport.legacyErrorHandlerParser()), java("""
                import java.util.List;
                import org.apache.kafka.clients.consumer.Consumer;
                import org.apache.kafka.clients.consumer.ConsumerRecord;
                import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
                import org.springframework.kafka.listener.MessageListenerContainer;
                import org.springframework.kafka.listener.SeekToCurrentErrorHandler;

                class KafkaErrors {
                    private final SeekToCurrentErrorHandler handler = new SeekToCurrentErrorHandler();
                    void configure(AbstractKafkaListenerContainerFactory factory) {
                        factory.setErrorHandler(handler);
                    }
                    void handle(Exception exception, List<ConsumerRecord<?, ?>> records,
                                Consumer<?, ?> consumer, MessageListenerContainer container) {
                        handler.handle(exception, records, consumer, container);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("DefaultErrorHandler"), printed);
            assertTrue(printed.contains("setCommonErrorHandler(handler)"), printed);
            assertTrue(printed.contains("handler.handleRemaining("), printed);
            assertFalse(printed.contains("SeekToCurrentErrorHandler"), printed);
        })));
    }

    @Test
    void migratesKafkaOperations2AndRemovesUsingCompletableFuture() {
        rewriteRun(java("""
                import org.springframework.kafka.core.KafkaOperations;
                import org.springframework.kafka.core.KafkaOperations2;

                class Producer {
                    KafkaOperations2<String, String> bridge(KafkaOperations<String, String> operations) {
                        return operations.usingCompletableFuture();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("KafkaOperations<String, String> bridge"), printed);
            assertTrue(printed.contains("return operations;"), printed);
            assertFalse(printed.contains("KafkaOperations2"), printed);
            assertFalse(printed.contains("usingCompletableFuture"), printed);
        })));
    }

    @Test
    void migratesListenableFutureCallbacksToCompletableFuture() {
        rewriteRun(java("""
                import org.springframework.kafka.core.KafkaOperations;
                import org.springframework.kafka.support.SendResult;
                import org.springframework.util.concurrent.ListenableFuture;
                import org.springframework.util.concurrent.ListenableFutureCallback;

                class Producer {
                    void send(KafkaOperations<String, String> operations) {
                        ListenableFuture<SendResult<String, String>> future =
                                operations.send("topic", "key", "value");
                        future.addCallback(new ListenableFutureCallback<>() {
                            public void onSuccess(SendResult<String, String> result) {
                                System.out.println(result);
                            }
                            public void onFailure(Throwable ex) {
                                System.err.println(ex.getMessage());
                            }
                        });
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("CompletableFuture<SendResult<String, String>>"), printed);
            assertTrue(printed.contains("future.whenComplete("), printed);
            assertTrue(printed.contains("if (ex == null)"), printed);
            assertFalse(printed.contains("ListenableFuture"), printed);
            assertFalse(printed.contains("addCallback"), printed);
        })));
    }

    @Test
    void migratesAllFourRemovedKafkaHeaderConstants() {
        rewriteRun(java("""
                import org.springframework.kafka.support.KafkaHeaders;
                class Headers {
                    String[] names() {
                        return new String[] {
                            KafkaHeaders.MESSAGE_KEY,
                            KafkaHeaders.PARTITION_ID,
                            KafkaHeaders.RECEIVED_MESSAGE_KEY,
                            KafkaHeaders.RECEIVED_PARTITION_ID
                        };
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("KafkaHeaders.KEY"), printed);
            assertTrue(printed.contains("KafkaHeaders.PARTITION"), printed);
            assertTrue(printed.contains("KafkaHeaders.RECEIVED_KEY"), printed);
            assertTrue(printed.contains("KafkaHeaders.RECEIVED_PARTITION"), printed);
            assertFalse(printed.contains("MESSAGE_KEY"), printed);
            assertFalse(printed.contains("PARTITION_ID"), printed);
        })));
    }

    @Test
    void migratesKafkaTestUtilsTimeoutsToDuration() {
        rewriteRun(java("""
                import org.apache.kafka.clients.consumer.Consumer;
                import org.springframework.kafka.test.utils.KafkaTestUtils;
                class KafkaTest {
                    void records(Consumer<String, String> consumer, long timeout) {
                        KafkaTestUtils.getRecords(consumer, 1000L);
                        KafkaTestUtils.getRecords(consumer, timeout, 1);
                        KafkaTestUtils.getSingleRecord(consumer, "topic", timeout);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("import java.time.Duration;"), printed);
            assertTrue(printed.contains("Duration.ofMillis(1000L)"), printed);
            assertTrue(printed.contains("Duration.ofMillis(timeout)"), printed);
        })));
    }

    @Test
    void officialCompositionIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
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
                })));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springkafka",
                                      "org.openrewrite.java.spring.kafka")
                .build();
    }
}
