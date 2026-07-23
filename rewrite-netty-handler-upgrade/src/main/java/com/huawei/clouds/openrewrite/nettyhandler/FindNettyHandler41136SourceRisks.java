package com.huawei.clouds.openrewrite.nettyhandler;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate handler APIs whose behavior, lifecycle or target-branch availability requires application evidence. */
public final class FindNettyHandler41136SourceRisks extends Recipe {
    static final String TLS_CONTEXT =
            "Netty 4.1.136 changes TLS defaults and fixes provider/session/hostname-verification behavior; verify " +
            "protocols, ciphers, client peer host, endpoint identification, session reuse and handshake/close timeouts";
    static final String TLS_DETECTION =
            "SslHandler encrypted-record detection changed to reject SSLv2 false positives; regression-test " +
            "fragmented, malformed, plaintext and TLS records at this protocol-detection boundary";
    static final String TRUST =
            "Trust-manager or certificate pinning behavior is security-sensitive; SHA-1 fingerprint constructors are " +
            "deprecated, so choose SHA-256 pins/trust ownership explicitly and test rotation and hostname validation";
    static final String LEGACY_TLS =
            "TLSv1/TLSv1.1/SSL protocols are disabled or deprecated on this migration path; remove legacy protocols " +
            "only after proving every peer and fallback policy";
    static final String NATIVE_RESOURCE =
            "Reference-counted OpenSSL contexts and native engines require explicit lifecycle ownership; verify " +
            "retain/release, delegated tasks, tcnative provider selection and native leak detection";
    static final String SNI =
            "SNI/client-hello handling now enforces hostname and bounded handshake defaults; test malformed names, " +
            "fragmented ClientHello, 64 KiB limits, 10-second timeout, async lookup failure and handler removal";
    static final String ALPN =
            "ALPN negotiation and pipeline replacement changed around handler removal/draining; test negotiated, " +
            "unsupported, failed and no-protocol paths with both JDK and OpenSSL providers";
    static final String TIMEOUT_IDLE =
            "Idle/timeout handlers changed reset, first-event and executor behavior; test read/write/all-idle events, " +
            "manual resets, pending writes, cancellation, event-loop ordering and handler removal";
    static final String TRAFFIC =
            "Traffic-shaping close paths now fail promises and release queued reference-counted messages; verify " +
            "queue limits, accounting, writability, cancellation and channel-close reference counts";
    static final String FLOW =
            "Flow-control behavior depends on AUTO_READ, queued messages and read-complete ordering; test manual read, " +
            "backpressure, re-entrancy, removal and reference counts";
    static final String IP_FILTER =
            "IP-filter rule order, null-rule termination and accept-if-no-rule policy are security decisions; verify " +
            "IPv4/IPv6, unresolved addresses, default allow/deny, rejection events and channel close behavior";
    static final String PROXY =
            "Proxy negotiation owns authentication, timeout, DNS/address and pipeline lifecycle behavior; test success, " +
            "refusal, partial responses, handler removal and secret redaction";
    static final String LOGGING =
            "LoggingHandler may emit payloads or credentials; verify log level, byte format, allocation cost and " +
            "redaction before enabling it in production";
    static final String PCAP =
            "PcapWriteHandler captures sensitive traffic and has builder/lifecycle changes; verify capture ownership, " +
            "forced TCP parameters, stream closure, truncation, concurrency and secret handling";
    static final String CHUNKED =
            "ChunkedWriteHandler coordinates backpressure and reference-counted input; test suspend/resume, zero writes, " +
            "failure, discard, channel close, handler removal and progress promises";
    static final String PIPELINE =
            "Dynamic pipeline mutation can interact with TLS/ALPN/SNI and queued handler state; verify event-loop " +
            "ordering, pending events, removal callbacks and reference counts";
    static final String TRANSPORT =
            "This transport option controls reads, connect timeouts or write pressure used by handlers; verify the " +
            "4.1.136 pipeline under slow peers, manual reads and high/low watermarks";
    static final String BRANCH_ONLY =
            "目标版本冲突（禁止降级）: this API exists on Netty 4.2 but is unavailable on the requested 4.1.136.Final " +
            "branch; the source needs a forward-target decision and must not be forced onto the lower branch";

    private static final String SSL_CONTEXT_BUILDER = "io.netty.handler.ssl.SslContextBuilder";
    private static final String SSL_CONTEXT = "io.netty.handler.ssl.SslContext";
    private static final String SSL_HANDLER = "io.netty.handler.ssl.SslHandler";
    private static final String OPEN_SSL = "io.netty.handler.ssl.OpenSsl";
    private static final String SELF_SIGNED = "io.netty.handler.ssl.util.SelfSignedCertificate";
    private static final String INSECURE_TRUST =
            "io.netty.handler.ssl.util.InsecureTrustManagerFactory";
    private static final String FINGERPRINT_TRUST =
            "io.netty.handler.ssl.util.FingerprintTrustManagerFactory";
    private static final String SSL_PROTOCOLS = "io.netty.handler.ssl.SslProtocols";
    private static final String OPEN_SSL_OPTION = "io.netty.handler.ssl.OpenSslContextOption";
    private static final String CHANNEL_OPTION = "io.netty.channel.ChannelOption";

    private static final Set<String> SSL_BUILDER_METHODS = Set.of(
            "forClient", "forServer", "sslProvider", "protocols", "ciphers",
            "applicationProtocolConfig", "endpointIdentificationAlgorithm", "startTls", "enableOcsp",
            "sessionCacheSize", "sessionTimeout", "trustManager", "keyManager", "option",
            "secureRandom", "build");
    private static final Set<String> SSL_HANDLER_METHODS = Set.of(
            "handshakeFuture", "closeFuture", "setHandshakeTimeout", "setHandshakeTimeoutMillis",
            "setCloseNotifyFlushTimeout", "setCloseNotifyFlushTimeoutMillis",
            "setCloseNotifyReadTimeout", "setCloseNotifyReadTimeoutMillis", "renegotiate",
            "engine", "applicationProtocol", "isEncrypted");
    private static final Set<String> TRANSPORT_OPTIONS = Set.of(
            "AUTO_READ", "WRITE_BUFFER_WATER_MARK", "CONNECT_TIMEOUT_MILLIS", "MAX_MESSAGES_PER_READ");
    private static final Set<String> LEGACY_PROTOCOLS = Set.of(
            "SSL_v2", "SSL_v2_HELLO", "SSL_v3", "TLS_v1", "TLS_v1_1");

    @Override
    public String getDisplayName() {
        return "Find Netty handler 4.1.136 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark TLS/SSL, trust, SNI, ALPN, native lifecycle, timeout, traffic, flow-control, filtering, proxy, " +
               "logging, PCAP, chunked-write, transport and 4.2-only source decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return NettyHandlerSupport.generated(cu.getSourcePath()) ? cu :
                        super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import import_, ExecutionContext ctx) {
                J.Import visited = super.visitImport(import_, ctx);
                String type = visited.getTypeName();
                return branchOnlyType(type) ? mark(visited, BRANCH_ONLY) : visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = typeName(visited.getType());
                String message = constructorMessage(type);
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                if (methodType == null) return visited;
                String owner = typeName(methodType.getDeclaringType());
                String message = methodMessage(owner, visited.getSimpleName());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference visited = super.visitMemberReference(memberRef, ctx);
                JavaType.Method methodType = visited.getMethodType();
                if (methodType == null) return visited;
                String message = methodMessage(typeName(methodType.getDeclaringType()),
                        visited.getReference().getSimpleName());
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                JavaType.Variable field = visited.getFieldType();
                if (field == null) return visited;
                String owner = typeName(field.getOwner());
                String name = visited.getSimpleName();
                String message = fieldMessage(owner, name);
                return message == null ? visited : mark(visited, message);
            }
        };
    }

    private static String constructorMessage(String type) {
        if (type == null) return null;
        if (FINGERPRINT_TRUST.equals(type) || INSECURE_TRUST.equals(type)) return TRUST;
        if (type.equals(SSL_HANDLER) || type.startsWith("io.netty.handler.ssl.")) {
            if (type.contains("Sni") || type.endsWith("SslClientHelloHandler")) return SNI;
            if (type.contains("ApplicationProtocol") || type.contains("Alpn") || type.contains("Npn")) return ALPN;
            if (type.contains("OpenSsl") || type.contains("ReferenceCounted")) return NATIVE_RESOURCE;
            return TLS_CONTEXT;
        }
        if (type.startsWith("io.netty.handler.timeout.")) return TIMEOUT_IDLE;
        if (type.startsWith("io.netty.handler.traffic.")) return TRAFFIC;
        if (type.startsWith("io.netty.handler.flow.")) return FLOW;
        if (type.startsWith("io.netty.handler.ipfilter.")) return IP_FILTER;
        if (type.startsWith("io.netty.handler.proxy.")) return PROXY;
        if (type.equals("io.netty.handler.logging.LoggingHandler")) return LOGGING;
        if (type.equals("io.netty.handler.pcap.PcapWriteHandler")) return PCAP;
        if (type.equals("io.netty.handler.stream.ChunkedWriteHandler")) return CHUNKED;
        return branchOnlyType(type) ? BRANCH_ONLY : null;
    }

    private static String methodMessage(String owner, String name) {
        if (owner == null) return null;
        if (SSL_CONTEXT_BUILDER.equals(owner) && "serverName".equals(name) ||
            SELF_SIGNED.equals(owner) && "builder".equals(name) ||
            OPEN_SSL.equals(owner) && "isRenegotiationSupported".equals(name)) return BRANCH_ONLY;
        if (SSL_CONTEXT_BUILDER.equals(owner) && SSL_BUILDER_METHODS.contains(name)) {
            return "trustManager".equals(name) ? TRUST : TLS_CONTEXT;
        }
        if (SSL_CONTEXT.equals(owner) && "newHandler".equals(name)) return TLS_CONTEXT;
        if (SSL_HANDLER.equals(owner) && SSL_HANDLER_METHODS.contains(name)) {
            return "isEncrypted".equals(name) ? TLS_DETECTION : TLS_CONTEXT;
        }
        if (FINGERPRINT_TRUST.equals(owner) || INSECURE_TRUST.equals(owner)) return TRUST;
        if (owner.startsWith("io.netty.handler.ssl.")) {
            if (owner.contains("Sni") || owner.endsWith("SslClientHelloHandler")) return SNI;
            if (owner.contains("ApplicationProtocol") || owner.contains("Alpn") || owner.contains("Npn")) return ALPN;
            if (owner.contains("OpenSsl") || owner.contains("ReferenceCounted")) return NATIVE_RESOURCE;
            return TLS_CONTEXT;
        }
        if (owner.startsWith("io.netty.handler.timeout.")) return TIMEOUT_IDLE;
        if (owner.startsWith("io.netty.handler.traffic.")) return TRAFFIC;
        if (owner.startsWith("io.netty.handler.flow.")) return FLOW;
        if (owner.startsWith("io.netty.handler.ipfilter.")) return IP_FILTER;
        if (owner.startsWith("io.netty.handler.proxy.")) return PROXY;
        if (owner.equals("io.netty.handler.logging.LoggingHandler")) return LOGGING;
        if (owner.equals("io.netty.handler.pcap.PcapWriteHandler")) return PCAP;
        if (owner.equals("io.netty.handler.stream.ChunkedWriteHandler")) return CHUNKED;
        if (owner.equals("io.netty.channel.ChannelPipeline") &&
            Set.of("addFirst", "addLast", "addBefore", "addAfter", "remove", "replace").contains(name)) {
            return PIPELINE;
        }
        return null;
    }

    private static String fieldMessage(String owner, String name) {
        if (owner == null) return null;
        if (OPEN_SSL_OPTION.equals(owner) && "USE_JDK_PROVIDER_SIGNATURES".equals(name)) return BRANCH_ONLY;
        if (INSECURE_TRUST.equals(owner) && "INSTANCE".equals(name)) return TRUST;
        if (SSL_PROTOCOLS.equals(owner) && LEGACY_PROTOCOLS.contains(name)) return LEGACY_TLS;
        if (CHANNEL_OPTION.equals(owner) && TRANSPORT_OPTIONS.contains(name)) return TRANSPORT;
        return null;
    }

    private static boolean branchOnlyType(String type) {
        return "io.netty.handler.ssl.util.SelfSignedCertificate.Builder".equals(type);
    }

    private static String typeName(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? null : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree :
                SearchResult.found(tree, message);
    }
}
