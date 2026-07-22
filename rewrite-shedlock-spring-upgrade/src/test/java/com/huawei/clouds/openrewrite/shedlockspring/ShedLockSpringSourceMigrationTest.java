package com.huawei.clouds.openrewrite.shedlockspring;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ShedLockSpringSourceMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(UpgradeShedLockSpringTest.environment()
                        .activateRecipes(UpgradeShedLockSpringTest.ANNOTATION_RECIPE))
                .parser(parser()).typeValidationOptions(TypeValidation.none());
    }

    @Test
    void migratesRealEpamLegacyImportAndStringDurations() {
        // Reduced from epam/cloud-pipeline at 17daf5f68ba893b067c6846a5dfaba93f8f964bc.
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.core.SchedulerLock;

                class AccessCodeCleaner {
                    @SchedulerLock(name = "AccessCodeCleaner_monitor", lockAtMostForString = "PT10M", lockAtLeastForString = "PT1M")
                    void monitor() {}
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

                class AccessCodeCleaner {
                    @SchedulerLock(name = "AccessCodeCleaner_monitor", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
                    void monitor() {}
                }
                """));
    }

    @Test
    void migratesRealToxiproxyPlaceholderDuration() {
        // Reduced from buckle/toxiproxy-frontend at ddddc3a1552dba0c75c85c31ed68bb68070d0605.
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.core.SchedulerLock;
                class BackupChecker {
                    @SchedulerLock(name = "BACKUP_CHECKER", lockAtMostForString = "${backup.cadence}")
                    public void check() {}
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

                class BackupChecker {
                    @SchedulerLock(name = "BACKUP_CHECKER", lockAtMostFor = "${backup.cadence}")
                    public void check() {}
                }
                """));
    }

    @Test
    void convertsLegacyMillisecondLiteralsToStrings() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.core.SchedulerLock;
                class Cleanup {
                    @SchedulerLock(name = "cleanup", lockAtMostFor = 120_000L, lockAtLeastFor = 5000)
                    void run() {}
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

                class Cleanup {
                    @SchedulerLock(name = "cleanup", lockAtMostFor = "120000", lockAtLeastFor = "5000")
                    void run() {}
                }
                """));
    }

    @Test
    void preservesLegacyNumericPrecedenceAndNegativeFallback() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.core.SchedulerLock;
                class Cleanup {
                    @SchedulerLock(name = "cleanup", lockAtMostFor = 120000, lockAtMostForString = "PT1M", lockAtLeastFor = -1, lockAtLeastForString = "PT10S")
                    void run() {}
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

                class Cleanup {
                    @SchedulerLock(name = "cleanup", lockAtMostFor = "120000", lockAtLeastFor = "PT10S")
                    void run() {}
                }
                """));
    }

    @Test
    void renamesShedLockTwoModeToInterceptMode() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
                import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_SCHEDULER;

                @EnableSchedulerLock(defaultLockAtMostFor = "PT5M", mode = PROXY_SCHEDULER)
                class SchedulingConfig {}
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
                import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_SCHEDULER;

                @EnableSchedulerLock(defaultLockAtMostFor = "PT5M", interceptMode = PROXY_SCHEDULER)
                class SchedulingConfig {}
                """));
    }

    @Test
    void preservesCurrentSpringAdviceModeAndInterceptMode() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
                import org.springframework.context.annotation.AdviceMode;
                import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_METHOD;

                @EnableSchedulerLock(defaultLockAtMostFor = "PT5M", interceptMode = PROXY_METHOD, mode = AdviceMode.PROXY)
                class SchedulingConfig {}
                """));
    }

    @Test
    void marksNonLiteralLegacyMillisecondsInsteadOfGuessing() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.core.SchedulerLock;
                class Cleanup {
                    static final long MAX = 120_000;
                    @SchedulerLock(name = "cleanup", lockAtMostFor = MAX)
                    void run() {}
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

                class Cleanup {
                    static final long MAX = 120_000;
                    /*~~(Legacy numeric lock duration is not a literal; convert its millisecond value to an explicit ShedLock 7 duration string)~~>*/@SchedulerLock(name = "cleanup", lockAtMostFor = MAX)
                    void run() {}
                }
                """));
    }

    @Test
    void preservesCurrentAnnotationAndUnrelatedAnnotation() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                @interface Other { long lockAtMostFor(); }
                class Cleanup {
                    @SchedulerLock(name = "cleanup", lockAtMostFor = "2m") void run() {}
                    @Other(lockAtMostFor = 120000) void other() {}
                }
                """));
    }

    private static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package net.javacrumbs.shedlock.core;
                public @interface SchedulerLock {
                    String name() default "";
                    long lockAtMostFor() default -1;
                    String lockAtMostForString() default "";
                    long lockAtLeastFor() default -1;
                    String lockAtLeastForString() default "";
                }
                """,
                """
                package net.javacrumbs.shedlock.spring.annotation;
                public @interface SchedulerLock {
                    String name() default "";
                    String lockAtMostFor() default "";
                    String lockAtLeastFor() default "";
                }
                """,
                """
                package org.springframework.context.annotation;
                public enum AdviceMode { PROXY, ASPECTJ }
                """,
                """
                package net.javacrumbs.shedlock.spring.annotation;
                import org.springframework.context.annotation.AdviceMode;
                public @interface EnableSchedulerLock {
                    enum InterceptMode { PROXY_SCHEDULER, PROXY_METHOD }
                    InterceptMode interceptMode() default InterceptMode.PROXY_METHOD;
                    AdviceMode mode() default AdviceMode.PROXY;
                    String defaultLockAtMostFor();
                    String defaultLockAtLeastFor() default "PT0S";
                    int order() default Integer.MAX_VALUE;
                }
                """);
    }
}
