package com.huawei.clouds.openrewrite.diagramjsminimap;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;

final class DiagramJsMinimapSupport {
    static final String PACKAGE = "diagram-js-minimap";
    static final String TARGET = "5.2.0";

    private DiagramJsMinimapSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String defaultBinding(JS.Import declaration) {
        return declaration.getImportClause() != null && declaration.getImportClause().getName() != null
                ? declaration.getImportClause().getName().getSimpleName() : null;
    }

    static String expressionName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess access) return access.getSimpleName();
        if (expression instanceof J.Literal literal) return String.valueOf(literal.getValue());
        return "";
    }

    static String propertyName(Expression expression) {
        return expressionName(expression);
    }

    static boolean isRootOrDistribution(String module) {
        return PACKAGE.equals(module) || (PACKAGE + "/dist/index.js").equals(module) ||
               (PACKAGE + "/dist/index.esm.js").equals(module);
    }
}
