package com.huawei.clouds.openrewrite.netflixeureka;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.Set;

/** Marks removed Eureka constructors, transport APIs, internal types, and namespace choices. */
public final class FindNetflixEurekaJavaRisks extends Recipe {
    private static final String DISCOVERY_CLIENT = "com.netflix.discovery.DiscoveryClient";
    private static final String DISCOVERY_MANAGER = "com.netflix.discovery.DiscoveryManager";
    private static final String OPTIONAL_ARGS = "com.netflix.discovery.AbstractDiscoveryClientOptionalArgs";
    private static final String INSTANCE_INFO = "com.netflix.appinfo.InstanceInfo";
    private static final String TRANSPORT_FACTORIES =
            "com.netflix.discovery.shared.transport.jersey.TransportClientFactories";
    private static final Set<String> REMOVED_TYPES = Set.of(
            "com.netflix.discovery.DiscoveryClient$DiscoveryClientOptionalArgs",
            "com.netflix.discovery.EurekaIdentityHeaderFilter",
            "com.netflix.discovery.InternalEurekaStatusModule",
            "com.netflix.discovery.shared.transport.jersey.Jersey1DiscoveryClientOptionalArgs",
            "com.netflix.discovery.shared.transport.jersey.Jersey1TransportClientFactories",
            "com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient",
            "com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl",
            "com.netflix.discovery.shared.transport.jersey.JerseyApplicationClient",
            "com.netflix.discovery.shared.transport.jersey.JerseyEurekaHttpClientFactory",
            "com.netflix.discovery.shared.transport.jersey.ApacheHttpClientConnectionCleaner",
            "com.netflix.discovery.util.ServoUtil",
            "com.netflix.discovery.util.ThresholdLevelsMetric$NoOpThresholdLevelMetric"
    );

    @Override
    public String getDisplayName() {
        return "Find Netflix Eureka 2 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark old DiscoveryClient/DiscoveryManager construction, removed optional-args setters, Jersey 1/Guice/internal APIs, Java EE annotation ambiguity, and custom DiscoveryClient subclasses.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("com.netflix.discovery..*", false),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        J.NewClass n = super.visitNewClass(newClass, ctx);
                        if (isType(n.getType(), DISCOVERY_CLIENT) && isLegacyDiscoveryClientConstructor(n)) {
                            return SearchResult.found(n,
                                    "Eureka 2.0.4 requires an explicit TransportClientFactories before optional args; choose Jersey3TransportClientFactories, Spring Cloud's transport, or a custom implementation and preserve TLS/filter settings");
                        }
                        if (isRemovedType(n.getType())) {
                            return SearchResult.found(n, removedTypeMessage(n.getType()));
                        }
                        return n;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (methodOn(m, DISCOVERY_MANAGER) && "initComponent".equals(m.getSimpleName()) &&
                            m.getArguments().size() < 4) {
                            return SearchResult.found(m,
                                    "DiscoveryManager.initComponent now requires TransportClientFactories before optional args; select a transport and preserve startup order explicitly");
                        }
                        if (methodOn(m, OPTIONAL_ARGS) && "setEurekaJerseyClient".equals(m.getSimpleName())) {
                            return SearchResult.found(m,
                                    "setEurekaJerseyClient and the Jersey 1 client were removed; move TLS, proxy, auth, filters, and connection management to the selected transport factory");
                        }
                        if (methodOn(m, OPTIONAL_ARGS) && "setTransportClientFactories".equals(m.getSimpleName())) {
                            return SearchResult.found(m,
                                    "TransportClientFactories is now a DiscoveryClient/DiscoveryManager constructor argument, not mutable optional-args state; preserve initialization ordering");
                        }
                        if (methodOn(m, DISCOVERY_CLIENT) && "localRegistrySize".equals(m.getSimpleName())) {
                            return SearchResult.found(m,
                                    "DiscoveryClient.localRegistrySize was removed when registry metrics moved from Servo to Spectator; derive size from the public registry view or a supported metric");
                        }
                        if ((methodOn(m, "com.netflix.discovery.shared.NamedConnectionPool") &&
                             "getConnectionCount".equals(m.getSimpleName())) ||
                            (methodOn(m, "com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient") &&
                             "getQuarantineSetSize".equals(m.getSimpleName())) ||
                            (methodOn(m, "com.netflix.discovery.util.ThresholdLevelsMetric") &&
                             "shutdown".equals(m.getSimpleName()))) {
                            return SearchResult.found(m,
                                    "This Eureka 1.x internal metric/connection inspection API was removed; replace it with supported Spectator or transport lifecycle observability");
                        }
                        return m;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                        if (c.getExtends() != null && TypeUtils.isAssignableTo(DISCOVERY_CLIENT, c.getExtends().getType())) {
                            return SearchResult.found(c,
                                    "Custom DiscoveryClient subclasses are coupled to changed constructors, transport fields, scheduling, metrics, and shutdown; prefer composition or port every override against 2.0.4");
                        }
                        return c;
                    }

                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation a = super.visitAnnotation(annotation, ctx);
                        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(a.getType());
                        if (fq != null && fq.getFullyQualifiedName().startsWith("javax.annotation.")) {
                            return SearchResult.found(a,
                                    "Eureka 2 moved lifecycle/injection annotations to Jakarta, but nullness annotations may still come from JSR-305; classify this exact annotation before changing its package");
                        }
                        return a;
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                        J.Identifier i = super.visitIdentifier(identifier, ctx);
                        if (!isTypePosition(i, getCursor().getParentTreeCursor().getValue())) {
                            return i;
                        }
                        String message = typeRiskMessage(i.getType());
                        return message == null ? i : SearchResult.found(i, message);
                    }

                    @Override
                    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                        J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);
                        if (!isTypePosition(f, getCursor().getParentTreeCursor().getValue())) {
                            return f;
                        }
                        String message = typeRiskMessage(f.getType());
                        return message == null ? f : SearchResult.found(f, message);
                    }
                });
    }

    private static boolean isLegacyDiscoveryClientConstructor(J.NewClass newClass) {
        JavaType.Method constructor = newClass.getConstructorType();
        if (constructor == null) {
            return false;
        }
        List<JavaType> parameters = constructor.getParameterTypes();
        if (parameters.size() < 3 || !isType(parameters.get(0), "com.netflix.appinfo.ApplicationInfoManager")) {
            return true;
        }
        return !isType(parameters.get(2), TRANSPORT_FACTORIES);
    }

    private static boolean methodOn(J.MethodInvocation method, String owner) {
        return method.getMethodType() != null &&
               TypeUtils.isAssignableTo(owner, method.getMethodType().getDeclaringType());
    }

    private static boolean isType(JavaType type, String fullyQualifiedName) {
        return TypeUtils.isAssignableTo(fullyQualifiedName, type);
    }

    private static boolean isRemovedType(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq == null) {
            return false;
        }
        String name = fq.getFullyQualifiedName();
        return REMOVED_TYPES.contains(name) || name.startsWith("com.netflix.discovery.guice.") ||
               name.startsWith("com.netflix.discovery.shared.transport.jersey.Jersey1") ||
               name.startsWith("com.sun.jersey.");
    }

    private static String typeRiskMessage(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        if (fq == null) {
            return null;
        }
        String name = fq.getFullyQualifiedName();
        if (isRemovedType(type)) {
            return removedTypeMessage(type);
        }
        if (name.startsWith("javax.annotation.")) {
            return "Eureka 2 moved lifecycle/injection annotations to Jakarta, but nullness annotations may still come from JSR-305; classify this exact annotation before changing its package";
        }
        return null;
    }

    private static String removedTypeMessage(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        String name = fq == null ? "" : fq.getFullyQualifiedName();
        if (name.startsWith("com.netflix.discovery.guice.") ||
            "com.netflix.discovery.InternalEurekaStatusModule".equals(name)) {
            return "Eureka 2.0.4 removed its built-in Guice/Governator bootstrap; construct configuration, ApplicationInfoManager, transport, and lifecycle explicitly";
        }
        if (name.startsWith("com.sun.jersey.") || name.contains("Jersey1") ||
            name.contains("EurekaJerseyClient") || name.contains("JerseyEurekaHttpClient")) {
            return "This Jersey 1 Eureka transport type was removed; port filters/providers/TLS/proxy/auth/pooling to Jersey 3 or the chosen custom transport";
        }
        if (name.contains("ServoUtil") || name.contains("NoOpThresholdLevelMetric")) {
            return "Eureka 2 replaced these internal Servo helpers with Spectator-based metrics; migrate observability without depending on client internals";
        }
        if (name.endsWith("DiscoveryClient$DiscoveryClientOptionalArgs")) {
            return "DiscoveryClientOptionalArgs was removed with the built-in Jersey 1 transport; use the optional-args type supplied by the selected transport module";
        }
        return "This Eureka 1.x type is absent from 2.0.4; select and test an explicit replacement rather than applying a name-only substitution";
    }

    private static boolean isTypePosition(J.Identifier identifier, Object parent) {
        return parent instanceof J.VariableDeclarations declarations && declarations.getTypeExpression() == identifier ||
               parent instanceof J.MethodDeclaration method && method.getReturnTypeExpression() == identifier ||
               parent instanceof J.ParameterizedType || parent instanceof J.ClassDeclaration;
    }

    private static boolean isTypePosition(J.FieldAccess fieldAccess, Object parent) {
        return parent instanceof J.VariableDeclarations declarations && declarations.getTypeExpression() == fieldAccess ||
               parent instanceof J.MethodDeclaration method && method.getReturnTypeExpression() == fieldAccess ||
               parent instanceof J.ParameterizedType || parent instanceof J.ClassDeclaration;
    }
}
