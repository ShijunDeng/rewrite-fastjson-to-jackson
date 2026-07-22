package com.huawei.clouds.openrewrite.ngxcolorpicker;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Shared path and TypeScript AST helpers. */
final class NgxColorPickerSupport {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            "node_modules", ".pnpm", ".yarn", ".npm", "bower_components", "target", "build", "dist",
            "out", ".next", ".angular", ".gradle", ".mvn", ".m2", ".output", "coverage",
            "storybook-static", "reports", "test-results"
    );

    private NgxColorPickerSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
    }

    static String localName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return "";
    }

    static String propertyName(JS.PropertyAssignment property) {
        Expression name = property.getName();
        if (name instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (name instanceof J.Literal literal) return String.valueOf(literal.getValue());
        return "";
    }

    static String expressionName(Object expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess fieldAccess) return fieldAccess.getSimpleName();
        if (expression instanceof JS.TypeTreeExpression type) return expressionName(type.getExpression());
        if (expression instanceof JS.TypeInfo type) return expressionName(type.getTypeIdentifier());
        return "";
    }

    static boolean isProjectSource(Path path) {
        Path parent = path.normalize().getParent();
        if (parent == null) return true;
        for (Path part : parent) {
            String directory = part.toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED_DIRECTORIES.contains(directory) || directory.startsWith("generated") ||
                directory.startsWith("install")) return false;
        }
        return true;
    }
}
