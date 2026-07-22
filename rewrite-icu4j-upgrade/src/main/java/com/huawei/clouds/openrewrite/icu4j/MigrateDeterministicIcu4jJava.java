package com.huawei.clouds.openrewrite.icu4j;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Map;

/** Apply only source transformations whose old ICU4J behavior has a direct target representation. */
public final class MigrateDeterministicIcu4jJava extends Recipe {
    private static final String IDNA = "com.ibm.icu.text.IDNA";
    private static final String LIST_FORMATTER = "com.ibm.icu.text.ListFormatter";
    private static final String STYLE = LIST_FORMATTER + "$Style";
    private static final Map<String, String> STYLE_ARGUMENTS = Map.of(
            "STANDARD", "ListFormatter.Type.AND, ListFormatter.Width.WIDE",
            "OR", "ListFormatter.Type.OR, ListFormatter.Width.WIDE",
            "UNIT", "ListFormatter.Type.UNITS, ListFormatter.Width.WIDE",
            "UNIT_SHORT", "ListFormatter.Type.UNITS, ListFormatter.Width.SHORT",
            "UNIT_NARROW", "ListFormatter.Type.UNITS, ListFormatter.Width.NARROW"
    );

    @Override
    public String getDisplayName() {
        return "Migrate deterministic ICU4J Java APIs";
    }

    @Override
    public String getDescription() {
        return "Preserve the pre-76 numeric meaning of IDNA.DEFAULT and replace the removed ListFormatter.Style " +
               "overload with its exact Type/Width representation; ambiguous Unicode behavior remains marked for review.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private final JavaTemplate zero = JavaTemplate.builder("0").build();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedIcu4jDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : (J.CompilationUnit) super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J visited = super.visitFieldAccess(fieldAccess, ctx);
                if (!(visited instanceof J.FieldAccess candidate)) return visited;
                JavaType.Variable field = candidate.getName().getFieldType();
                if (field != null && "DEFAULT".equals(field.getName()) &&
                    TypeUtils.isOfClassType(field.getOwner(), IDNA)) {
                    maybeRemoveImport(IDNA);
                    return zero.apply(updateCursor(candidate), candidate.getCoordinates().replace());
                }
                return candidate;
            }

            @Override
            public J visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J visited = super.visitIdentifier(identifier, ctx);
                if (!(visited instanceof J.Identifier candidate)) return visited;
                if (getCursor().firstEnclosing(J.FieldAccess.class) != null) return candidate;
                JavaType.Variable field = candidate.getFieldType();
                if (field != null && "DEFAULT".equals(field.getName()) &&
                    TypeUtils.isOfClassType(field.getOwner(), IDNA)) {
                    maybeRemoveImport(IDNA);
                    return zero.apply(updateCursor(candidate), candidate.getCoordinates().replace());
                }
                return candidate;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J visitedTree = super.visitMethodInvocation(invocation, ctx);
                if (!(visitedTree instanceof J.MethodInvocation visited)) return visitedTree;
                JavaType.Method method = visited.getMethodType();
                if (!oldStyleOverload(method) || visited.getArguments().size() != 2) return visited;
                String style = styleConstant(visited.getArguments().get(1));
                String mapped = STYLE_ARGUMENTS.get(style);
                if (mapped == null) return visited;
                maybeAddImport(LIST_FORMATTER);
                maybeRemoveImport(STYLE.replace('$', '.'));
                JavaTemplate replacement = JavaTemplate.builder(
                                "#{any(com.ibm.icu.util.ULocale)}, " + mapped)
                        .imports(LIST_FORMATTER)
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                "package com.ibm.icu.util; public final class ULocale {}",
                                "package com.ibm.icu.text; import com.ibm.icu.util.ULocale; " +
                                "public final class ListFormatter { " +
                                "public enum Type { AND, OR, UNITS } " +
                                "public enum Width { WIDE, SHORT, NARROW } " +
                                "public static ListFormatter getInstance(ULocale locale, Type type, Width width) " +
                                "{ return null; } }"
                        ))
                        .build();
                return replacement.apply(updateCursor(visited), visited.getCoordinates().replaceArguments(),
                        visited.getArguments().get(0));
            }
        };
    }

    private static boolean oldStyleOverload(JavaType.Method method) {
        if (method == null || !"getInstance".equals(method.getName()) ||
            !TypeUtils.isOfClassType(method.getDeclaringType(), LIST_FORMATTER)) return false;
        List<JavaType> parameters = method.getParameterTypes();
        return parameters.size() == 2 && TypeUtils.isOfClassType(parameters.get(1), STYLE);
    }

    private static String styleConstant(Expression expression) {
        if (!TypeUtils.isOfClassType(expression.getType(), STYLE)) return null;
        if (expression instanceof J.FieldAccess access) return access.getName().getSimpleName();
        return expression instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }
}
