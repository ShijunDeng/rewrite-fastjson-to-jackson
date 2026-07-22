package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Set;

/** Removes Ivy-obsolete NgModule metadata at attributed Angular decorator nodes. */
public final class MigrateDeterministicAngularCompilerSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Angular compiler source";
    }

    @Override
    public String getDescription() {
        return "Removes entryComponents only from metadata of NgModule decorators imported from @angular/core.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> ngModuleAliases = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> previous = ngModuleAliases;
                ngModuleAliases = new HashSet<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if ("@angular/core".equals(moduleName(visited))) {
                            String alias = importedAlias(visited, "NgModule");
                            if (alias != null) ngModuleAliases.add(alias);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                ngModuleAliases = previous;
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                J.Annotation annotation = getCursor().firstEnclosing(J.Annotation.class);
                if ("entryComponents".equals(expressionName(visited.getName())) && annotation != null &&
                    ngModuleAliases.contains(annotation.getSimpleName()) &&
                    isDirectAnnotationProperty(getCursor(), annotation)) return null;
                return visited;
            }
        };
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String importedAlias(JS.Import declaration, String importedName) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return null;
        for (JS.ImportSpecifier element : named.getElements()) {
            Expression specifier = element.getSpecifier();
            if (specifier instanceof J.Identifier identifier && importedName.equals(identifier.getSimpleName())) {
                return identifier.getSimpleName();
            }
            if (specifier instanceof JS.Alias alias && importedName.equals(alias.getPropertyName().getSimpleName()) &&
                alias.getAlias() instanceof J.Identifier identifier) return identifier.getSimpleName();
        }
        return null;
    }

    static String namespaceAlias(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause != null && clause.getNamedBindings() instanceof JS.Alias alias &&
            alias.getAlias() instanceof J.Identifier identifier) return identifier.getSimpleName();
        return null;
    }

    static String expressionName(Object expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess access) return access.getSimpleName();
        if (expression instanceof J.Literal literal) return String.valueOf(literal.getValue());
        if (expression instanceof JS.TypeTreeExpression type) return expressionName(type.getExpression());
        return "";
    }

    static boolean isDirectAnnotationProperty(Cursor cursor, J.Annotation annotation) {
        J.NewClass metadata = cursor.firstEnclosing(J.NewClass.class);
        return metadata != null && annotation.getArguments().stream()
                .anyMatch(argument -> argument.getId().equals(metadata.getId()));
    }
}
