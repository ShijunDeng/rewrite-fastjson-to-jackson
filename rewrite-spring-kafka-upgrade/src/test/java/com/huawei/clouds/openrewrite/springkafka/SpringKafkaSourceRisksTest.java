package com.huawei.clouds.openrewrite.springkafka;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SpringKafkaSourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringKafka3315SourceRisks())
                .parser(SpringKafkaTestSupport.parser());
    }

    @Test
    void marksLegacyAndDefaultErrorHandlerDecisionsSeparately() {
        rewriteRun(java("""
                import org.springframework.kafka.listener.ErrorHandler;
                import org.springframework.kafka.listener.SeekToCurrentBatchErrorHandler;
                import org.springframework.kafka.listener.DefaultErrorHandler;
                import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

                class Errors {
                    ErrorHandler legacy;
                    SeekToCurrentBatchErrorHandler batch;
                    void configure(ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
                        DefaultErrorHandler handler = new DefaultErrorHandler();
                        handler.setCommitRecovered(true);
                        factory.setCommonErrorHandler(handler);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringKafka3315SourceRisks.LEGACY_ERROR_HANDLER), printed);
            assertTrue(printed.contains(FindSpringKafka3315SourceRisks.DEFAULT_ERROR_HANDLER), printed);
            assertTrue(printed.contains(FindSpringKafka3315SourceRisks.CONTAINER), printed);
        })));
    }

    @Test
    void marksJsonSerializerDeserializerTypeMapperAndTrustBoundary() {
        rewriteRun(java("""
                import org.springframework.kafka.support.serializer.JsonSerializer;
                import org.springframework.kafka.support.serializer.JsonDeserializer;
                import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
                import org.springframework.kafka.support.mapping.DefaultJackson2JavaTypeMapper;

                class JsonConfig {
                    Object consumer() {
                        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
                        deserializer.addTrustedPackages("*");
                        deserializer.ignoreTypeHeaders();
                        DefaultJackson2JavaTypeMapper mapper = new DefaultJackson2JavaTypeMapper();
                        mapper.addTrustedPackages("example.events");
                        return new ErrorHandlingDeserializer<>(deserializer);
                    }
                    Object producer() { return new JsonSerializer<>(); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringKafka3315SourceRisks.JSON)))));
    }

    @Test
    void marksContainerAckConcurrencyPollAndInterceptorBoundaries() {
        rewriteRun(java("""
                import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
                import org.springframework.kafka.listener.ContainerProperties;

                class Containers {
                    void configure(ConcurrentKafkaListenerContainerFactory<String, String> factory) {
                        factory.setConcurrency(4);
                        factory.setBatchListener(true);
                        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
                        factory.getContainerProperties().setAsyncAcks(true);
                        factory.getContainerProperties().setPollTimeout(5000L);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringKafka3315SourceRisks.CONTAINER)))));
    }

    @Test
    void marksTransactionEosAndChainedManagerBoundaries() {
        rewriteRun(java("""
                import org.springframework.kafka.transaction.ChainedKafkaTransactionManager;
                import org.springframework.kafka.transaction.KafkaTransactionManager;
                import org.springframework.kafka.listener.ContainerProperties;

                class Transactions {
                    ContainerProperties.EOSMode mode = ContainerProperties.EOSMode.ALPHA;
                    Object chain(KafkaTransactionManager<Object, Object> kafka) {
                        return new ChainedKafkaTransactionManager<>(kafka);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringKafka3315SourceRisks.TRANSACTION)))));
    }

    @Test
    void marksRetryTopicDltAndAfterRollbackBoundaries() {
        rewriteRun(java("""
                import org.springframework.kafka.annotation.KafkaListener;
                import org.springframework.kafka.annotation.RetryableTopic;
                import org.springframework.kafka.annotation.DltHandler;
                import org.springframework.kafka.retrytopic.DltStrategy;
                import org.springframework.kafka.listener.DefaultAfterRollbackProcessor;
                import org.springframework.util.backoff.FixedBackOff;

                class Retries {
                    @RetryableTopic(attempts = "3", dltStrategy = DltStrategy.FAIL_ON_ERROR)
                    @KafkaListener(topics = "orders")
                    void listen(String order) {}

                    @DltHandler
                    void dlt(String order) {}

                    Object processor() {
                        DefaultAfterRollbackProcessor<Object, Object> processor =
                                new DefaultAfterRollbackProcessor<>(new FixedBackOff(100L, 2L));
                        processor.setCommitRecovered(true);
                        return processor;
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringKafka3315SourceRisks.RETRY_DLT), printed);
            assertTrue(printed.contains(FindSpringKafka3315SourceRisks.LISTENER), printed);
        })));
    }

    @Test
    void marksObservationImportsEvenWhenTheSourceClasspathIsStill29() {
        rewriteRun(java("""
                import org.springframework.kafka.support.micrometer.KafkaTemplateObservation;
                import org.springframework.kafka.support.micrometer.KafkaListenerObservationConvention;
                class Observed {}
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringKafka3315SourceRisks.OBSERVATION)))));
    }

    @Test
    void marksDirectKafkaClientApisAndTemplateFutures() {
        rewriteRun(java("""
                import org.apache.kafka.clients.admin.AdminClient;
                import org.apache.kafka.clients.consumer.ConsumerRecord;
                import org.springframework.kafka.core.KafkaTemplate;

                class Boundary {
                    AdminClient admin;
                    ConsumerRecord<String, String> record;
                    void send(KafkaTemplate<String, String> template) {
                        template.send("topic", "value");
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringKafka3315SourceRisks.KAFKA_CLIENT), printed);
            assertTrue(printed.contains(FindSpringKafka3315SourceRisks.FUTURE), printed);
        })));
    }

    @Test
    void unrelatedJavaAndGeneratedSourcesAreIgnored() {
        rewriteRun(
                java("""
                        class Plain {
                            String value = "JsonDeserializer";
                            void setTransactionManager(Object value) {}
                            void setObservationEnabled(boolean value) {}
                            void configure() {
                                setTransactionManager(new Object());
                                setObservationEnabled(true);
                            }
                        }
                        """, source -> source.afterRecipe(after ->
                        assertFalse(after.printAll().contains("~~("), after.printAll()))),
                java("""
                        import org.springframework.kafka.support.serializer.JsonDeserializer;
                        class Generated { JsonDeserializer<Object> value; }
                        """, source -> source.path("target/generated/Generated.java")));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import org.springframework.kafka.support.serializer.JsonDeserializer;
                        class JsonConfig {
                            Object value() {
                                JsonDeserializer<Object> d = new JsonDeserializer<>();
                                d.addTrustedPackages("*");
                                return d;
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(occurrences(after.printAll(),
                                FindSpringKafka3315SourceRisks.JSON) > 0, after.printAll()))));
    }

    @Test
    void riskRecipeDoesNotRewriteSourceTextOtherThanSearchMarkers() {
        rewriteRun(java("""
                import org.springframework.kafka.listener.DefaultErrorHandler;
                class Errors {
                    DefaultErrorHandler handler = new DefaultErrorHandler();
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("new DefaultErrorHandler()"), printed);
            assertFalse(printed.contains("SeekToCurrentErrorHandler"), printed);
        })));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
