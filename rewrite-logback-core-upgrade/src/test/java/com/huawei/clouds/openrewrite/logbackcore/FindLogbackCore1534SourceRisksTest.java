package com.huawei.clouds.openrewrite.logbackcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindLogbackCore1534SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindLogbackCore1534SourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath("logback-core"));
    }

    static Stream<Arguments> importRisks() {
        return Stream.of(
                Arguments.of("ch.qos.logback.core.joran.GenericConfigurator",
                        FindLogbackCore1534SourceRisks.JORAN),
                Arguments.of("ch.qos.logback.core.joran.spi.Interpreter",
                        FindLogbackCore1534SourceRisks.JORAN),
                Arguments.of("ch.qos.logback.core.db.DBHelper",
                        FindLogbackCore1534SourceRisks.DATABASE),
                Arguments.of("ch.qos.logback.core.rolling.helper.ArchiveRemover",
                        FindLogbackCore1534SourceRisks.ROLLING_TIME),
                Arguments.of("ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP",
                        FindLogbackCore1534SourceRisks.ROLLING_POLICY),
                Arguments.of("ch.qos.logback.core.net.server.RemoteReceiverServerRunner",
                        FindLogbackCore1534SourceRisks.RECEIVER),
                Arguments.of("ch.qos.logback.core.net.HardenedObjectInputStream",
                        FindLogbackCore1534SourceRisks.DESERIALIZATION),
                Arguments.of("ch.qos.logback.core.net.ObjectWriterFactory",
                        FindLogbackCore1534SourceRisks.DESERIALIZATION),
                Arguments.of("ch.qos.logback.core.boolex.JaninoEventEvaluatorBase",
                        FindLogbackCore1534SourceRisks.JANINO),
                Arguments.of("ch.qos.logback.core.status.ViewStatusMessagesServletBase",
                        FindLogbackCore1534SourceRisks.STATUS),
                Arguments.of("ch.qos.logback.core.net.SMTPAppenderBase",
                        FindLogbackCore1534SourceRisks.JAKARTA),
                Arguments.of("ch.qos.logback.core.net.LoginAuthenticator",
                        FindLogbackCore1534SourceRisks.JAKARTA)
        );
    }

    @ParameterizedTest(name = "marks import {0}")
    @MethodSource("importRisks")
    void marksExactRemovedOrChangedTypeImports(String type, String message) {
        rewriteRun(java("import " + type + ";\nclass Extension {}",
                source -> source.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    @Test
    void marksCustomJoranActionContextRollingAndStatusContracts() {
        rewriteRun(
                java("""
                        import ch.qos.logback.core.joran.action.Action;
                        abstract class CustomAction extends Action {}
                        """, source -> source.path("CustomAction.java").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534SourceRisks.JORAN))),
                java("""
                        import ch.qos.logback.core.Context;
                        abstract class CustomContext implements Context {}
                        """, source -> source.path("CustomContext.java").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534SourceRisks.CONTEXT))),
                java("""
                        import ch.qos.logback.core.rolling.helper.ArchiveRemover;
                        abstract class CustomArchiveRemover implements ArchiveRemover {}
                        """, source -> source.path("CustomArchiveRemover.java").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534SourceRisks.ROLLING_TIME))),
                java("""
                        import ch.qos.logback.core.status.StatusListener;
                        abstract class CustomStatusListener implements StatusListener {}
                        """, source -> source.path("CustomStatusListener.java").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534SourceRisks.STATUS))));
    }

    @Test
    void marksConfigurationWatchListConstructionAndChangedReturnContract() {
        rewriteRun(java("""
                import ch.qos.logback.core.joran.spi.ConfigurationWatchList;

                class Watcher {
                    boolean changed() {
                        ConfigurationWatchList watchList = new ConfigurationWatchList();
                        return watchList.changeDetected();
                    }
                }
                """, source -> source.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(),
                        FindLogbackCore1534SourceRisks.WATCH_LIST))));
    }

    @Test
    void marksRollingLifecycleAndRemovedInternalMethodsAndFields() {
        rewriteRun(
                java("""
                        import ch.qos.logback.core.rolling.RollingFileAppender;
                        class Rolling {
                            void force(RollingFileAppender<Object> appender) {
                                appender.start();
                                appender.rollover();
                            }
                        }
                        """, source -> source.path("Rolling.java").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534SourceRisks.ROLLING_POLICY))),
                java("""
                        import ch.qos.logback.core.util.ContextUtil;
                        class Host {
                            String name() throws Exception {
                                return ContextUtil.getLocalHostName();
                            }
                        }
                        """, source -> source.path("Host.java").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534SourceRisks.REMOVED_INTERNAL))),
                java("""
                        import ch.qos.logback.core.CoreConstants;
                        class CollisionMap {
                            String key = CoreConstants.RFA_FILENAME_PATTERN_COLLISION_MAP;
                        }
                        """, source -> source.path("CollisionMap.java").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(),
                                FindLogbackCore1534SourceRisks.REMOVED_INTERNAL))));
    }

    @Test
    void marksDeserializationMethodBoundary() {
        rewriteRun(java("""
                import ch.qos.logback.core.net.ObjectWriter;
                import java.io.IOException;
                import java.io.Serializable;

                class Wire {
                    void write(ObjectWriter writer, Serializable value) throws IOException {
                        writer.write(value);
                    }
                }
                """, source -> source.after(actual -> actual)
                .afterRecipe(after -> assertContains(after.printAll(),
                        FindLogbackCore1534SourceRisks.DESERIALIZATION))));
    }

    @Test
    void ignoresStablePublicTypesBusinessLookalikesAndComments() {
        rewriteRun(
                java("""
                        import ch.qos.logback.core.Appender;
                        import ch.qos.logback.core.util.FileSize;
                        class Stable {
                            Appender<Object> appender;
                            FileSize size;
                            // ch.qos.logback.core.joran.GenericConfigurator
                        }
                        """, source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))),
                java("""
                        class ConfigurationWatchList {
                            boolean changeDetected() {
                                return false;
                            }
                        }
                        class BusinessWatcher {
                            boolean changed(ConfigurationWatchList list) {
                                return list.changeDetected();
                            }
                        }
                        """, source -> source.path("business/BusinessWatcher.java")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void skipsGeneratedSourceAndMarkersAreIdempotent() {
        rewriteRun(java("import ch.qos.logback.core.db.DBHelper;\nclass Generated {}",
                source -> source.path("target/generated/Generated.java")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("import ch.qos.logback.core.db.DBHelper;\nclass DbExtension {}",
                        source -> source.after(actual -> actual)
                        .afterRecipe(after -> assertCount(after.printAll(),
                                FindLogbackCore1534SourceRisks.DATABASE, 1))));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("~~("), actual);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
