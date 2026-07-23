package com.huawei.clouds.openrewrite.sqlformatter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate Java API and formatter-output decisions blocked by the workbook's cross-ecosystem target. */
public final class FindSqlFormatter15SourceRisks extends Recipe {
    static final String OUTPUT =
            "Java SqlFormatter formatting call detected; Maven has no com.github.vertical-blank:sql-formatter:15.6.5. " +
            "Do not assume npm sql-formatter output equivalence—golden-test whitespace, comments, quoting, case, " +
            "placeholders, multi-statement delimiters, line endings, and every production dialect";
    static final String DIALECT =
            "Java SqlFormatter dialect selection detected; map this enum/string/default explicitly if the inventory is " +
            "corrected to the unrelated npm 15.6.5 package, and test unsupported SQL/stored procedures and fallbacks";
    static final String CONFIG =
            "Java FormatConfig behavior detected; preserve indent, max column length, uppercase safety, lines between " +
            "queries, placeholder list/map order, and skipWhitespaceNearBlockParentheses semantics during any migration";
    static final String EXTENSION =
            "Java formatter extension detected; its DialectConfig/operator customization has no automatic equivalent in " +
            "the unrelated npm 15.6.5 package and requires an explicit, tested design";
    static final String JAVA_API =
            "This Java sql-formatter API type belongs to the Maven 1.x/2.x library; version 15.6.5 is an unrelated npm " +
            "release, so correct the inventory or replace this boundary deliberately before removing the Java artifact";

    private static final Set<String> CONFIG_METHODS = Set.of(
            "builder", "indent", "maxColumnLength", "params", "uppercase", "linesBetweenQueries",
            "skipWhitespaceNearBlockParentheses", "build");

    @Override
    public String getDisplayName() {
        return "Find Vertical Blank SQL Formatter 15.6.5 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark type-attributed formatting, dialect, FormatConfig, extension, and retained Java API nodes whose " +
               "semantics cannot cross automatically to the unrelated npm 15.6.5 release.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedSqlFormatterDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                String name = method.getName();
                if ((isFormatter(owner) || isSqlFormatter(owner)) && "format".equals(name)) {
                    return mark(visited, OUTPUT);
                }
                if (isSqlFormatter(owner) && Set.of("of", "standard").contains(name)) return mark(visited, DIALECT);
                if (isFormatter(owner) && "extend".equals(name)) return mark(visited, EXTENSION);
                if (isFormatConfig(owner) && CONFIG_METHODS.contains(name)) return mark(visited, CONFIG);
                if (isLibrary(owner)) return mark(visited, JAVA_API);
                return visited;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                     ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
                JavaType type = visited.getTypeExpression() == null ? null : visited.getTypeExpression().getType();
                return libraryType(type) ? mark(visited, JAVA_API) : visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration declaration, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(declaration, ctx);
                JavaType.Method method = visited.getMethodType();
                return method != null && (libraryType(method.getReturnType()) || method.getParameterTypes().stream()
                        .anyMatch(FindSqlFormatter15SourceRisks::libraryType)) ? mark(visited, JAVA_API) : visited;
            }
        };
    }

    private static boolean isSqlFormatter(String owner) {
        return "com.github.vertical_blank.sqlformatter.SqlFormatter".equals(owner);
    }

    private static boolean isFormatter(String owner) {
        return "com.github.vertical_blank.sqlformatter.SqlFormatter$Formatter".equals(owner);
    }

    private static boolean isFormatConfig(String owner) {
        return owner.equals("com.github.vertical_blank.sqlformatter.core.FormatConfig") ||
               owner.equals("com.github.vertical_blank.sqlformatter.core.FormatConfig$FormatConfigBuilder");
    }

    private static boolean isLibrary(String owner) {
        return owner.startsWith("com.github.vertical_blank.sqlformatter.") || isSqlFormatter(owner);
    }

    private static boolean libraryType(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq != null && isLibrary(fq.getFullyQualifiedName());
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
