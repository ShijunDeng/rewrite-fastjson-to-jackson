package com.huawei.clouds.openrewrite.nettycodechttp2;

final class NettyCodecHttp2TestApi {
    private NettyCodecHttp2TestApi() {
    }

    static String[] sources() {
        return new String[]{
                "package io.netty.channel; public interface ChannelHandler {}",
                "package io.netty.channel; public interface ChannelHandlerContext {}",
                "package io.netty.handler.codec.http2; public interface Http2Connection {}",
                "package io.netty.handler.codec.http2; public interface Http2ConnectionEncoder {}",
                "package io.netty.handler.codec.http2; public interface Http2FrameReader {}",
                """
                package io.netty.handler.codec.http2;
                public interface Http2FrameListener {
                    default void onPushPromiseRead(io.netty.channel.ChannelHandlerContext ctx, int streamId,
                                                   int promisedStreamId, Http2Headers headers, int padding) {}
                }
                """,
                """
                package io.netty.handler.codec.http2;
                public class DefaultHttp2ConnectionDecoder {
                    public DefaultHttp2ConnectionDecoder(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r) {}
                    public DefaultHttp2ConnectionDecoder(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                            Http2PromisedRequestVerifier v, boolean settings, boolean ping) {}
                    public DefaultHttp2ConnectionDecoder(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                            Http2PromisedRequestVerifier v, boolean settings, boolean ping, boolean headers) {}
                    public DefaultHttp2ConnectionDecoder(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                            Http2PromisedRequestVerifier v, boolean settings, boolean ping, boolean headers,
                            boolean pseudo) {}
                }
                """,
                """
                package io.netty.handler.codec.http2;
                public class DelegatingDecompressorFrameListener {
                    public DelegatingDecompressorFrameListener(Http2Connection c, Http2FrameListener l) {}
                    public DelegatingDecompressorFrameListener(Http2Connection c, Http2FrameListener l, int max) {}
                    public DelegatingDecompressorFrameListener(Http2Connection c, Http2FrameListener l, boolean strict) {}
                    public DelegatingDecompressorFrameListener(Http2Connection c, Http2FrameListener l, boolean strict, int max) {}
                }
                """,
                """
                package io.netty.handler.codec.http2;
                public class DefaultHttp2HeadersDecoder {
                    public DefaultHttp2HeadersDecoder() {}
                    public DefaultHttp2HeadersDecoder(boolean validate) {}
                    public DefaultHttp2HeadersDecoder(boolean validate, boolean values) {}
                    public DefaultHttp2HeadersDecoder(boolean validate, long max) {}
                    public DefaultHttp2HeadersDecoder(boolean validate, boolean values, long max) {}
                    public DefaultHttp2HeadersDecoder(boolean validate, long max, int huffman) {}
                    public void maxHeaderTableSize(long max) {}
                }
                """,
                """
                package io.netty.handler.codec.http2;
                public interface Http2Headers {
                    Http2Headers add(CharSequence name, CharSequence value);
                    Http2Headers set(CharSequence name, CharSequence value);
                    Http2Headers setAll(Http2Headers headers);
                    Http2Headers scheme(CharSequence value);
                    Http2Headers authority(CharSequence value);
                    Http2Headers path(CharSequence value);
                    Http2Headers method(CharSequence value);
                    Http2Headers status(CharSequence value);
                }
                """,
                """
                package io.netty.handler.codec.http2;
                public class DefaultHttp2Headers implements Http2Headers {
                    public DefaultHttp2Headers() {}
                    public DefaultHttp2Headers(boolean validate) {}
                    public DefaultHttp2Headers(boolean validate, int hint) {}
                    public DefaultHttp2Headers(boolean validate, boolean values, int hint) {}
                    public static Object defaultHtt2NameValidator() { return null; }
                    public static Object defaultHttp2ValueValidator() { return null; }
                    public DefaultHttp2Headers add(CharSequence n, CharSequence v) { return this; }
                    public DefaultHttp2Headers set(CharSequence n, CharSequence v) { return this; }
                    public DefaultHttp2Headers setAll(Http2Headers h) { return this; }
                    public DefaultHttp2Headers scheme(CharSequence v) { return this; }
                    public DefaultHttp2Headers authority(CharSequence v) { return this; }
                    public DefaultHttp2Headers path(CharSequence v) { return this; }
                    public DefaultHttp2Headers method(CharSequence v) { return this; }
                    public DefaultHttp2Headers status(CharSequence v) { return this; }
                }
                """,
                """
                package io.netty.handler.codec.http2;
                public class DefaultHttp2HeadersEncoder implements AutoCloseable {
                    public DefaultHttp2HeadersEncoder() {}
                    public void maxHeaderTableSize(long max) {}
                    public void close() {}
                }
                """,
                """
                package io.netty.handler.codec.http2;
                public class Http2ConnectionHandler implements io.netty.channel.ChannelHandler {
                    public Http2ConnectionHandler() {}
                }
                """,
                """
                package io.netty.handler.codec.http2;
                public class Http2FrameCodec extends Http2ConnectionHandler {
                    public Http2FrameCodec() {}
                    public Http2Connection connection() { return null; }
                }
                """,
                "package io.netty.handler.codec.http2; public class Http2MultiplexCodec extends Http2ConnectionHandler { public Http2MultiplexCodec() {} }",
                "package io.netty.handler.codec.http2; public class Http2MultiplexHandler implements io.netty.channel.ChannelHandler { public Http2MultiplexHandler(io.netty.channel.ChannelHandler h) {} }",
                "package io.netty.handler.codec.http2; public class Http2StreamChannel { public void close() {} }",
                "package io.netty.handler.codec.http2; public class Http2FrameStream { public int id() { return 1; } }",
                "package io.netty.handler.codec.http2; public class Http2UnknownFrame {}",
                "package io.netty.handler.codec.http2; public class Http2PriorityFrame {}",
                "package io.netty.handler.codec.http2; public class Http2PushPromiseFrame {}",
                "package io.netty.handler.codec.http2; public class Http2ClientUpgradeCodec implements io.netty.channel.ChannelHandler { public Http2ClientUpgradeCodec(Http2ConnectionHandler h) {} }",
                """
                package io.netty.handler.codec.http2;
                public class Http2ServerUpgradeCodec implements io.netty.channel.ChannelHandler {
                    public Http2ServerUpgradeCodec(Http2ConnectionHandler h) {}
                    public Http2ServerUpgradeCodec(String n, Http2ConnectionHandler h) {}
                    public Http2ServerUpgradeCodec(Http2FrameCodec c, io.netty.channel.ChannelHandler... h) {}
                    public Http2ServerUpgradeCodec(String n, Http2ConnectionHandler h, io.netty.channel.ChannelHandler... extra) {}
                }
                """,
                "package io.netty.handler.codec.http2; public final class Http2SecurityUtil { public static Object CIPHERS = null; }",
                """
                package io.netty.handler.ssl;
                public class ApplicationProtocolNegotiationHandler implements io.netty.channel.ChannelHandler {
                    public ApplicationProtocolNegotiationHandler(String fallback) {}
                    public void configurePipeline() {}
                }
                """,
                builder("Http2FrameCodecBuilder", "Http2FrameCodec"),
                builder("Http2MultiplexCodecBuilder", "Http2MultiplexCodec"),
                builder("Http2ConnectionHandlerBuilder", "Http2ConnectionHandler"),
                builder("HttpToHttp2ConnectionHandlerBuilder", "Http2ConnectionHandler"),
                """
                package io.netty.handler.codec.http2;
                public class InboundHttp2ToHttpAdapterBuilder {
                    public InboundHttp2ToHttpAdapterBuilder(Http2Connection c) {}
                    public InboundHttp2ToHttpAdapterBuilder maxContentLength(int value) { return this; }
                    public InboundHttp2ToHttpAdapterBuilder propagateSettings(boolean value) { return this; }
                    public InboundHttp2ToHttpAdapter build() { return null; }
                }
                """,
                "package io.netty.handler.codec.http2; public class InboundHttp2ToHttpAdapter implements Http2FrameListener { public InboundHttp2ToHttpAdapter() {} }",
                "package io.netty.handler.codec.http2; public class HttpToHttp2ConnectionHandler extends Http2ConnectionHandler { public HttpToHttp2ConnectionHandler() {} }",
                "package io.netty.handler.codec.http2; public final class HttpConversionUtil { public static Object toHttp2Headers(Object x) { return null; } }",
                """
                package io.netty.handler.codec.http2;
                public class Http2Settings {
                    public Http2Settings maxConcurrentStreams(long value) { return this; }
                    public Http2Settings initialWindowSize(int value) { return this; }
                    public Http2Settings maxFrameSize(int value) { return this; }
                    public Http2Settings headerTableSize(long value) { return this; }
                    public Http2Settings maxHeaderListSize(long value) { return this; }
                    public Http2Settings pushEnabled(boolean value) { return this; }
                    public Http2Settings connectProtocolEnabled(boolean value) { return this; }
                }
                """,
                "package io.netty.handler.codec.http2; public class Http2Stream {}",
                """
                package io.netty.handler.codec.http2;
                public class DefaultHttp2LocalFlowController {
                    public DefaultHttp2LocalFlowController(Http2Connection c) {}
                    public DefaultHttp2LocalFlowController(Http2Connection c, float ratio, boolean autoRefill) {}
                    public boolean consumeBytes(Http2Stream stream, int bytes) { return true; }
                    public void incrementWindowSize(Http2Stream stream, int delta) {}
                    public void windowUpdateRatio(float ratio) {}
                }
                """,
                "package io.netty.handler.codec.http2; public class DefaultHttp2RemoteFlowController { public DefaultHttp2RemoteFlowController(Http2Connection c) {} public void writePendingBytes() {} }",
                "package io.netty.handler.codec.http2; public class StreamBufferingEncoder { public StreamBufferingEncoder(Http2ConnectionEncoder e) {} public void writeSettingsAck() {} }",
                "package io.netty.handler.codec.http2; public final class Http2StreamChannelOption { public static final Object AUTO_STREAM_FLOW_CONTROL = null; }",
                "package io.netty.handler.codec.http2; public final class Http2CodecUtil { public static final char SETTINGS_ENABLE_CONNECT_PROTOCOL = 8; }",
                "package io.netty.handler.codec.http2; public interface Http2PromisedRequestVerifier { boolean isAuthoritative(); }"
        };
    }

    private static String builder(String name, String product) {
        return """
                package io.netty.handler.codec.http2;
                public class %1$s {
                    public static %1$s forServer() { return new %1$s(); }
                    public static %1$s forClient() { return new %1$s(); }
                    public %1$s validateHeaders(boolean v) { return this; }
                    public %1$s validateRequiredPseudoHeaders(boolean v) { return this; }
                    public %1$s decoderEnforceMaxConsecutiveEmptyDataFrames(int v) { return this; }
                    public %1$s decoderEnforceMaxRstFramesPerWindow(int v, int s) { return this; }
                    public %1$s encoderEnforceMaxRstFramesPerWindow(int v, int s) { return this; }
                    public %1$s decoderEnforceMaxSmallContinuationFrames(int v) { return this; }
                    public %1$s encoderEnforceMaxConcurrentStreams(boolean v) { return this; }
                    public %1$s encoderEnforceMaxQueuedControlFrames(int v) { return this; }
                    public %1$s encoderIgnoreMaxHeaderListSize(boolean v) { return this; }
                    public %1$s maxReservedStreams(int v) { return this; }
                    public %1$s autoAckSettingsFrame(boolean v) { return this; }
                    public %1$s autoAckPingFrame(boolean v) { return this; }
                    public %1$s flushPreface(boolean v) { return this; }
                    public %1$s decoupleCloseAndGoAway(boolean v) { return this; }
                    public %1$s gracefulShutdownTimeoutMillis(long v) { return this; }
                    public %1$s initialSettings(Http2Settings v) { return this; }
                    public %2$s build() { return null; }
                }
                """.formatted(name, product);
    }
}
