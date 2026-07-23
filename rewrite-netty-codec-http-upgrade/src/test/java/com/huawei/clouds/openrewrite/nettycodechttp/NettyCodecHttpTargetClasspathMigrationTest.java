package com.huawei.clouds.openrewrite.nettycodechttp;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NettyCodecHttpTargetClasspathMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateValidatedHttpDecoderConstructors())
                .parser(JavaParser.fromJavaVersion().classpath("netty-codec-http"));
    }

    @Test
    void migratesPinnedNettyHttpClientCodecTestPatternAgainstTheRealTargetJar() {
        rewriteRun(java(
                """
                  import io.netty.handler.codec.http.HttpRequestDecoder;

                  class HttpClientCodecTest {
                      Object decoder() {
                          return new HttpRequestDecoder(4096, 8192, 8192, true);
                      }
                  }
                  """,
                """
                  import io.netty.handler.codec.http.HttpDecoderConfig;
                  import io.netty.handler.codec.http.HttpRequestDecoder;

                  class HttpClientCodecTest {
                      Object decoder() {
                          return new HttpRequestDecoder(new HttpDecoderConfig().setMaxInitialLineLength(4096).setMaxHeaderSize(8192).setMaxChunkSize(8192));
                      }
                  }
                  """));
    }

    @Test
    void realTargetClasspathTransformationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                """
                  import io.netty.handler.codec.http.HttpServerCodec;

                  class ServerPipeline {
                      Object decoder() {
                          return new HttpServerCodec(4096, 8192, 16384, true, 256, false, true);
                      }
                  }
                  """,
                """
                  import io.netty.handler.codec.http.HttpDecoderConfig;
                  import io.netty.handler.codec.http.HttpServerCodec;

                  class ServerPipeline {
                      Object decoder() {
                          return new HttpServerCodec(new HttpDecoderConfig().setMaxInitialLineLength(4096).setMaxHeaderSize(8192).setMaxChunkSize(16384).setInitialBufferSize(256).setAllowDuplicateContentLengths(false).setAllowPartialChunks(true));
                      }
                  }
                  """));
    }
}
