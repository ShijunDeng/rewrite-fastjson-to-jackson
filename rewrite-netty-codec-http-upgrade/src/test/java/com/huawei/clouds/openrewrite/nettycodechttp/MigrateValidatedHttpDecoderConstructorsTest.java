package com.huawei.clouds.openrewrite.nettycodechttp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class MigrateValidatedHttpDecoderConstructorsTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateValidatedHttpDecoderConstructors())
                .parser(JavaParser.fromJavaVersion().dependsOn(NettyCodecHttpTestApi.decoderSources()));
    }

    @ParameterizedTest(name = "{0} arity {1}")
    @MethodSource("validatedConstructors")
    void migratesEverySafeValidatedConstructor(String type, int arity, String before, String after) {
        rewriteRun(java("""
                import io.netty.handler.codec.http.*;
                class C { Object decoder() { return %s; } }
                """.formatted(before), """
                import io.netty.handler.codec.http.*;
                class C { Object decoder() { return %s; } }
                """.formatted(after)));
    }

    static Stream<Arguments> validatedConstructors() {
        return Stream.of("HttpRequestDecoder", "HttpResponseDecoder", "HttpServerCodec").flatMap(type -> Stream.of(
                Arguments.of(type, 4,
                        "new " + type + "(4096, 8192, 16384, true)",
                        "new " + type + "(new HttpDecoderConfig().setMaxInitialLineLength(4096).setMaxHeaderSize(8192).setMaxChunkSize(16384))"),
                Arguments.of(type, 5,
                        "new " + type + "(4096, 8192, 16384, true, 256)",
                        "new " + type + "(new HttpDecoderConfig().setMaxInitialLineLength(4096).setMaxHeaderSize(8192).setMaxChunkSize(16384).setInitialBufferSize(256))"),
                Arguments.of(type, 6,
                        "new " + type + "(4096, 8192, 16384, true, 256, false)",
                        "new " + type + "(new HttpDecoderConfig().setMaxInitialLineLength(4096).setMaxHeaderSize(8192).setMaxChunkSize(16384).setInitialBufferSize(256).setAllowDuplicateContentLengths(false))"),
                Arguments.of(type, 7,
                        "new " + type + "(4096, 8192, 16384, true, 256, false, true)",
                        "new " + type + "(new HttpDecoderConfig().setMaxInitialLineLength(4096).setMaxHeaderSize(8192).setMaxChunkSize(16384).setInitialBufferSize(256).setAllowDuplicateContentLengths(false).setAllowPartialChunks(true))")
        ));
    }

    @Test
    void preservesExpressionOrderAndEvaluationCount() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.HttpRequestDecoder;
                class C {
                    int value() { return 1; }
                    boolean option() { return true; }
                    Object decoder() { return new HttpRequestDecoder(value(), value(), value(), true, value(), option(), option()); }
                }
                """, """
                import io.netty.handler.codec.http.HttpDecoderConfig;
                import io.netty.handler.codec.http.HttpRequestDecoder;

                class C {
                    int value() { return 1; }
                    boolean option() { return true; }
                    Object decoder() { return new HttpRequestDecoder(new HttpDecoderConfig().setMaxInitialLineLength(value()).setMaxHeaderSize(value()).setMaxChunkSize(value()).setInitialBufferSize(value()).setAllowDuplicateContentLengths(option()).setAllowPartialChunks(option())); }
                }
                """));
    }

    @Test
    void migratesNettyHttpClientCodecTestFixtureAtTargetCommit() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.HttpRequestDecoder;
                class HttpClientCodecTest {
                    Object decoder() { return new HttpRequestDecoder(4096, 8192, 8192, true); }
                }
                """, """
                import io.netty.handler.codec.http.HttpDecoderConfig;
                import io.netty.handler.codec.http.HttpRequestDecoder;

                class HttpClientCodecTest {
                    Object decoder() { return new HttpRequestDecoder(new HttpDecoderConfig().setMaxInitialLineLength(4096).setMaxHeaderSize(8192).setMaxChunkSize(8192)); }
                }
                """));
    }

    @Test
    void falseAndDynamicValidationAreNotChanged() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.*;
                class C {
                    Object a() { return new HttpRequestDecoder(1, 2, 3, false); }
                    Object b(boolean validate) { return new HttpResponseDecoder(1, 2, 3, validate, 4); }
                    Object c(Boolean validate) { return new HttpServerCodec(1, 2, 3, validate, 4, false); }
                }
                """));
    }

    @Test
    void clientCodecIsDeliberatelyNotReordered() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.HttpClientCodec;
                class C {
                    boolean option(){ return true; }
                    Object client(){ return new HttpClientCodec(1, 2, 3, option(), true, option()); }
                }
                """));
    }

    @Test
    void localLookalikeAndAnonymousSubclassAreNoop() {
        rewriteRun(spec -> spec.typeValidationOptions(TypeValidation.none()), java("""
                class HttpRequestDecoder { HttpRequestDecoder(int a,int b,int c,boolean d){} }
                class C { Object decoder(){ return new HttpRequestDecoder(1, 2, 3, true); } }
                """), java("""
                import io.netty.handler.codec.http.HttpResponseDecoder;
                class C { Object decoder(){ return new HttpResponseDecoder(1, 2, 3, true) {}; } }
                """));
    }

    @Test
    void qualifiesOfficialConfigWhenSimpleNameIsOwnedByApplication() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.HttpRequestDecoder;
                class HttpDecoderConfig {}
                class C { Object decoder(){ return new HttpRequestDecoder(1, 2, 3, true); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("new io.netty.handler.codec.http.HttpDecoderConfig()"), printed);
                    assertFalse(printed.contains("import io.netty.handler.codec.http.HttpDecoderConfig;"), printed);
                })));
    }

    @Test
    void qualifiesOfficialConfigWhenAnotherConfigIsImported() {
        rewriteRun(java("""
                import com.acme.HttpDecoderConfig;
                import io.netty.handler.codec.http.HttpRequestDecoder;
                class C {
                    HttpDecoderConfig businessConfig;
                    Object decoder(){ return new HttpRequestDecoder(1, 2, 3, true); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("import com.acme.HttpDecoderConfig;"), printed);
                    assertTrue(printed.contains("new io.netty.handler.codec.http.HttpDecoderConfig()"), printed);
                    assertFalse(printed.contains("import io.netty.handler.codec.http.HttpDecoderConfig;"), printed);
                })));
    }

    @Test
    void generatedSourceIsNoop() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.HttpServerCodec;
                class C { Object decoder(){ return new HttpServerCodec(1, 2, 3, true); } }
                """, source -> source.path("target/generated-sources/C.java")));
    }

    @Test
    void twoCyclesAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java("""
                import io.netty.handler.codec.http.HttpRequestDecoder;
                class C { Object decoder(){ return new HttpRequestDecoder(1, 2, 3, true); } }
                """, """
                import io.netty.handler.codec.http.HttpDecoderConfig;
                import io.netty.handler.codec.http.HttpRequestDecoder;

                class C { Object decoder(){ return new HttpRequestDecoder(new HttpDecoderConfig().setMaxInitialLineLength(1).setMaxHeaderSize(2).setMaxChunkSize(3)); } }
                """));
    }
}
