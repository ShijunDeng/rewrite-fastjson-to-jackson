package com.huawei.clouds.openrewrite.jsch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.Set;

/** Marks attributed JSch API calls whose target behavior cannot be selected without application intent. */
public final class FindJsch227JavaRisks extends Recipe {
    private static final Set<String> EXTENSION_POINTS = Set.of(
            "com.jcraft.jsch.Identity", "com.jcraft.jsch.HostKeyRepository", "com.jcraft.jsch.ConfigRepository",
            "com.jcraft.jsch.Proxy", "com.jcraft.jsch.SocketFactory", "com.jcraft.jsch.Logger");
    private static final Set<String> ALGORITHM_KEYS = Set.of(
            "kex", "server_host_key", "PubkeyAcceptedAlgorithms", "PubkeyAcceptedKeyTypes", "cipher.s2c", "cipher.c2s", "mac.s2c",
            "mac.c2s", "compression.s2c", "compression.c2s", "CheckCiphers", "CheckKexes",
            "CheckSignatures", "FingerprintHash", "enable_server_sig_algs", "enable_strict_kex");
    private static final String ALGORITHM_MESSAGE =
            "JSch 2.27.7 changed secure algorithm defaults and ordering across this upgrade path; verify the exact server/client negotiation and do not blindly re-enable ssh-rsa/SHA-1, CBC, weak KEX, or weak MAC algorithms";
    private static final String TRUST_MESSAGE =
            "Host-key trust boundary: verify the known_hosts source, hashing/fingerprint format, rotation behavior, and StrictHostKeyChecking policy; never replace verification with an accept-all callback";
    private static final String IDENTITY_MESSAGE =
            "Identity/key loading boundary: test encrypted OpenSSH keys, passphrase failures, Ed25519/Ed448/curve support, optional Bouncy Castle availability, agent repositories, and exception handling on the deployment JDK";
    private static final String SESSION_MESSAGE =
            "SSH connection boundary: integration-test negotiation, authentication, proxy/socket behavior, timeout units, keepalives, strict KEX, reconnect/cleanup, and server compatibility with JSch 2.27.7";
    private static final String EXTENSION_MESSAGE =
            "Custom JSch extension point: recompile against 2.27.7 and verify interface defaults, exception behavior, algorithm naming, thread safety, cleanup, and classloader/service dependencies";
    private static final String DEPRECATED_MESSAGE =
            "This JSch API is deprecated or unsupported in 2.27.7; migrate to the documented typed overload and verify its changed exception/resource semantics";
    private static final String FINGERPRINT_MESSAGE =
            "Host-key fingerprint formatting/default hash changed on this upgrade path; do not parse display text, and verify stored fingerprints, UI, audit records, and comparisons with an explicit approved hash";

    @Override
    public String getDisplayName() {
        return "Find JSch 2.27.7 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Precisely mark attributed algorithm, host-key, identity, session, deprecated API, fingerprint, and " +
               "custom extension points that require security or operational intent.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedJschDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration,
                                                             ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDeclaration, ctx);
                JavaType.FullyQualified type = c.getType();
                if (type != null && EXTENSION_POINTS.stream().anyMatch(extension ->
                        !extension.equals(type.getFullyQualifiedName()) && TypeUtils.isAssignableTo(extension, type))) {
                    return mark(c, EXTENSION_MESSAGE);
                }
                return c;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method type = m.getMethodType();
                if (type == null) return m;
                String owner = owner(type);
                String name = type.getName();

                if (("com.jcraft.jsch.JSch".equals(owner) || "com.jcraft.jsch.Session".equals(owner)) &&
                    ("setConfig".equals(name) || "getConfig".equals(name)) && literalKey(m, ALGORITHM_KEYS)) {
                    return mark(m, ALGORITHM_MESSAGE);
                }
                if ("com.jcraft.jsch.Session".equals(owner) && "setConfig".equals(name) &&
                    literalPair(m, "StrictHostKeyChecking", Set.of("no", "ask", "accept-new"))) {
                    return mark(m, TRUST_MESSAGE);
                }
                if ("com.jcraft.jsch.JSch".equals(owner) &&
                    Set.of("setKnownHosts", "setHostKeyRepository").contains(name)) return mark(m, TRUST_MESSAGE);
                if ("com.jcraft.jsch.JSch".equals(owner) &&
                    Set.of("addIdentity", "setIdentityRepository").contains(name)) return mark(m, IDENTITY_MESSAGE);
                if ("com.jcraft.jsch.Session".equals(owner) &&
                    Set.of("connect", "setProxy", "setSocketFactory", "setConfigRepository").contains(name)) {
                    return mark(m, SESSION_MESSAGE);
                }
                if ("com.jcraft.jsch.HostKey".equals(owner) &&
                    Set.of("getFingerPrint", "getFingerprint").contains(name)) return mark(m, FINGERPRINT_MESSAGE);
                if (deprecatedCall(type)) return mark(m, DEPRECATED_MESSAGE);
                return m;
            }
        };
    }

    private static boolean deprecatedCall(JavaType.Method type) {
        String owner = owner(type);
        String name = type.getName();
        List<JavaType> parameters = type.getParameterTypes();
        if ("com.jcraft.jsch.JSch".equals(owner) && "removeIdentity".equals(name)) {
            return parameter(parameters, 0, "java.lang.String");
        }
        if ("com.jcraft.jsch.ChannelSession".equals(owner) && "setEnv".equals(name)) {
            return parameter(parameters, 0, "java.util.Hashtable");
        }
        if ("com.jcraft.jsch.ChannelSftp".equals(owner) && "get".equals(name)) {
            return parameters.size() == 2 && parameter(parameters, 1, "int") ||
                   parameters.size() == 3 && parameter(parameters, 2, "int");
        }
        if ("com.jcraft.jsch.ChannelSftp".equals(owner) && "setFilenameEncoding".equals(name)) {
            return parameter(parameters, 0, "java.lang.String");
        }
        if ("com.jcraft.jsch.Identity".equals(owner) && "decrypt".equals(name)) return true;
        return "com.jcraft.jsch.KeyPair".equals(owner) && "setPassphrase".equals(name);
    }

    private static boolean parameter(List<JavaType> parameters, int index, String type) {
        return parameters.size() > index && ("int".equals(type)
                ? parameters.get(index) == JavaType.Primitive.Int
                : TypeUtils.isOfClassType(parameters.get(index), type));
    }

    private static String owner(JavaType.Method type) {
        JavaType.FullyQualified owner = TypeUtils.asFullyQualified(type.getDeclaringType());
        return owner == null ? "" : owner.getFullyQualifiedName();
    }

    private static boolean literalKey(J.MethodInvocation invocation, Set<String> keys) {
        return !invocation.getArguments().isEmpty() && invocation.getArguments().get(0) instanceof J.Literal literal &&
               literal.getValue() instanceof String key && keys.contains(key);
    }

    private static boolean literalPair(J.MethodInvocation invocation, String key, Set<String> values) {
        return invocation.getArguments().size() >= 2 && invocation.getArguments().get(0) instanceof J.Literal left &&
               key.equals(left.getValue()) && invocation.getArguments().get(1) instanceof J.Literal right &&
               right.getValue() instanceof String value && values.contains(value.toLowerCase(java.util.Locale.ROOT));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
