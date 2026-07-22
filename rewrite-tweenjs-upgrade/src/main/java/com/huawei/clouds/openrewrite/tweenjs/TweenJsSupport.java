package com.huawei.clouds.openrewrite.tweenjs;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashMap;
import java.util.Map;

final class TweenJsSupport {
    static final String PACKAGE = "@tweenjs/tween.js";
    static final String TARGET = "23.1.1";

    private TweenJsSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess access) return access.getSimpleName();
        if (expression instanceof J.Literal literal) return String.valueOf(literal.getValue());
        return "";
    }

    static String defaultBinding(JS.Import declaration) {
        return declaration.getImportClause() != null && declaration.getImportClause().getName() != null
                ? declaration.getImportClause().getName().getSimpleName() : null;
    }

    static String importedName(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (specifier.getSpecifier() instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
    }

    static String importedBinding(JS.Import declaration, String wanted) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return null;
        for (JS.ImportSpecifier element : named.getElements()) {
            if (!wanted.equals(importedName(element))) continue;
            if (element.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
            if (element.getSpecifier() instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier id) {
                return id.getSimpleName();
            }
        }
        return null;
    }

    static String namespaceBinding(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause != null && clause.getNamedBindings() instanceof JS.Alias alias &&
            alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return null;
    }

    static Map<String, Integer> declarationCounts(JS.CompilationUnit cu, ExecutionContext ctx) {
        Map<String, Integer> declarations = new HashMap<>();
        new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                              ExecutionContext scanCtx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, scanCtx);
                declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                            ExecutionContext scanCtx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                return visited;
            }

            @Override
            public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                            ExecutionContext scanCtx) {
                JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, scanCtx);
                declarations.merge(visited.getName().getSimpleName(), 1, Integer::sum);
                return visited;
            }
        }.visit(cu, ctx);
        return declarations;
    }

    static boolean isDistributionEntry(String module) {
        return (PACKAGE + "/dist/tween.esm.js").equals(module) ||
               (PACKAGE + "/dist/tween.cjs.js").equals(module) ||
               (PACKAGE + "/dist/tween.cjs").equals(module) ||
               (PACKAGE + "/dist/index.cjs.js").equals(module) ||
               (PACKAGE + "/dist/index.cjs").equals(module);
    }
}
