package com.huawei.clouds.openrewrite.ngxtranslate;

import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class HttpLoaderSupport {
    static final String HTTP_LOADER = "@ngx-translate/http-loader";
    static final String CORE = "@ngx-translate/core";
    static final String ANGULAR_HTTP = "@angular/common/http";
    static final String TARGET = "17.0.0";
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "node_modules", "bower_components", "dist", "build", "out", "target", "coverage",
            "generated", ".angular", ".cache", ".next", ".nuxt"
    );

    private HttpLoaderSupport() {
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

    static String localName(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (specifier.getSpecifier() instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        return importedName(specifier);
    }

    static String propertyName(Expression name) {
        if (name instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (name instanceof J.Literal literal) return String.valueOf(literal.getValue());
        return compact(name).replaceAll("^[\"']|[\"']$", "");
    }

    static String compact(Object tree) {
        return tree == null ? "" : tree.toString().replaceAll("\\s+", "");
    }

    static boolean isProjectPath(Path path) {
        for (Path segment : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }
}
