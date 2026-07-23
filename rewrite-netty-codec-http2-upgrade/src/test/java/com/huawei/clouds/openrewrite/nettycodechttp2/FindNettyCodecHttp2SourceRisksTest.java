package com.huawei.clouds.openrewrite.nettycodechttp2;

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

class FindNettyCodecHttp2SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNettyCodecHttp2SourceRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(NettyCodecHttp2TestApi.sources()));
    }

    @ParameterizedTest(name = "header validation: {0}")
    @MethodSource("headerValidationCases")
    void marksExactHeaderValidationDecisions(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.HEADER_VALIDATION);
    }

    static Stream<Arguments> headerValidationCases() {
        return Stream.of(
                Arguments.of("headers name validation disabled", """
                        import io.netty.handler.codec.http2.DefaultHttp2Headers;
                        class C { Object headers = new DefaultHttp2Headers(false); }
                        """),
                Arguments.of("headers name validation runtime-selected", """
                        import io.netty.handler.codec.http2.DefaultHttp2Headers;
                        class C { Object headers(boolean validate) { return new DefaultHttp2Headers(validate); } }
                        """),
                Arguments.of("headers value validation disabled", """
                        import io.netty.handler.codec.http2.DefaultHttp2Headers;
                        class C { Object headers = new DefaultHttp2Headers(true, false, 16); }
                        """),
                Arguments.of("headers value validation runtime-selected", """
                        import io.netty.handler.codec.http2.DefaultHttp2Headers;
                        class C {
                            Object headers(boolean validateValues) {
                                return new DefaultHttp2Headers(true, validateValues, 16);
                            }
                        }
                        """),
                Arguments.of("decoder name validation disabled", """
                        import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
                        class C { Object decoder = new DefaultHttp2HeadersDecoder(false, 8192L); }
                        """),
                Arguments.of("decoder value validation disabled", """
                        import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
                        class C { Object decoder = new DefaultHttp2HeadersDecoder(true, false, 8192L); }
                        """),
                Arguments.of("connection decoder validation disabled", connectionDecoder(
                        "true", "true", "false", "true")),
                Arguments.of("builder validation disabled", """
                        import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
                        class C { Object codec = Http2FrameCodecBuilder.forServer().validateHeaders(false).build(); }
                        """),
                Arguments.of("builder validation runtime-selected", """
                        import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
                        class C {
                            Object codec(boolean validate) {
                                return Http2ConnectionHandlerBuilder.forServer().validateHeaders(validate).build();
                            }
                        }
                        """),
                Arguments.of("header add", """
                        import io.netty.handler.codec.http2.Http2Headers;
                        class C { void add(Http2Headers headers) { headers.add("x-name", "value"); } }
                        """),
                Arguments.of("header set", """
                        import io.netty.handler.codec.http2.Http2Headers;
                        class C { void set(Http2Headers headers) { headers.set("x-name", "value"); } }
                        """),
                Arguments.of("headers setAll", """
                        import io.netty.handler.codec.http2.Http2Headers;
                        class C { void setAll(Http2Headers target, Http2Headers source) { target.setAll(source); } }
                        """),
                Arguments.of("pseudo-header scheme", """
                        import io.netty.handler.codec.http2.Http2Headers;
                        class C { void scheme(Http2Headers headers) { headers.scheme("https"); } }
                        """),
                Arguments.of("pseudo-header authority", """
                        import io.netty.handler.codec.http2.Http2Headers;
                        class C { void authority(Http2Headers headers) { headers.authority("example.com"); } }
                        """),
                Arguments.of("pseudo-header path", """
                        import io.netty.handler.codec.http2.Http2Headers;
                        class C { void path(Http2Headers headers) { headers.path("/a"); } }
                        """),
                Arguments.of("pseudo-header method", """
                        import io.netty.handler.codec.http2.Http2Headers;
                        class C { void method(Http2Headers headers) { headers.method("CONNECT"); } }
                        """),
                Arguments.of("pseudo-header status", """
                        import io.netty.handler.codec.http2.Http2Headers;
                        class C { void status(Http2Headers headers) { headers.status("200"); } }
                        """));
    }

    @Test
    void literalTrueValidationBoundariesAreNotMarked() {
        rewriteRun(
                java("""
                        import io.netty.handler.codec.http2.DefaultHttp2Headers;
                        class NameValidation { Object headers = new DefaultHttp2Headers(true); }
                        """),
                java("""
                        import io.netty.handler.codec.http2.DefaultHttp2Headers;
                        class ValueValidation { Object headers = new DefaultHttp2Headers(true, true, 16); }
                        """),
                java("""
                        import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
                        class BuilderValidation {
                            Object codec = Http2FrameCodecBuilder.forServer().validateHeaders(true).build();
                        }
                        """));
    }

    @Test
    void enabledHeadersDecoderStillMarksLimitsButNotValidation() {
        assertMarkedAndMissing("""
                import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
                class C { Object decoder = new DefaultHttp2HeadersDecoder(true, true, 8192L); }
                """, FindNettyCodecHttp2SourceRisks.HEADER_LIMITS,
                FindNettyCodecHttp2SourceRisks.HEADER_VALIDATION);
    }

    @ParameterizedTest(name = "required pseudo-headers: {0}")
    @MethodSource("requiredPseudoCases")
    void marksImplicitDisabledOrRuntimeSelectedRequiredPseudoValidation(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.REQUIRED_PSEUDO);
    }

    static Stream<Arguments> requiredPseudoCases() {
        return Stream.of(
                Arguments.of("legacy three-argument decoder", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object decoder(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r) {
                                return new DefaultHttp2ConnectionDecoder(c, e, r);
                            }
                        }
                        """),
                Arguments.of("seven-argument decoder is implicit", connectionDecoder(
                        "true", "true", "true", null)),
                Arguments.of("eight-argument decoder disabled", connectionDecoder(
                        "true", "true", "true", "false")),
                Arguments.of("eight-argument decoder runtime-selected", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object decoder(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                                           Http2PromisedRequestVerifier v, boolean required) {
                                return new DefaultHttp2ConnectionDecoder(c, e, r, v, true, true, true, required);
                            }
                        }
                        """),
                Arguments.of("builder disabled", """
                        import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
                        class C {
                            Object codec = Http2MultiplexCodecBuilder.forServer()
                                    .validateRequiredPseudoHeaders(false).build();
                        }
                        """),
                Arguments.of("builder runtime-selected", """
                        import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
                        class C {
                            Object codec(boolean required) {
                                return Http2FrameCodecBuilder.forServer()
                                        .validateRequiredPseudoHeaders(required).build();
                            }
                        }
                        """));
    }

    @Test
    void literalTrueRequiredPseudoValidationIsNotMarked() {
        rewriteRun(
                java(connectionDecoder("true", "true", "true", "true")),
                java("""
                        import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
                        class Builder {
                            Object codec = Http2ConnectionHandlerBuilder.forServer()
                                    .validateRequiredPseudoHeaders(true).build();
                        }
                        """));
    }

    @ParameterizedTest(name = "header limits: {0}")
    @MethodSource("headerLimitCases")
    void marksExactHeaderAndHpackLimitDecisions(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.HEADER_LIMITS);
    }

    static Stream<Arguments> headerLimitCases() {
        return Stream.of(
                Arguments.of("default decoder", """
                        import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
                        class C { Object decoder = new DefaultHttp2HeadersDecoder(); }
                        """),
                Arguments.of("maximum header list", """
                        import io.netty.handler.codec.http2.Http2Settings;
                        class C { Object settings = new Http2Settings().maxHeaderListSize(65536L); }
                        """),
                Arguments.of("HPACK table size", """
                        import io.netty.handler.codec.http2.Http2Settings;
                        class C { Object settings = new Http2Settings().headerTableSize(4096L); }
                        """),
                Arguments.of("decoder HPACK table limit", """
                        import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
                        class C { void limit(DefaultHttp2HeadersDecoder decoder) { decoder.maxHeaderTableSize(4096L); } }
                        """),
                Arguments.of("encoder HPACK table limit", """
                        import io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder;
                        class C { void limit(DefaultHttp2HeadersEncoder encoder) { encoder.maxHeaderTableSize(4096L); } }
                        """),
                Arguments.of("encoder ignores peer limit", """
                        import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
                        class C {
                            Object codec = Http2FrameCodecBuilder.forServer()
                                    .encoderIgnoreMaxHeaderListSize(true).build();
                        }
                        """));
    }

    @ParameterizedTest(name = "abuse limits: {0}")
    @MethodSource("abuseLimitCases")
    void marksExactControlFrameAndStreamAbuseLimits(String label, String methodCall) {
        assertMarked(builderCall(methodCall), FindNettyCodecHttp2SourceRisks.ABUSE_LIMITS);
    }

    static Stream<Arguments> abuseLimitCases() {
        return Stream.of(
                Arguments.of("empty DATA", "decoderEnforceMaxConsecutiveEmptyDataFrames(2)"),
                Arguments.of("decoder RST window", "decoderEnforceMaxRstFramesPerWindow(200, 30)"),
                Arguments.of("encoder RST window", "encoderEnforceMaxRstFramesPerWindow(200, 30)"),
                Arguments.of("small CONTINUATION", "decoderEnforceMaxSmallContinuationFrames(8)"),
                Arguments.of("encoder concurrent streams", "encoderEnforceMaxConcurrentStreams(true)"),
                Arguments.of("queued control frames", "encoderEnforceMaxQueuedControlFrames(128)"),
                Arguments.of("reserved streams", "maxReservedStreams(32)"));
    }

    @Test
    void marksSettingsConcurrentStreamsAsAnAbuseBoundary() {
        assertMarked("""
                import io.netty.handler.codec.http2.Http2Settings;
                class C { Object settings = new Http2Settings().maxConcurrentStreams(100L); }
                """, FindNettyCodecHttp2SourceRisks.ABUSE_LIMITS);
    }

    @Test
    void marksMaximumFrameSizeAsAnAbuseBoundary() {
        assertMarked("""
                import io.netty.handler.codec.http2.Http2Settings;
                class C { Object settings = new Http2Settings().maxFrameSize(16384); }
                """, FindNettyCodecHttp2SourceRisks.ABUSE_LIMITS);
    }

    @ParameterizedTest(name = "decompression: {0}")
    @MethodSource("decompressionCases")
    void marksEveryTypedDecompressorConstruction(String label, String expression) {
        assertMarked("""
                import io.netty.handler.codec.http2.*;
                class C {
                    Object decoder(Http2Connection connection, Http2FrameListener listener) {
                        return %s;
                    }
                }
                """.formatted(expression), FindNettyCodecHttp2SourceRisks.DECOMPRESSION);
    }

    static Stream<Arguments> decompressionCases() {
        return Stream.of(
                Arguments.of("legacy two-argument", "new DelegatingDecompressorFrameListener(connection, listener)"),
                Arguments.of("bounded allocation", "new DelegatingDecompressorFrameListener(connection, listener, 1048576)"),
                Arguments.of("strict legacy overload", "new DelegatingDecompressorFrameListener(connection, listener, true)"),
                Arguments.of("strict bounded allocation",
                        "new DelegatingDecompressorFrameListener(connection, listener, true, 1048576)"));
    }

    @ParameterizedTest(name = "flow control: {0}")
    @MethodSource("flowControlCases")
    void marksExactFlowControlAndBackpressureBoundaries(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.FLOW_CONTROL);
    }

    static Stream<Arguments> flowControlCases() {
        return Stream.of(
                Arguments.of("local controller construction", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object flow(Http2Connection c) { return new DefaultHttp2LocalFlowController(c); }
                        }
                        """),
                Arguments.of("auto-refill connection window", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object flow(Http2Connection c) {
                                return new DefaultHttp2LocalFlowController(c, 0.5f, true);
                            }
                        }
                        """),
                Arguments.of("remote controller construction", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object flow(Http2Connection c) { return new DefaultHttp2RemoteFlowController(c); }
                        }
                        """),
                Arguments.of("stream buffering encoder construction", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object encoder(Http2ConnectionEncoder e) { return new StreamBufferingEncoder(e); }
                        }
                        """),
                Arguments.of("consume bytes", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            void consume(DefaultHttp2LocalFlowController flow, Http2Stream stream) {
                                flow.consumeBytes(stream, 1024);
                            }
                        }
                        """),
                Arguments.of("increment stream window", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            void increment(DefaultHttp2LocalFlowController flow, Http2Stream stream) {
                                flow.incrementWindowSize(stream, 1024);
                            }
                        }
                        """),
                Arguments.of("window update ratio", """
                        import io.netty.handler.codec.http2.DefaultHttp2LocalFlowController;
                        class C {
                            void ratio(DefaultHttp2LocalFlowController flow) { flow.windowUpdateRatio(0.5f); }
                        }
                        """),
                Arguments.of("write pending bytes", """
                        import io.netty.handler.codec.http2.DefaultHttp2RemoteFlowController;
                        class C { void write(DefaultHttp2RemoteFlowController flow) { flow.writePendingBytes(); } }
                        """),
                Arguments.of("initial stream window", """
                        import io.netty.handler.codec.http2.Http2Settings;
                        class C { Object settings = new Http2Settings().initialWindowSize(65535); }
                        """),
                Arguments.of("automatic stream flow-control option", """
                        import io.netty.handler.codec.http2.Http2StreamChannelOption;
                        class C { Object option = Http2StreamChannelOption.AUTO_STREAM_FLOW_CONTROL; }
                        """));
    }

    @ParameterizedTest(name = "ACK/preface: {0}")
    @MethodSource("ackPrefaceCases")
    void marksExactAckPrefaceAndShutdownDecisions(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.ACK_PREFACE);
    }

    static Stream<Arguments> ackPrefaceCases() {
        return Stream.of(
                Arguments.of("manual SETTINGS acknowledgement", connectionDecoder(
                        "false", "true", "true", "true")),
                Arguments.of("manual PING acknowledgement", connectionDecoder(
                        "true", "false", "true", "true")),
                Arguments.of("builder SETTINGS acknowledgement", builderCall("autoAckSettingsFrame(false)")),
                Arguments.of("builder PING acknowledgement", builderCall("autoAckPingFrame(false)")),
                Arguments.of("explicit preface flush", builderCall("flushPreface(false)")),
                Arguments.of("decoupled close and GOAWAY", builderCall("decoupleCloseAndGoAway(true)")),
                Arguments.of("graceful timeout", builderCall("gracefulShutdownTimeoutMillis(30000L)")),
                Arguments.of("buffered encoder SETTINGS ACK", """
                        import io.netty.handler.codec.http2.StreamBufferingEncoder;
                        class C { void ack(StreamBufferingEncoder encoder) { encoder.writeSettingsAck(); } }
                        """));
    }

    @ParameterizedTest(name = "upgrade and ALPN: {0}")
    @MethodSource("upgradeAlpnCases")
    void marksExactTlsAlpnAndH2cUpgradeBoundaries(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.UPGRADE_ALPN);
    }

    static Stream<Arguments> upgradeAlpnCases() {
        return Stream.of(
                Arguments.of("client h2c upgrade", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object upgrade(Http2ConnectionHandler h) { return new Http2ClientUpgradeCodec(h); }
                        }
                        """),
                Arguments.of("server handler upgrade", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object upgrade(Http2ConnectionHandler h) { return new Http2ServerUpgradeCodec(h); }
                        }
                        """),
                Arguments.of("server named two-argument upgrade", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object upgrade(Http2ConnectionHandler h) {
                                return new Http2ServerUpgradeCodec("h2c", h);
                            }
                        }
                        """),
                Arguments.of("frame-codec upgrade", """
                        import io.netty.channel.ChannelHandler;
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object upgrade(Http2FrameCodec codec, ChannelHandler h) {
                                return new Http2ServerUpgradeCodec(codec, h);
                            }
                        }
                        """),
                Arguments.of("ALPN handler", """
                        import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
                        class C { Object alpn = new ApplicationProtocolNegotiationHandler("http/1.1"); }
                        """),
                Arguments.of("ALPN pipeline callback", """
                        import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
                        class C {
                            void configure(ApplicationProtocolNegotiationHandler alpn) {
                                alpn.configurePipeline();
                            }
                        }
                        """),
                Arguments.of("HTTP/2 TLS cipher suite", """
                        import io.netty.handler.codec.http2.Http2SecurityUtil;
                        class C { Object ciphers = Http2SecurityUtil.CIPHERS; }
                        """));
    }

    @ParameterizedTest(name = "HTTP conversion: {0}")
    @MethodSource("conversionCases")
    void marksExactHttpConversionAndAggregationBoundaries(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.CONVERSION);
    }

    static Stream<Arguments> conversionCases() {
        return Stream.of(
                Arguments.of("inbound adapter builder", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object adapter(Http2Connection c) { return new InboundHttp2ToHttpAdapterBuilder(c); }
                        }
                        """),
                Arguments.of("maximum converted content", """
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object adapter(Http2Connection c) {
                                return new InboundHttp2ToHttpAdapterBuilder(c).maxContentLength(1048576).build();
                            }
                        }
                        """),
                Arguments.of("inbound adapter", """
                        import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
                        class C { Object adapter = new InboundHttp2ToHttpAdapter(); }
                        """),
                Arguments.of("HTTP-to-HTTP2 handler", """
                        import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
                        class C { Object handler = new HttpToHttp2ConnectionHandler(); }
                        """),
                Arguments.of("header conversion utility", """
                        import io.netty.handler.codec.http2.HttpConversionUtil;
                        class C { Object convert(Object headers) { return HttpConversionUtil.toHttp2Headers(headers); } }
                        """));
    }

    @ParameterizedTest(name = "multiplex lifecycle: {0}")
    @MethodSource("multiplexCases")
    void marksExactFrameAndStreamMultiplexBoundaries(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.MULTIPLEX);
    }

    static Stream<Arguments> multiplexCases() {
        return Stream.of(
                Arguments.of("frame codec", """
                        import io.netty.handler.codec.http2.Http2FrameCodec;
                        class C { Object codec = new Http2FrameCodec(); }
                        """),
                Arguments.of("multiplex codec", """
                        import io.netty.handler.codec.http2.Http2MultiplexCodec;
                        class C { Object codec = new Http2MultiplexCodec(); }
                        """),
                Arguments.of("multiplex handler", """
                        import io.netty.channel.ChannelHandler;
                        import io.netty.handler.codec.http2.Http2MultiplexHandler;
                        class C {
                            Object handler(ChannelHandler child) { return new Http2MultiplexHandler(child); }
                        }
                        """),
                Arguments.of("stream channel close", """
                        import io.netty.handler.codec.http2.Http2StreamChannel;
                        class C { void close(Http2StreamChannel channel) { channel.close(); } }
                        """),
                Arguments.of("real frame stream id", """
                        import io.netty.handler.codec.http2.Http2FrameStream;
                        class C { int id(Http2FrameStream stream) { return stream.id(); } }
                        """),
                Arguments.of("unknown frame", """
                        import io.netty.handler.codec.http2.Http2UnknownFrame;
                        class C { Object frame = new Http2UnknownFrame(); }
                        """),
                Arguments.of("priority frame", """
                        import io.netty.handler.codec.http2.Http2PriorityFrame;
                        class C { Object frame = new Http2PriorityFrame(); }
                        """));
    }

    @ParameterizedTest(name = "PUSH/CONNECT: {0}")
    @MethodSource("pushConnectCases")
    void marksExactPushAndExtendedConnectBoundaries(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.PUSH_CONNECT);
    }

    static Stream<Arguments> pushConnectCases() {
        return Stream.of(
                Arguments.of("PUSH_PROMISE frame", """
                        import io.netty.handler.codec.http2.Http2PushPromiseFrame;
                        class C { Object frame = new Http2PushPromiseFrame(); }
                        """),
                Arguments.of("promised request verifier", """
                        import io.netty.handler.codec.http2.Http2PromisedRequestVerifier;
                        class C { boolean verify(Http2PromisedRequestVerifier v) { return v.isAuthoritative(); } }
                        """),
                Arguments.of("push enabled setting", """
                        import io.netty.handler.codec.http2.Http2Settings;
                        class C { Object settings = new Http2Settings().pushEnabled(false); }
                        """),
                Arguments.of("extended CONNECT setting", """
                        import io.netty.handler.codec.http2.Http2Settings;
                        class C { Object settings = new Http2Settings().connectProtocolEnabled(true); }
                        """),
                Arguments.of("extended CONNECT setting key", """
                        import io.netty.handler.codec.http2.Http2CodecUtil;
                        class C { char setting = Http2CodecUtil.SETTINGS_ENABLE_CONNECT_PROTOCOL; }
                        """),
                Arguments.of("PUSH_PROMISE listener callback", """
                        import io.netty.channel.ChannelHandlerContext;
                        import io.netty.handler.codec.http2.*;
                        class C {
                            void push(Http2FrameListener listener, ChannelHandlerContext ctx, Http2Headers headers) {
                                listener.onPushPromiseRead(ctx, 1, 3, headers, 0);
                            }
                        }
                        """));
    }

    @Test
    void marksHeaderEncoderConstructionAndCloseExactly() {
        rewriteRun(java("""
                import io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder;
                class C {
                    Object create() { return new DefaultHttp2HeadersEncoder(); }
                    void close(DefaultHttp2HeadersEncoder encoder) { encoder.close(); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertCount(after.printAll(), FindNettyCodecHttp2SourceRisks.ENCODER_RESOURCE, 2))));
    }

    @ParameterizedTest(name = "custom codec: {0}")
    @MethodSource("customCodecCases")
    void marksExactCustomBuilderHandlerAndListenerExtensions(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.CUSTOM_CODEC);
    }

    static Stream<Arguments> customCodecCases() {
        return Stream.of(
                Arguments.of("frame codec builder", """
                        import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
                        class CustomBuilder extends Http2FrameCodecBuilder {}
                        """),
                Arguments.of("connection handler", """
                        import io.netty.handler.codec.http2.Http2ConnectionHandler;
                        class CustomHandler extends Http2ConnectionHandler {}
                        """),
                Arguments.of("frame codec", """
                        import io.netty.handler.codec.http2.Http2FrameCodec;
                        class CustomCodec extends Http2FrameCodec {}
                        """),
                Arguments.of("frame listener", """
                        import io.netty.handler.codec.http2.Http2FrameListener;
                        class CustomListener implements Http2FrameListener {}
                        """));
    }

    @ParameterizedTest(name = "4.2-only API: {0}")
    @MethodSource("branchApiCases")
    void marksExact42OnlyMethodsReferencesAndUpgradeConstructor(String label, String source) {
        assertMarked(source, FindNettyCodecHttp2SourceRisks.BRANCH_API);
    }

    static Stream<Arguments> branchApiCases() {
        return Stream.of(
                Arguments.of("name validator method", """
                        import io.netty.handler.codec.http2.DefaultHttp2Headers;
                        class C { Object validator = DefaultHttp2Headers.defaultHtt2NameValidator(); }
                        """),
                Arguments.of("value validator method", """
                        import io.netty.handler.codec.http2.DefaultHttp2Headers;
                        class C { Object validator = DefaultHttp2Headers.defaultHttp2ValueValidator(); }
                        """),
                Arguments.of("name validator member reference", """
                        import java.util.function.Supplier;
                        import io.netty.handler.codec.http2.DefaultHttp2Headers;
                        class C { Supplier<Object> validator = DefaultHttp2Headers::defaultHtt2NameValidator; }
                        """),
                Arguments.of("value validator member reference", """
                        import java.util.function.Supplier;
                        import io.netty.handler.codec.http2.DefaultHttp2Headers;
                        class C { Supplier<Object> validator = DefaultHttp2Headers::defaultHttp2ValueValidator; }
                        """),
                Arguments.of("named handler with one extra handler", """
                        import io.netty.channel.ChannelHandler;
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object upgrade(Http2ConnectionHandler h, ChannelHandler extra) {
                                return new Http2ServerUpgradeCodec("h2c", h, extra);
                            }
                        }
                        """),
                Arguments.of("named handler with several extra handlers", """
                        import io.netty.channel.ChannelHandler;
                        import io.netty.handler.codec.http2.*;
                        class C {
                            Object upgrade(Http2ConnectionHandler h, ChannelHandler a, ChannelHandler b) {
                                return new Http2ServerUpgradeCodec("h2c", h, a, b);
                            }
                        }
                        """));
    }

    @Test
    void targetBranchServerUpgradeOverloadsAreUpgradeRisksNotBranchApis() {
        rewriteRun(java("""
                import io.netty.channel.ChannelHandler;
                import io.netty.handler.codec.http2.*;
                class C {
                    Object one(Http2ConnectionHandler handler) {
                        return new Http2ServerUpgradeCodec(handler);
                    }
                    Object two(Http2ConnectionHandler handler) {
                        return new Http2ServerUpgradeCodec("h2c", handler);
                    }
                    Object frame(Http2FrameCodec codec, ChannelHandler handler) {
                        return new Http2ServerUpgradeCodec(codec, handler);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertCount(printed, FindNettyCodecHttp2SourceRisks.UPGRADE_ALPN, 3);
            assertMissing(printed, FindNettyCodecHttp2SourceRisks.BRANCH_API);
        })));
    }

    @Test
    void exactSimpleNamesOnApplicationTypesAreNoop() {
        rewriteRun(java("""
                interface Http2FrameListener {}
                class DefaultHttp2Headers {
                    DefaultHttp2Headers(boolean validate) {}
                    static Object defaultHtt2NameValidator() { return null; }
                    static Object defaultHttp2ValueValidator() { return null; }
                    void add(String name, String value) {}
                }
                class Http2FrameCodecBuilder {
                    Http2FrameCodecBuilder validateHeaders(boolean value) { return this; }
                    Http2FrameCodecBuilder decoderEnforceMaxRstFramesPerWindow(int value, int seconds) { return this; }
                }
                class DelegatingDecompressorFrameListener {
                    DelegatingDecompressorFrameListener(Object c, Http2FrameListener l) {}
                }
                class Http2ServerUpgradeCodec {
                    Http2ServerUpgradeCodec(String name, Object handler, Object... extras) {}
                }
                class Http2SecurityUtil {
                    static Object CIPHERS;
                }
                class C {
                    Object ciphers = Http2SecurityUtil.CIPHERS;
                    void use(DefaultHttp2Headers headers, Http2FrameCodecBuilder builder, Http2FrameListener listener) {
                        new DefaultHttp2Headers(false);
                        DefaultHttp2Headers.defaultHtt2NameValidator();
                        DefaultHttp2Headers.defaultHttp2ValueValidator();
                        headers.add("name", "value");
                        builder.validateHeaders(false).decoderEnforceMaxRstFramesPerWindow(1, 1);
                        new DelegatingDecompressorFrameListener(new Object(), listener);
                        new Http2ServerUpgradeCodec("h2c", new Object(), new Object());
                    }
                }
                """));
    }

    @Test
    void sameNamedMethodsOnNonHttp2OwnersAreNoop() {
        rewriteRun(java("""
                class BusinessSettings {
                    BusinessSettings maxHeaderListSize(long value) { return this; }
                    BusinessSettings maxConcurrentStreams(long value) { return this; }
                    BusinessSettings initialWindowSize(int value) { return this; }
                    BusinessSettings connectProtocolEnabled(boolean value) { return this; }
                    BusinessSettings autoAckPingFrame(boolean value) { return this; }
                }
                class C {
                    Object configure() {
                        return new BusinessSettings().maxHeaderListSize(10).maxConcurrentStreams(2)
                                .initialWindowSize(1).connectProtocolEnabled(true).autoAckPingFrame(false);
                    }
                }
                """));
    }

    @Test
    void generatedAndInstalledSourcesAreNoop() {
        String source = """
                import io.netty.handler.codec.http2.*;
                class %s {
                    Object codec = Http2FrameCodecBuilder.forServer()
                            .validateHeaders(false).decoderEnforceMaxRstFramesPerWindow(10, 1).build();
                }
                """;
        rewriteRun(
                java(source.formatted("MavenGenerated"), spec ->
                        spec.path("target/generated-sources/http2/MavenGenerated.java")),
                java(source.formatted("GradleGenerated"), spec ->
                        spec.path("build/generated/source/http2/GradleGenerated.java")),
                java(source.formatted("Installed"), spec ->
                        spec.path("installation/lib/Installed.java")),
                java(source.formatted("Cached"), spec ->
                        spec.path(".m2/repository/Cached.java")));
    }

    @Test
    void sourceMarkersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
                        class C {
                            Object codec = Http2FrameCodecBuilder.forServer()
                                    .validateHeaders(false)
                                    .validateRequiredPseudoHeaders(false)
                                    .decoderEnforceMaxRstFramesPerWindow(100, 30)
                                    .build();
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertCount(printed, FindNettyCodecHttp2SourceRisks.HEADER_VALIDATION, 1);
                    assertCount(printed, FindNettyCodecHttp2SourceRisks.REQUIRED_PSEUDO, 1);
                    assertCount(printed, FindNettyCodecHttp2SourceRisks.ABUSE_LIMITS, 1);
                })));
    }

    @Test
    void marksApacheCxfNettyHttp2PipelineFixture() {
        // Reduced from:
        // https://github.com/apache/cxf/blob/eca4abb9966e2d2eb5e987a080d059bf9e0ff47a/rt/transports/http-netty/netty-client/src/main/java/org/apache/cxf/transport/http/netty/client/NettyHttpClientPipelineFactory.java#L127-L146
        rewriteRun(java("""
                import io.netty.handler.codec.http2.*;
                import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
                class NettyHttpClientPipelineFactory {
                    Object handler(Http2Connection connection, Http2FrameListener listener, int maxContentLength) {
                        return new DelegatingDecompressorFrameListener(connection,
                                new InboundHttp2ToHttpAdapterBuilder(connection)
                                        .maxContentLength(maxContentLength)
                                        .build());
                    }
                    Object alpn() {
                        return new ApplicationProtocolNegotiationHandler("http/1.1");
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertContains(printed, FindNettyCodecHttp2SourceRisks.DECOMPRESSION);
            assertContains(printed, FindNettyCodecHttp2SourceRisks.CONVERSION);
            assertContains(printed, FindNettyCodecHttp2SourceRisks.UPGRADE_ALPN);
        })));
    }

    @Test
    void marksEclipseVertxCustomFrameCodecFixture() {
        // Reduced from:
        // https://github.com/eclipse-vertx/vert.x/blob/3ef9c8fef187a4a604953f0914e76de97cf28a45/vertx-core/src/main/java/io/vertx/core/http/impl/http2/multiplex/Http2CustomFrameCodecBuilder.java
        rewriteRun(java("""
                import io.netty.handler.codec.http2.*;
                class Http2CustomFrameCodecBuilder extends Http2FrameCodecBuilder {
                    Http2FrameCodec configure(Http2Connection connection, Http2FrameListener listener,
                                              Http2Settings settings) {
                        new DelegatingDecompressorFrameListener(connection, listener, 0);
                        return decoderEnforceMaxSmallContinuationFrames(8)
                                .decoderEnforceMaxRstFramesPerWindow(200, 30)
                                .encoderEnforceMaxRstFramesPerWindow(200, 30)
                                .initialSettings(settings)
                                .build();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertContains(printed, FindNettyCodecHttp2SourceRisks.CUSTOM_CODEC);
            assertContains(printed, FindNettyCodecHttp2SourceRisks.ABUSE_LIMITS);
            assertContains(printed, FindNettyCodecHttp2SourceRisks.DECOMPRESSION);
        })));
    }

    @Test
    void marksNetflixZuulHttp2ConfigurationFixture() {
        // Reduced from:
        // https://github.com/Netflix/zuul/blob/9bea4a7244711104e965214b99152baee065ede8/zuul-core/src/main/java/com/netflix/zuul/netty/server/http2/Http2OrHttpHandler.java#L153-L174
        rewriteRun(java("""
                import io.netty.handler.codec.http2.*;
                class Http2OrHttpHandler {
                    Http2FrameCodec configure(long maxStreams, int window, long table, long headers,
                                              boolean connect, int maxRst, int rstWindow, int continuations) {
                        Http2Settings settings = new Http2Settings()
                                .maxConcurrentStreams(maxStreams)
                                .initialWindowSize(window)
                                .headerTableSize(table)
                                .maxHeaderListSize(headers)
                                .connectProtocolEnabled(connect);
                        return Http2FrameCodecBuilder.forServer()
                                .initialSettings(settings)
                                .validateHeaders(true)
                                .encoderEnforceMaxRstFramesPerWindow(maxRst, rstWindow)
                                .decoderEnforceMaxSmallContinuationFrames(continuations)
                                .gracefulShutdownTimeoutMillis(30000L)
                                .build();
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertContains(printed, FindNettyCodecHttp2SourceRisks.ABUSE_LIMITS);
            assertContains(printed, FindNettyCodecHttp2SourceRisks.FLOW_CONTROL);
            assertContains(printed, FindNettyCodecHttp2SourceRisks.HEADER_LIMITS);
            assertContains(printed, FindNettyCodecHttp2SourceRisks.PUSH_CONNECT);
            assertContains(printed, FindNettyCodecHttp2SourceRisks.ACK_PREFACE);
        })));
    }

    private static String connectionDecoder(String settings, String ping, String headers, String required) {
        String tail = required == null ? "" : ", " + required;
        return """
                import io.netty.handler.codec.http2.*;
                class C {
                    Object decoder(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                                   Http2PromisedRequestVerifier v) {
                        return new DefaultHttp2ConnectionDecoder(c, e, r, v, %s, %s, %s%s);
                    }
                }
                """.formatted(settings, ping, headers, tail);
    }

    private static String builderCall(String methodCall) {
        return """
                import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
                class C {
                    Object codec = Http2FrameCodecBuilder.forServer().%s.build();
                }
                """.formatted(methodCall);
    }

    private void assertMarked(String source, String message) {
        rewriteRun(java(source, spec -> spec.after(actual -> actual).afterRecipe(after ->
                assertContains(after.printAll(), message))));
    }

    private void assertMarkedAndMissing(String source, String present, String missing) {
        rewriteRun(java(source, spec -> spec.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertContains(printed, present);
            assertMissing(printed, missing);
        })));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertMissing(String actual, String unexpected) {
        assertFalse(actual.contains(unexpected), () -> "Did not expect <" + unexpected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) {
            found++;
        }
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
