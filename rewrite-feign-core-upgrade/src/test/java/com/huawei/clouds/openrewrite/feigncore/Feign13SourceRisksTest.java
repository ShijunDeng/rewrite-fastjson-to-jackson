package com.huawei.clouds.openrewrite.feigncore;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class Feign13SourceRisksTest implements RewriteTest {
    private static final String RETRY_AFTER =
            "RetryableException.retryAfter() returns epoch milliseconds (Long) in Feign 13 instead of Date; preserve null handling and choose Date/Instant/duration semantics at this exact use";
    private static final String QUERY_MAP =
            "@QueryMap(encoded=true) no longer controls query-map encoding; choose a QueryMapEncoder and verify percent encoding, nulls, collections, nested values, and already-encoded input";
    private static final String BUILDER =
            "Feign 13 BaseBuilder has two type parameters and final build() delegates to internalBuild(); adapt this custom builder explicitly and verify capability enrichment, cloning, and interceptor ordering";
    private static final String RESPONSE_INTERCEPTOR =
            "Feign 13 stores a chain of response interceptors; repeated responseInterceptor calls append instead of replacing the previous interceptor, so verify ordering, short-circuiting, decoding, and exception behavior";
    private static final String CONTRACT =
            "Custom Feign Contract extension detected; recompile against 13.6 and verify inherited annotations, parameter processors, default/static methods, validation warnings, and QueryMap encoding";
    private static final String RETRYER =
            "Custom retry extension detected; Feign 13 uses nullable epoch milliseconds for retry-after, so verify clock arithmetic, backoff, interruption, clone state, maximum attempts, and propagation policy";
    private static final String CONSTRUCTOR =
            "This deprecated RetryableException constructor accepts Date only for compatibility; migrate the retry-after argument to nullable epoch milliseconds without changing no-retry/null or clock semantics";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindFeign13SourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath("feign-core"));
    }

    @Test
    void marksAmbiguousRetryAfterDateUse() {
        rewriteRun(java(
                """
                import feign.RetryableException;
                import java.util.Date;
                class Backoff { Date next(RetryableException failure) { return failure.retryAfter(); } }
                """,
                """
                import feign.RetryableException;
                import java.util.Date;
                class Backoff { Date next(RetryableException failure) { return /*~~(%s)~~>*/failure.retryAfter(); } }
                """.formatted(RETRY_AFTER)));
    }

    @Test
    void marksDateRetryableExceptionConstructor() {
        rewriteRun(java(
                """
                import feign.Request;
                import feign.RetryableException;
                import java.util.Date;
                class ErrorFactory {
                    RetryableException create(Request request) {
                        return new RetryableException(503, "busy", Request.HttpMethod.GET, new Date(), request);
                    }
                }
                """,
                """
                import feign.Request;
                import feign.RetryableException;
                import java.util.Date;
                class ErrorFactory {
                    RetryableException create(Request request) {
                        return /*~~(%s)~~>*/new RetryableException(503, "busy", Request.HttpMethod.GET, new Date(), request);
                    }
                }
                """.formatted(CONSTRUCTOR)));
    }

    @Test
    void marksEncodedQueryMapAtAnnotation() {
        rewriteRun(java(
                """
                import feign.QueryMap;
                import feign.RequestLine;
                import java.util.Map;
                interface SearchApi { @RequestLine("GET /search") String find(@QueryMap(encoded = true) Map<String, Object> query); }
                """,
                """
                import feign.QueryMap;
                import feign.RequestLine;
                import java.util.Map;
                interface SearchApi { @RequestLine("GET /search") String find(/*~~(%s)~~>*/@QueryMap(encoded = true) Map<String, Object> query); }
                """.formatted(QUERY_MAP)));
    }

    @Test
    void encodedFalseQueryMapIsNotMarked() {
        rewriteRun(java(
                """
                import feign.QueryMap;
                import java.util.Map;
                interface SearchApi { String find(@QueryMap Map<String, Object> query); }
                """));
    }

    @Test
    void marksCustomFeignBuilderExtensionAtBaseType() {
        rewriteRun(java(
                """
                import feign.Feign;
                class AuditedBuilder extends Feign.Builder { }
                """,
                """
                import feign.Feign;
                class AuditedBuilder extends /*~~(%s)~~>*/Feign.Builder { }
                """.formatted(BUILDER)));
    }

    @Test
    void marksResponseInterceptorConfigurationAtExactCall() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package feign;
                        public abstract class BaseBuilder<B extends BaseBuilder<B>> {
                            public B responseInterceptor(ResponseInterceptor interceptor) { return null; }
                        }
                        """,
                        """
                        package feign;
                        public interface ResponseInterceptor { }
                        """)),
                java(
                        """
                        import feign.BaseBuilder;
                        import feign.ResponseInterceptor;
                        abstract class ClientBuilder extends BaseBuilder<ClientBuilder> {
                            void configure(ResponseInterceptor audit, ResponseInterceptor metrics) {
                                responseInterceptor(audit).responseInterceptor(metrics);
                            }
                        }
                        """,
                        """
                        import feign.BaseBuilder;
                        import feign.ResponseInterceptor;
                        abstract class ClientBuilder extends /*~~(%s)~~>*/BaseBuilder<ClientBuilder> {
                            void configure(ResponseInterceptor audit, ResponseInterceptor metrics) {
                                /*~~(%s)~~>*/responseInterceptor(audit).responseInterceptor(metrics);
                            }
                        }
                        """.formatted(BUILDER, RESPONSE_INTERCEPTOR)));
    }

    @Test
    void marksCustomContractAtImplementedType() {
        rewriteRun(java(
                """
                import feign.Contract;
                import feign.MethodMetadata;
                import java.util.List;
                class OwnedContract implements Contract {
                    public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) { return List.of(); }
                }
                """,
                """
                import feign.Contract;
                import feign.MethodMetadata;
                import java.util.List;
                class OwnedContract implements /*~~(%s)~~>*/Contract {
                    public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) { return List.of(); }
                }
                """.formatted(CONTRACT)));
    }

    @Test
    void marksCustomRetryerAtImplementedType() {
        rewriteRun(java(
                """
                import feign.RetryableException;
                import feign.Retryer;
                class OwnedRetryer implements Retryer {
                    public void continueOrPropagate(RetryableException e) { throw e; }
                    public Retryer clone() { return this; }
                }
                """,
                """
                import feign.RetryableException;
                import feign.Retryer;
                class OwnedRetryer implements /*~~(%s)~~>*/Retryer {
                    public void continueOrPropagate(RetryableException e) { throw e; }
                    public Retryer clone() { return this; }
                }
                """.formatted(RETRYER)));
    }

    @Test
    void sameNamedApplicationTypesAreNoop() {
        rewriteRun(java(
                """
                import java.util.Date;
                class LocalFailure { Date retryAfter() { return new Date(); } }
                class LocalBuilder { LocalBuilder responseInterceptor(Object value) { return this; } }
                """));
    }

    @Test
    void generatedCachesAndLeafFilenameAreFilteredCorrectly() {
        rewriteRun(
                java("""
                        import feign.RetryableException;
                        class Cached { Object read(RetryableException e) { return e.retryAfter(); } }
                        """, source -> source.path(".gradle/generated-cache/Cached.java")),
                java("""
                        import feign.RetryableException;
                        class Installed { Object read(RetryableException e) { return e.retryAfter(); } }
                        """, source -> source.path("installations/output/Installed.java")),
                java(
                        """
                        import feign.RetryableException;
                        class install { Object read(RetryableException e) { return e.retryAfter(); } }
                        """,
                        """
                        import feign.RetryableException;
                        class install { Object read(RetryableException e) { return /*~~(%s)~~>*/e.retryAfter(); } }
                        """.formatted(RETRY_AFTER), source -> source.path("install.java")));
    }
}
