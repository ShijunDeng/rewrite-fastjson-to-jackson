package com.huawei.clouds.openrewrite.junitjupiter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate JUnit Jupiter 6 behavior changes that require test-owner review. */
public final class FindJUnitJupiter6SourceRisks extends Recipe {
    private static final Set<String> JRE_ANNOTATIONS = Set.of(
            "org.junit.jupiter.api.condition.EnabledOnJre",
            "org.junit.jupiter.api.condition.DisabledOnJre",
            "org.junit.jupiter.api.condition.EnabledForJreRange",
            "org.junit.jupiter.api.condition.DisabledForJreRange");
    private static final String JRE_TYPE = "org.junit.jupiter.api.condition.JRE";
    private static final Set<String> OLD_JRE_CONSTANTS = Set.of(
            "JAVA_8", "JAVA_9", "JAVA_10", "JAVA_11", "JAVA_12", "JAVA_13", "JAVA_14", "JAVA_15", "JAVA_16");
    static final String JRE =
            "JUnit 6 runs on Java 17+; this JRE condition references a pre-17 runtime and can become always enabled, " +
            "always disabled, redundant, or invalid. Confirm test intent before removing or narrowing it";
    static final String CSV =
            "JUnit 6 uses FastCSV: malformed quoting, characters after a closing quote, headers, whitespace/null handling, " +
            "exception types/messages, and parameterized display names may change. Re-run this data set and review assertions";
    static final String LINE_SEPARATOR =
            "CsvFileSource.lineSeparator was removed in JUnit 6; it now auto-detects CR, LF, or CRLF. Remove the attribute " +
            "only after confirming the referenced resource uses one of those separators";
    static final String NESTED =
            "JUnit 6 deterministically reorders sibling @Nested classes and inherits @TestMethodOrder into nested classes; " +
            "review stateful/order-sensitive tests and add explicit orderers where order is contractual";
    static final String NULL_CREATOR =
            "JUnit 6 computeIfAbsent contracts require a non-null created value and expose JSpecify nullness; this creator " +
            "can return null, so define an explicit absence strategy before enabling nullness checks";
    static final String STORE_IMPLEMENTATION =
            "This custom ExtensionContext.Store implementation must implement/verify the JUnit 6 computeIfAbsent family, " +
            "non-null contracts, ancestor lookup, and AutoCloseable resource lifecycle";

    @Override
    public String getDisplayName() {
        return "Find JUnit Jupiter 6 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark JRE conditions, CSV annotations, nested-test ordering, nullable Store creators, and custom Store " +
               "implementations whose JUnit 6 behavior cannot be selected safely from syntax alone.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedJUnitJupiterApiDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                String type = TypeUtils.asFullyQualified(visited.getType()) == null ? "" :
                        TypeUtils.asFullyQualified(visited.getType()).getFullyQualifiedName();
                if (JRE_ANNOTATIONS.contains(type) && pre17(visited)) return mark(visited, JRE);
                if ("org.junit.jupiter.params.provider.CsvFileSource".equals(type)) {
                    return mark(visited, hasNamedArgument(visited, "lineSeparator") ? LINE_SEPARATOR : CSV);
                }
                if ("org.junit.jupiter.params.provider.CsvSource".equals(type)) return mark(visited, CSV);
                if ("org.junit.jupiter.api.Nested".equals(type)) return mark(visited, NESTED);
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || !"computeIfAbsent".equals(method.getName()) ||
                    !TypeUtils.isOfClassType(method.getDeclaringType(),
                            "org.junit.jupiter.api.extension.ExtensionContext$Store")) return visited;
                return visited.getArguments().size() > 1 && lambdaCanReturnNull(visited.getArguments().get(1))
                        ? mark(visited, NULL_CREATOR) : visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                return visited.getImplements() != null && visited.getImplements().stream()
                        .anyMatch(type -> TypeUtils.isOfClassType(type.getType(),
                                "org.junit.jupiter.api.extension.ExtensionContext$Store"))
                        ? mark(visited, STORE_IMPLEMENTATION) : visited;
            }
        };
    }

    private static boolean pre17(J.Annotation annotation) {
        boolean[] found = {false};
        new JavaIsoVisitor<boolean[]>() {
            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, boolean[] p) {
                J.FieldAccess f = super.visitFieldAccess(fieldAccess, p);
                JavaType.Variable field = f.getName().getFieldType();
                if (OLD_JRE_CONSTANTS.contains(f.getSimpleName()) && field != null &&
                    TypeUtils.isOfClassType(field.getOwner(), JRE_TYPE)) p[0] = true;
                return f;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, boolean[] p) {
                J.Identifier i = super.visitIdentifier(identifier, p);
                JavaType.Variable field = i.getFieldType();
                if (OLD_JRE_CONSTANTS.contains(i.getSimpleName()) && field != null &&
                    TypeUtils.isOfClassType(field.getOwner(), JRE_TYPE)) p[0] = true;
                return i;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, boolean[] p) {
                J.Literal l = super.visitLiteral(literal, p);
                if (l.getValue() instanceof Number number) {
                    int value = number.intValue();
                    if (value >= 8 && value <= 16) p[0] = true;
                }
                return l;
            }
        }.visit(annotation, found);
        return found[0];
    }

    private static boolean hasNamedArgument(J.Annotation annotation, String name) {
        return annotation.getArguments() != null && annotation.getArguments().stream()
                .filter(J.Assignment.class::isInstance).map(J.Assignment.class::cast)
                .anyMatch(assignment -> assignment.getVariable() instanceof J.Identifier identifier &&
                                        name.equals(identifier.getSimpleName()));
    }

    private static boolean lambdaCanReturnNull(J argument) {
        if (!(argument instanceof J.Lambda lambda)) return false;
        J body = lambda.getBody();
        if (body instanceof Expression expression) return directNull(expression);
        if (!(body instanceof J.Block block)) return false;
        boolean[] found = {false};
        new JavaIsoVisitor<boolean[]>() {
            @Override
            public J.Lambda visitLambda(J.Lambda nested, boolean[] p) {
                return nested;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, boolean[] p) {
                return classDecl;
            }

            @Override
            public J.Return visitReturn(J.Return return_, boolean[] p) {
                J.Return r = super.visitReturn(return_, p);
                if (r.getExpression() != null && directNull(r.getExpression())) p[0] = true;
                return r;
            }
        }.visit(block, found);
        return found[0];
    }

    private static boolean directNull(Expression expression) {
        if (expression instanceof J.Literal literal) return literal.getValue() == null;
        if (expression instanceof J.Parentheses<?> parentheses && parentheses.getTree() instanceof Expression nested) {
            return directNull(nested);
        }
        if (expression instanceof J.TypeCast cast) return directNull(cast.getExpression());
        return expression instanceof J.Ternary ternary &&
               (directNull(ternary.getTruePart()) || directNull(ternary.getFalsePart()));
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
