package com.huawei.clouds.openrewrite.springkafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringKafkaConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringKafka3315ConfigurationRisks());
    }

    static Stream<Arguments> exactProperties() {
        return Stream.of(
                Arguments.of("spring.kafka.consumer.properties.spring.json.trusted.packages=*\n",
                        FindSpringKafka3315ConfigurationRisks.JSON),
                Arguments.of("spring.kafka.producer.properties.spring.json.add.type.headers=false\n",
                        FindSpringKafka3315ConfigurationRisks.JSON),
                Arguments.of("spring.kafka.consumer.properties.spring.json.value.default.type=example.Event\n",
                        FindSpringKafka3315ConfigurationRisks.JSON),
                Arguments.of("spring.kafka.listener.ack-mode=manual_immediate\n",
                        FindSpringKafka3315ConfigurationRisks.CONTAINER),
                Arguments.of("spring.kafka.listener.async-acks=true\n",
                        FindSpringKafka3315ConfigurationRisks.CONTAINER),
                Arguments.of("spring.kafka.listener.auth-exception-retry-interval=5s\n",
                        FindSpringKafka3315ConfigurationRisks.CONTAINER),
                Arguments.of("spring.kafka.producer.transaction-id-prefix=orders-${HOSTNAME}-\n",
                        FindSpringKafka3315ConfigurationRisks.TRANSACTION),
                Arguments.of("spring.kafka.listener.eos-mode=ALPHA\n",
                        FindSpringKafka3315ConfigurationRisks.TRANSACTION),
                Arguments.of("spring.kafka.producer.properties.enable.idempotence=true\n",
                        FindSpringKafka3315ConfigurationRisks.TRANSACTION),
                Arguments.of("spring.kafka.consumer.properties.isolation.level=read_committed\n",
                        FindSpringKafka3315ConfigurationRisks.TRANSACTION),
                Arguments.of("spring.kafka.retry.topic.delay=1000\n",
                        FindSpringKafka3315ConfigurationRisks.RETRY_DLT),
                Arguments.of("spring.kafka.retry.topic.max-attempts=4\n",
                        FindSpringKafka3315ConfigurationRisks.RETRY_DLT),
                Arguments.of("spring.kafka.listener.observation-enabled=true\n",
                        FindSpringKafka3315ConfigurationRisks.OBSERVATION),
                Arguments.of("spring.kafka.listener.micrometer-enabled=true\n",
                        FindSpringKafka3315ConfigurationRisks.OBSERVATION),
                Arguments.of("spring.kafka.consumer.properties.partition.assignment.strategy=example.Assignor\n",
                        FindSpringKafka3315ConfigurationRisks.NATIVE_CLIENT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exactProperties")
    void marksExactPropertyBoundaries(String before, String message) {
        rewriteRun(properties(before, source -> source.path("application.properties")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    @Test
    void marksEquivalentNestedYamlPaths() {
        rewriteRun(yaml("""
                spring:
                  kafka:
                    consumer:
                      properties:
                        spring:
                          json:
                            trusted:
                              packages: "*"
                    listener:
                      ack-mode: manual_immediate
                      observation-enabled: true
                    producer:
                      transaction-id-prefix: "orders-${HOSTNAME}-"
                    retry:
                      topic:
                        delay: 1000
                """, source -> source.path("application.yml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringKafka3315ConfigurationRisks.JSON), printed);
            assertTrue(printed.contains(FindSpringKafka3315ConfigurationRisks.CONTAINER), printed);
            assertTrue(printed.contains(FindSpringKafka3315ConfigurationRisks.TRANSACTION), printed);
            assertTrue(printed.contains(FindSpringKafka3315ConfigurationRisks.RETRY_DLT), printed);
            assertTrue(printed.contains(FindSpringKafka3315ConfigurationRisks.OBSERVATION), printed);
        })));
    }

    @Test
    void marksSpringXmlErrorHandlerSerializerContainerAndTransactionBeans() {
        rewriteRun(xml("""
                <beans>
                  <bean id="handler" class="org.springframework.kafka.listener.SeekToCurrentErrorHandler"/>
                  <bean id="json" class="org.springframework.kafka.support.serializer.JsonDeserializer"/>
                  <bean id="factory" class="org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory">
                    <property name="errorHandler" ref="handler"/>
                    <property name="ackMode" value="MANUAL_IMMEDIATE"/>
                    <property name="transactionManager" ref="kafkaTransactionManager"/>
                  </bean>
                </beans>
                """, source -> source.path("src/main/resources/kafka-context.xml")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringKafka3315ConfigurationRisks.XML),
                                after.printAll()))));
    }

    @Test
    void ignoresUnrelatedLookalikesAndOrdinaryKafkaSettings() {
        rewriteRun(
                properties("""
                        app.note=spring.kafka.listener.ack-mode
                        spring.kafka.bootstrap-servers=localhost:9092
                        spring.kafka.consumer.group-id=orders
                        """, source -> source.afterRecipe(after ->
                        assertFalse(after.printAll().contains("~~("), after.printAll()))),
                yaml("""
                        app:
                          note: spring.kafka.consumer.properties.spring.json.trusted.packages
                        spring:
                          kafka:
                            bootstrap-servers: localhost:9092
                            consumer:
                              group-id: orders
                        """, source -> source.afterRecipe(after ->
                        assertFalse(after.printAll().contains("~~("), after.printAll()))),
                xml("<beans><bean class=\"example.JsonDeserializer\"/></beans>",
                        source -> source.afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~("), after.printAll()))),
                xml("""
                        <beans>
                          <bean class="example.UnrelatedContainer">
                            <property name="ackMode" value="MANUAL"/>
                            <property name="transactionManager" ref="database"/>
                          </bean>
                        </beans>
                        """, source -> source.afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~("), after.printAll()))));
    }

    @Test
    void generatedConfigurationIsIgnored() {
        rewriteRun(properties(
                "spring.kafka.consumer.properties.spring.json.trusted.packages=*\n",
                source -> source.path("target/classes/application.properties")
                        .afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~("), after.printAll()))));
    }

    @Test
    void structuredMarkersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties("spring.kafka.listener.eos-mode=ALPHA\n",
                        source -> source.path("application.properties")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertEquals(1, occurrences(after.printAll(),
                                                FindSpringKafka3315ConfigurationRisks.TRANSACTION)))));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
