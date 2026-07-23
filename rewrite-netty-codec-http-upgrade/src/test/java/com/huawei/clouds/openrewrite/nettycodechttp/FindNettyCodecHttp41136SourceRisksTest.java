package com.huawei.clouds.openrewrite.nettycodechttp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindNettyCodecHttp41136SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNettyCodecHttp41136SourceRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(NettyCodecHttpTestApi.riskSources()));
    }

    @ParameterizedTest(name = "header validation {0}")
    @MethodSource("headerValidationCases")
    void marksDisabledOrRuntimeSelectedHeaderValidation(String label, String source) {
        assertMarked(source, FindNettyCodecHttp41136SourceRisks.HEADER_VALIDATION);
    }

    static Stream<Arguments> headerValidationCases() {
        return Stream.of(
                Arguments.of("request false", """
                        import io.netty.handler.codec.http.HttpRequestDecoder;
                        class C { Object x = new HttpRequestDecoder(1, 2, 3, false); }
                        """),
                Arguments.of("response dynamic", """
                        import io.netty.handler.codec.http.HttpResponseDecoder;
                        class C { Object x(boolean validate) { return new HttpResponseDecoder(1, 2, 3, validate, 4); } }
                        """),
                Arguments.of("server false", """
                        import io.netty.handler.codec.http.HttpServerCodec;
                        class C { Object x = new HttpServerCodec(1, 2, 3, false, 4, false, true); }
                        """),
                Arguments.of("client false", """
                        import io.netty.handler.codec.http.HttpClientCodec;
                        class C { Object x = new HttpClientCodec(1, 2, 3, false, false); }
                        """),
                Arguments.of("config false", """
                        import io.netty.handler.codec.http.HttpDecoderConfig;
                        class C { Object x = new HttpDecoderConfig().setValidateHeaders(false); }
                        """),
                Arguments.of("config dynamic", """
                        import io.netty.handler.codec.http.HttpDecoderConfig;
                        class C { Object x(boolean validate) { return new HttpDecoderConfig().setValidateHeaders(validate); } }
                        """),
                Arguments.of("factory false", """
                        import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
                        class C { Object x = DefaultHttpHeadersFactory.headersFactory().withValidation(false); }
                        """),
                Arguments.of("Dubbo DefaultHttpHeaders false", """
                        import io.netty.handler.codec.http.DefaultHttpHeaders;
                        class HttpUtils { Object copy(){ return new DefaultHttpHeaders(false); } }
                        """));
    }

    @Test
    void marksValidationEnabledClientConstructorForManualOrderPreservation() {
        assertMarked("""
                import io.netty.handler.codec.http.HttpClientCodec;
                class C { boolean option(){return true;} Object x = new HttpClientCodec(1, 2, 3, option(), true, option()); }
                """, FindNettyCodecHttp41136SourceRisks.CLIENT_CONSTRUCTOR);
    }

    @Test
    void literalTrueConfigSetterIsClean() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.HttpDecoderConfig;
                class C { Object x = new HttpDecoderConfig().setValidateHeaders(true); }
                """));
    }

    @ParameterizedTest(name = "parser behavior {0}")
    @MethodSource("parserCases")
    void marksStrictParserAndCustomTokenDecisions(String label, String source, String message) {
        assertMarked(source, message);
    }

    static Stream<Arguments> parserCases() {
        return Stream.of(
                Arguments.of("request decoder", """
                        import io.netty.handler.codec.http.*;
                        class C { Object x = new HttpRequestDecoder(new HttpDecoderConfig()); }
                        """, FindNettyCodecHttp41136SourceRisks.PARSING),
                Arguments.of("client codec", """
                        import io.netty.handler.codec.http.HttpClientCodec;
                        class C { Object x = new HttpClientCodec(1, 2, 3, false, true); }
                        """, FindNettyCodecHttp41136SourceRisks.CLIENT_CONSTRUCTOR),
                Arguments.of("custom decoder", """
                        import io.netty.handler.codec.http.HttpObjectDecoder;
                        class CustomDecoder extends HttpObjectDecoder {}
                        """, FindNettyCodecHttp41136SourceRisks.CUSTOM_DECODER),
                Arguments.of("custom method constructor", """
                        import io.netty.handler.codec.http.HttpMethod;
                        class C { Object x(String method) { return new HttpMethod(method); } }
                        """, FindNettyCodecHttp41136SourceRisks.CUSTOM_METHOD),
                Arguments.of("method valueOf", """
                        import io.netty.handler.codec.http.HttpMethod;
                        class C { Object x(String method) { return HttpMethod.valueOf(method); } }
                        """, FindNettyCodecHttp41136SourceRisks.CUSTOM_METHOD),
                Arguments.of("version valueOf", """
                        import io.netty.handler.codec.http.HttpVersion;
                        class C { Object x(String version) { return HttpVersion.valueOf(version); } }
                        """, FindNettyCodecHttp41136SourceRisks.CUSTOM_METHOD));
    }

    @ParameterizedTest(name = "changed behavior {0}")
    @MethodSource("behaviorCases")
    void marksChangedRuntimeBehavior(String label, String source, String message) {
        assertMarked(source, message);
    }

    static Stream<Arguments> behaviorCases() {
        return Stream.of(
                Arguments.of("multipart decoder", """
                        import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
                        class C { Object x(Object request){ return new HttpPostRequestDecoder(request); } }
                        """, FindNettyCodecHttp41136SourceRisks.MULTIPART),
                Arguments.of("multipart lifecycle", """
                        import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
                        class C { void x(HttpPostRequestDecoder decoder){ decoder.destroy(); } }
                        """, FindNettyCodecHttp41136SourceRisks.MULTIPART),
                Arguments.of("form encoder", """
                        import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
                        class C { void x(HttpPostRequestEncoder encoder){ encoder.addBodyAttribute("flag", ""); } }
                        """, FindNettyCodecHttp41136SourceRisks.MULTIPART),
                Arguments.of("query decoder", """
                        import io.netty.handler.codec.http.QueryStringDecoder;
                        class C { Object x = new QueryStringDecoder("/p?flag&x=+"); }
                        """, FindNettyCodecHttp41136SourceRisks.QUERY),
                Arguments.of("query component", """
                        import io.netty.handler.codec.http.QueryStringDecoder;
                        class C { String x(String raw){ return QueryStringDecoder.decodeComponent(raw); } }
                        """, FindNettyCodecHttp41136SourceRisks.QUERY),
                Arguments.of("aggregator", """
                        import io.netty.handler.codec.http.HttpObjectAggregator;
                        class C { Object x = new HttpObjectAggregator(1048576); }
                        """, FindNettyCodecHttp41136SourceRisks.AGGREGATION),
                Arguments.of("CORS", """
                        import io.netty.handler.codec.http.cors.*;
                        class C { Object x = new CorsHandler(CorsConfigBuilder.forAnyOrigin().build()); }
                        """, FindNettyCodecHttp41136SourceRisks.AGGREGATION),
                Arguments.of("client upgrade", """
                        import io.netty.handler.codec.http.HttpClientUpgradeHandler;
                        class C { Object x = new HttpClientUpgradeHandler(null, null, 8192); }
                        """, FindNettyCodecHttp41136SourceRisks.AGGREGATION),
                Arguments.of("HTTP compression", """
                        import io.netty.handler.codec.http.HttpContentCompressor;
                        class C { Object x = new HttpContentCompressor(); }
                        """, FindNettyCodecHttp41136SourceRisks.COMPRESSION),
                Arguments.of("Zstd", """
                        import io.netty.handler.codec.compression.ZstdDecoder;
                        class C { Object x = new ZstdDecoder(); }
                        """, FindNettyCodecHttp41136SourceRisks.COMPRESSION),
                Arguments.of("WebSocket compression", """
                        import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
                        class C { Object x = new WebSocketServerCompressionHandler(); }
                        """, FindNettyCodecHttp41136SourceRisks.COMPRESSION),
                Arguments.of("UTF-8 frames", """
                        import io.netty.handler.codec.http.websocketx.Utf8FrameValidator;
                        class C { Object x = new Utf8FrameValidator(); }
                        """, FindNettyCodecHttp41136SourceRisks.COMPRESSION),
                Arguments.of("date range", """
                        import io.netty.handler.codec.DateFormatter;
                        class C { Object x(CharSequence value){ return DateFormatter.parseHttpDate(value, 0, value.length()); } }
                        """, FindNettyCodecHttp41136SourceRisks.DATE),
                Arguments.of("RFC9112 config", """
                        import io.netty.handler.codec.http.HttpDecoderConfig;
                        class C { Object x = new HttpDecoderConfig().setUseRfc9112TransferEncoding(false); }
                        """, FindNettyCodecHttp41136SourceRisks.RFC9112),
                Arguments.of("RFC9112 system property", """
                        class C { void x(){ System.setProperty("io.netty.handler.codec.http.rfc9112TransferEncoding", "false"); } }
                        """, FindNettyCodecHttp41136SourceRisks.RFC9112));
    }

    @ParameterizedTest(name = "SPDY/4.2 API {0}")
    @MethodSource("branchCases")
    void marksSpdyLifecycleAnd42OnlyApis(String label, String source, String message) {
        assertMarked(source, message);
    }

    static Stream<Arguments> branchCases() {
        return Stream.of(
                Arguments.of("SpdyHttpDecoder subtype", """
                        import io.netty.handler.codec.spdy.SpdyHttpDecoder;
                        class CustomSpdyDecoder extends SpdyHttpDecoder {}
                        """, FindNettyCodecHttp41136SourceRisks.SPDY_LIFECYCLE),
                Arguments.of("Spdy channelInactive override", """
                        import io.netty.channel.ChannelHandlerContext;
                        import io.netty.handler.codec.spdy.SpdyHttpDecoder;
                        class CustomSpdyDecoder extends SpdyHttpDecoder {
                            @Override public void channelInactive(ChannelHandlerContext ctx) throws Exception { super.channelInactive(ctx); }
                        }
                        """, FindNettyCodecHttp41136SourceRisks.SPDY_LIFECYCLE),
                Arguments.of("mask generator variable", """
                        import io.netty.handler.codec.http.websocketx.WebSocketFrameMaskGenerator;
                        class C { WebSocketFrameMaskGenerator generator; }
                        """, FindNettyCodecHttp41136SourceRisks.BRANCH_API),
                Arguments.of("random generator", """
                        import io.netty.handler.codec.http.websocketx.RandomWebSocketFrameMaskGenerator;
                        class C { Object generator = RandomWebSocketFrameMaskGenerator.INSTANCE; }
                        """, FindNettyCodecHttp41136SourceRisks.BRANCH_API),
                Arguments.of("masking encoder constructor", """
                        import io.netty.handler.codec.http.websocketx.*;
                        class C { Object encoder(WebSocketFrameMaskGenerator generator){ return new WebSocket13FrameEncoder(generator); } }
                        """, FindNettyCodecHttp41136SourceRisks.BRANCH_API),
                Arguments.of("six-argument server upgrade", """
                        import io.netty.handler.codec.http.*;
                        class C { Object x(HttpServerUpgradeHandler.SourceCodec source,
                            HttpServerUpgradeHandler.UpgradeCodecFactory factory, HttpHeadersFactory headers) {
                            return new HttpServerUpgradeHandler(source, factory, 8192, headers, headers, true);
                        } }
                        """, FindNettyCodecHttp41136SourceRisks.BRANCH_API),
                Arguments.of("resolve handshaker", """
                        import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
                        class C { Object x(Object request){ return WebSocketServerHandshakerFactory.resolveHandshaker(request, "/", null, null); } }
                        """, FindNettyCodecHttp41136SourceRisks.BRANCH_API),
                Arguments.of("SPDY delegate", """
                        import io.netty.buffer.ByteBuf;
                        import io.netty.handler.codec.spdy.SpdyFrameDecoderDelegate;
                        class C implements SpdyFrameDecoderDelegate {
                            public void readUnknownFrame(int type, byte flags, ByteBuf payload) {}
                        }
                        """, FindNettyCodecHttp41136SourceRisks.BRANCH_API),
                Arguments.of("SPDY codec method", """
                        import io.netty.buffer.ByteBuf;
                        import io.netty.handler.codec.spdy.SpdyFrameCodec;
                        class C { void x(SpdyFrameCodec codec, ByteBuf payload){ codec.readUnknownFrame(1, (byte) 0, payload); } }
                        """, FindNettyCodecHttp41136SourceRisks.BRANCH_API));
    }

    @Test
    void threeArgumentServerUpgradeIsBehaviorRiskNot42Api() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.HttpServerUpgradeHandler;
                class C { Object x(HttpServerUpgradeHandler.SourceCodec source,
                    HttpServerUpgradeHandler.UpgradeCodecFactory factory) {
                    return new HttpServerUpgradeHandler(source, factory, 8192);
                } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindNettyCodecHttp41136SourceRisks.AGGREGATION), printed);
            assertFalse(printed.contains(FindNettyCodecHttp41136SourceRisks.BRANCH_API), printed);
        })));
    }

    @Test
    void marksApacheDubboHttpCommandDecoderFixture() {
        assertMarked("""
                import io.netty.handler.codec.http.HttpRequest;
                import io.netty.handler.codec.http.QueryStringDecoder;
                import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
                class HttpCommandDecoder {
                    void decode(HttpRequest request) {
                        QueryStringDecoder query = new QueryStringDecoder(request.uri());
                        HttpPostRequestDecoder post = null;
                        try {
                            post = new HttpPostRequestDecoder(request);
                            post.getBodyHttpDatas();
                        } finally {
                            if (post != null) post.destroy();
                        }
                    }
                }
                """, FindNettyCodecHttp41136SourceRisks.MULTIPART);
    }

    @Test
    void unrelatedLookalikesAndPlainStringsAreClean() {
        rewriteRun(java("""
                class HttpMethod { static Object valueOf(String value){return null;} }
                class HttpObjectAggregator { HttpObjectAggregator(int value){} }
                class C { Object a = HttpMethod.valueOf("X"); Object b = new HttpObjectAggregator(1);
                    String text = "io.netty.handler.codec.http.websocketx.WebSocketFrameMaskGenerator"; }
                """));
    }

    @Test
    void generatedSourceIsClean() {
        rewriteRun(java("""
                import io.netty.handler.codec.http.HttpRequestDecoder;
                class C { Object x = new HttpRequestDecoder(1, 2, 3, false); }
                """, source -> source.path("target/generated-sources/C.java")));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java("""
                import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
                class C { Object x(Object request){ return new HttpPostRequestDecoder(request); } }
                """, source -> source.after(actual -> actual).afterRecipe(after -> assertEquals(1,
                occurrences(after.printAll(), FindNettyCodecHttp41136SourceRisks.MULTIPART)))));
    }

    private void assertMarked(String source, String message) {
        rewriteRun(java(source, spec -> spec.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
