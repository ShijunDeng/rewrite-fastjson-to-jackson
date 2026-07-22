package com.huawei.clouds.openrewrite.shedlockspring;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class ShedLockSpringJavaRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindShedLockSpring7JavaRisks()).parser(parser())
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void marksRealSiglusProxySchedulerAndJavaxBaseline() {
        // Reduced from SIGLUS/siglus-api at 79268df6c77c1d2dd9108b0522e14b53e75ac704.
        rewriteRun(java(
                """
                import javax.annotation.PostConstruct;
                import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
                import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_SCHEDULER;

                @EnableSchedulerLock(defaultLockAtMostFor = "PT5M", interceptMode = PROXY_SCHEDULER)
                class Application { @PostConstruct void init() {} }
                """,
                """
                /*~~(ShedLock 7 runs with Spring 6.2/7; migrate this framework-facing javax API to its Jakarta equivalent)~~>*/import javax.annotation.PostConstruct;
                import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
                import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_SCHEDULER;

                /*~~(PROXY_SCHEDULER is deprecated for removal and uses a Spring 6.2 reflection workaround; migrate deliberately to PROXY_METHOD)~~>*/@EnableSchedulerLock(/*~~(lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks)~~>*/defaultLockAtMostFor = "PT5M", interceptMode = PROXY_SCHEDULER)
                class Application { @PostConstruct void init() {} }
                """));
    }

    @Test
    void marksDefaultProxyMethodAndExplicitAdvisorOrder() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
                @EnableSchedulerLock(defaultLockAtMostFor = "10m", order = 100)
                class Config {}
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
                /*~~(ShedLock 7 defaults to PROXY_METHOD; direct calls are locked too, so verify proxyability, self-invocation and advisor order)~~>*/@EnableSchedulerLock(/*~~(lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks)~~>*/defaultLockAtMostFor = "10m", /*~~(Explicit ShedLock advisor order: verify ordering against transactions, retries, async and security advice)~~>*/order = 100)
                class Config {}
                """));
    }

    @Test
    void marksUnproxyableLockedMethodAndExplicitDuration() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                class Jobs {
                    @SchedulerLock(name = "cleanup", lockAtMostFor = "5m")
                    private final void cleanup() {}
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                class Jobs {
                    /*~~(PROXY_METHOD cannot reliably advise private/static/final methods or methods on a final proxy target; make the scheduled boundary proxyable)~~>*/@SchedulerLock(name = "cleanup", /*~~(lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks)~~>*/lockAtMostFor = "5m")
                    private final void cleanup() {}
                }
                """));
    }

    @Test
    void marksSelfInvocationOfLockedMethod() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                class Jobs {
                    @SchedulerLock(name = "cleanup", lockAtMostFor = "5m") public void cleanup() {}
                    public void trigger() { cleanup(); }
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                class Jobs {
                    @SchedulerLock(name = "cleanup", /*~~(lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks)~~>*/lockAtMostFor = "5m") public void cleanup() {}
                    public void trigger() { /*~~(Self-invocation bypasses the Spring PROXY_METHOD advisor; call through a proxied collaborator or move the locked boundary)~~>*/cleanup(); }
                }
                """));
    }

    @Test
    void marksMultipleProvidersAndMissingSelection() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.core.LockProvider;
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                import org.springframework.context.annotation.Bean;
                class Config {
                    @Bean LockProvider jdbc() { return null; }
                    @Bean LockProvider redis() { return null; }
                    @SchedulerLock(name = "cleanup", lockAtMostFor = "5m") public void cleanup() {}
                }
                """,
                """
                import net.javacrumbs.shedlock.core.LockProvider;
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                import org.springframework.context.annotation.Bean;
                class Config {
                    /*~~(Multiple LockProvider beans detected; select one with @Primary or bind each scheduled path using @LockProviderToUse)~~>*/@Bean LockProvider jdbc() { return null; }
                    /*~~(Multiple LockProvider beans detected; select one with @Primary or bind each scheduled path using @LockProviderToUse)~~>*/@Bean LockProvider redis() { return null; }
                    /*~~(Multiple LockProvider beans exist but this lock has no @LockProviderToUse selection; runtime execution can fail)~~>*/@SchedulerLock(name = "cleanup", /*~~(lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks)~~>*/lockAtMostFor = "5m") public void cleanup() {}
                }
                """));
    }

    @Test
    void primaryProviderAvoidsAmbiguousSelectionMarker() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.core.LockProvider;
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Primary;
                class Config {
                    @Bean @Primary LockProvider jdbc() { return null; }
                    @Bean LockProvider redis() { return null; }
                    @SchedulerLock(name = "cleanup", lockAtMostFor = "5m") public void cleanup() {}
                }
                """,
                """
                import net.javacrumbs.shedlock.core.LockProvider;
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Primary;
                class Config {
                    @Bean @Primary LockProvider jdbc() { return null; }
                    @Bean LockProvider redis() { return null; }
                    @SchedulerLock(name = "cleanup", /*~~(lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks)~~>*/lockAtMostFor = "5m") public void cleanup() {}
                }
                """));
    }

    @Test
    void marksProviderSelectionAndDynamicDurations() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.spring.annotation.LockProviderToUse;
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                class Jobs {
                    @LockProviderToUse("jdbc")
                    @SchedulerLock(name = "job-#{#tenant}", lockAtMostFor = "${job.max}", lockAtLeastFor = "-1s")
                    public void run(String tenant) {}
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.LockProviderToUse;
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                class Jobs {
                    /*~~(Verify the selected LockProvider bean name exists for every method, type and package path; resolution fails at execution time)~~>*/@LockProviderToUse("jdbc")
                    @SchedulerLock(/*~~(Dynamic lock name uses a placeholder/SpEL expression; validate parameter names, bean access, uniqueness and failure behavior)~~>*/name = "job-#{#tenant}", /*~~(Duration uses a placeholder/SpEL expression; validate resolution, parse failures, negativity and atLeast <= atMost)~~>*/lockAtMostFor = "${job.max}", /*~~(Invalid ShedLock 7 duration; use a non-negative millisecond, simple-unit or ISO-8601 value)~~>*/lockAtLeastFor = "-1s")
                    public void run(String tenant) {}
                }
                """));
    }

    @Test
    void marksRealReactiveSubscribeInsideLockedMethod() {
        // Reduced from pagopa/pn-address-manager at 0ddf0d65d3e831d05793fc9252d9a49b313695f2.
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                import reactor.core.publisher.Mono;
                class PendingRequestService {
                    @SchedulerLock(name = "cleanStoppedRequest", lockAtMostFor = "5m")
                    public void clean() { Mono.just("batch").subscribe(); }
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                import reactor.core.publisher.Mono;
                class PendingRequestService {
                    @SchedulerLock(name = "cleanStoppedRequest", /*~~(lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks)~~>*/lockAtMostFor = "5m")
                    public void clean() { /*~~(Reactive work is subscribed asynchronously inside the locked method; the lock can be released before the pipeline completes)~~>*/Mono.just("batch").subscribe(); }
                }
                """));
    }

    @Test
    void marksReactiveReturnPrimitiveReturnAndOtherAdvice() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                import org.springframework.transaction.annotation.Transactional;
                import reactor.core.publisher.Mono;
                class Jobs {
                    @Transactional @SchedulerLock(name = "reactive", lockAtMostFor = "1m") public Mono<String> reactive() { return Mono.just("x"); }
                    @SchedulerLock(name = "count", lockAtMostFor = "1m") public int count() { return 1; }
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                import org.springframework.transaction.annotation.Transactional;
                import reactor.core.publisher.Mono;
                class Jobs {
                    /*~~(The proxy locks Publisher/future creation, not necessarily asynchronous execution; move the lock around the actual work and test cancellation/error paths)~~>*//*~~(ShedLock shares this join point with another advisor; integration-test ordering, exception propagation and thread hand-off)~~>*/@Transactional @SchedulerLock(name = "reactive", /*~~(lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks)~~>*/lockAtMostFor = "1m") public Mono<String> reactive() { return Mono.just("x"); }
                    /*~~(ShedLock PROXY_METHOD rejects primitive non-void return types; use void, Optional, or an explicit locking boundary)~~>*/@SchedulerLock(name = "count", /*~~(lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks)~~>*/lockAtMostFor = "1m") public int count() { return 1; }
                }
                """));
    }

    @Test
    void marksKeepAliveManualConfigurationAndProviderErrors() {
        rewriteRun(java(
                """
                import java.time.Duration;
                import java.time.Instant;
                import net.javacrumbs.shedlock.core.LockConfiguration;
                import net.javacrumbs.shedlock.core.LockProvider;
                import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
                class Config {
                    void configure(LockProvider provider) {
                        new KeepAliveLockProvider(provider, null);
                        provider.lock(new LockConfiguration(Instant.now(), "job", Duration.ofMinutes(5), Duration.ZERO));
                    }
                }
                """,
                """
                import java.time.Duration;
                import java.time.Instant;
                import net.javacrumbs.shedlock.core.LockConfiguration;
                import net.javacrumbs.shedlock.core.LockProvider;
                import net.javacrumbs.shedlock.support.KeepAliveLockProvider;
                class Config {
                    void configure(LockProvider provider) {
                        /*~~(KeepAlive extends at the interval midpoint, requires lockAtMostFor >= 30s, an extensible provider and a managed scheduler; test shutdown and extension failure)~~>*/new KeepAliveLockProvider(provider, null);
                        /*~~(LockProvider.lock returns empty when unavailable but ShedLock 7 providers throw LockException on unexpected errors; review retries and catch clauses)~~>*/provider.lock(/*~~(Verify createdAt, Duration bounds and the provider clock for this manual lock configuration)~~>*/new LockConfiguration(Instant.now(), "job", Duration.ofMinutes(5), Duration.ZERO));
                    }
                }
                """));
    }

    @Test
    void marksCustomProviderLegacyConstructorAndExtension() {
        rewriteRun(java(
                """
                import java.time.Duration;
                import java.time.Instant;
                import java.util.Optional;
                import net.javacrumbs.shedlock.core.LockConfiguration;
                import net.javacrumbs.shedlock.core.LockProvider;
                import net.javacrumbs.shedlock.core.SimpleLock;
                import net.javacrumbs.shedlock.core.LockExtender;
                class CustomProvider implements LockProvider {
                    public Optional<SimpleLock> lock(LockConfiguration configuration) { return Optional.empty(); }
                    void use(SimpleLock lock) {
                        new LockConfiguration("job", Instant.now());
                        lock.extend(Duration.ofMinutes(1), Duration.ZERO);
                        LockExtender.extendActiveLock(Duration.ofMinutes(1), Duration.ZERO);
                    }
                }
                """,
                """
                import java.time.Duration;
                import java.time.Instant;
                import java.util.Optional;
                import net.javacrumbs.shedlock.core.LockConfiguration;
                import net.javacrumbs.shedlock.core.LockProvider;
                import net.javacrumbs.shedlock.core.SimpleLock;
                import net.javacrumbs.shedlock.core.LockExtender;
                /*~~(Custom LockProvider: retest LockException propagation, unavailable-lock vs provider-error handling, clock source and extension support)~~>*/class CustomProvider implements LockProvider {
                    public Optional<SimpleLock> lock(LockConfiguration configuration) { return Optional.empty(); }
                    void use(SimpleLock lock) {
                        /*~~(Legacy LockConfiguration constructor detected; ShedLock 7 requires createdAt, name, lockAtMostFor Duration and lockAtLeastFor Duration)~~>*/new LockConfiguration("job", Instant.now());
                        /*~~(Lock extension replaces the active SimpleLock and uses thread-local context; verify one-time unlock, provider support and thread hand-off)~~>*/lock.extend(Duration.ofMinutes(1), Duration.ZERO);
                        /*~~(Lock extension replaces the active SimpleLock and uses thread-local context; verify one-time unlock, provider support and thread hand-off)~~>*/LockExtender.extendActiveLock(Duration.ofMinutes(1), Duration.ZERO);
                    }
                }
                """));
    }

    @Test
    void marksVirtualThreadHandOff() {
        rewriteRun(java(
                """
                import java.util.concurrent.Executors;
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                class Jobs {
                    @SchedulerLock(name = "virtual", lockAtMostFor = "1m")
                    public void run() { Executors.newVirtualThreadPerTaskExecutor().submit(() -> {}); }
                }
                """,
                """
                import java.util.concurrent.Executors;
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
                class Jobs {
                    @SchedulerLock(name = "virtual", /*~~(lockAtMostFor is a crash safety bound, not a timeout; keep it above worst-case execution and account for provider clocks)~~>*/lockAtMostFor = "1m")
                    public void run() { /*~~(Virtual-thread hand-off detected; verify the lock covers actual task completion and do not rely on LockAssert/LockExtender ThreadLocal state across threads)~~>*/Executors.newVirtualThreadPerTaskExecutor().submit(() -> {}); }
                }
                """));
    }

    @Test
    void leavesLockAssertAndUnrelatedJavaUntouched() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.core.LockAssert;
                class Jobs { void verify() { LockAssert.assertLocked(); } }
                """));
    }

    private static JavaParser.Builder<?, ?> parser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package net.javacrumbs.shedlock.spring.annotation;
                public @interface SchedulerLock { String name() default ""; String lockAtMostFor() default ""; String lockAtLeastFor() default ""; }
                """,
                """
                package net.javacrumbs.shedlock.spring.annotation;
                public @interface EnableSchedulerLock {
                    enum InterceptMode { PROXY_SCHEDULER, PROXY_METHOD }
                    InterceptMode interceptMode() default InterceptMode.PROXY_METHOD;
                    String defaultLockAtMostFor(); String defaultLockAtLeastFor() default "PT0S"; int order() default Integer.MAX_VALUE;
                }
                """,
                """
                package net.javacrumbs.shedlock.spring.annotation;
                public @interface LockProviderToUse { String value(); }
                """,
                """
                package org.springframework.context.annotation;
                public @interface Bean { String value() default ""; }
                """,
                """
                package org.springframework.context.annotation;
                public @interface Primary {}
                """,
                """
                package org.springframework.transaction.annotation;
                public @interface Transactional {}
                """,
                """
                package javax.annotation;
                public @interface PostConstruct {}
                """,
                """
                package net.javacrumbs.shedlock.core;
                import java.util.Optional;
                public interface LockProvider { Optional<SimpleLock> lock(LockConfiguration configuration); }
                """,
                """
                package net.javacrumbs.shedlock.core;
                import java.time.Duration;
                import java.util.Optional;
                public interface SimpleLock { void unlock(); Optional<SimpleLock> extend(Duration atMost, Duration atLeast); }
                """,
                """
                package net.javacrumbs.shedlock.core;
                import java.time.Duration;
                import java.time.Instant;
                public class LockConfiguration {
                    public LockConfiguration(String name, Instant atMost) {}
                    public LockConfiguration(Instant createdAt, String name, Duration atMost, Duration atLeast) {}
                }
                """,
                """
                package net.javacrumbs.shedlock.core;
                import java.time.Duration;
                public final class LockExtender { public static void extendActiveLock(Duration atMost, Duration atLeast) {} }
                """,
                """
                package net.javacrumbs.shedlock.core;
                public final class LockAssert { public static void assertLocked() {} }
                """,
                """
                package net.javacrumbs.shedlock.support;
                import java.util.Optional;
                import java.util.concurrent.ScheduledExecutorService;
                import net.javacrumbs.shedlock.core.*;
                public class KeepAliveLockProvider implements LockProvider {
                    public KeepAliveLockProvider(LockProvider provider, ScheduledExecutorService executor) {}
                    public Optional<SimpleLock> lock(LockConfiguration configuration) { return Optional.empty(); }
                }
                """,
                """
                package reactor.core.publisher;
                public class Mono<T> {
                    public static <T> Mono<T> just(T value) { return new Mono<>(); }
                    public void subscribe() {}
                }
                """);
    }
}
