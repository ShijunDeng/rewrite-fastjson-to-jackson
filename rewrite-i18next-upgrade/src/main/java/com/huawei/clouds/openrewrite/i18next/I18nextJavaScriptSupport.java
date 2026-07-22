package com.huawei.clouds.openrewrite.i18next;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.tree.JS;

import java.util.Set;

final class I18nextJavaScriptSupport {
    private I18nextJavaScriptSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String value
                ? value : "";
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
        return expression instanceof J.FieldAccess field ? field.getSimpleName() : "";
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
}
