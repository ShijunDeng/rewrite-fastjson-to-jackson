package com.huawei.clouds.openrewrite.feignokhttp;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks type-attributed Feign/OkHttp nodes whose runtime behavior needs an application decision. */
public final class FindFeignOkHttp13SourceRisks extends Recipe {
    static final String DEFAULT_CLIENT =
            "The no-arg Feign OkHttp adapter creates its own OkHttpClient; verify adapter reuse, connection-pool and " +
            "dispatcher lifetime, shutdown hooks, thread ownership, proxy/authentication, and metrics after Feign 13.6";
    static final String DELEGATE_CLIENT =
            "This Feign adapter receives a caller-owned OkHttpClient; preserve singleton reuse and verify dispatcher, " +
            "connection-pool, cache, interceptor order, shutdown ownership, async calls, and cancellation under 13.6";
    static final String REQUEST_OPTIONS =
            "Feign Request.Options overrides this adapter's connect timeout, read timeout, and followRedirects per call; " +
            "retest precedence against OkHttp write/call/ping timeouts, retries, cancellation, and redirect policy";
    static final String TIMEOUT =
            "OkHttp timeout configuration detected; Feign applies connect/read/followRedirects through Request.Options " +
            "but leaves write/call/ping settings on the delegate, so verify units, precedence, retries, and cancellation";
    static final String HTTP2 =
            "OkHttp protocol/HTTP2 configuration detected; Feign 13.6 now records response protocol using Protocol.name(), " +
            "so retest protocol ordering, ALPN fallback, multiplexing, pings, HTTP/2 errors, logging, and metrics";
    static final String TLS =
            "Custom OkHttp TLS configuration detected; OkHttp moves from the selected 3.6/4.6/4.11 lines to 4.12, so " +
            "revalidate trust manager pairing, hostname verification, pins, TLS versions/ciphers, ALPN, and rotation";
    static final String INTERCEPTOR =
            "OkHttp interceptor configuration detected; verify application/network interceptor ordering, transparent gzip, " +
            "manual Accept-/Content-Encoding, request-body replay, authentication, retries, logging, and response closure";
    static final String TRANSPORT =
            "OkHttp transport policy detected; retest redirect/authentication/proxy/DNS/retry behavior against Feign " +
            "Request.Options, replayable bodies, HTTP status handling, cancellation, and the 4.12 connection pool";
    static final String COMPRESSION =
            "Manual HTTP content-encoding header detected; OkHttp transparent gzip is conditional on Accept-Encoding, so " +
            "verify compression ownership, content length, interceptor order, retryability, and double compression";
    static final String REQUEST_BODY =
            "Custom request-body construction detected; verify media type/charset, empty POST/PUT/PATCH bodies, streaming " +
            "one-shot or duplex replay, content length, compression, cancellation, and retry behavior with OkHttp 4.12";
    static final String LIFECYCLE =
            "OkHttp dispatcher/connection-pool lifecycle configuration detected; share clients deliberately and verify " +
            "executor shutdown, evictAll, idle connections, cache closure, async completion, cancellation, and redeploys";
    static final String RESPONSE_BODY =
            "OkHttp response-body consumption detected; confirm exactly-once close, streaming ownership, empty bodies, " +
            "charset, cancellation, decompression, async exceptional completion, and connection reuse";
    static final String FINAL_CLIENT =
            "OkHttpClient accessors are final in OkHttp 4; replace inheritance/mocking with composition or Call.Factory " +
            "and recompile this subtype against the 4.12.0 line brought by Feign 13.6";
    static final String INTERNAL_API =
            "OkHttp internal API is not published compatibility surface and changed substantially in OkHttp 4; replace " +
            "this import with public okhttp3 APIs before adopting Feign OkHttp 13.6";
    static final String NULL_CREDENTIAL =
            "Credentials.basic() no longer accepts null username/password in OkHttp 4; decide an explicit credential or " +
            "fail fast before the Feign OkHttp 13.6 upgrade";

    private static final Set<String> TIMEOUT_METHODS = Set.of(
            "connectTimeout", "readTimeout", "writeTimeout", "callTimeout");
    private static final Set<String> HTTP2_METHODS = Set.of("protocols", "pingInterval");
    private static final Set<String> TLS_METHODS = Set.of(
            "sslSocketFactory", "hostnameVerifier", "certificatePinner", "connectionSpecs", "socketFactory");
    private static final Set<String> INTERCEPTOR_METHODS = Set.of(
            "addInterceptor", "addNetworkInterceptor", "interceptors", "networkInterceptors");
    private static final Set<String> TRANSPORT_METHODS = Set.of(
            "retryOnConnectionFailure", "followRedirects", "followSslRedirects", "authenticator",
            "proxyAuthenticator", "proxy", "proxySelector", "dns", "cookieJar");
    private static final Set<String> LIFECYCLE_METHODS = Set.of("dispatcher", "connectionPool", "cache");
    private static final Set<String> REQUEST_BUILDER_BODY_METHODS = Set.of("method", "post", "put", "patch");
    private static final Set<String> MULTIPART_METHODS = Set.of("addFormDataPart", "addPart", "setType");
    private static final Set<String> RESPONSE_BODY_METHODS = Set.of(
            "string", "bytes", "byteString", "byteStream", "charStream", "source", "close");

    @Override
    public String getDisplayName() {
        return "Find Feign OkHttp 13 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact type-attributed Feign OkHttp construction, request, timeout, HTTP/2, TLS, compression, " +
               "interceptor, response, and client-lifecycle nodes requiring behavioral regression decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedFeignOkHttpDependency.excluded(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                return visited.getQualid().printTrimmed(getCursor()).startsWith("okhttp3.internal.")
                        ? mark(visited, INTERNAL_API) : visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = typeName(visited.getType());
                if ("feign.okhttp.OkHttpClient".equals(type)) {
                    return mark(visited, noArguments(visited) ? DEFAULT_CLIENT : DELEGATE_CLIENT);
                }
                if ("feign.Request$Options".equals(type)) return mark(visited, REQUEST_OPTIONS);
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
                TypeTree base = visited.getExtends();
                if (base != null && "okhttp3.OkHttpClient".equals(typeName(base.getType()))) {
                    return visited.withExtends(mark(base, FINAL_CLIENT));
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                String owner = method == null ? null : typeName(method.getDeclaringType());
                String name = visited.getSimpleName();

                if (isOwner(owner, "okhttp3.OkHttpClient$Builder")) {
                    if (TIMEOUT_METHODS.contains(name)) return mark(visited, TIMEOUT);
                    if (HTTP2_METHODS.contains(name)) return mark(visited, HTTP2);
                    if (TLS_METHODS.contains(name)) return mark(visited, TLS);
                    if (INTERCEPTOR_METHODS.contains(name)) return mark(visited, INTERCEPTOR);
                    if (TRANSPORT_METHODS.contains(name)) return mark(visited, TRANSPORT);
                    if ("minWebSocketMessageToCompress".equals(name)) return mark(visited, COMPRESSION);
                    if (LIFECYCLE_METHODS.contains(name)) return mark(visited, LIFECYCLE);
                }
                if (isOwner(owner, "okhttp3.OkHttpClient")) {
                    if (LIFECYCLE_METHODS.contains(name)) return mark(visited, LIFECYCLE);
                    if (Set.of("newBuilder", "newCall").contains(name)) return mark(visited, LIFECYCLE);
                }
                if (isOwner(owner, "okhttp3.ConnectionPool") && "evictAll".equals(name)) {
                    return mark(visited, LIFECYCLE);
                }
                if (isOwner(owner, "okhttp3.RequestBody", "okhttp3.RequestBody$Companion") &&
                    "create".equals(name)) return mark(visited, REQUEST_BODY);
                if (isOwner(owner, "okhttp3.Request$Builder") && REQUEST_BUILDER_BODY_METHODS.contains(name)) {
                    return mark(visited, REQUEST_BODY);
                }
                if (isOwner(owner, "okhttp3.MultipartBody$Builder") && MULTIPART_METHODS.contains(name)) {
                    return mark(visited, REQUEST_BODY);
                }
                if (isOwner(owner, "feign.RequestTemplate") && Set.of("body", "bodyTemplate").contains(name)) {
                    return mark(visited, REQUEST_BODY);
                }
                if (encodingHeader(visited, owner)) return mark(visited, COMPRESSION);
                if (isOwner(owner, "okhttp3.ResponseBody") && RESPONSE_BODY_METHODS.contains(name)) {
                    return mark(visited, RESPONSE_BODY);
                }
                if (isOwner(owner, "okhttp3.Credentials", "okhttp3.Credentials$Companion") &&
                    "basic".equals(name) && visited.getArguments().stream().anyMatch(FindFeignOkHttp13SourceRisks::isNull)) {
                    return mark(visited, NULL_CREDENTIAL);
                }
                return visited;
            }
        };
    }

    private static boolean encodingHeader(J.MethodInvocation invocation, String owner) {
        if (!Set.of("header", "addHeader", "setHeader").contains(invocation.getSimpleName()) ||
            !isOwner(owner, "okhttp3.Request$Builder", "feign.RequestTemplate")) return false;
        return invocation.getArguments().stream().filter(J.Literal.class::isInstance).map(J.Literal.class::cast)
                .map(J.Literal::getValue).filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(value -> "Accept-Encoding".equalsIgnoreCase(value) ||
                                   "Content-Encoding".equalsIgnoreCase(value));
    }

    private static boolean isNull(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() == null;
    }

    private static boolean isOwner(String actual, String... expected) {
        if (actual == null) return false;
        for (String value : expected) if (value.equals(actual)) return true;
        return false;
    }

    private static boolean noArguments(J.NewClass newClass) {
        return newClass.getArguments().isEmpty() || newClass.getArguments().stream().allMatch(J.Empty.class::isInstance);
    }

    private static String typeName(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? null : fq.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
