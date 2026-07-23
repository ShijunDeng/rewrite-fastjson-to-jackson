package com.huawei.clouds.openrewrite.nettycodechttp;

import java.util.stream.Stream;

final class NettyCodecHttpTestApi {
    private NettyCodecHttpTestApi() {
    }

    static String[] decoderSources() {
        return new String[]{
                "package io.netty.handler.codec.http; public abstract class HttpObjectDecoder {}",
                """
                package io.netty.handler.codec.http;
                public final class HttpDecoderConfig {
                    public HttpDecoderConfig setMaxInitialLineLength(int value) { return this; }
                    public HttpDecoderConfig setMaxHeaderSize(int value) { return this; }
                    public HttpDecoderConfig setMaxChunkSize(int value) { return this; }
                    public HttpDecoderConfig setInitialBufferSize(int value) { return this; }
                    public HttpDecoderConfig setAllowDuplicateContentLengths(boolean value) { return this; }
                    public HttpDecoderConfig setAllowPartialChunks(boolean value) { return this; }
                    public HttpDecoderConfig setValidateHeaders(boolean value) { return this; }
                    public HttpDecoderConfig setUseRfc9112TransferEncoding(boolean value) { return this; }
                }
                """,
                decoder("HttpRequestDecoder", "extends HttpObjectDecoder"),
                decoder("HttpResponseDecoder", "extends HttpObjectDecoder"),
                decoder("HttpServerCodec", ""),
                "package com.acme; public class HttpDecoderConfig {}",
                """
                package io.netty.handler.codec.http;
                public class HttpClientCodec {
                    public HttpClientCodec(int a, int b, int c, boolean fail, boolean validate) {}
                    public HttpClientCodec(int a, int b, int c, boolean fail, boolean validate, boolean parse) {}
                    public HttpClientCodec(int a, int b, int c, boolean fail, boolean validate, int initial) {}
                    public HttpClientCodec(int a, int b, int c, boolean fail, boolean validate, int initial, boolean parse) {}
                    public HttpClientCodec(int a, int b, int c, boolean fail, boolean validate, int initial, boolean parse, boolean duplicate) {}
                    public HttpClientCodec(int a, int b, int c, boolean fail, boolean validate, int initial, boolean parse, boolean duplicate, boolean partial) {}
                    public HttpClientCodec(HttpDecoderConfig config, boolean parse, boolean fail) {}
                }
                """,
                """
                package io.netty.handler.codec.http;
                public final class DefaultHttpHeadersFactory {
                    public static DefaultHttpHeadersFactory headersFactory() { return null; }
                    public DefaultHttpHeadersFactory withValidation(boolean value) { return this; }
                }
                """,
                "package io.netty.handler.codec.http; public class DefaultHttpHeaders { public DefaultHttpHeaders(){} public DefaultHttpHeaders(boolean validate){} }"
        };
    }

    static String[] riskSources() {
        String[] additional = {
                "package io.netty.channel; public interface ChannelHandlerContext {}",
                "package io.netty.buffer; public class ByteBuf {}",
                "package io.netty.handler.codec.http; public interface HttpHeadersFactory {}",
                "package io.netty.handler.codec.http; public interface HttpRequest { String uri(); }",
                """
                package io.netty.handler.codec.http;
                public final class HttpMethod {
                    public HttpMethod(String value) {}
                    public static HttpMethod valueOf(String value) { return null; }
                }
                """,
                """
                package io.netty.handler.codec.http;
                public final class HttpVersion {
                    public HttpVersion(String value, boolean strict) {}
                    public static HttpVersion valueOf(String value) { return null; }
                }
                """,
                """
                package io.netty.handler.codec.http;
                public class QueryStringDecoder {
                    public QueryStringDecoder(String value) {}
                    public static String decodeComponent(String value) { return null; }
                    public String path() { return null; }
                    public java.util.Map<String, java.util.List<String>> parameters() { return null; }
                }
                """,
                "package io.netty.handler.codec.http; public class QueryStringEncoder { public QueryStringEncoder(String value) {} public QueryStringEncoder addParam(String n,String v){return this;} }",
                "package io.netty.handler.codec.http; public class HttpObjectAggregator { public HttpObjectAggregator(int max) {} public void decode(){} }",
                "package io.netty.handler.codec.http; public class HttpClientUpgradeHandler { public HttpClientUpgradeHandler(Object a,Object b,int c){} public void decode(){} }",
                """
                package io.netty.handler.codec.http;
                public class HttpServerUpgradeHandler {
                    public interface SourceCodec {}
                    public interface UpgradeCodecFactory {}
                    public HttpServerUpgradeHandler(SourceCodec a, UpgradeCodecFactory b, int c) {}
                    public HttpServerUpgradeHandler(SourceCodec a, UpgradeCodecFactory b, int c,
                            HttpHeadersFactory d, HttpHeadersFactory e, boolean f) {}
                    public void decode() {}
                }
                """,
                "package io.netty.handler.codec.http; public class HttpContentCompressor { public HttpContentCompressor(){} public void encode(){} }",
                "package io.netty.handler.codec.http; public class HttpContentDecompressor { public HttpContentDecompressor(){} public void decode(){} }",
                "package io.netty.handler.codec.http; public abstract class HttpContentEncoder {}",
                "package io.netty.handler.codec.http; public abstract class HttpContentDecoder {}",
                "package io.netty.handler.codec.http.cors; public class CorsHandler { public CorsHandler(Object config){} public void read(){} }",
                "package io.netty.handler.codec.http.cors; public class CorsConfigBuilder { public static CorsConfigBuilder forAnyOrigin(){return null;} public Object build(){return null;} }",
                "package io.netty.handler.codec.http.multipart; public class HttpPostRequestDecoder { public HttpPostRequestDecoder(Object request){} public java.util.List<Object> getBodyHttpDatas(){return null;} public Object getBodyHttpData(String n){return null;} public void destroy(){} }",
                "package io.netty.handler.codec.http.multipart; public class HttpPostRequestEncoder { public HttpPostRequestEncoder(Object request, boolean multipart){} public void addBodyAttribute(String n,String v){} }",
                """
                package io.netty.handler.codec;
                public final class DateFormatter {
                    public static java.util.Date parseHttpDate(CharSequence value) { return null; }
                    public static java.util.Date parseHttpDate(CharSequence value, int start, int end) { return null; }
                }
                """,
                "package io.netty.handler.codec.compression; public class BrotliDecoder { public BrotliDecoder(){} }",
                "package io.netty.handler.codec.compression; public class ZstdDecoder { public ZstdDecoder(){} }",
                """
                package io.netty.handler.codec.spdy;
                public class SpdyHttpDecoder {
                    public void channelInactive(io.netty.channel.ChannelHandlerContext ctx) throws Exception {}
                }
                """,
                """
                package io.netty.handler.codec.spdy;
                public interface SpdyFrameDecoderDelegate {
                    void readUnknownFrame(int type, byte flags, io.netty.buffer.ByteBuf payload);
                }
                """,
                "package io.netty.handler.codec.spdy; public class SpdyFrameCodec { public void readUnknownFrame(int t, byte f, io.netty.buffer.ByteBuf b){} }",
                """
                package io.netty.handler.codec.http.websocketx;
                public interface WebSocketFrameMaskGenerator { int nextMask(); }
                """,
                """
                package io.netty.handler.codec.http.websocketx;
                public final class RandomWebSocketFrameMaskGenerator implements WebSocketFrameMaskGenerator {
                    public static final RandomWebSocketFrameMaskGenerator INSTANCE = null;
                    public int nextMask(){ return 0; }
                }
                """,
                "package io.netty.handler.codec.http.websocketx; public class WebSocket07FrameEncoder { public WebSocket07FrameEncoder(WebSocketFrameMaskGenerator g){} }",
                "package io.netty.handler.codec.http.websocketx; public class WebSocket08FrameEncoder { public WebSocket08FrameEncoder(WebSocketFrameMaskGenerator g){} }",
                "package io.netty.handler.codec.http.websocketx; public class WebSocket13FrameEncoder { public WebSocket13FrameEncoder(WebSocketFrameMaskGenerator g){} }",
                """
                package io.netty.handler.codec.http.websocketx;
                public final class WebSocketServerHandshakerFactory {
                    public static Object resolveHandshaker(Object request, String url, String protocols, Object config) { return null; }
                }
                """,
                "package io.netty.handler.codec.http.websocketx; public class Utf8FrameValidator { public Utf8FrameValidator(){} }",
                "package io.netty.handler.codec.http.websocketx.extensions.compression; public class WebSocketClientCompressionHandler { public WebSocketClientCompressionHandler(){} }",
                "package io.netty.handler.codec.http.websocketx.extensions.compression; public class WebSocketServerCompressionHandler { public WebSocketServerCompressionHandler(){} }"
        };
        return Stream.concat(Stream.of(decoderSources()), Stream.of(additional)).toArray(String[]::new);
    }

    private static String decoder(String name, String inheritance) {
        return """
                package io.netty.handler.codec.http;
                public class %s %s {
                    public %s(int a, int b, int c, boolean validate) {}
                    public %s(int a, int b, int c, boolean validate, int initial) {}
                    public %s(int a, int b, int c, boolean validate, int initial, boolean duplicate) {}
                    public %s(int a, int b, int c, boolean validate, int initial, boolean duplicate, boolean partial) {}
                    public %s(HttpDecoderConfig config) {}
                }
                """.formatted(name, inheritance, name, name, name, name, name);
    }
}
