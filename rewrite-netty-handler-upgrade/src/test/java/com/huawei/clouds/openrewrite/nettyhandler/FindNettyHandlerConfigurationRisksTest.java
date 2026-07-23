package com.huawei.clouds.openrewrite.nettyhandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class FindNettyHandlerConfigurationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindNettyHandlerConfigurationRisks());
    }

    @ParameterizedTest(name = "Properties TLS key {0}")
    @ValueSource(strings = {
            "io.netty.handler.ssl.conscrypt.useBufferAllocator",
            "io.netty.handler.ssl.noOpenSsl",
            "io.netty.handler.ssl.openssl.engine",
            "io.netty.handler.ssl.openssl.useKeyManagerFactory",
            "io.netty.handler.ssl.openssl.bioNonApplicationBufferSize",
            "io.netty.handler.ssl.openssl.useTasks",
            "io.netty.handler.ssl.openssl.sessionCacheServer",
            "io.netty.handler.ssl.openssl.sessionCacheClient",
            "io.netty.handler.ssl.util.selfSignedKeyStrength",
            "jdk.tls.client.protocols",
            "jdk.tls.server.protocols",
            "jdk.tls.namedGroups",
            "jdk.tls.client.enableSessionTicketExtension",
            "jdk.tls.server.enableSessionTicketExtension",
            "jdk.tls.ephemeralDHKeySize",
            "javax.net.ssl.sessionCacheSize"
    })
    void marksEveryDocumentedTlsProperty(String key) {
        rewriteRun(properties(key + "=true", source -> source.path("application.properties")
                .after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyHandlerConfigurationRisks.CONFIG),
                                after.printAll()))));
    }

    @Test
    void marksFlatAndNestedYamlPaths() {
        rewriteRun(
                yaml("""
                        io.netty.handler.ssl.noOpenSsl: true
                        """, source -> source.path("flat.yml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyHandlerConfigurationRisks.CONFIG),
                                after.printAll()))),
                yaml("""
                        io:
                          netty:
                            handler:
                              ssl:
                                openssl:
                                  useTasks: true
                        """, source -> source.path("nested.yaml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyHandlerConfigurationRisks.CONFIG),
                                after.printAll()))),
                yaml("""
                        jdk:
                          tls:
                            namedGroups: X25519,secp256r1
                        """, source -> source.path("jdk.yml").after(actual -> actual).afterRecipe(after ->
                        assertTrue(after.printAll().contains(FindNettyHandlerConfigurationRisks.CONFIG),
                                after.printAll()))));
    }

    @Test
    void ignoresLookalikesAndUnrelatedConfiguration() {
        rewriteRun(
                properties("""
                        xio.netty.handler.ssl.noOpenSsl=true
                        io.netty.handler.codec.http.maxHeaderSize=8192
                        javax.net.ssl.keyStore=/run/secrets/tls.p12
                        """),
                yaml("""
                        xio:
                          netty:
                            handler:
                              ssl:
                                noOpenSsl: true
                        server:
                          ssl:
                            enabled: true
                        """));
    }

    @Test
    void markerIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                properties("jdk.tls.client.protocols=TLSv1.3", source ->
                        source.after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(),
                                        FindNettyHandlerConfigurationRisks.CONFIG)))));
    }

    @ParameterizedTest(name = "generated configuration {0}")
    @ValueSource(strings = {"target", "build", "generated", "GENERATED-resources", ".gradle", "dist"})
    void ignoresGeneratedConfiguration(String parent) {
        rewriteRun(properties("io.netty.handler.ssl.noOpenSsl=true",
                source -> source.path(parent + "/application.properties")));
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }
}
