package com.huawei.clouds.openrewrite.nettycodechttp2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.stream.Stream;

import static org.openrewrite.java.Assertions.java;

class MigrateDeprecatedHttp2ConstructorsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDeprecatedHttp2Constructors())
                .parser(JavaParser.fromJavaVersion().dependsOn(NettyCodecHttp2TestApi.sources()));
    }

    @ParameterizedTest(name = "behavior-preserving constructor {0}")
    @MethodSource("safeConstructorMigrations")
    void migratesOnlyConstructorsWithTargetEquivalentDefaults(String label, String before, String after) {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.*;
                class Pipeline {
                    Object configure(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                                     Http2PromisedRequestVerifier v, Http2FrameListener l) {
                        return %s;
                    }
                }
                """.formatted(before), """
                import io.netty.handler.codec.http2.*;
                class Pipeline {
                    Object configure(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                                     Http2PromisedRequestVerifier v, Http2FrameListener l) {
                        return %s;
                    }
                }
                """.formatted(after)));
    }

    static Stream<Arguments> safeConstructorMigrations() {
        return Stream.of(
                Arguments.of("connection decoder adds validateHeaders",
                        "new DefaultHttp2ConnectionDecoder(c, e, r, v, true, true)",
                        "new DefaultHttp2ConnectionDecoder(c, e, r, v, true, true, true)"),
                Arguments.of("two-argument decompressor adds unbounded maxAllocation",
                        "new DelegatingDecompressorFrameListener(c, l)",
                        "new DelegatingDecompressorFrameListener(c, l, 0)"),
                Arguments.of("strict decompressor adds unbounded maxAllocation",
                        "new DelegatingDecompressorFrameListener(c, l, true)",
                        "new DelegatingDecompressorFrameListener(c, l, true, 0)"),
                Arguments.of("lenient decompressor adds unbounded maxAllocation",
                        "new DelegatingDecompressorFrameListener(c, l, false)",
                        "new DelegatingDecompressorFrameListener(c, l, false, 0)"),
                Arguments.of("literal no-op Huffman capacity is removed",
                        "new DefaultHttp2HeadersDecoder(true, 8192L, 32)",
                        "new DefaultHttp2HeadersDecoder(true, 8192L)"));
    }

    @Test
    void appendingDefaultsPreservesOriginalExpressionOrderAndEvaluationCount() {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.*;
                class Pipeline {
                    Http2Connection c(){ return null; }
                    Http2ConnectionEncoder e(){ return null; }
                    Http2FrameReader r(){ return null; }
                    Http2PromisedRequestVerifier v(){ return null; }
                    boolean option(){ return true; }
                    Object decoder() {
                        return new DefaultHttp2ConnectionDecoder(c(), e(), r(), v(), option(), option());
                    }
                }
                """, """
                import io.netty.handler.codec.http2.*;
                class Pipeline {
                    Http2Connection c(){ return null; }
                    Http2ConnectionEncoder e(){ return null; }
                    Http2FrameReader r(){ return null; }
                    Http2PromisedRequestVerifier v(){ return null; }
                    boolean option(){ return true; }
                    Object decoder() {
                        return new DefaultHttp2ConnectionDecoder(c(), e(), r(), v(), option(), option(), true);
                    }
                }
                """));
    }

    @Test
    void targetConstructorsAndSemanticallyDifferentOverloadsAreNoop() {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.*;
                class Pipeline {
                    Object configure(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                                     Http2PromisedRequestVerifier v, Http2FrameListener l) {
                        Object a = new DefaultHttp2ConnectionDecoder(c, e, r, v, true, true, false);
                        Object b = new DefaultHttp2ConnectionDecoder(c, e, r, v, true, true, false, false);
                        Object d = new DelegatingDecompressorFrameListener(c, l, 4096);
                        Object e1 = new DelegatingDecompressorFrameListener(c, l, true, 4096);
                        Object h = new DefaultHttp2HeadersDecoder(true, false, 8192L);
                        return a;
                    }
                }
                """));
    }

    @ParameterizedTest(name = "observable Huffman argument {0}")
    @MethodSource("observableHuffmanArguments")
    void doesNotDropObservableOrNonLiteralHuffmanArgument(String label, String expression, String helper) {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
                class Pipeline {
                    %s
                    Object decoder() {
                        return new DefaultHttp2HeadersDecoder(true, 8192L, %s);
                    }
                }
                """.formatted(helper, expression)));
    }

    static Stream<Arguments> observableHuffmanArguments() {
        return Stream.of(
                Arguments.of("variable", "capacity", "int capacity = 32;"),
                Arguments.of("method invocation", "capacity()", "int capacity(){ return 32; }"),
                Arguments.of("post-increment", "capacity++", "int capacity = 32;"),
                Arguments.of("cast expression", "(int) 32L", ""));
    }

    @Test
    void signedIntegerLiteralIsAlsoSafeToRemove() {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
                class Pipeline {
                    Object decoder() {
                        return new DefaultHttp2HeadersDecoder(true, 8192L, -1);
                    }
                }
                """, """
                import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
                class Pipeline {
                    Object decoder() {
                        return new DefaultHttp2HeadersDecoder(true, 8192L);
                    }
                }
                """));
    }

    @Test
    void localLookalikesAndAnonymousSubclassesAreNoop() {
        rewriteRun(spec -> spec.typeValidationOptions(TypeValidation.none()),
                java("""
                        class DelegatingDecompressorFrameListener {
                            DelegatingDecompressorFrameListener(Object c, Object l) {}
                        }
                        class LocalPipeline {
                            Object x = new DelegatingDecompressorFrameListener(null, null);
                        }
                        """),
                java("""
                        import io.netty.handler.codec.http2.*;
                        class AnonymousPipeline {
                            Object x(Http2Connection c, Http2FrameListener l) {
                                return new DelegatingDecompressorFrameListener(c, l) {};
                            }
                        }
                        """));
    }

    @Test
    void generatedSourcesAreNeverChanged() {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.*;
                class GeneratedPipeline {
                    Object x(Http2Connection c, Http2FrameListener l) {
                        return new DelegatingDecompressorFrameListener(c, l);
                    }
                }
                """, source -> source.path("target/generated-sources/annotations/GeneratedPipeline.java")));
    }

    @Test
    void migratesFixedApacheCxfFixture() {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.*;
                class NettyHttpClientPipelineFactory {
                    private final int maxContentLength = 1048576;
                    Http2FrameListener listener(Http2Connection connection) {
                        return new DelegatingDecompressorFrameListener(connection,
                                new InboundHttp2ToHttpAdapterBuilder(connection)
                                        .maxContentLength(maxContentLength)
                                        .propagateSettings(true)
                                        .build());
                    }
                }
                """, """
                import io.netty.handler.codec.http2.*;
                class NettyHttpClientPipelineFactory {
                    private final int maxContentLength = 1048576;
                    Http2FrameListener listener(Http2Connection connection) {
                        return new DelegatingDecompressorFrameListener(connection,
                                new InboundHttp2ToHttpAdapterBuilder(connection)
                                        .maxContentLength(maxContentLength)
                                        .propagateSettings(true)
                                        .build(), 0);
                    }
                }
                """));
    }

    @Test
    void migratesFixedAlephBuilderFixture() {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.*;
                class AlephHttp2FrameCodecBuilder extends Http2FrameCodecBuilder {
                    Http2FrameListener decompress(Http2Connection connection, Http2FrameListener listener) {
                        return new DelegatingDecompressorFrameListener(connection, listener);
                    }
                }
                """, """
                import io.netty.handler.codec.http2.*;
                class AlephHttp2FrameCodecBuilder extends Http2FrameCodecBuilder {
                    Http2FrameListener decompress(Http2Connection connection, Http2FrameListener listener) {
                        return new DelegatingDecompressorFrameListener(connection, listener, 0);
                    }
                }
                """));
    }

    @Test
    void migratesConstructorsAttributedFromTheRealEarliestSelectedJar() {
        rewriteRun(spec -> spec.parser(JavaParser.fromJavaVersion().classpath("netty-codec-http2")),
                java("""
                        import io.netty.handler.codec.http2.*;
                        class RealNettyClasspath {
                            Object decoder(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                                           Http2PromisedRequestVerifier v) {
                                return new DefaultHttp2ConnectionDecoder(c, e, r, v, true, true);
                            }
                            Object decompressor(Http2Connection c, Http2FrameListener l) {
                                return new DelegatingDecompressorFrameListener(c, l);
                            }
                            Object headers() {
                                return new DefaultHttp2HeadersDecoder(true, 8192L, 32);
                            }
                        }
                        """, """
                        import io.netty.handler.codec.http2.*;
                        class RealNettyClasspath {
                            Object decoder(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                                           Http2PromisedRequestVerifier v) {
                                return new DefaultHttp2ConnectionDecoder(c, e, r, v, true, true, true);
                            }
                            Object decompressor(Http2Connection c, Http2FrameListener l) {
                                return new DelegatingDecompressorFrameListener(c, l, 0);
                            }
                            Object headers() {
                                return new DefaultHttp2HeadersDecoder(true, 8192L);
                            }
                        }
                        """));
    }

    @Test
    void constructorMigrationsAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java("""
                import io.netty.handler.codec.http2.*;
                class Pipeline {
                    Object x(Http2Connection c, Http2FrameListener l) {
                        return new DelegatingDecompressorFrameListener(c, l);
                    }
                }
                """, """
                import io.netty.handler.codec.http2.*;
                class Pipeline {
                    Object x(Http2Connection c, Http2FrameListener l) {
                        return new DelegatingDecompressorFrameListener(c, l, 0);
                    }
                }
                """));
    }
}
