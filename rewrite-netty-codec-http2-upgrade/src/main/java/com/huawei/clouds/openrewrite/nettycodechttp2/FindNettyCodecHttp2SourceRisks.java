package com.huawei.clouds.openrewrite.nettycodechttp2;

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

/** Locate HTTP/2 validation, resource, flow-control, protocol and branch compatibility decisions. */
public final class FindNettyCodecHttp2SourceRisks extends Recipe {
    static final String HEADER_VALIDATION =
            "HTTP/2 header name/value validation is disabled or runtime-selected; 4.1.136 rejects more malformed " +
            "names and pseudo-header layouts—enable validation and test lowercase, token, trailer and injection cases";
    static final String REQUIRED_PSEUDO =
            "Mandatory HTTP/2 pseudo-header validation is disabled, runtime-selected or implicit; decide whether to " +
            "enable validateRequiredPseudoHeaders and test request/response/trailer and CONNECT combinations";
    static final String HEADER_LIMITS =
            "HPACK/header table or header-list limits affect memory, interoperability and GOAWAY behavior; set an " +
            "explicit budget and test peer SETTINGS, oversized headers, CONTINUATION frames and compression state";
    static final String ABUSE_LIMITS =
            "HTTP/2 control-frame/stream abuse limits changed for Rapid Reset, empty DATA, small CONTINUATION, queued " +
            "control frames and concurrent streams; set explicit production limits and test rejection windows";
    static final String DECOMPRESSION =
            "HTTP/2 decompression allocation and cleanup changed; maxAllocation=0 preserves the old unbounded behavior—" +
            "choose a budget and test compressed bombs, padding, stream reset/removal and ByteBuf release";
    static final String FLOW_CONTROL =
            "HTTP/2 flow-control and stream buffering behavior changed; verify window updates, AUTO_READ, auto refill, " +
            "queued frames, child-channel writability, cancellation and reference counts under backpressure";
    static final String ACK_PREFACE =
            "HTTP/2 SETTINGS/PING acknowledgement, preface flush or graceful GOAWAY behavior is customized; verify " +
            "the first outbound bytes, manual ACK ordering, close races, timeout and StreamBufferingEncoder behavior";
    static final String UPGRADE_ALPN =
            "HTTP/2 TLS/ALPN or h2c upgrade pipeline detected; verify protocol selection, upgrade headers/settings, " +
            "handler ordering/removal, fallback, prior knowledge and ByteBuf ownership in the packaged application";
    static final String CONVERSION =
            "HTTP/1↔HTTP/2 conversion/aggregation behavior changed for paths, cookies and max-content failures; test " +
            "absolute/origin/CONNECT targets, malformed cookies, 413 stream errors and full-message release";
    static final String MULTIPLEX =
            "HTTP/2 frame/stream multiplex lifecycle changed for priority events, unknown frames, real stream ids and " +
            "child closure; verify event routing, stream association, handler sharability and reference counts";
    static final String PUSH_CONNECT =
            "HTTP/2 PUSH_PROMISE or extended CONNECT settings/association detected; validate promised stream ownership, " +
            "SETTINGS_ENABLE_CONNECT_PROTOCOL negotiation, disabled-push behavior and proxy interoperability";
    static final String ENCODER_RESOURCE =
            "DefaultHttp2HeadersEncoder now owns closeable HPACK state; verify its owner closes it exactly once, " +
            "including construction failure, handler removal and connection shutdown paths";
    static final String CUSTOM_CODEC =
            "Custom HTTP/2 builder/handler/listener code participates in changed defaults and frame lifecycle; rebase " +
            "protected overrides on 4.1.136 and test errors, user events, close/removal and reference counts";
    static final String BRANCH_API =
            "This API is public in Netty 4.2.10/4.2.12 but unavailable in the 4.1.136 target branch; this is a target " +
            "version conflict (downgrade forbidden), so keep 4.2 or replace it only after an explicit architecture decision";

    private static final String PACKAGE = "io.netty.handler.codec.http2.";
    private static final String DEFAULT_HEADERS = PACKAGE + "DefaultHttp2Headers";
    private static final String DEFAULT_HEADERS_DECODER = PACKAGE + "DefaultHttp2HeadersDecoder";
    private static final String CONNECTION_DECODER = PACKAGE + "DefaultHttp2ConnectionDecoder";
    private static final String DECOMPRESSOR = PACKAGE + "DelegatingDecompressorFrameListener";
    private static final String HEADERS_ENCODER = PACKAGE + "DefaultHttp2HeadersEncoder";
    private static final String SERVER_UPGRADE = PACKAGE + "Http2ServerUpgradeCodec";
    private static final Set<String> BUILDER_TYPES = Set.of(
            PACKAGE + "Http2ConnectionHandlerBuilder", PACKAGE + "Http2FrameCodecBuilder",
            PACKAGE + "Http2MultiplexCodecBuilder", PACKAGE + "HttpToHttp2ConnectionHandlerBuilder",
            PACKAGE + "InboundHttp2ToHttpAdapterBuilder");
    private static final Set<String> UPGRADE_TYPES = Set.of(
            PACKAGE + "Http2ClientUpgradeCodec", SERVER_UPGRADE, PACKAGE + "Http2SecurityUtil",
            "io.netty.handler.ssl.ApplicationProtocolNegotiationHandler");
    private static final Set<String> CONVERSION_TYPES = Set.of(
            PACKAGE + "HttpConversionUtil", PACKAGE + "HttpToHttp2ConnectionHandler",
            PACKAGE + "HttpToHttp2ConnectionHandlerBuilder", PACKAGE + "InboundHttp2ToHttpAdapter",
            PACKAGE + "InboundHttp2ToHttpAdapterBuilder");
    private static final Set<String> MULTIPLEX_TYPES = Set.of(
            PACKAGE + "Http2FrameCodec", PACKAGE + "Http2MultiplexCodec", PACKAGE + "Http2MultiplexHandler",
            PACKAGE + "Http2StreamChannel", PACKAGE + "Http2FrameStream", PACKAGE + "Http2UnknownFrame",
            PACKAGE + "Http2PriorityFrame");
    private static final Set<String> FLOW_TYPES = Set.of(
            PACKAGE + "DefaultHttp2LocalFlowController", PACKAGE + "DefaultHttp2RemoteFlowController",
            PACKAGE + "StreamBufferingEncoder", PACKAGE + "Http2StreamChannelOption");
    private static final Set<String> PUSH_TYPES = Set.of(
            PACKAGE + "Http2PushPromiseFrame", PACKAGE + "Http2PromisedRequestVerifier");
    private static final Set<String> CUSTOM_BASES = Set.of(
            PACKAGE + "Http2ConnectionHandler", PACKAGE + "Http2FrameCodec", PACKAGE + "Http2FrameListener");
    private static final Set<String> ABUSE_METHODS = Set.of(
            "decoderEnforceMaxConsecutiveEmptyDataFrames", "decoderEnforceMaxRstFramesPerWindow",
            "encoderEnforceMaxRstFramesPerWindow", "decoderEnforceMaxSmallContinuationFrames",
            "encoderEnforceMaxConcurrentStreams", "encoderEnforceMaxQueuedControlFrames", "maxConcurrentStreams",
            "maxReservedStreams", "maxFrameSize");
    private static final Set<String> HEADER_LIMIT_METHODS = Set.of(
            "maxHeaderListSize", "maxHeaderTableSize", "headerTableSize", "encoderIgnoreMaxHeaderListSize");
    private static final Set<String> FLOW_METHODS = Set.of(
            "initialWindowSize", "windowUpdateRatio", "autoRefillConnectionWindow", "incrementWindowSize",
            "consumeBytes", "writePendingBytes");
    private static final Set<String> ACK_METHODS = Set.of(
            "autoAckSettingsFrame", "autoAckPingFrame", "writeSettingsAck", "flushPreface",
            "decoupleCloseAndGoAway", "gracefulShutdownTimeoutMillis");
    private static final Set<String> HEADER_MUTATIONS = Set.of(
            "add", "set", "setAll", "method", "scheme", "authority", "path", "status");

    @Override
    public String getDisplayName() {
        return "Find Netty HTTP/2 codec 4.1.136 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact validation, HPACK/abuse limits, decompression, flow-control, ACK/preface, conversion, " +
               "multiplex, PUSH/CONNECT and 4.2-only API decisions that require application evidence.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return NettyCodecHttp2Support.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                JavaType.FullyQualified type = visited.getType();
                if (type == null) return visited;
                boolean customBuilder = BUILDER_TYPES.stream().anyMatch(base -> TypeUtils.isAssignableTo(base, type)) &&
                                        BUILDER_TYPES.stream().noneMatch(base -> TypeUtils.isOfClassType(type, base));
                boolean customCodec = CUSTOM_BASES.stream().anyMatch(base -> TypeUtils.isAssignableTo(base, type)) &&
                                      CUSTOM_BASES.stream().noneMatch(base -> TypeUtils.isOfClassType(type, base));
                return customBuilder || customCodec ? mark(visited, CUSTOM_CODEC) : visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                JavaType type = visited.getType();
                List<Expression> args = arguments(visited);
                J.NewClass result = visited;

                if (TypeUtils.isOfClassType(type, SERVER_UPGRADE) && branchUpgradeConstructor(visited)) {
                    return mark(result, BRANCH_API);
                }
                if (TypeUtils.isOfClassType(type, DEFAULT_HEADERS)) {
                    if (!args.isEmpty() && !literalTrue(args.get(0))) result = mark(result, HEADER_VALIDATION);
                    if (defaultHeadersValueValidationDisabled(visited, args)) {
                        result = mark(result, HEADER_VALIDATION);
                    }
                    return result;
                }
                if (TypeUtils.isOfClassType(type, DEFAULT_HEADERS_DECODER)) {
                    if (!args.isEmpty() && !literalTrue(args.get(0))) result = mark(result, HEADER_VALIDATION);
                    if (headersDecoderValueValidationDisabled(visited, args)) {
                        result = mark(result, HEADER_VALIDATION);
                    }
                    return mark(result, HEADER_LIMITS);
                }
                if (TypeUtils.isOfClassType(type, CONNECTION_DECODER)) {
                    if (args.size() >= 7 && !literalTrue(args.get(6))) result = mark(result, HEADER_VALIDATION);
                    if (args.size() < 8 || !literalTrue(args.get(7))) result = mark(result, REQUIRED_PSEUDO);
                    if ((args.size() >= 5 && !literalTrue(args.get(4))) ||
                        (args.size() >= 6 && !literalTrue(args.get(5)))) result = mark(result, ACK_PREFACE);
                    return result;
                }
                if (TypeUtils.isOfClassType(type, DECOMPRESSOR)) return mark(result, DECOMPRESSION);
                if (TypeUtils.isOfClassType(type, HEADERS_ENCODER)) return mark(result, ENCODER_RESOURCE);
                if (UPGRADE_TYPES.stream().anyMatch(name -> TypeUtils.isAssignableTo(name, type))) {
                    return mark(result, UPGRADE_ALPN);
                }
                if (CONVERSION_TYPES.stream().anyMatch(name -> TypeUtils.isAssignableTo(name, type))) {
                    return mark(result, CONVERSION);
                }
                if (MULTIPLEX_TYPES.stream().anyMatch(name -> TypeUtils.isAssignableTo(name, type))) {
                    return mark(result, MULTIPLEX);
                }
                if (FLOW_TYPES.stream().anyMatch(name -> TypeUtils.isAssignableTo(name, type))) {
                    return mark(result, FLOW_CONTROL);
                }
                if (PUSH_TYPES.stream().anyMatch(name -> TypeUtils.isAssignableTo(name, type))) {
                    return mark(result, PUSH_CONNECT);
                }
                return result;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                JavaType owner = method == null ? null : method.getDeclaringType();
                String name = visited.getSimpleName();
                List<Expression> args = arguments(visited);

                if (TypeUtils.isOfClassType(owner, DEFAULT_HEADERS) &&
                    Set.of("defaultHtt2NameValidator", "defaultHttp2ValueValidator").contains(name)) {
                    return mark(visited, BRANCH_API);
                }
                if ("validateHeaders".equals(name) && isBuilder(owner) &&
                    (args.isEmpty() || !literalTrue(args.get(0)))) return mark(visited, HEADER_VALIDATION);
                if ("validateRequiredPseudoHeaders".equals(name) && isBuilder(owner) &&
                    (args.isEmpty() || !literalTrue(args.get(0)))) return mark(visited, REQUIRED_PSEUDO);
                if (HEADER_LIMIT_METHODS.contains(name) && http2Owner(owner)) return mark(visited, HEADER_LIMITS);
                if (ABUSE_METHODS.contains(name) && http2Owner(owner)) return mark(visited, ABUSE_LIMITS);
                if (FLOW_METHODS.contains(name) && http2Owner(owner)) return mark(visited, FLOW_CONTROL);
                if (ACK_METHODS.contains(name) && http2Owner(owner)) return mark(visited, ACK_PREFACE);
                if (TypeUtils.isAssignableTo(PACKAGE + "Http2Headers", owner) && HEADER_MUTATIONS.contains(name)) {
                    return mark(visited, HEADER_VALIDATION);
                }
                if ("maxContentLength".equals(name) &&
                    TypeUtils.isOfClassType(owner, PACKAGE + "InboundHttp2ToHttpAdapterBuilder")) {
                    return mark(visited, CONVERSION);
                }
                if (("pushEnabled".equals(name) || "connectProtocolEnabled".equals(name) ||
                     "onPushPromiseRead".equals(name)) && http2Owner(owner)) return mark(visited, PUSH_CONNECT);
                if (TypeUtils.isOfClassType(owner, HEADERS_ENCODER) && "close".equals(name)) {
                    return mark(visited, ENCODER_RESOURCE);
                }
                if (UPGRADE_TYPES.stream().anyMatch(type -> TypeUtils.isAssignableTo(type, owner))) {
                    return mark(visited, UPGRADE_ALPN);
                }
                if (CONVERSION_TYPES.stream().anyMatch(type -> TypeUtils.isAssignableTo(type, owner))) {
                    return mark(visited, CONVERSION);
                }
                if (MULTIPLEX_TYPES.stream().anyMatch(type -> TypeUtils.isAssignableTo(type, owner))) {
                    return mark(visited, MULTIPLEX);
                }
                if (FLOW_TYPES.stream().anyMatch(type -> TypeUtils.isAssignableTo(type, owner))) {
                    return mark(visited, FLOW_CONTROL);
                }
                if (PUSH_TYPES.stream().anyMatch(type -> TypeUtils.isAssignableTo(type, owner))) {
                    return mark(visited, PUSH_CONNECT);
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                JavaType.Variable field = visited.getName().getFieldType();
                JavaType owner = field == null ? null : field.getOwner();
                if (TypeUtils.isOfClassType(owner, PACKAGE + "Http2StreamChannelOption") &&
                    "AUTO_STREAM_FLOW_CONTROL".equals(visited.getSimpleName())) return mark(visited, FLOW_CONTROL);
                if (TypeUtils.isOfClassType(owner, PACKAGE + "Http2CodecUtil") &&
                    "SETTINGS_ENABLE_CONNECT_PROTOCOL".equals(visited.getSimpleName())) return mark(visited, PUSH_CONNECT);
                if (TypeUtils.isOfClassType(owner, PACKAGE + "Http2SecurityUtil") &&
                    "CIPHERS".equals(visited.getSimpleName())) return mark(visited, UPGRADE_ALPN);
                return visited;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference visited = super.visitMemberReference(memberRef, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method != null && TypeUtils.isOfClassType(method.getDeclaringType(), DEFAULT_HEADERS) &&
                    Set.of("defaultHtt2NameValidator", "defaultHttp2ValueValidator").contains(method.getName())) {
                    return mark(visited, BRANCH_API);
                }
                return visited;
            }
        };
    }

    private static boolean defaultHeadersValueValidationDisabled(J.NewClass newClass, List<Expression> args) {
        JavaType.Method constructor = newClass.getConstructorType();
        return constructor != null && constructor.getParameterTypes().size() == 3 && args.size() == 3 &&
               constructor.getParameterTypes().get(1) == JavaType.Primitive.Boolean && !literalTrue(args.get(1));
    }

    private static boolean headersDecoderValueValidationDisabled(J.NewClass newClass, List<Expression> args) {
        JavaType.Method constructor = newClass.getConstructorType();
        return constructor != null && args.size() >= 2 && constructor.getParameterTypes().size() == args.size() &&
               constructor.getParameterTypes().get(1) == JavaType.Primitive.Boolean && !literalTrue(args.get(1));
    }

    private static boolean branchUpgradeConstructor(J.NewClass newClass) {
        JavaType.Method constructor = newClass.getConstructorType();
        if (constructor == null || constructor.getParameterTypes().size() != 3) return false;
        List<JavaType> p = constructor.getParameterTypes();
        return TypeUtils.isOfClassType(p.get(0), "java.lang.String") &&
               TypeUtils.isOfClassType(p.get(1), PACKAGE + "Http2ConnectionHandler") &&
               p.get(2) instanceof JavaType.Array array &&
               TypeUtils.isOfClassType(array.getElemType(), "io.netty.channel.ChannelHandler");
    }

    private static boolean isBuilder(JavaType owner) {
        return BUILDER_TYPES.stream().anyMatch(type -> TypeUtils.isAssignableTo(type, owner));
    }

    private static boolean http2Owner(JavaType owner) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(owner);
        return type != null && type.getFullyQualifiedName().startsWith(PACKAGE);
    }

    private static List<Expression> arguments(J.NewClass newClass) {
        return newClass.getArguments().stream().filter(argument -> !(argument instanceof J.Empty)).toList();
    }

    private static List<Expression> arguments(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().filter(argument -> !(argument instanceof J.Empty)).toList();
    }

    private static boolean literalTrue(Expression expression) {
        return expression instanceof J.Literal literal && Boolean.TRUE.equals(literal.getValue());
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
