package com.huawei.clouds.openrewrite.springretry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class SpringRetrySourceRisksTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.springretry.FindSpringRetry2_0SourceRisks";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(SpringRetryTestSupport.recipe(RECIPE)).parser(SpringRetryTestSupport.parser());
    }

    @Test
    void marksRetryableProxyExpressionAndStatefulBoundariesTogether() {
        rewriteRun(marked("""
                import java.io.IOException;
                import org.springframework.retry.annotation.Backoff;
                import org.springframework.retry.annotation.Retryable;
                class Client {
                    @Retryable(
                        stateful = true,
                        include = IOException.class,
                        maxAttemptsExpression = "#{${client.retry.attempts:3}}",
                        exceptionExpression = "@classifier.canRetry(#root)",
                        backoff = @Backoff(delayExpression = "${client.retry.delay:100}"))
                    void call() {}
                }
                """,
                FindSpringRetry20SourceRisks.PROXY_RECOVERY,
                FindSpringRetry20SourceRisks.EXPRESSION,
                FindSpringRetry20SourceRisks.STATEFUL_CACHE));
    }

    @Test
    void marksCircuitBreakerExpressionsAndRecoveryMethod() {
        rewriteRun(marked("""
                import org.springframework.retry.annotation.CircuitBreaker;
                import org.springframework.retry.annotation.Recover;
                class Client {
                    @CircuitBreaker(
                        openTimeoutExpression = "#{${client.open:5000}}",
                        resetTimeoutExpression = "${client.reset:20000}")
                    String call() { return "ok"; }
                    @Recover String recover(RuntimeException failure) { return "fallback"; }
                }
                """,
                FindSpringRetry20SourceRisks.PROXY_RECOVERY,
                FindSpringRetry20SourceRisks.EXPRESSION));
    }

    @Test
    void marksEnableRetryProxyBoundary() {
        rewriteRun(marked("""
                import org.springframework.retry.annotation.EnableRetry;
                @EnableRetry(proxyTargetClass = true)
                class RetryConfiguration {
                }
                """, FindSpringRetry20SourceRisks.PROXY_RECOVERY));
    }

    @Test
    void marksRetryListenerDefaultMethodsAndOnSuccessBoundary() {
        rewriteRun(marked("""
                import org.springframework.retry.RetryCallback;
                import org.springframework.retry.RetryContext;
                import org.springframework.retry.RetryListener;
                class AuditListener implements RetryListener {
                    public <T, E extends Throwable> boolean open(
                            RetryContext context, RetryCallback<T, E> callback) { return true; }
                    public <T, E extends Throwable> void onError(
                            RetryContext context, RetryCallback<T, E> callback, Throwable failure) {}
                    public <T, E extends Throwable> void close(
                            RetryContext context, RetryCallback<T, E> callback, Throwable failure) {}
                }
                """, FindSpringRetry20SourceRisks.LISTENER));
    }

    @Test
    void marksRetryListenerSupportWithoutBlindTypeRewrite() {
        rewriteRun(marked("""
                import org.springframework.retry.RetryCallback;
                import org.springframework.retry.RetryContext;
                import org.springframework.retry.listener.RetryListenerSupport;
                class AuditListener extends RetryListenerSupport {
                    @Override
                    public <T, E extends Throwable> void onError(
                            RetryContext context, RetryCallback<T, E> callback, Throwable failure) {}
                }
                """,
                FindSpringRetry20SourceRisks.LISTENER_SUPPORT,
                FindSpringRetry20SourceRisks.LISTENER));
    }

    @Test
    void marksRetryTemplateListenerRegistrationAndOrdering() {
        rewriteRun(marked("""
                import org.springframework.retry.RetryListener;
                import org.springframework.retry.support.RetryTemplate;
                class Configuration {
                    void configure(RetryTemplate template, RetryListener listener) {
                        template.registerListener(listener, 0);
                        template.setListeners(new RetryListener[] { listener });
                    }
                }
                """, FindSpringRetry20SourceRisks.LISTENER));
    }

    @Test
    void marksRemovedTwoArgumentRethrowOverride() {
        rewriteRun(marked("""
                import org.springframework.retry.RetryContext;
                import org.springframework.retry.support.RetryTemplate;
                class LegacyTemplate extends RetryTemplate {
                    @Override
                    protected <E extends Throwable> void rethrow(
                            RetryContext context, String message) throws E {
                        super.rethrow(context, message);
                    }
                }
                """, FindSpringRetry20SourceRisks.REMOVED_RETHROW));
    }

    @Test
    void marksRetryConfigurationBuildAdviceReturnBreak() {
        rewriteRun(marked("""
                import org.aopalliance.aop.Advice;
                import org.springframework.retry.annotation.RetryConfiguration;
                class LegacyConfiguration extends RetryConfiguration {
                    @Override
                    protected Advice buildAdvice() {
                        return super.buildAdvice();
                    }
                }
                """, FindSpringRetry20SourceRisks.ADVICE_RETURN));
    }

    @ParameterizedTest(name = "policy/backoff boundary {0}")
    @MethodSource("policySources")
    void marksPolicyBackoffAndTimeoutBoundaries(String label, String source) {
        rewriteRun(markedAtPath(source, FindSpringRetry20SourceRisks.BACKOFF_POLICY,
                label + "/Policy.java"));
    }

    static Stream<Arguments> policySources() {
        return Stream.of(
                Arguments.of("simple-policy", """
                        import org.springframework.retry.policy.SimpleRetryPolicy;
                        class Policy { Object value = new SimpleRetryPolicy(5); }
                        """),
                Arguments.of("timeout-policy", """
                        import org.springframework.retry.policy.TimeoutRetryPolicy;
                        class Policy { Object value = new TimeoutRetryPolicy(1000); }
                        """),
                Arguments.of("fixed-backoff", """
                        import org.springframework.retry.backoff.FixedBackOffPolicy;
                        class Policy { Object value = new FixedBackOffPolicy(); }
                        """),
                Arguments.of("exponential-backoff", """
                        import org.springframework.retry.backoff.ExponentialBackOffPolicy;
                        class Policy { Object value = new ExponentialBackOffPolicy(); }
                        """),
                Arguments.of("builder-timeout", """
                        import org.springframework.retry.support.RetryTemplate;
                        class Policy { Object value = RetryTemplate.builder().withinMillis(1000).build(); }
                        """),
                Arguments.of("template-execute", """
                        import org.springframework.retry.support.RetryTemplate;
                        class Policy { Object value = new RetryTemplate().execute(context -> "ok"); }
                        """));
    }

    @Test
    void marksStatefulContextCachesAndRetryState() {
        rewriteRun(marked("""
                import org.springframework.retry.policy.MapRetryContextCache;
                import org.springframework.retry.support.DefaultRetryState;
                import org.springframework.retry.support.RetryTemplate;
                class StatefulConfiguration {
                    void configure(RetryTemplate template) {
                        template.setRetryContextCache(new MapRetryContextCache(100));
                        new DefaultRetryState("business-key");
                    }
                }
                """, FindSpringRetry20SourceRisks.STATEFUL_CACHE));
    }

    @Test
    void marksStatisticsListenerHierarchyBoundary() {
        rewriteRun(marked("""
                import org.springframework.retry.stats.DefaultStatisticsRepository;
                import org.springframework.retry.stats.StatisticsListener;
                class Stats {
                    Object listener = new StatisticsListener(new DefaultStatisticsRepository());
                }
                """, FindSpringRetry20SourceRisks.STATISTICS));
    }

    @Test
    void marksMicrometerMetricsRetryListenerEvenWhenOptionalTypeIsNotOnLegacyClasspath() {
        rewriteRun(marked("""
                import org.springframework.retry.support.MetricsRetryListener;
                class MetricsConfiguration {
                }
                """, FindSpringRetry20SourceRisks.METRICS));
    }

    @Test
    void marksSerializedRetryPolicyBoundary() {
        rewriteRun(marked("""
                import org.springframework.retry.policy.SimpleRetryPolicy;
                class PersistedPolicy extends SimpleRetryPolicy {
                    private static final long serialVersionUID = 1L;
                }
                """, FindSpringRetry20SourceRisks.SERIALIZATION));
    }

    @Test
    void sameNamedApplicationTypesAndMethodsAreNotMarked() {
        rewriteRun(java("""
                @interface Retryable { boolean stateful() default false; }
                class RetryTemplate {
                    void registerListener(Object value) {}
                    void withinMillis(long value) {}
                }
                class Local {
                    @Retryable(stateful = true)
                    void call(RetryTemplate template) {
                        template.registerListener(this);
                        template.withinMillis(1);
                    }
                }
                """));
    }

    @Test
    void generatedAndCacheJavaSourcesAreIgnored() {
        rewriteRun(
                java("""
                        import org.springframework.retry.listener.RetryListenerSupport;
                        class Generated extends RetryListenerSupport {}
                        """, source -> source.path("build/generated/sources/Generated.java")),
                java("""
                        import org.springframework.retry.policy.SimpleRetryPolicy;
                        class Cached { Object policy = new SimpleRetryPolicy(); }
                        """, source -> source.path(".m2/cache/Cached.java")));
    }

    @Test
    void processesOneOpsListenerFixture() throws Exception {
        rewriteRun(marked(fixture("oneops-listener.java"),
                FindSpringRetry20SourceRisks.LISTENER_SUPPORT,
                FindSpringRetry20SourceRisks.LISTENER));
    }

    @Test
    void processesNetflixGenieExpressionFixture() throws Exception {
        rewriteRun(marked(fixture("netflix-genie-retryable.java"),
                FindSpringRetry20SourceRisks.PROXY_RECOVERY,
                FindSpringRetry20SourceRisks.EXPRESSION));
    }

    private static org.openrewrite.test.SourceSpecs marked(String source, String... markers) {
        return marked(source, markers, "Source.java");
    }

    private static org.openrewrite.test.SourceSpecs markedAtPath(String source, String marker, String path) {
        return marked(source, new String[]{marker}, path);
    }

    private static org.openrewrite.test.SourceSpecs marked(String source, String[] markers, String path) {
        return java(source, spec -> spec.path(path).after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            for (String marker : markers) assertTrue(printed.contains(marker), printed);
        }));
    }

    private static String fixture(String name) throws IOException, URISyntaxException {
        return Files.readString(Path.of(SpringRetrySourceRisksTest.class
                .getResource("/fixtures/real/" + name).toURI()));
    }
}
