package com.huawei.clouds.openrewrite.nettycodechttp;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.Set;

/** Locate source-level Netty HTTP parsing, ownership and branch-API decisions. */
public final class FindNettyCodecHttp41136SourceRisks extends Recipe {
    static final String HEADER_VALIDATION =
            "Header validation is disabled or runtime-selected; 4.1.136 deprecates this security escape hatch—enable " +
            "validation, sanitize trusted exceptions, and test CR/LF header injection before deployment";
    static final String CLIENT_CONSTRUCTOR =
            "This deprecated HttpClientCodec constructor still carries validateHeaders; migrate deliberately to " +
            "HttpDecoderConfig while preserving failOnMissingResponse/parse-after-CONNECT evaluation and behavior";
    static final String PARSING =
            "Netty 4.1.136 applies stricter request-line, method/version boundary, header, chunk-extension and " +
            "Content-Length parsing; test malformed traffic, proxy chains, DecoderResult failures and connection closure";
    static final String CUSTOM_DECODER =
            "This custom HttpObjectDecoder participates in changed start-line/chunk/header parsing; rebase overrides on " +
            "4.1.136, preserve reference counts, and test fragmented, malformed and oversized inputs";
    static final String CUSTOM_METHOD =
            "Custom/dynamic HTTP method or version tokens are validated more strictly in 4.1.136; reject control " +
            "characters before construction and test encoder output against intermediaries";
    static final String MULTIPART =
            "Multipart/form decoding changed for no-value fields, locale-independent tokens, size overflow and cleanup; " +
            "test empty values, mixed-case metadata, disk thresholds, limits and release/delete lifecycle";
    static final String QUERY =
            "Query-string decoding has corrected no-value and optional plus handling; assert duplicate/empty parameters, " +
            "literal '+', percent decoding, charset failures and round-trip behavior";
    static final String AGGREGATION =
            "HTTP aggregation/CORS/upgrade behavior changed for 413, AUTO_READ, preflight content and incomplete upgrades; " +
            "test backpressure, pipelining, rejection, handler removal and ByteBuf release";
    static final String COMPRESSION =
            "HTTP/WebSocket compression and decompression gained allocation limits and framing fixes; set explicit " +
            "budgets and test zip bombs, Brotli/Zstd availability, window bits, control frames and fragmented messages";
    static final String DATE =
            "DateFormatter no longer parses a trailing token beyond the requested range; test nonstandard Date headers, " +
            "substring bounds and rejection/fallback policy";
    static final String RFC9112 =
            "RFC 9112 transfer-encoding enforcement is disabled or runtime-selected; this compatibility escape hatch " +
            "can re-enable request smuggling—remove it or document the trusted boundary and add TE+CL rejection tests";
    static final String SPDY_LIFECYCLE =
            "SpdyHttpDecoder cleanup moved from channelInactive to handlerRemoved in 4.1.136; update overrides/super calls " +
            "and verify outstanding FullHttpMessage reference counts on close and pipeline removal";
    static final String BRANCH_API =
            "This API exists in Netty 4.2.10 but not in the supplied 4.1.136 target branch; the source/target rows " +
            "conflict and remain unchanged—keep 4.2 or replace the API only after an explicit architecture decision";

    private static final String HTTP_OBJECT_DECODER = "io.netty.handler.codec.http.HttpObjectDecoder";
    private static final String SPDY_HTTP_DECODER = "io.netty.handler.codec.spdy.SpdyHttpDecoder";
    private static final String SPDY_DELEGATE = "io.netty.handler.codec.spdy.SpdyFrameDecoderDelegate";
    private static final String MASK_GENERATOR =
            "io.netty.handler.codec.http.websocketx.WebSocketFrameMaskGenerator";
    private static final Set<String> DECODER_TYPES = Set.of(
            "io.netty.handler.codec.http.HttpRequestDecoder",
            "io.netty.handler.codec.http.HttpResponseDecoder",
            "io.netty.handler.codec.http.HttpServerCodec",
            "io.netty.handler.codec.http.HttpClientCodec");
    private static final Set<String> VALIDATED_CONSTRUCTOR_TYPES = Set.of(
            "io.netty.handler.codec.http.HttpRequestDecoder",
            "io.netty.handler.codec.http.HttpResponseDecoder",
            "io.netty.handler.codec.http.HttpServerCodec");
    private static final Set<String> AGGREGATION_TYPES = Set.of(
            "io.netty.handler.codec.http.HttpObjectAggregator",
            "io.netty.handler.codec.http.cors.CorsHandler",
            "io.netty.handler.codec.http.cors.CorsConfigBuilder",
            "io.netty.handler.codec.http.HttpClientUpgradeHandler",
            "io.netty.handler.codec.http.HttpServerUpgradeHandler");
    private static final Set<String> COMPRESSION_TYPES = Set.of(
            "io.netty.handler.codec.http.HttpContentCompressor",
            "io.netty.handler.codec.http.HttpContentDecompressor",
            "io.netty.handler.codec.http.HttpContentEncoder",
            "io.netty.handler.codec.http.HttpContentDecoder",
            "io.netty.handler.codec.compression.BrotliDecoder",
            "io.netty.handler.codec.compression.ZstdDecoder",
            "io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler",
            "io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler",
            "io.netty.handler.codec.http.websocketx.Utf8FrameValidator");
    private static final Set<String> FOUR_TWO_ONLY_TYPES = Set.of(
            MASK_GENERATOR,
            "io.netty.handler.codec.http.websocketx.RandomWebSocketFrameMaskGenerator");
    private static final Set<String> MASK_ENCODERS = Set.of(
            "io.netty.handler.codec.http.websocketx.WebSocket07FrameEncoder",
            "io.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder",
            "io.netty.handler.codec.http.websocketx.WebSocket13FrameEncoder");

    @Override
    public String getDisplayName() {
        return "Find Netty HTTP codec 4.1.136 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact header-validation, parser, multipart/query, aggregation/upgrade, compression, SPDY lifecycle " +
               "and Netty 4.2-only API decisions that require application evidence.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return NettyCodecHttpSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                return FOUR_TWO_ONLY_TYPES.contains(visited.getTypeName()) ? mark(visited, BRANCH_API) : visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                JavaType.FullyQualified type = visited.getType();
                if (type != null && TypeUtils.isAssignableTo(SPDY_HTTP_DECODER, type)) {
                    return mark(visited, SPDY_LIFECYCLE);
                }
                if (type != null && TypeUtils.isAssignableTo(SPDY_DELEGATE, type)) {
                    return mark(visited, BRANCH_API);
                }
                if (type != null && TypeUtils.isAssignableTo(HTTP_OBJECT_DECODER, type) &&
                    !TypeUtils.isOfClassType(type, HTTP_OBJECT_DECODER) &&
                    DECODER_TYPES.stream().noneMatch(name -> TypeUtils.isOfClassType(type, name))) {
                    return mark(visited, CUSTOM_DECODER);
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                JavaType.FullyQualified ownerType = owner == null ? null : owner.getType();
                if ("channelInactive".equals(visited.getSimpleName()) && ownerType != null &&
                    TypeUtils.isAssignableTo(SPDY_HTTP_DECODER, ownerType)) return mark(visited, SPDY_LIFECYCLE);
                if ("readUnknownFrame".equals(visited.getSimpleName()) && ownerType != null &&
                    TypeUtils.isAssignableTo(SPDY_DELEGATE, ownerType)) return mark(visited, BRANCH_API);
                JavaType.Method methodType = visited.getMethodType();
                if (methodType != null && (fourTwoOnly(methodType.getReturnType()) ||
                    methodType.getParameterTypes().stream().anyMatch(FindNettyCodecHttp41136SourceRisks::fourTwoOnly))) {
                    return mark(visited, BRANCH_API);
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                     ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(multiVariable, ctx);
                return fourTwoOnly(visited.getType()) ? mark(visited, BRANCH_API) : visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                JavaType type = visited.getType();
                List<Expression> args = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                if (FOUR_TWO_ONLY_TYPES.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name))) {
                    return mark(visited, BRANCH_API);
                }
                if (MASK_ENCODERS.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name)) && args.size() == 1 &&
                    TypeUtils.isOfClassType(args.get(0).getType(), MASK_GENERATOR)) return mark(visited, BRANCH_API);
                if (TypeUtils.isOfClassType(type, "io.netty.handler.codec.http.HttpServerUpgradeHandler") &&
                    sixArgument42UpgradeConstructor(visited)) return mark(visited, BRANCH_API);
                if (VALIDATED_CONSTRUCTOR_TYPES.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name)) &&
                    args.size() >= 4 && args.size() <= 7 && !literalTrue(args.get(3))) {
                    return mark(visited, HEADER_VALIDATION);
                }
                if (TypeUtils.isOfClassType(type, "io.netty.handler.codec.http.DefaultHttpHeaders") &&
                    args.size() == 1 && !literalTrue(args.get(0))) return mark(visited, HEADER_VALIDATION);
                if (TypeUtils.isOfClassType(type, "io.netty.handler.codec.http.HttpClientCodec") &&
                    args.size() >= 5 && args.size() <= 9) {
                    return mark(visited, literalTrue(args.get(4)) ? CLIENT_CONSTRUCTOR : HEADER_VALIDATION);
                }
                if (DECODER_TYPES.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name))) {
                    return mark(visited, PARSING);
                }
                if (AGGREGATION_TYPES.stream().anyMatch(name -> TypeUtils.isAssignableTo(name, type))) {
                    return mark(visited, AGGREGATION);
                }
                if (COMPRESSION_TYPES.stream().anyMatch(name -> TypeUtils.isAssignableTo(name, type))) {
                    return mark(visited, COMPRESSION);
                }
                String fqn = fullyQualified(type);
                if (fqn.startsWith("io.netty.handler.codec.http.multipart.")) return mark(visited, MULTIPART);
                if (TypeUtils.isOfClassType(type, "io.netty.handler.codec.http.QueryStringDecoder") ||
                    TypeUtils.isOfClassType(type, "io.netty.handler.codec.http.QueryStringEncoder")) return mark(visited, QUERY);
                if (TypeUtils.isOfClassType(type, "io.netty.handler.codec.http.HttpMethod") ||
                    TypeUtils.isOfClassType(type, "io.netty.handler.codec.http.HttpVersion")) return mark(visited, CUSTOM_METHOD);
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                JavaType owner = method == null ? null : method.getDeclaringType();
                String ownerName = fullyQualified(owner);
                String name = visited.getSimpleName();
                List<Expression> args = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                if ("setValidateHeaders".equals(name) &&
                    TypeUtils.isOfClassType(owner, "io.netty.handler.codec.http.HttpDecoderConfig") &&
                    (args.isEmpty() || !literalTrue(args.get(0)))) return mark(visited, HEADER_VALIDATION);
                if ("withValidation".equals(name) &&
                    TypeUtils.isOfClassType(owner, "io.netty.handler.codec.http.DefaultHttpHeadersFactory") &&
                    (args.isEmpty() || !literalTrue(args.get(0)))) return mark(visited, HEADER_VALIDATION);
                if ("setUseRfc9112TransferEncoding".equals(name) &&
                    TypeUtils.isOfClassType(owner, "io.netty.handler.codec.http.HttpDecoderConfig") &&
                    (args.isEmpty() || !literalTrue(args.get(0)))) return mark(visited, RFC9112);
                if (TypeUtils.isOfClassType(owner, "java.lang.System") &&
                    Set.of("setProperty", "getProperty", "clearProperty").contains(name) && !args.isEmpty() &&
                    args.get(0) instanceof J.Literal literal &&
                    "io.netty.handler.codec.http.rfc9112TransferEncoding".equals(literal.getValue())) {
                    return mark(visited, RFC9112);
                }
                if (("valueOf".equals(name) || "newMethod".equals(name)) &&
                    (TypeUtils.isOfClassType(owner, "io.netty.handler.codec.http.HttpMethod") ||
                     TypeUtils.isOfClassType(owner, "io.netty.handler.codec.http.HttpVersion"))) {
                    return mark(visited, CUSTOM_METHOD);
                }
                if (TypeUtils.isOfClassType(owner, SPDY_HTTP_DECODER) && "channelInactive".equals(name)) {
                    return mark(visited, SPDY_LIFECYCLE);
                }
                if ((TypeUtils.isOfClassType(owner, SPDY_DELEGATE) ||
                     TypeUtils.isOfClassType(owner, "io.netty.handler.codec.spdy.SpdyFrameCodec")) &&
                    "readUnknownFrame".equals(name)) return mark(visited, BRANCH_API);
                if (TypeUtils.isOfClassType(owner,
                        "io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory") &&
                    "resolveHandshaker".equals(name)) return mark(visited, BRANCH_API);
                if (ownerName.startsWith("io.netty.handler.codec.http.multipart.")) return mark(visited, MULTIPART);
                if (TypeUtils.isOfClassType(owner, "io.netty.handler.codec.http.QueryStringDecoder") ||
                    TypeUtils.isOfClassType(owner, "io.netty.handler.codec.http.QueryStringEncoder")) return mark(visited, QUERY);
                if (TypeUtils.isOfClassType(owner, "io.netty.handler.codec.DateFormatter") &&
                    "parseHttpDate".equals(name)) return mark(visited, DATE);
                if (AGGREGATION_TYPES.stream().anyMatch(type -> TypeUtils.isAssignableTo(type, owner))) {
                    return mark(visited, AGGREGATION);
                }
                if (COMPRESSION_TYPES.stream().anyMatch(type -> TypeUtils.isAssignableTo(type, owner))) {
                    return mark(visited, COMPRESSION);
                }
                return visited;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference visited = super.visitMemberReference(memberRef, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null) return visited;
                JavaType owner = method.getDeclaringType();
                if (TypeUtils.isOfClassType(owner,
                        "io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory") &&
                    "resolveHandshaker".equals(method.getName())) return mark(visited, BRANCH_API);
                if ((TypeUtils.isOfClassType(owner, SPDY_DELEGATE) || TypeUtils.isOfClassType(owner, SPDY_HTTP_DECODER)) &&
                    Set.of("readUnknownFrame", "channelInactive").contains(method.getName())) {
                    return mark(visited, BRANCH_API);
                }
                return visited;
            }
        };
    }

    private static boolean sixArgument42UpgradeConstructor(J.NewClass newClass) {
        JavaType.Method constructor = newClass.getConstructorType();
        if (constructor == null || constructor.getParameterTypes().size() != 6) return false;
        List<JavaType> p = constructor.getParameterTypes();
        return TypeUtils.isOfClassType(p.get(0),
                "io.netty.handler.codec.http.HttpServerUpgradeHandler$SourceCodec") &&
               TypeUtils.isOfClassType(p.get(1),
                "io.netty.handler.codec.http.HttpServerUpgradeHandler$UpgradeCodecFactory") &&
               p.get(2) == JavaType.Primitive.Int &&
               TypeUtils.isOfClassType(p.get(3), "io.netty.handler.codec.http.HttpHeadersFactory") &&
               TypeUtils.isOfClassType(p.get(4), "io.netty.handler.codec.http.HttpHeadersFactory") &&
               p.get(5) == JavaType.Primitive.Boolean;
    }

    private static boolean literalTrue(Expression expression) {
        return expression instanceof J.Literal literal && Boolean.TRUE.equals(literal.getValue());
    }

    private static boolean fourTwoOnly(JavaType type) {
        return FOUR_TWO_ONLY_TYPES.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name));
    }

    private static String fullyQualified(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? "" : fq.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
