package com.huawei.clouds.openrewrite.ngxtranslate;

import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

final class NgxTranslateSupport {
    static final String PACKAGE = "@ngx-translate/core";
    static final String HTTP_LOADER = "@ngx-translate/http-loader";
    static final String TARGET = "17.0.0";
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "node_modules", ".pnpm", ".yarn", ".npm", "bower_components", "target", "build", "dist",
            "out", ".next", ".angular", "coverage", "generated"
    );
    static final Map<String, String> PUBLIC_RENAMES = Map.of(
            "DefaultLangChangeEvent", "FallbackLangChangeEvent",
            "FakeMissingTranslationHandler", "DefaultMissingTranslationHandler",
            "TranslateFakeCompiler", "TranslateNoOpCompiler",
            "TranslateFakeLoader", "TranslateNoOpLoader"
    );

    private NgxTranslateSupport() {
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static String importedName(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (specifier.getSpecifier() instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
    }

    static String localBinding(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (specifier.getSpecifier() instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return importedName(specifier);
    }

    static String typeName(Object type) {
        if (type instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (type instanceof J.FieldAccess fieldAccess) return fieldAccess.getSimpleName();
        if (type instanceof JS.TypeInfo info) return typeName(info.getTypeIdentifier());
        if (type instanceof JS.TypeTreeExpression expression) return typeName(expression.getExpression());
        return "";
    }

    static boolean isProjectPath(Path path) {
        for (Path part : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(part.toString())) return false;
        }
        return true;
    }
}
