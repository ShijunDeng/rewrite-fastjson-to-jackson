package com.huawei.clouds.openrewrite.nettyhandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindNettyHandler41136SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNettyHandler41136SourceRisks()).parser(targetParser());
    }

    @ParameterizedTest(name = "source risk {0}")
    @MethodSource("sourceRisks")
    void marksTypedHandlerRisk(String label, String source, String message) {
        rewriteRun(java(source, spec -> spec.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(message), after.printAll()))));
    }

    static Stream<Arguments> sourceRisks() {
        return Stream.of(
                Arguments.of("TLS context", """
                        import io.netty.handler.ssl.*;
                        class C { SslContext context() throws Exception {
                            return SslContextBuilder.forClient().protocols("TLSv1.3").build();
                        } }
                        """, FindNettyHandler41136SourceRisks.TLS_CONTEXT),
                Arguments.of("record detection", """
                        import io.netty.buffer.ByteBuf;
                        import io.netty.handler.ssl.SslHandler;
                        class C { boolean encrypted(ByteBuf in) { return SslHandler.isEncrypted(in, false); } }
                        """, FindNettyHandler41136SourceRisks.TLS_DETECTION),
                Arguments.of("fingerprint trust", """
                        import io.netty.handler.ssl.util.FingerprintTrustManagerFactory;
                        class C { Object trust() {
                            return new FingerprintTrustManagerFactory("00:11:22:33");
                        } }
                        """, FindNettyHandler41136SourceRisks.TRUST),
                Arguments.of("insecure trust", """
                        import io.netty.handler.ssl.SslContextBuilder;
                        import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
                        class C { Object trust() {
                            return SslContextBuilder.forClient()
                                    .trustManager(InsecureTrustManagerFactory.INSTANCE);
                        } }
                        """, FindNettyHandler41136SourceRisks.TRUST),
                Arguments.of("legacy TLS", """
                        import io.netty.handler.ssl.SslProtocols;
                        class C { String protocol = SslProtocols.TLS_v1_1; }
                        """, FindNettyHandler41136SourceRisks.LEGACY_TLS),
                Arguments.of("native OpenSSL", """
                        import io.netty.handler.ssl.OpenSsl;
                        class C { boolean nativeAvailable() { return OpenSsl.isAvailable(); } }
                        """, FindNettyHandler41136SourceRisks.NATIVE_RESOURCE),
                Arguments.of("SNI", """
                        import io.netty.handler.ssl.SniHandler;
                        class C { String host(SniHandler handler) { return handler.hostname(); } }
                        """, FindNettyHandler41136SourceRisks.SNI),
                Arguments.of("ALPN", """
                        import io.netty.handler.ssl.JdkAlpnApplicationProtocolNegotiator;
                        class C { Object alpn() {
                            return new JdkAlpnApplicationProtocolNegotiator("h2", "http/1.1");
                        } }
                        """, FindNettyHandler41136SourceRisks.ALPN),
                Arguments.of("idle timeout", """
                        import io.netty.handler.timeout.IdleStateHandler;
                        class C { Object idle() { return new IdleStateHandler(10, 20, 30); } }
                        """, FindNettyHandler41136SourceRisks.TIMEOUT_IDLE),
                Arguments.of("traffic shaping", """
                        import io.netty.handler.traffic.ChannelTrafficShapingHandler;
                        class C { Object traffic() { return new ChannelTrafficShapingHandler(1024); } }
                        """, FindNettyHandler41136SourceRisks.TRAFFIC),
                Arguments.of("flow control", """
                        import io.netty.handler.flow.FlowControlHandler;
                        class C { Object flow() { return new FlowControlHandler(); } }
                        """, FindNettyHandler41136SourceRisks.FLOW),
                Arguments.of("IP filter", """
                        import io.netty.handler.ipfilter.RuleBasedIpFilter;
                        class C { Object filter() { return new RuleBasedIpFilter(true); } }
                        """, FindNettyHandler41136SourceRisks.IP_FILTER),
                Arguments.of("proxy", """
                        import io.netty.handler.proxy.HttpProxyHandler;
                        import java.net.InetSocketAddress;
                        class C { Object proxy() {
                            return new HttpProxyHandler(new InetSocketAddress("proxy", 8080));
                        } }
                        """, FindNettyHandler41136SourceRisks.PROXY),
                Arguments.of("logging", """
                        import io.netty.handler.logging.*;
                        class C { Object logging() {
                            return new LoggingHandler(LogLevel.DEBUG, ByteBufFormat.HEX_DUMP);
                        } }
                        """, FindNettyHandler41136SourceRisks.LOGGING),
                Arguments.of("PCAP", """
                        import io.netty.handler.pcap.PcapWriteHandler;
                        import java.io.OutputStream;
                        class C { Object pcap() {
                            return new PcapWriteHandler(OutputStream.nullOutputStream());
                        } }
                        """, FindNettyHandler41136SourceRisks.PCAP),
                Arguments.of("chunked writes", """
                        import io.netty.handler.stream.ChunkedWriteHandler;
                        class C { Object chunks() { return new ChunkedWriteHandler(16); } }
                        """, FindNettyHandler41136SourceRisks.CHUNKED),
                Arguments.of("pipeline lifecycle", """
                        import io.netty.channel.ChannelPipeline;
                        class C { void remove(ChannelPipeline pipeline) { pipeline.remove("ssl"); } }
                        """, FindNettyHandler41136SourceRisks.PIPELINE),
                Arguments.of("transport option", """
                        import io.netty.channel.ChannelOption;
                        class C { Object option = ChannelOption.AUTO_READ; }
                        """, FindNettyHandler41136SourceRisks.TRANSPORT)
        );
    }

    @Test
    void marksAllProvenFourTwoOnlyApis() {
        rewriteRun(spec -> spec.parser(branchParser()),
                java("""
                        import io.netty.handler.ssl.OpenSsl;
                        import io.netty.handler.ssl.OpenSslContextOption;
                        import io.netty.handler.ssl.SslContextBuilder;
                        import io.netty.handler.ssl.util.SelfSignedCertificate;
                        import javax.net.ssl.SNIServerName;

                        class BranchApis {
                            void configure(SslContextBuilder builder, SNIServerName name) {
                                builder.serverName(name);
                                SelfSignedCertificate.builder();
                                OpenSsl.isRenegotiationSupported();
                                Object option = OpenSslContextOption.USE_JDK_PROVIDER_SIGNATURES;
                            }
                        }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertTrue(occurrences(after.printAll(),
                                FindNettyHandler41136SourceRisks.BRANCH_ONLY) >= 4, after.printAll()))));
    }

    @Test
    void marksApacheDubboFixedCommitTlsFixture() {
        // Reduced from Apache Dubbo @ eb1d8abaebdc2ce1e15d6236cf9f9179d34e9082 (Apache-2.0).
        rewriteRun(java("""
                /*
                 * Licensed to the Apache Software Foundation (ASF) under one
                 * or more contributor license agreements.
                 */
                package org.apache.dubbo.remoting.transport.netty4.ssl;

                import io.netty.buffer.ByteBuf;
                import io.netty.channel.ChannelHandlerContext;
                import io.netty.handler.ssl.SslContext;
                import io.netty.handler.ssl.SslHandler;

                class SslServerTlsHandler {
                    void decode(ChannelHandlerContext ctx, ByteBuf buf, SslContext sslContext) {
                        if (SslHandler.isEncrypted(buf)) {
                            SslHandler sslHandler = sslContext.newHandler(ctx.alloc());
                            ctx.pipeline().addFirst("ssl", sslHandler);
                        }
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
            String printed = after.printAll();
            assertTrue(printed.contains(FindNettyHandler41136SourceRisks.TLS_DETECTION), printed);
            assertTrue(printed.contains(FindNettyHandler41136SourceRisks.TLS_CONTEXT), printed);
            assertTrue(printed.contains(FindNettyHandler41136SourceRisks.PIPELINE), printed);
        })));
    }

    @Test
    void marksApacheRocketMqFixedCommitDetectionFixture() {
        // Reduced from Apache RocketMQ @ 577b89f2cdddf0d42cfd3c1b6effdac0cd0e467c (Apache-2.0).
        rewriteRun(java("""
                /*
                 * Licensed to the Apache Software Foundation (ASF) under one
                 * or more contributor license agreements.
                 */
                package org.apache.rocketmq.proxy.grpc;

                import io.netty.buffer.ByteBuf;
                import io.netty.handler.ssl.SslHandler;

                class ProxyAndTlsProtocolNegotiator {
                    boolean detectsTls(ByteBuf in) {
                        return in.readableBytes() >= 5 && SslHandler.isEncrypted(in);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                assertTrue(after.printAll().contains(FindNettyHandler41136SourceRisks.TLS_DETECTION),
                        after.printAll()))));
    }

    @Test
    void typeAttributionPreventsBusinessLookalikes() {
        rewriteRun(java("""
                package com.acme;
                class SslHandler { static boolean isEncrypted(Object b) { return true; } }
                class IdleStateHandler { IdleStateHandler(int a, int b, int c) {} }
                class C {
                    boolean test(Object b) { return SslHandler.isEncrypted(b); }
                    Object idle() { return new IdleStateHandler(1, 2, 3); }
                }
                """));
    }

    @Test
    void markersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                java("""
                        import io.netty.handler.timeout.IdleStateHandler;
                        class C { Object idle() { return new IdleStateHandler(1, 2, 3); } }
                        """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertEquals(1, occurrences(after.printAll(),
                                FindNettyHandler41136SourceRisks.TIMEOUT_IDLE)))));
    }

    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().classpath(
                "netty-handler", "netty-buffer", "netty-common", "netty-transport", "netty-codec")
                .dependsOn("""
                        package io.netty.handler.proxy;
                        public class HttpProxyHandler {
                            public HttpProxyHandler(java.net.SocketAddress address) {}
                        }
                        """);
    }

    private static JavaParser.Builder<?, ?> branchParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                """
                package io.netty.handler.ssl;
                public class SslContextBuilder {
                    public SslContextBuilder serverName(javax.net.ssl.SNIServerName name) { return this; }
                }
                """,
                """
                package io.netty.handler.ssl;
                public final class OpenSsl {
                    public static boolean isRenegotiationSupported() { return true; }
                }
                """,
                """
                package io.netty.handler.ssl;
                public final class OpenSslContextOption {
                    public static final Object USE_JDK_PROVIDER_SIGNATURES = new Object();
                }
                """,
                """
                package io.netty.handler.ssl.util;
                public final class SelfSignedCertificate {
                    public static Builder builder() { return new Builder(); }
                    public static final class Builder {}
                }
                """);
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
