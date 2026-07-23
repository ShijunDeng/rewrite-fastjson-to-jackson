package com.huawei.clouds.openrewrite.springkafka;

import org.openrewrite.java.JavaParser;

/**
 * Historical Spring Kafka needs its compile-time dependency closure for valid
 * Java type attribution; naming only the spring-kafka jar leaves method and
 * constructor types incomplete.
 */
final class SpringKafkaTestSupport {
    private SpringKafkaTestSupport() {
    }

    static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().classpath(
                "spring-kafka",
                "spring-kafka-test",
                "kafka-clients",
                "spring-retry",
                "spring-context",
                "spring-aop",
                "spring-beans",
                "spring-core",
                "spring-jcl",
                "spring-expression",
                "spring-messaging",
                "spring-tx",
                "spring-test",
                "jackson-annotations",
                "jackson-core",
                "jackson-databind"
        ).dependsOn(
                // Removed before 2.9.5, but still valid input for the 2.8.11 branch.
                """
                package org.springframework.kafka.listener;

                import java.util.List;
                import org.apache.kafka.clients.consumer.Consumer;
                import org.apache.kafka.clients.consumer.ConsumerRecord;
                import org.springframework.util.backoff.BackOff;

                public class SeekToCurrentErrorHandler {
                    public SeekToCurrentErrorHandler() {
                    }
                    public SeekToCurrentErrorHandler(BackOff backOff) {
                    }
                    public void handle(Exception exception, List<ConsumerRecord<?, ?>> records,
                            Consumer<?, ?> consumer, MessageListenerContainer container) {
                    }
                }
                """
        );
    }

    static JavaParser.Builder<?, ?> legacyErrorHandlerParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package org.apache.kafka.clients.consumer;
                public interface Consumer<K, V> {
                }
                """,
                """
                package org.apache.kafka.clients.consumer;
                public class ConsumerRecord<K, V> {
                }
                """,
                """
                package org.springframework.kafka.listener;
                public interface MessageListenerContainer {
                }
                """,
                """
                package org.springframework.kafka.listener;

                import java.util.List;
                import org.apache.kafka.clients.consumer.Consumer;
                import org.apache.kafka.clients.consumer.ConsumerRecord;

                public class SeekToCurrentErrorHandler {
                    public SeekToCurrentErrorHandler() {
                    }
                    public void handle(Exception exception, List<ConsumerRecord<?, ?>> records,
                            Consumer<?, ?> consumer, MessageListenerContainer container) {
                    }
                }
                """,
                """
                package org.springframework.kafka.listener;

                import java.util.List;
                import org.apache.kafka.clients.consumer.Consumer;
                import org.apache.kafka.clients.consumer.ConsumerRecord;

                public class DefaultErrorHandler {
                    public DefaultErrorHandler() {
                    }
                    public void handleRemaining(Exception exception, List<ConsumerRecord<?, ?>> records,
                            Consumer<?, ?> consumer, MessageListenerContainer container) {
                    }
                }
                """,
                """
                package org.springframework.kafka.config;

                import org.springframework.kafka.listener.DefaultErrorHandler;
                import org.springframework.kafka.listener.SeekToCurrentErrorHandler;

                public abstract class AbstractKafkaListenerContainerFactory {
                    public void setErrorHandler(SeekToCurrentErrorHandler handler) {
                    }
                    public void setCommonErrorHandler(DefaultErrorHandler handler) {
                    }
                }
                """
        );
    }
}
