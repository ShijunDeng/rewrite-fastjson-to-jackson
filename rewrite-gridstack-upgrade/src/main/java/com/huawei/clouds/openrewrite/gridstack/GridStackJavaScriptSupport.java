package com.huawei.clouds.openrewrite.gridstack;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class GridStackJavaScriptSupport {
    private GridStackJavaScriptSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal &&
               literal.getValue() instanceof String value ? value : "";
    }

    static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return expression instanceof JS.Alias alias ? alias.getPropertyName().getSimpleName() : "";
    }

    static String localName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return "";
    }

    static void collectNamed(JS.Import declaration, String imported, Set<String> aliases) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) {
            return;
        }
        for (JS.ImportSpecifier specifier : named.getElements()) {
            if (imported.equals(importedName(specifier))) {
                aliases.add(localName(specifier));
            }
        }
    }

    static String propertyName(Expression expression) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (expression instanceof J.Literal literal && literal.getValue() != null) {
            return literal.getValue().toString();
        }
        return "";
    }

    static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return expression instanceof J.FieldAccess access ? access.getSimpleName() : "";
    }

    static String typeName(TypeTree type) {
        if (type instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (type instanceof J.FieldAccess access) {
            return access.getSimpleName();
        }
        if (type instanceof JS.TypeTreeExpression expression) {
            return expressionName(expression.getExpression());
        }
        if (type instanceof JS.TypeInfo info) {
            return typeName(info.getTypeIdentifier());
        }
        return "";
    }

    static DeclarationInventory declarations(JS.CompilationUnit cu, ExecutionContext ctx) {
        Map<String, Integer> variables = new HashMap<>();
        Set<String> types = new HashSet<>();
        new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                variables.merge(visited.getSimpleName(), 1, Integer::sum);
                return visited;
            }

            @Override
            public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                            ExecutionContext scanCtx) {
                JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, scanCtx);
                types.add(visited.getName().getSimpleName());
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                            ExecutionContext scanCtx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                types.add(visited.getSimpleName());
                return visited;
            }
        }.visit(cu, ctx);
        return new DeclarationInventory(variables, types);
    }

    record DeclarationInventory(Map<String, Integer> variables, Set<String> types) {
    }
}
