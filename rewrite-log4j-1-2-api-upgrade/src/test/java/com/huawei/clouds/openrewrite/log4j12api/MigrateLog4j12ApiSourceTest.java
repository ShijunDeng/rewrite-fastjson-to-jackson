package com.huawei.clouds.openrewrite.log4j12api;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class MigrateLog4j12ApiSourceTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Log4j12ApiTestSupport.activate(Log4j12ApiTestSupport.SAFE_SOURCE))
                .parser(Log4j12ApiTestSupport.parser());
    }

    @Test
    void reusesOfficialSetLevelMigrationForItsExactSafeSurface() {
        // Fixed from openrewrite/rewrite-logging-frameworks@c357a720, Log4j1ToLog4j2Test.loggerSetLevel.
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import org.apache.log4j.Level;
                        import org.apache.log4j.Logger;

                        class Test {
                            void method(Logger logger) {
                                logger.setLevel(Level.INFO);
                            }
                        }
                        """,
                        """
                        import org.apache.logging.log4j.Level;
                        import org.apache.logging.log4j.Logger;
                        import org.apache.logging.log4j.core.config.Configurator;

                        class Test {
                            void method(Logger logger) {
                                Configurator.setLevel(logger, Level.INFO);
                            }
                        }
                        """));
    }

    @Test
    void composesOfficialStaticTargetAndSetLevelLeaves() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import org.apache.log4j.Level;
                        import org.apache.log4j.Logger;

                        class Application {
                            private static final Logger LOG = Logger.getLogger(Application.class);
                            static {
                                LOG.setLevel(Level.WARN);
                            }
                        }
                        """,
                        """
                        import org.apache.logging.log4j.Level;
                        import org.apache.logging.log4j.LogManager;
                        import org.apache.logging.log4j.Logger;
                        import org.apache.logging.log4j.core.config.Configurator;

                        class Application {
                            private static final Logger LOG = LogManager.getLogger(Application.class);
                            static {
                                Configurator.setLevel(LOG, Level.WARN);
                            }
                        }
                        """));
    }

    @Test
    void leavesMixedBackendSpecificSurfaceForReview() {
        rewriteRun(java("""
                import org.apache.log4j.Level;
                import org.apache.log4j.Logger;
                class Mixed {
                    void configure(Logger logger) {
                        logger.info("still on the bridge");
                        logger.setLevel(Level.INFO);
                    }
                }
                """));
    }

    @Test
    void leavesWildcardImportsForReview() {
        rewriteRun(java("""
                import org.apache.log4j.*;
                class Wildcard {
                    void configure(Logger logger) {
                        logger.setLevel(Level.INFO);
                    }
                }
                """));
    }

    @Test
    void leavesSameNamedBusinessTypesAlone() {
        rewriteRun(java("""
                class Level { static final Level INFO = new Level(); }
                class Logger { void setLevel(Level level) {} }
                class Business {
                    void configure(Logger logger) {
                        logger.setLevel(Level.INFO);
                    }
                }
                """));
    }

    @Test
    void skipsGeneratedSource() {
        rewriteRun(java(
                """
                import org.apache.log4j.Level;
                import org.apache.log4j.Logger;
                class Generated {
                    void configure(Logger logger) {
                        logger.setLevel(Level.INFO);
                    }
                }
                """,
                source -> source.path("target/generated-sources/Generated.java")));
    }

    @Test
    void riskMarkerFindsRealApacheGobblinProgrammaticConfiguration() {
        // apache/gobblin@fcfb06b41d041cb797622264cf5322296753fdea,
        // gobblin-aws/src/main/java/org/apache/gobblin/aws/Log4jConfigHelper.java.
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiSourceRisks())
                        .parser(Log4j12ApiTestSupport.parser()),
                java(
                        """
                        import java.util.Properties;
                        import org.apache.log4j.PropertyConfigurator;
                        class Log4jConfigHelper {
                            static void updateLog4jConfiguration(Properties originalProperties) {
                                PropertyConfigurator.configure(originalProperties);
                            }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("since 2.24.0")))));
    }

    @Test
    void realLttngCommentIsANegativeSourceFixture() {
        // lttng/lttng-ust@e65b8914742a2b3aaf8d2fd3c24404b63062b1de,
        // doc/examples/java-log4j/HelloLog4j.java:78. Comments must not become API findings.
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiSourceRisks()),
                java("""
                        class HelloLog4j {
                            void configure(String fileName) {
                                // PropertyConfigurator.configure(fileName);
                            }
                        }
                        """));
    }

    @Test
    void marksBackendCustomComponentAndContextBoundaries() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiSourceRisks())
                        .parser(Log4j12ApiTestSupport.parser()),
                java(
                        """
                        import org.apache.log4j.AppenderSkeleton;
                        import org.apache.log4j.Logger;
                        import org.apache.log4j.MDC;
                        import org.apache.log4j.spi.LoggingEvent;
                        class CustomAppender extends AppenderSkeleton {
                            void configure(Logger logger) {
                                logger.setAdditivity(false);
                                MDC.put("request", "42");
                            }
                            protected void append(LoggingEvent event) {}
                            public void close() {}
                            public boolean requiresLayout() { return false; }
                        }
                        """,
                        source -> source.after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("backend-specific"));
                            assertTrue(printed.contains("limited set of Log4j 1 appenders"));
                            assertTrue(printed.contains("MDC/NDC"));
                        })));
    }

    @Test
    void sourceRiskMarkersAreTwoCycleIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindLog4j12ApiSourceRisks())
                        .parser(Log4j12ApiTestSupport.parser())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import org.apache.log4j.PropertyConfigurator;
                        class Config {
                            void load() {
                                PropertyConfigurator.configure("log4j.properties");
                            }
                        }
                        """,
                        source -> source.after(actual -> actual)));
    }
}
