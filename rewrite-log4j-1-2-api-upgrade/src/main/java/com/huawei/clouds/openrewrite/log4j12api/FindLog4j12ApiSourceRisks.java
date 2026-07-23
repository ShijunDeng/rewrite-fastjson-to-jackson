package com.huawei.clouds.openrewrite.log4j12api;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate bridge-specific source usages which cannot be migrated without application semantics. */
public final class FindLog4j12ApiSourceRisks extends Recipe {
    static final String CONFIGURATION =
            "Log4j 1 programmatic configuration is limited in the 2.25.5 bridge; since 2.24.0 " +
            "PropertyConfigurator/DOMConfigurator require log4j1.compatibility=true. Prefer converting the " +
            "configuration or migrate this call to the selected backend, then test startup and reload";
    static final String BACKEND =
            "This Logger/Category call is backend-specific rather than part of the supported logging API; migrate " +
            "it to an owned Log4j 2/Core or framework integration and verify levels, appenders and additivity";
    static final String CUSTOM_COMPONENT =
            "The bridge supports only a limited set of Log4j 1 appenders, layouts, filters and triggering policies; " +
            "port this custom component to a Log4j 2 plugin or prove bridge support and test packaging/discovery";
    static final String INTERNAL =
            "This Log4j 1 repository, SPI, JMX, network, renderer or implementation-specific API is not a stable " +
            "bridge contract; select a Log4j 2 equivalent and test security, lifecycle, serialization and failures";
    static final String CONTEXT =
            "MDC/NDC and serialized LoggingEvent/ThrowableInformation cross thread-context and wire-format " +
            "boundaries; verify inheritance, cleanup, map values, location information and stored event compatibility";

    private static final Set<String> CONFIGURATION_TYPES = Set.of(
            "org.apache.log4j.PropertyConfigurator", "org.apache.log4j.BasicConfigurator",
            "org.apache.log4j.xml.DOMConfigurator");
    private static final Set<String> BACKEND_METHODS = Set.of(
            "setLevel", "setPriority", "setAdditivity", "addAppender", "removeAppender",
            "removeAllAppenders", "callAppenders", "setResourceBundle");

    @Override
    public String getDisplayName() {
        return "Find Log4j 1.2 API bridge source risks";
    }

    @Override
    public String getDescription() {
        return "Mark programmatic configuration, backend-specific Category/Logger calls, custom components, " +
               "repository/JMX/extension APIs and thread-context or serialization boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit source) ||
                    UpgradeSelectedLog4j12ApiDependency.generated(source.getSourcePath())) return tree;
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(
                            J.MethodInvocation method, ExecutionContext ec) {
                        J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                        JavaType.Method type = visited.getMethodType();
                        String owner = type == null ? "" : fqn(type.getDeclaringType());
                        if (CONFIGURATION_TYPES.contains(owner)) return mark(visited, CONFIGURATION);
                        if (("org.apache.log4j.Logger".equals(owner) ||
                             "org.apache.log4j.Category".equals(owner)) &&
                            BACKEND_METHODS.contains(visited.getSimpleName())) return mark(visited, BACKEND);
                        if (riskyNamespace(owner)) return mark(visited, INTERNAL);
                        if (contextType(owner)) return mark(visited, CONTEXT);
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(
                            J.ClassDeclaration classDecl, ExecutionContext ec) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ec);
                        JavaType.FullyQualified type = visited.getType();
                        if (type != null && (TypeUtils.isAssignableTo(
                                "org.apache.log4j.AppenderSkeleton", type) ||
                            TypeUtils.isAssignableTo("org.apache.log4j.Layout", type) ||
                            TypeUtils.isAssignableTo("org.apache.log4j.spi.Filter", type) ||
                            TypeUtils.isAssignableTo("org.apache.log4j.spi.TriggeringEventEvaluator", type))) {
                            return mark(visited, CUSTOM_COMPONENT);
                        }
                        return visited;
                    }

                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ec) {
                        J.Identifier visited = super.visitIdentifier(identifier, ec);
                        String type = fqn(visited.getType());
                        if (riskyNamespace(type)) return mark(visited, INTERNAL);
                        if (contextType(type)) return mark(visited, CONTEXT);
                        return visited;
                    }
                }.visitNonNull(source, ctx);
            }
        };
    }

    private static boolean riskyNamespace(String type) {
        return type.startsWith("org.apache.log4j.jmx.") ||
               type.startsWith("org.apache.log4j.net.") ||
               type.startsWith("org.apache.log4j.jdbc.") ||
               type.startsWith("org.apache.log4j.or.") ||
               type.startsWith("org.apache.log4j.spi.") ||
               type.startsWith("org.apache.log4j.varia.") ||
               type.startsWith("org.apache.log4j.chainsaw.") ||
               type.startsWith("org.apache.log4j.lf5.");
    }

    private static boolean contextType(String type) {
        return Set.of("org.apache.log4j.MDC", "org.apache.log4j.NDC",
                      "org.apache.log4j.spi.LoggingEvent",
                      "org.apache.log4j.spi.ThrowableInformation").contains(type);
    }

    private static String fqn(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
