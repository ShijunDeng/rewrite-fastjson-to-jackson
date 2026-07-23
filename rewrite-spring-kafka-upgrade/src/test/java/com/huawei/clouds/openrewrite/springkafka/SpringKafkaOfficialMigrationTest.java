package com.huawei.clouds.openrewrite.springkafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.ReplaceConstantWithAnotherConstant;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        Environment environment = environment();
        Recipe official = environment.activateRecipes(
                "org.openrewrite.java.spring.kafka.UpgradeSpringKafka_3_0");
        DeclarativeRecipe local = assertInstanceOf(
                DeclarativeRecipe.class, environment.activateRecipes(RECIPE));

        assertEquals(List.of(FindSpringKafkaAuthoredSourceFiles.class),
                local.getPreconditions().stream().map(Object::getClass).toList());
        List<Recipe> officialChildren = effectiveChildren(official);
        UpgradeDependencyVersion broadUpgrade = officialChildren.stream()
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("org.springframework.kafka", broadUpgrade.getGroupId());
        assertEquals("spring-kafka", broadUpgrade.getArtifactId());
        assertEquals("3.0.x", broadUpgrade.getNewVersion());
        assertNull(broadUpgrade.getOverrideManagedVersion());

        List<String> safeOfficialSignatures = officialChildren.stream()
                .filter(recipe -> !(recipe instanceof UpgradeDependencyVersion))
                .map(SpringKafkaOfficialMigrationTest::signature)
                .toList();
        List<String> localSignatures = effectiveChildren(local).stream()
                .map(SpringKafkaOfficialMigrationTest::signature)
                .toList();
        assertEquals(safeOfficialSignatures, localSignatures);

        assertFalse(flatten(local).map(SpringKafkaOfficialMigrationTest::unwrap)
                .anyMatch(UpgradeDependencyVersion.class::isInstance));
        assertFalse(flatten(local).map(Recipe::getName)
                .anyMatch("org.openrewrite.java.spring.kafka.UpgradeSpringKafka_3_0"::equals));

        Recipe futureMajor = environment.activateRecipes(
                "org.openrewrite.java.spring.kafka.UpgradeSpringKafka_4_0");
        assertEquals("org.openrewrite.java.spring.kafka.UpgradeSpringKafka_4_0",
                futureMajor.getName());
        assertFalse(flatten(local).map(Recipe::getName).anyMatch(futureMajor.getName()::equals));
    }

    @Test
    void directlyReusedOfficialErrorHandlerCompositionHasExactFixedLeaves() {
        Recipe official = environment().activateRecipes(
                "org.openrewrite.java.spring.kafka.UpgradeSpringKafka_2_8_ErrorHandlers");
        List<Recipe> children = effectiveChildren(official);
        assertEquals(3, children.size());

        ChangeMethodName handle = assertInstanceOf(ChangeMethodName.class, children.get(0));
        assertEquals("org.springframework.kafka.listener.SeekToCurrentErrorHandler handle(..)",
                handle.getMethodPattern());
        assertEquals("handleRemaining", handle.getNewMethodName());

        ChangeMethodName setter = assertInstanceOf(ChangeMethodName.class, children.get(1));
        assertEquals(
                "org.springframework.kafka.config.AbstractKafkaListenerContainerFactory setErrorHandler(..)",
                setter.getMethodPattern());
        assertEquals("setCommonErrorHandler", setter.getNewMethodName());

        ChangeType handlerType = assertInstanceOf(ChangeType.class, children.get(2));
        assertEquals("org.springframework.kafka.listener.SeekToCurrentErrorHandler",
                handlerType.getOldFullyQualifiedTypeName());
        assertEquals("org.springframework.kafka.listener.DefaultErrorHandler",
                handlerType.getNewFullyQualifiedTypeName());
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
    void migratesStaticImportsWithTheOfficialCoreConstantRecipe() {
        rewriteRun(java("""
                import static org.springframework.kafka.support.KafkaHeaders.MESSAGE_KEY;
                import static org.springframework.kafka.support.KafkaHeaders.RECEIVED_PARTITION_ID;

                class Headers {
                    String key = MESSAGE_KEY;
                    String partition = RECEIVED_PARTITION_ID;
                }
                """, """
                import static org.springframework.kafka.support.KafkaHeaders.KEY;
                import static org.springframework.kafka.support.KafkaHeaders.RECEIVED_PARTITION;

                class Headers {
                    String key = KEY;
                    String partition = RECEIVED_PARTITION;
                }
                """));
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
                        KafkaTestUtils.getOneRecord(
                                "topic", "key", "value", 1, true, true, 1000L);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("import java.time.Duration;"), printed);
            assertTrue(printed.contains("Duration.ofMillis(1000L)"), printed);
            assertTrue(printed.contains("Duration.ofMillis(timeout)"), printed);
            assertTrue(printed.contains(
                    "\"topic\", \"key\", \"value\", 1, true, true, Duration.ofMillis(1000L)"), printed);
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

    @Test
    void generatedSourceIsNoopForEveryOfficialComponent() {
        rewriteRun(java("""
                import org.springframework.kafka.core.KafkaOperations;
                import org.springframework.kafka.core.KafkaOperations2;

                class GeneratedProducer {
                    KafkaOperations2<String, String> bridge(KafkaOperations<String, String> operations) {
                        return operations.usingCompletableFuture();
                    }
                }
                """, source -> source.path("target/generated-sources/GeneratedProducer.java")));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springkafka",
                                      "org.openrewrite.java.spring.kafka")
                .build();
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .map(SpringKafkaOfficialMigrationTest::unwrap)
                .filter(child -> !child.getClass().getName().endsWith("PreconditionBellwether"))
                .toList();
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe unwrapped = recipe;
        while (unwrapped instanceof Recipe.DelegatingRecipe delegating) {
            unwrapped = delegating.getDelegate();
        }
        return unwrapped;
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        return Stream.concat(Stream.of(recipe),
                recipe.getRecipeList().stream().flatMap(SpringKafkaOfficialMigrationTest::flatten));
    }

    private static String signature(Recipe recipe) {
        if (recipe instanceof ChangeType changeType) {
            return changeType.getName() + "|" + changeType.getOldFullyQualifiedTypeName() + "|" +
                   changeType.getNewFullyQualifiedTypeName() + "|" + changeType.getIgnoreDefinition();
        }
        if (recipe instanceof ReplaceConstantWithAnotherConstant constant) {
            return constant.getName() + "|" + constant.getExistingFullyQualifiedConstantName() + "|" +
                   constant.getFullyQualifiedConstantName();
        }
        return recipe.getName();
    }
}
