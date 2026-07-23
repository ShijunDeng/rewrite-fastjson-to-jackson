package com.huawei.clouds.openrewrite.log4j12api;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class Log4j12ApiBuildAndConfigurationRiskTest implements RewriteTest {
    @Test
    void preservesAndPreciselyMarksHigherMavenVersion() {
        rewriteRun(
                spec -> spec.recipe(Log4j12ApiTestSupport.activate(Log4j12ApiTestSupport.RECOMMENDED)),
                pomXml(
                        pom("2.26.0"),
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>2.26.0</version>"));
                            assertTrue(printed.contains("目标版本冲突（禁止降级）"));
                        })));
    }

    @Test
    void preservesAndPreciselyMarksHigherGradleVersions() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiBuildRisks()),
                buildGradle(
                        "dependencies { implementation 'org.apache.logging.log4j:log4j-1.2-api:2.26.0' }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("2.26.0"));
                            assertTrue(after.printAll().contains("目标版本冲突（禁止降级）"));
                        })),
                buildGradleKts(
                        "dependencies { implementation(\"org.apache.logging.log4j:log4j-1.2-api:3.0.0-beta3\") }",
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("3.0.0-beta3"));
                            assertTrue(after.printAll().contains("目标版本冲突（禁止降级）"));
                        })));
    }

    @Test
    void arbitrarilyLargeHigherVersionCannotOverflowOrDowngrade() {
        String version = "999999999999999999999999999999.0.0";
        rewriteRun(
                spec -> spec.recipe(Log4j12ApiTestSupport.activate(
                        Log4j12ApiTestSupport.RECOMMENDED)),
                xml(
                        pom(version),
                        source -> source.path("overflow/pom.xml")
                                .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("<version>" + version + "</version>"));
                            assertTrue(printed.contains("目标版本冲突（禁止降级）"));
                            org.junit.jupiter.api.Assertions.assertFalse(
                                    printed.contains("<version>2.25.5</version>"));
                        })));
    }

    @Test
    void marksOutsideOwnerVariantDuplicatesAndFamilyWithoutMutating() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiBuildRisks()),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                          <properties><bridge.version>[2.17,3)</bridge.version></properties>
                          <dependencies>
                            <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-1.2-api</artifactId><version>${bridge.version}</version></dependency>
                            <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-1.2-api</artifactId><version>2.24.3</version><classifier>sources</classifier></dependency>
                            <dependency><groupId>log4j</groupId><artifactId>log4j</artifactId><version>1.2.17</version></dependency>
                            <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-api</artifactId><version>2.20.0</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains(FindLog4j12ApiBuildRisks.OWNER));
                            assertTrue(printed.contains(FindLog4j12ApiBuildRisks.VARIANT));
                            assertTrue(printed.contains(FindLog4j12ApiBuildRisks.DUPLICATE));
                            assertTrue(printed.contains(FindLog4j12ApiBuildRisks.ALIGNMENT));
                        })));
    }

    @Test
    void marksVersionCatalogOwnerAndSkipsGeneratedCatalog() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiBuildRisks()),
                text(
                        """
                        [versions]
                        log4j = "2.20.0"
                        [libraries]
                        log4j12 = { module = "org.apache.logging.log4j:log4j-1.2-api", version.ref = "log4j" }
                        """,
                        source -> source.path("gradle/libs.versions.toml")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertTrue(after.printAll().contains("version catalog owns")))),
                text(
                        "[libraries]\nlog4j12 = { module = \"org.apache.logging.log4j:log4j-1.2-api\" }\n",
                        source -> source.path("build/generated/libs.versions.toml")));
    }

    @Test
    void marksLog4jPropertiesAtExactEntries() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiConfigurationRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                properties(
                        """
                        log4j.rootLogger=INFO, console
                        log4j.appender.console=example.CustomAppender
                        log4j.appender.console.layout.ConversionPattern=${tenant} %m%n
                        business.value=unchanged
                        """,
                        source -> source.path("src/main/resources/log4j.properties")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("only partially converted"));
                                    assertTrue(printed.contains("custom or implementation-specific class"));
                                    assertTrue(printed.contains("${sys:name}"));
                                    org.junit.jupiter.api.Assertions.assertFalse(
                                            printed.contains("/*~~(business.value"));
                                })));
    }

    @Test
    void marksExplicitCompatibilityProperty() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiConfigurationRisks()),
                properties(
                        "log4j1.compatibility=true\nother.flag=true\n",
                        source -> source.path("src/main/resources/log4j2.system.properties")
                                .after(actual -> actual).afterRecipe(after ->
                                        assertTrue(after.printAll().contains("since 2.24.0")))));
    }

    @Test
    void marksLog4jXmlRootCustomClassInterpolationAndDoctype() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiConfigurationRisks()),
                xml(
                        """
                        <!DOCTYPE log4j:configuration SYSTEM "log4j.dtd">
                        <log4j:configuration xmlns:log4j="http://jakarta.apache.org/log4j/">
                          <appender name="A" class="example.CustomAppender">
                            <param name="File" value="${tenant}.log"/>
                          </appender>
                        </log4j:configuration>
                        """,
                        source -> source.path("src/main/resources/log4j.xml")
                                .after(actual -> actual).afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("only partially converted"));
                                    assertTrue(printed.contains("custom or implementation-specific class"));
                                    assertTrue(printed.contains("${sys:name}"));
                                    assertTrue(printed.contains("external entity"));
                                })));
    }

    @Test
    void configurationRisksSkipGeneratedFilesAndUnrelatedProperties() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiConfigurationRisks()),
                properties("log4j.rootLogger=INFO\n",
                        source -> source.path("target/classes/log4j.properties")),
                properties("business.value=${tenant}\n",
                        source -> source.path("src/main/resources/application.properties")));
    }

    private static String pom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>demo</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>org.apache.logging.log4j</groupId>
                    <artifactId>log4j-1.2-api</artifactId>
                    <version>%s</version>
                  </dependency></dependencies>
                </project>
                """.formatted(version);
    }
}
