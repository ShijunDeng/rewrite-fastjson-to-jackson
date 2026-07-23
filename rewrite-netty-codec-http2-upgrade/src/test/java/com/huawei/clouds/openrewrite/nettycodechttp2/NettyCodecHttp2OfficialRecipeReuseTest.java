package com.huawei.clouds.openrewrite.nettycodechttp2;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.AddLiteralMethodArgument;
import org.openrewrite.java.DeleteMethodArgument;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class NettyCodecHttp2OfficialRecipeReuseTest implements RewriteTest {
    private static final String LOCAL_RECOMMENDED =
            "com.huawei.clouds.openrewrite.nettycodechttp2.MigrateNettyCodecHttp2To4_1_136";
    private static final String OFFICIAL_4_2 = "org.openrewrite.netty.UpgradeNetty_4_1_to_4_2";

    @Test
    void constructorMigrationUsesExactOfficialCoreDelegates() {
        List<Recipe> delegates = MigrateDeprecatedHttp2Constructors.officialCoreRecipes();
        assertEquals(4, delegates.size());

        AddLiteralMethodArgument connection = assertInstanceOf(
                AddLiteralMethodArgument.class, delegates.get(0));
        assertEquals(
                "io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder <constructor>(" +
                "io.netty.handler.codec.http2.Http2Connection," +
                "io.netty.handler.codec.http2.Http2ConnectionEncoder," +
                "io.netty.handler.codec.http2.Http2FrameReader," +
                "io.netty.handler.codec.http2.Http2PromisedRequestVerifier,boolean,boolean)",
                connection.getMethodPattern());
        assertEquals(6, connection.getArgumentIndex());
        assertEquals(true, connection.getLiteral());
        assertEquals("boolean", connection.getPrimitiveType());

        AddLiteralMethodArgument decompressor = assertInstanceOf(
                AddLiteralMethodArgument.class, delegates.get(1));
        assertEquals(
                "io.netty.handler.codec.http2.DelegatingDecompressorFrameListener <constructor>(" +
                "io.netty.handler.codec.http2.Http2Connection," +
                "io.netty.handler.codec.http2.Http2FrameListener)",
                decompressor.getMethodPattern());
        assertEquals(2, decompressor.getArgumentIndex());
        assertEquals(0, decompressor.getLiteral());
        assertEquals("int", decompressor.getPrimitiveType());

        AddLiteralMethodArgument strictDecompressor = assertInstanceOf(
                AddLiteralMethodArgument.class, delegates.get(2));
        assertEquals(
                "io.netty.handler.codec.http2.DelegatingDecompressorFrameListener <constructor>(" +
                "io.netty.handler.codec.http2.Http2Connection," +
                "io.netty.handler.codec.http2.Http2FrameListener,boolean)",
                strictDecompressor.getMethodPattern());
        assertEquals(3, strictDecompressor.getArgumentIndex());
        assertEquals(0, strictDecompressor.getLiteral());
        assertEquals("int", strictDecompressor.getPrimitiveType());

        DeleteMethodArgument huffmanCapacity = assertInstanceOf(
                DeleteMethodArgument.class, delegates.get(3));
        assertEquals(
                "io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder <constructor>(boolean,long,int)",
                huffmanCapacity.getMethodPattern());
        assertEquals(2, huffmanCapacity.getArgumentIndex());
    }

    @Test
    void officialNettyAggregateActivatesButIsExcludedFromThePatchRecipe() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        Recipe official = environment.activateRecipes(OFFICIAL_4_2);
        assertEquals(OFFICIAL_4_2, official.getName());
        List<String> officialChildren = official.getRecipeList().stream().map(Recipe::getName).toList();
        assertTrue(officialChildren.contains(
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion"), officialChildren.toString());
        assertTrue(officialChildren.contains(
                "org.openrewrite.java.netty.EventLoopGroupToMultiThreadIoEventLoopGroupRecipes"),
                officialChildren.toString());

        Recipe local = environment.activateRecipes(LOCAL_RECOMMENDED);
        assertFalse(local.getRecipeList().stream().map(Recipe::getName).anyMatch(OFFICIAL_4_2::equals));
    }

    @Test
    void officialArgumentRecipesAndLocalSafetyGuardsRunTogether() {
        rewriteRun(spec -> spec.recipe(new MigrateDeprecatedHttp2Constructors())
                        .parser(JavaParser.fromJavaVersion().dependsOn(NettyCodecHttp2TestApi.sources())),
                java("""
                        import io.netty.handler.codec.http2.*;
                        class AllConstructors {
                            Object connection(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                                              Http2PromisedRequestVerifier v) {
                                return new DefaultHttp2ConnectionDecoder(c, e, r, v, true, true);
                            }
                            Object decompressor(Http2Connection c, Http2FrameListener l) {
                                return new DelegatingDecompressorFrameListener(c, l);
                            }
                            Object strict(Http2Connection c, Http2FrameListener l) {
                                return new DelegatingDecompressorFrameListener(c, l, false);
                            }
                            Object literalHeaders() {
                                return new DefaultHttp2HeadersDecoder(true, 8192L, 32);
                            }
                            Object observableHeaders(int capacity) {
                                return new DefaultHttp2HeadersDecoder(true, 8192L, capacity);
                            }
                        }
                        """, """
                        import io.netty.handler.codec.http2.*;
                        class AllConstructors {
                            Object connection(Http2Connection c, Http2ConnectionEncoder e, Http2FrameReader r,
                                              Http2PromisedRequestVerifier v) {
                                return new DefaultHttp2ConnectionDecoder(c, e, r, v, true, true, true);
                            }
                            Object decompressor(Http2Connection c, Http2FrameListener l) {
                                return new DelegatingDecompressorFrameListener(c, l, 0);
                            }
                            Object strict(Http2Connection c, Http2FrameListener l) {
                                return new DelegatingDecompressorFrameListener(c, l, false, 0);
                            }
                            Object literalHeaders() {
                                return new DefaultHttp2HeadersDecoder(true, 8192L);
                            }
                            Object observableHeaders(int capacity) {
                                return new DefaultHttp2HeadersDecoder(true, 8192L, capacity);
                            }
                        }
                        """));
    }
}
