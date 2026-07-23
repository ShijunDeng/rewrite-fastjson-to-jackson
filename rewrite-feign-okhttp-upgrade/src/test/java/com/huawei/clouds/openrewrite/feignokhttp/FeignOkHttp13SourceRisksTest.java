package com.huawei.clouds.openrewrite.feignokhttp;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FeignOkHttp13SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindFeignOkHttp13SourceRisks())
                .parser(JavaParser.fromJavaVersion().classpath(
                        "feign-core", "feign-okhttp", "okhttp", "okio-jvm", "kotlin-stdlib"));
    }

    @Test
    void marksRealDefaultAdapterFixtureFromBaeldung() {
        // Reduced from eugenp/tutorials at 1023d82c27af842a9e86b4663227819db8b19d7a:
        // https://github.com/eugenp/tutorials/blob/1023d82c27af842a9e86b4663227819db8b19d7a/feign/src/main/java/com/baeldung/feign/retry/ResilientFeignClientBuilder.java
        rewriteRun(java(
                """
                import feign.Feign;
                import feign.okhttp.OkHttpClient;
                class ClientFactory { Object client() { return Feign.builder().client(new OkHttpClient()); } }
                """,
                """
                import feign.Feign;
                import feign.okhttp.OkHttpClient;
                class ClientFactory { Object client() { return Feign.builder().client(/*~~(%s)~~>*/new OkHttpClient()); } }
                """.formatted(FindFeignOkHttp13SourceRisks.DEFAULT_CLIENT)));
    }

    @Test
    void marksRealCallerOwnedDelegateFixtureFromTwitch4j() {
        // Reduced from twitch4j/twitch4j at 3ea102457324357be1959bd5fd4e5e41eb3b4dd3:
        // https://github.com/twitch4j/twitch4j/blob/3ea102457324357be1959bd5fd4e5e41eb3b4dd3/rest-kraken/src/main/java/com/github/twitch4j/kraken/TwitchKrakenBuilder.java
        rewriteRun(java(
                """
                import feign.okhttp.OkHttpClient;
                class Factory { Object client(okhttp3.OkHttpClient.Builder builder) { return new OkHttpClient(builder.build()); } }
                """,
                """
                import feign.okhttp.OkHttpClient;
                class Factory { Object client(okhttp3.OkHttpClient.Builder builder) { return /*~~(%s)~~>*/new OkHttpClient(builder.build()); } }
                """.formatted(FindFeignOkHttp13SourceRisks.DELEGATE_CLIENT)));
    }

    @Test
    void marksEveryTimeoutKindAtExactCall() {
        rewriteRun(java(
                """
                import java.time.Duration;
                import okhttp3.OkHttpClient;
                class Timeouts { void configure(OkHttpClient.Builder b) {
                    b.connectTimeout(Duration.ofSeconds(1));
                    b.readTimeout(Duration.ofSeconds(2));
                    b.writeTimeout(Duration.ofSeconds(3));
                    b.callTimeout(Duration.ofSeconds(4));
                } }
                """,
                """
                import java.time.Duration;
                import okhttp3.OkHttpClient;
                class Timeouts { void configure(OkHttpClient.Builder b) {
                    /*~~(%1$s)~~>*/b.connectTimeout(Duration.ofSeconds(1));
                    /*~~(%1$s)~~>*/b.readTimeout(Duration.ofSeconds(2));
                    /*~~(%1$s)~~>*/b.writeTimeout(Duration.ofSeconds(3));
                    /*~~(%1$s)~~>*/b.callTimeout(Duration.ofSeconds(4));
                } }
                """.formatted(FindFeignOkHttp13SourceRisks.TIMEOUT)));
    }

    @Test
    void marksFeignRequestOptionsPrecedenceBoundary() {
        rewriteRun(java(
                """
                import feign.Request;
                class Options { Request.Options value() { return new Request.Options(1000, 2000, false); } }
                """,
                """
                import feign.Request;
                class Options { Request.Options value() { return /*~~(%s)~~>*/new Request.Options(1000, 2000, false); } }
                """.formatted(FindFeignOkHttp13SourceRisks.REQUEST_OPTIONS)));
    }

    @Test
    void marksHttp2ProtocolsAndPing() {
        rewriteRun(java(
                """
                import java.time.Duration;
                import java.util.List;
                import okhttp3.OkHttpClient;
                import okhttp3.Protocol;
                class H2 { void configure(OkHttpClient.Builder b) {
                    b.protocols(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1));
                    b.pingInterval(Duration.ofSeconds(30));
                } }
                """,
                """
                import java.time.Duration;
                import java.util.List;
                import okhttp3.OkHttpClient;
                import okhttp3.Protocol;
                class H2 { void configure(OkHttpClient.Builder b) {
                    /*~~(%1$s)~~>*/b.protocols(List.of(Protocol.HTTP_2, Protocol.HTTP_1_1));
                    /*~~(%1$s)~~>*/b.pingInterval(Duration.ofSeconds(30));
                } }
                """.formatted(FindFeignOkHttp13SourceRisks.HTTP2)));
    }

    @Test
    void marksTlsConfigurationAtBuilderMethods() {
        rewriteRun(java(
                """
                import java.util.List;
                import javax.net.ssl.HostnameVerifier;
                import javax.net.ssl.SSLSocketFactory;
                import javax.net.ssl.X509TrustManager;
                import okhttp3.CertificatePinner;
                import okhttp3.ConnectionSpec;
                import okhttp3.OkHttpClient;
                class Tls { void configure(OkHttpClient.Builder b, SSLSocketFactory f, X509TrustManager tm, HostnameVerifier hv) {
                    b.sslSocketFactory(f, tm);
                    b.hostnameVerifier(hv);
                    b.certificatePinner(new CertificatePinner.Builder().build());
                    b.connectionSpecs(List.of(ConnectionSpec.MODERN_TLS));
                } }
                """,
                """
                import java.util.List;
                import javax.net.ssl.HostnameVerifier;
                import javax.net.ssl.SSLSocketFactory;
                import javax.net.ssl.X509TrustManager;
                import okhttp3.CertificatePinner;
                import okhttp3.ConnectionSpec;
                import okhttp3.OkHttpClient;
                class Tls { void configure(OkHttpClient.Builder b, SSLSocketFactory f, X509TrustManager tm, HostnameVerifier hv) {
                    /*~~(%1$s)~~>*/b.sslSocketFactory(f, tm);
                    /*~~(%1$s)~~>*/b.hostnameVerifier(hv);
                    /*~~(%1$s)~~>*/b.certificatePinner(new CertificatePinner.Builder().build());
                    /*~~(%1$s)~~>*/b.connectionSpecs(List.of(ConnectionSpec.MODERN_TLS));
                } }
                """.formatted(FindFeignOkHttp13SourceRisks.TLS)));
    }

    @Test
    void marksApplicationAndNetworkInterceptors() {
        rewriteRun(java(
                """
                import okhttp3.Interceptor;
                import okhttp3.OkHttpClient;
                class Chain { void configure(OkHttpClient.Builder b, Interceptor i) {
                    b.addInterceptor(i);
                    b.addNetworkInterceptor(i);
                    b.interceptors();
                    b.networkInterceptors();
                } }
                """,
                """
                import okhttp3.Interceptor;
                import okhttp3.OkHttpClient;
                class Chain { void configure(OkHttpClient.Builder b, Interceptor i) {
                    /*~~(%1$s)~~>*/b.addInterceptor(i);
                    /*~~(%1$s)~~>*/b.addNetworkInterceptor(i);
                    /*~~(%1$s)~~>*/b.interceptors();
                    /*~~(%1$s)~~>*/b.networkInterceptors();
                } }
                """.formatted(FindFeignOkHttp13SourceRisks.INTERCEPTOR)));
    }

    @Test
    void marksTransportPolicyMethods() {
        rewriteRun(java(
                """
                import okhttp3.Authenticator;
                import okhttp3.Dns;
                import okhttp3.OkHttpClient;
                class Transport { void configure(OkHttpClient.Builder b, Authenticator a, Dns dns) {
                    b.retryOnConnectionFailure(false);
                    b.followRedirects(false);
                    b.followSslRedirects(false);
                    b.authenticator(a);
                    b.dns(dns);
                } }
                """,
                """
                import okhttp3.Authenticator;
                import okhttp3.Dns;
                import okhttp3.OkHttpClient;
                class Transport { void configure(OkHttpClient.Builder b, Authenticator a, Dns dns) {
                    /*~~(%1$s)~~>*/b.retryOnConnectionFailure(false);
                    /*~~(%1$s)~~>*/b.followRedirects(false);
                    /*~~(%1$s)~~>*/b.followSslRedirects(false);
                    /*~~(%1$s)~~>*/b.authenticator(a);
                    /*~~(%1$s)~~>*/b.dns(dns);
                } }
                """.formatted(FindFeignOkHttp13SourceRisks.TRANSPORT)));
    }

    @Test
    void marksManualCompressionHeadersAndWebSocketThreshold() {
        rewriteRun(java(
                """
                import feign.RequestTemplate;
                import okhttp3.OkHttpClient;
                import okhttp3.Request;
                class Compression { void configure(RequestTemplate f, Request.Builder r, OkHttpClient.Builder b) {
                    f.header("Accept-Encoding", "gzip");
                    r.addHeader("Content-Encoding", "gzip");
                    b.minWebSocketMessageToCompress(0L);
                    f.header("X-Encoding-Mode", "manual");
                } }
                """,
                """
                import feign.RequestTemplate;
                import okhttp3.OkHttpClient;
                import okhttp3.Request;
                class Compression { void configure(RequestTemplate f, Request.Builder r, OkHttpClient.Builder b) {
                    /*~~(%1$s)~~>*/f.header("Accept-Encoding", "gzip");
                    /*~~(%1$s)~~>*/r.addHeader("Content-Encoding", "gzip");
                    /*~~(%1$s)~~>*/b.minWebSocketMessageToCompress(0L);
                    f.header("X-Encoding-Mode", "manual");
                } }
                """.formatted(FindFeignOkHttp13SourceRisks.COMPRESSION)));
    }

    @Test
    void marksRequestBodyFactoriesAndFeignTemplates() {
        rewriteRun(java(
                """
                import feign.RequestTemplate;
                import java.nio.charset.StandardCharsets;
                import okhttp3.MediaType;
                import okhttp3.RequestBody;
                class Bodies { void build(RequestTemplate t, MediaType m) {
                    RequestBody.create(m, new byte[0]);
                    t.body(new byte[0], StandardCharsets.UTF_8);
                    t.bodyTemplate("{payload}");
                } }
                """,
                """
                import feign.RequestTemplate;
                import java.nio.charset.StandardCharsets;
                import okhttp3.MediaType;
                import okhttp3.RequestBody;
                class Bodies { void build(RequestTemplate t, MediaType m) {
                    /*~~(%1$s)~~>*/RequestBody.create(m, new byte[0]);
                    /*~~(%1$s)~~>*/t.body(new byte[0], StandardCharsets.UTF_8);
                    /*~~(%1$s)~~>*/t.bodyTemplate("{payload}");
                } }
                """.formatted(FindFeignOkHttp13SourceRisks.REQUEST_BODY)));
    }

    @Test
    void marksOkHttpRequestAndMultipartBodyMethods() {
        rewriteRun(java(
                """
                import okhttp3.MediaType;
                import okhttp3.MultipartBody;
                import okhttp3.Request;
                import okhttp3.RequestBody;
                class Bodies { void build(Request.Builder r, MultipartBody.Builder m, RequestBody b) {
                    r.post(b); r.put(b); r.patch(b); r.method("DELETE", b);
                    m.addPart(b); m.addFormDataPart("file", "a.txt", b); m.setType(MediaType.get("multipart/form-data"));
                } }
                """,
                """
                import okhttp3.MediaType;
                import okhttp3.MultipartBody;
                import okhttp3.Request;
                import okhttp3.RequestBody;
                class Bodies { void build(Request.Builder r, MultipartBody.Builder m, RequestBody b) {
                    /*~~(%1$s)~~>*/r.post(b); /*~~(%1$s)~~>*/r.put(b); /*~~(%1$s)~~>*/r.patch(b); /*~~(%1$s)~~>*/r.method("DELETE", b);
                    /*~~(%1$s)~~>*/m.addPart(b); /*~~(%1$s)~~>*/m.addFormDataPart("file", "a.txt", b); /*~~(%1$s)~~>*/m.setType(MediaType.get("multipart/form-data"));
                } }
                """.formatted(FindFeignOkHttp13SourceRisks.REQUEST_BODY)));
    }

    @Test
    void marksDelegatePoolDispatcherAndNewCallLifecycle() {
        rewriteRun(java(
                """
                import okhttp3.ConnectionPool;
                import okhttp3.Dispatcher;
                import okhttp3.OkHttpClient;
                import okhttp3.Request;
                class Lifetime { void close(OkHttpClient c, OkHttpClient.Builder b, ConnectionPool pool, Dispatcher d, Request r) {
                    b.connectionPool(pool); b.dispatcher(d); b.cache(null);
                    c.connectionPool(); c.dispatcher(); c.newBuilder(); c.newCall(r); pool.evictAll();
                } }
                """,
                """
                import okhttp3.ConnectionPool;
                import okhttp3.Dispatcher;
                import okhttp3.OkHttpClient;
                import okhttp3.Request;
                class Lifetime { void close(OkHttpClient c, OkHttpClient.Builder b, ConnectionPool pool, Dispatcher d, Request r) {
                    /*~~(%1$s)~~>*/b.connectionPool(pool); /*~~(%1$s)~~>*/b.dispatcher(d); /*~~(%1$s)~~>*/b.cache(null);
                    /*~~(%1$s)~~>*/c.connectionPool(); /*~~(%1$s)~~>*/c.dispatcher(); /*~~(%1$s)~~>*/c.newBuilder(); /*~~(%1$s)~~>*/c.newCall(r); /*~~(%1$s)~~>*/pool.evictAll();
                } }
                """.formatted(FindFeignOkHttp13SourceRisks.LIFECYCLE)));
    }

    @Test
    void marksResponseBodyConsumptionMethods() {
        rewriteRun(java(
                """
                import okhttp3.ResponseBody;
                class Consume { void read(ResponseBody b) throws Exception {
                    b.string(); b.bytes(); b.byteString(); b.byteStream(); b.charStream(); b.source(); b.close();
                } }
                """,
                """
                import okhttp3.ResponseBody;
                class Consume { void read(ResponseBody b) throws Exception {
                    /*~~(%1$s)~~>*/b.string(); /*~~(%1$s)~~>*/b.bytes(); /*~~(%1$s)~~>*/b.byteString(); /*~~(%1$s)~~>*/b.byteStream(); /*~~(%1$s)~~>*/b.charStream(); /*~~(%1$s)~~>*/b.source(); /*~~(%1$s)~~>*/b.close();
                } }
                """.formatted(FindFeignOkHttp13SourceRisks.RESPONSE_BODY)));
    }

    @Test
    void marksNullCredentialButNotExplicitCredential() {
        rewriteRun(java(
                """
                import okhttp3.Credentials;
                class Auth { void values() { Credentials.basic(null, "secret"); Credentials.basic("user", "secret"); } }
                """,
                """
                import okhttp3.Credentials;
                class Auth { void values() { /*~~(%s)~~>*/Credentials.basic(null, "secret"); Credentials.basic("user", "secret"); } }
                """.formatted(FindFeignOkHttp13SourceRisks.NULL_CREDENTIAL)));
    }

    @Test
    void marksFinalSubclassAndInternalImport() {
        rewriteRun(java(
                """
                import okhttp3.OkHttpClient;
                import okhttp3.internal.http.HttpMethod;
                class MockClient extends OkHttpClient { }
                """,
                """
                import okhttp3.OkHttpClient;
                /*~~(%1$s)~~>*/import okhttp3.internal.http.HttpMethod;
                class MockClient extends /*~~(%2$s)~~>*/OkHttpClient { }
                """.formatted(FindFeignOkHttp13SourceRisks.INTERNAL_API,
                        FindFeignOkHttp13SourceRisks.FINAL_CLIENT)));
    }

    @Test
    void sameNamedApplicationTypesAndOrdinaryHeadersAreNoop() {
        rewriteRun(java("""
                class OkHttpClient { static class Builder { Builder connectTimeout(int i) { return this; } } }
                class RequestTemplate { void header(String k, String v) { } }
                class Use { void x(OkHttpClient.Builder b, RequestTemplate t) { b.connectTimeout(1); t.header("Accept-Encoding", "gzip"); } }
                """));
    }

    @Test
    void generatedInstallCacheParentsAreNoopButLeafInstallJavaIsAudited() {
        rewriteRun(
                java("import feign.okhttp.OkHttpClient; class A { Object x(){ return new OkHttpClient(); } }", source -> source.path("generated-test/A.java")),
                java("import feign.okhttp.OkHttpClient; class B { Object x(){ return new OkHttpClient(); } }", source -> source.path("installations/B.java")),
                java("import feign.okhttp.OkHttpClient; class C { Object x(){ return new OkHttpClient(); } }", source -> source.path("target/generated/C.java")),
                java("import feign.okhttp.OkHttpClient; class install { Object x(){ return new OkHttpClient(); } }",
                        "import feign.okhttp.OkHttpClient; class install { Object x(){ return /*~~(%s)~~>*/new OkHttpClient(); } }".formatted(FindFeignOkHttp13SourceRisks.DEFAULT_CLIENT),
                        source -> source.path("install.java")));
    }

    @Test
    void sourceMarkersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import feign.okhttp.OkHttpClient; class C { Object x(){ return new OkHttpClient(); } }",
                "import feign.okhttp.OkHttpClient; class C { Object x(){ return /*~~(%s)~~>*/new OkHttpClient(); } }".formatted(FindFeignOkHttp13SourceRisks.DEFAULT_CLIENT)));
    }
}
