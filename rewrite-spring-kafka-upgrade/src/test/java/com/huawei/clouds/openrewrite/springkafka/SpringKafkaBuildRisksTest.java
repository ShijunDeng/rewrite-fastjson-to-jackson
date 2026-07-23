package com.huawei.clouds.openrewrite.springkafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.xml.Assertions.xml;

class SpringKafkaBuildRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSpringKafka3315BuildRisks());
    }

    @ParameterizedTest(name = "Maven no-downgrade {0}")
    @ValueSource(strings = {"3.3.16", "3.4.0-M1", "4.0.0", "999999999999999999999.0.0"})
    void marksEveryHigherMavenVersionWithTheExactConflict(String version) {
        rewriteRun(xml(UpgradeSpringKafkaDependencyTest.pom(version), source -> source.path(version + "/pom.xml")
                .after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("<version>" + version + "</version>"), printed);
                    assertTrue(printed.contains(SpringKafkaUpgradeSupport.TARGET_CONFLICT), printed);
                    assertEquals(1, occurrences(printed, SpringKafkaUpgradeSupport.TARGET_CONFLICT), printed);
                    assertFalse(printed.contains("<version>3.3.15</version>"), printed);
                })));
    }

    @Test
    void marksHigherVersionThroughAUniqueMavenPropertyWithoutChangingItsText() {
        String source = UpgradeSpringKafkaDependencyTest.project(
                "<properties><v>3.4.0</v></properties><dependencies>" +
                UpgradeSpringKafkaDependencyTest.dep("${v}") + "</dependencies>");
        rewriteRun(xml(source, spec -> spec.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains("<v>3.4.0</v>"), printed);
            assertTrue(printed.contains("<version>"), printed);
            assertTrue(printed.contains("${v}"), printed);
            assertTrue(printed.contains(SpringKafkaUpgradeSupport.TARGET_CONFLICT), printed);
        })));
    }

    @Test
    void marksHigherGroovyAndKotlinVersionsWithoutChangingCoordinates() {
        rewriteRun(
                buildGradle("dependencies { implementation 'org.springframework.kafka:spring-kafka:3.3.16' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("spring-kafka:3.3.16"), printed);
                            assertTrue(printed.contains(SpringKafkaUpgradeSupport.TARGET_CONFLICT), printed);
                        })),
                buildGradleKts("dependencies { implementation(\"org.springframework.kafka:spring-kafka:4.0.0\") }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("spring-kafka:4.0.0"), printed);
                            assertTrue(printed.contains(SpringKafkaUpgradeSupport.TARGET_CONFLICT), printed);
                        })));
    }

    @ParameterizedTest(name = "approved/target has no primary marker {0}")
    @ValueSource(strings = {"2.8.11", "2.9.5", "3.3.15"})
    void approvedSourcesAndTargetHaveNoPrimaryRisk(String version) {
        rewriteRun(xml(UpgradeSpringKafkaDependencyTest.pom(version), source -> source.path(version + "/pom.xml")));
    }

    @Test
    void marksOutsideOwnerAndVariantBoundaries() {
        rewriteRun(
                xml(UpgradeSpringKafkaDependencyTest.pom("3.3.14"), source -> source.path("outside/pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindSpringKafka3315BuildRisks.OUTSIDE)))),
                xml(UpgradeSpringKafkaDependencyTest.project("<dependencies><dependency>" +
                        "<groupId>org.springframework.kafka</groupId><artifactId>spring-kafka</artifactId>" +
                        "</dependency></dependencies>"), source -> source.path("owner/pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindSpringKafka3315BuildRisks.OWNER)))),
                xml(UpgradeSpringKafkaDependencyTest.project("<dependencies>" +
                        UpgradeSpringKafkaDependencyTest.dep("2.9.5", "<classifier>tests</classifier>") +
                        "</dependencies>"), source -> source.path("variant/pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains(FindSpringKafka3315BuildRisks.VARIANT)))));
    }

    @Test
    void marksJavaSpringKafkaRetryObservationJacksonAndTestAlignment() {
        String pom = UpgradeSpringKafkaDependencyTest.project("""
                <properties><maven.compiler.release>11</maven.compiler.release></properties>
                <dependencies>
                  %s
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>5.3.25</version></dependency>
                  <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>3.2.3</version></dependency>
                  <dependency><groupId>org.springframework.retry</groupId><artifactId>spring-retry</artifactId><version>1.3.4</version></dependency>
                  <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-observation</artifactId><version>1.10.0</version></dependency>
                  <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>2.13.3</version></dependency>
                  <dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka-test</artifactId><version>2.9.5</version></dependency>
                </dependencies>
                """.formatted(UpgradeSpringKafkaDependencyTest.dep("2.9.5")));
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindSpringKafka3315BuildRisks.JAVA_BASELINE), printed);
            assertTrue(printed.contains(FindSpringKafka3315BuildRisks.SPRING_BASELINE), printed);
            assertTrue(printed.contains(FindSpringKafka3315BuildRisks.KAFKA_ALIGNMENT), printed);
            assertTrue(printed.contains(FindSpringKafka3315BuildRisks.RETRY_ALIGNMENT), printed);
            assertTrue(printed.contains(FindSpringKafka3315BuildRisks.OBSERVATION_ALIGNMENT), printed);
            assertTrue(printed.contains(FindSpringKafka3315BuildRisks.JACKSON_ALIGNMENT), printed);
            assertTrue(printed.contains(FindSpringKafka3315BuildRisks.TEST_ALIGNMENT), printed);
        })));
    }

    @Test
    void exactPublishedCompanionVersionsAreNotMarked() {
        String pom = UpgradeSpringKafkaDependencyTest.project("""
                <properties><maven.compiler.release>17</maven.compiler.release></properties>
                <dependencies>
                  %s
                  <dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>6.2.18</version></dependency>
                  <dependency><groupId>org.apache.kafka</groupId><artifactId>kafka-clients</artifactId><version>3.8.1</version></dependency>
                  <dependency><groupId>org.springframework.retry</groupId><artifactId>spring-retry</artifactId><version>2.0.12</version></dependency>
                  <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-observation</artifactId><version>1.14.14</version></dependency>
                  <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-tracing</artifactId><version>1.4.13</version></dependency>
                  <dependency><groupId>com.fasterxml.jackson.core</groupId><artifactId>jackson-databind</artifactId><version>2.18.6</version></dependency>
                  <dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka-test</artifactId><version>3.3.15</version></dependency>
                </dependencies>
                """.formatted(UpgradeSpringKafkaDependencyTest.dep("3.3.15")));
        rewriteRun(xml(pom, source -> source.path("pom.xml")));
    }

    @Test
    void doesNotAcceptCoreAndTracingVersionsFromTheWrongMicrometerLine() {
        String pom = UpgradeSpringKafkaDependencyTest.project("""
                <dependencies>
                  %s
                  <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-observation</artifactId><version>1.4.13</version></dependency>
                  <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-tracing</artifactId><version>1.14.14</version></dependency>
                </dependencies>
                """.formatted(UpgradeSpringKafkaDependencyTest.dep("3.3.15")));
        rewriteRun(xml(pom, source -> source.path("pom.xml").after(actual -> actual).afterRecipe(after ->
                assertEquals(2, occurrences(after.printAll(),
                        FindSpringKafka3315BuildRisks.OBSERVATION_ALIGNMENT), after.printAll()))));
    }

    @Test
    void marksGradleJava11AndCatalogOwnership() {
        rewriteRun(
                buildGradle("""
                        java { sourceCompatibility = JavaVersion.VERSION_11 }
                        dependencies { implementation 'org.springframework.kafka:spring-kafka:2.9.5' }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringKafka3315BuildRisks.JAVA_BASELINE)))),
                buildGradleKts("""
                        dependencies { implementation(libs.spring.kafka) }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindSpringKafka3315BuildRisks.OWNER)))));
    }

    @Test
    void conflictMarkerIsIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml(UpgradeSpringKafkaDependencyTest.pom("4.0.0"), source -> source.path("pom.xml")
                        .after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(),
                                        SpringKafkaUpgradeSupport.TARGET_CONFLICT)))));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
