package com.huawei.clouds.openrewrite.mermaid;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;

final class MermaidJavaScriptSupport {
    private MermaidJavaScriptSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return stringLiteral(declaration.getModuleSpecifier());
    }

    static boolean isMermaidModule(String module) {
        return module != null && (MermaidSupport.PACKAGE.equals(module) ||
                                  module.startsWith(MermaidSupport.PACKAGE + "/"));
    }

    static String stringLiteral(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value ? value : null;
    }

    static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess field) return field.getSimpleName();
        return "";
    }

    static String propertyName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.Literal literal && literal.getValue() instanceof String value) return value;
        return "";
    }

    static boolean isOwnedSelect(Expression expression, java.util.Set<String> defaultAliases) {
        if (expression instanceof J.Identifier identifier) {
            return defaultAliases.contains(identifier.getSimpleName());
        }
        if (expression instanceof J.FieldAccess field && "mermaidAPI".equals(field.getSimpleName()) &&
            field.getTarget() instanceof J.Identifier identifier) {
            return defaultAliases.contains(identifier.getSimpleName());
        }
        return false;
    }
}
