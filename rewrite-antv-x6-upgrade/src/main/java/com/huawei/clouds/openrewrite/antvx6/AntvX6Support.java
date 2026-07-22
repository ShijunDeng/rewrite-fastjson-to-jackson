package com.huawei.clouds.openrewrite.antvx6;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class AntvX6Support {
    static final String PACKAGE = "@antv/x6";
    static final String TARGET = "3.1.7";
    static final Set<String> SOURCES = Set.of(
            "1.30.0", "1.31.0", "1.34.14", "1.34.5", "2.0.2", "2.11.1", "2.11.3");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    static final Set<String> CONSOLIDATED = Set.of(
            "@antv/x6-plugin-selection", "@antv/x6-plugin-transform", "@antv/x6-plugin-scroller",
            "@antv/x6-plugin-keyboard", "@antv/x6-plugin-history", "@antv/x6-plugin-clipboard",
            "@antv/x6-plugin-snapline", "@antv/x6-plugin-dnd", "@antv/x6-plugin-minimap",
            "@antv/x6-plugin-stencil", "@antv/x6-plugin-export", "@antv/x6-common", "@antv/x6-geometry");
    static final Set<String> SHAPES = Set.of(
            "@antv/x6-react-shape", "@antv/x6-vue-shape", "@antv/x6-angular-shape");
    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", "dist", "build", "out", "generated", "install", ".next", ".nuxt",
            ".cache", ".yarn", ".mvn", ".m2", "coverage", "target", "vendor");

    private AntvX6Support() {
    }

    static boolean isProjectPath(Path path) {
        for (Path component : path) {
            if (EXCLUDED.contains(component.toString().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    static boolean isPackageJson(Path path) {
        return isProjectPath(path) && path.getFileName() != null &&
               "package.json".equals(path.getFileName().toString());
    }

    static boolean isLockfile(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return "package-lock.json".equals(name) || "npm-shrinkwrap.json".equals(name) ||
               "yarn.lock".equals(name) || "pnpm-lock.yaml".equals(name) || "pnpm-lock.yml".equals(name);
    }

    static boolean isConfig(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("tsconfig") || name.startsWith("jsconfig") ||
               name.startsWith("vite.config") || name.startsWith("webpack.config") ||
               name.startsWith("rollup.config") || name.startsWith("jest.config");
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal && literal.getValue() instanceof String value) {
            return value;
        }
        return member.getKey() instanceof Json.Identifier identifier ? identifier.getName() : "";
    }

    static String stringValue(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }

    static String directSection(Cursor cursor) {
        Cursor object = cursor.getParent();
        Cursor section = object == null ? null : object.getParent();
        Cursor root = section == null ? null : section.getParent();
        Cursor document = root == null ? null : root.getParent();
        if (section != null && section.getValue() instanceof Json.Member member &&
            document != null && document.getValue() instanceof Json.Document) {
            String name = key(member);
            return SECTIONS.contains(name) ? name : "";
        }
        return "";
    }

    static String containingMember(Cursor cursor) {
        Cursor current = cursor.getParent();
        while (current != null) {
            if (current.getValue() instanceof Json.Member member) return key(member);
            current = current.getParent();
        }
        return "";
    }

    static boolean selected(String declaration) {
        if (declaration == null) return false;
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return SOURCES.contains(candidate) &&
               (candidate.equals(declaration) || ("^" + candidate).equals(declaration) ||
                ("~" + candidate).equals(declaration));
    }

    static String targetDeclaration(String declaration) {
        return declaration.startsWith("^") ? "^" + TARGET :
               declaration.startsWith("~") ? "~" + TARGET : TARGET;
    }

    static boolean target(String declaration) {
        return TARGET.equals(declaration) || ("^" + TARGET).equals(declaration) ||
               ("~" + TARGET).equals(declaration);
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal &&
               literal.getValue() instanceof String value ? value : "";
    }

    static boolean namedOnly(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        return clause != null && clause.getName() == null &&
               clause.getNamedBindings() instanceof JS.NamedImports named && !named.getElements().isEmpty();
    }

    static boolean internalPath(String value) {
        return value.startsWith("@antv/x6/lib/") || value.startsWith("@antv/x6/es/") ||
               value.startsWith("@antv/x6/src/");
    }

    static boolean internalReference(String value) {
        return internalPath(value) || value.contains("/@antv/x6/lib/") ||
               value.contains("/@antv/x6/es/") || value.contains("/@antv/x6/src/");
    }
}
