package com.huawei.clouds.openrewrite.slf4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks Java source decisions that require provider or logging intent. */
public final class FindSlf4jJavaRisks extends Recipe {
    private static final String SERVICE_PROVIDER = "org.slf4j.spi.SLF4JServiceProvider";
    private static final String LOGGER = "org.slf4j.Logger";
    private static final Set<String> REMOVED_BINDERS = Set.of(
            "org.slf4j.impl.StaticLoggerBinder",
            "org.slf4j.impl.StaticMDCBinder",
            "org.slf4j.impl.StaticMarkerBinder");
    private static final Set<String> FLUENT_ENTRY_POINTS = Set.of(
            "atTrace", "atDebug", "atInfo", "atWarn", "atError", "atLevel");

    @Override
    public String getDisplayName() {
        return "Find SLF4J 2 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed Static*Binder use, custom SLF4JServiceProvider implementations, reflective binder " +
               "references, and discarded fluent logging chains that never call log().";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration,
                                                             ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDeclaration, ctx);
                JavaType.FullyQualified type = c.getType();
                if (type == null) {
                    return c;
                }
                if (REMOVED_BINDERS.contains(type.getFullyQualifiedName())) {
                    return SearchResult.found(c,
                            "SLF4J 2 ignores Static*Binder implementations; implement SLF4JServiceProvider and register it with ServiceLoader");
                }
                if (TypeUtils.isAssignableTo(SERVICE_PROVIDER, type)) {
                    return SearchResult.found(c,
                            "Custom SLF4JServiceProvider: verify requested API version, early MDC initialization, all factories, initialize(), and ServiceLoader registration");
                }
                return c;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null || enclosedByMarkedBinderClass()) {
                    return id;
                }
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(id.getType());
                return type != null && REMOVED_BINDERS.contains(type.getFullyQualifiedName())
                        ? SearchResult.found(id,
                        "SLF4J 2 removed the Static*Binder contract; replace this provider-internal access with the public API or a service provider")
                        : id;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (!(l.getValue() instanceof String value)) {
                    return l;
                }
                return REMOVED_BINDERS.stream().anyMatch(value::contains)
                        ? SearchResult.found(l,
                        "Reflective Static*Binder lookup is incompatible with SLF4J 2 ServiceLoader providers") : l;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (methodType != null && "setProperty".equals(methodType.getName()) &&
                    TypeUtils.isOfClassType(methodType.getDeclaringType(), "java.lang.System") &&
                    m.getArguments().size() == 2 && m.getArguments().get(0) instanceof J.Literal key &&
                    "slf4j.provider".equals(key.getValue())) {
                    return SearchResult.found(m,
                            "Explicit slf4j.provider selection: verify this class implements the SLF4J 2 service provider contract and is visible to LoggerFactory's class loader");
                }
                if (!"log".equals(m.getSimpleName()) && isDiscardedStatement() && containsFluentEntryPoint(m)) {
                    return SearchResult.found(m,
                            "This discarded SLF4J fluent chain never calls log(); no logging event will be emitted");
                }
                return m;
            }

            private boolean enclosedByMarkedBinderClass() {
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                return enclosing != null && enclosing.getType() != null &&
                       REMOVED_BINDERS.contains(enclosing.getType().getFullyQualifiedName());
            }

            private boolean isDiscardedStatement() {
                return getCursor().getParentTreeCursor().getValue() instanceof J.Block;
            }
        };
    }

    private static boolean containsFluentEntryPoint(J.MethodInvocation invocation) {
        JavaType.Method methodType = invocation.getMethodType();
        if (methodType != null && FLUENT_ENTRY_POINTS.contains(methodType.getName()) &&
            TypeUtils.isOfClassType(methodType.getDeclaringType(), LOGGER)) {
            return true;
        }
        Expression select = invocation.getSelect();
        return select instanceof J.MethodInvocation nested && containsFluentEntryPoint(nested);
    }
}
