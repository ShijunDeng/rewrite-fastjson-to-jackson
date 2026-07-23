package com.huawei.clouds.openrewrite.log4j12api;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class Log4j12ApiRecommendedRecipeTest implements RewriteTest {
    @Test
    void ownedCoreOptInUpgradesDependencyAndMigratesOfficialSourceInOneTwoCycleRun() {
        rewriteRun(
                spec -> spec.recipe(Log4j12ApiTestSupport.activate(
                                Log4j12ApiTestSupport.WITH_OWNED_CORE))
                        .parser(Log4j12ApiTestSupport.parser())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(
                        pomWithOwnedCore("2.20.0"),
                        pomWithOwnedCore("2.25.5")),
                java(
                        """
                        import org.apache.log4j.Level;
                        import org.apache.log4j.Logger;
                        class Bootstrap {
                            void configure(Logger logger) {
                                logger.setLevel(Level.INFO);
                            }
                        }
                        """,
                        """
                        import org.apache.logging.log4j.Level;
                        import org.apache.logging.log4j.Logger;
                        import org.apache.logging.log4j.core.config.Configurator;

                        class Bootstrap {
                            void configure(Logger logger) {
                                Configurator.setLevel(logger, Level.INFO);
                            }
                        }
                        """));
    }

    @Test
    void safeDefaultNeverIntroducesTheOptionalLog4jCoreConfigurator() {
        rewriteRun(
                spec -> spec.recipe(Log4j12ApiTestSupport.activate(
                                Log4j12ApiTestSupport.RECOMMENDED))
                        .parser(Log4j12ApiTestSupport.parser()),
                pomXml(
                        pom("2.20.0"),
                        pom("2.25.5")),
                java(
                        """
                        import org.apache.log4j.Level;
                        import org.apache.log4j.Logger;
                        class Bootstrap {
                            void configure(Logger logger) {
                                logger.setLevel(Level.INFO);
                            }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("org.apache.log4j"));
                            assertTrue(printed.contains("backend-specific"));
                            org.junit.jupiter.api.Assertions.assertFalse(
                                    printed.contains("Configurator"));
                        })));
    }

    @Test
    void mixedSourceIsNotPartiallyMigratedAndReceivesExactReviewMarker() {
        rewriteRun(
                spec -> spec.recipe(Log4j12ApiTestSupport.activate(Log4j12ApiTestSupport.RECOMMENDED))
                        .parser(Log4j12ApiTestSupport.parser()),
                java(
                        """
                        import org.apache.log4j.Level;
                        import org.apache.log4j.Logger;
                        class Mixed {
                            void configure(Logger logger) {
                                logger.info("business log");
                                logger.setLevel(Level.INFO);
                            }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("org.apache.log4j"));
                            assertTrue(printed.contains("backend-specific"));
                            org.junit.jupiter.api.Assertions.assertFalse(
                                    printed.contains("Configurator.setLevel"));
                        })));
    }

    @Test
    void generatedBuildSourceAndConfigurationStayUntouchedTogether() {
        rewriteRun(
                spec -> spec.recipe(Log4j12ApiTestSupport.activate(Log4j12ApiTestSupport.RECOMMENDED))
                        .parser(Log4j12ApiTestSupport.parser()),
                pomXml(pom("2.20.0"), source -> source.path("target/generated/pom.xml")),
                java(
                        """
                        import org.apache.log4j.Level;
                        import org.apache.log4j.Logger;
                        class Generated {
                            void configure(Logger logger) { logger.setLevel(Level.INFO); }
                        }
                        """,
                        source -> source.path("build/generated/Generated.java")),
                org.openrewrite.properties.Assertions.properties(
                        "log4j.rootLogger=INFO\n",
                        source -> source.path("target/classes/log4j.properties")));
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

    private static String pomWithOwnedCore(String version) {
        return pom(version).replace(
                "</dependencies>",
                """
                    <dependency>
                      <groupId>org.apache.logging.log4j</groupId>
                      <artifactId>log4j-core</artifactId>
                      <version>2.25.5</version>
                    </dependency>
                  </dependencies>""");
    }

}
