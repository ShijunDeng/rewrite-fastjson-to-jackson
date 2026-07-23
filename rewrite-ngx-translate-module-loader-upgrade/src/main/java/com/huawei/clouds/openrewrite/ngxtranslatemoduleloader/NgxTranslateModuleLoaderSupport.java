package com.huawei.clouds.openrewrite.ngxtranslatemoduleloader;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class NgxTranslateModuleLoaderSupport {
    static final String PACKAGE = "@larscom/ngx-translate-module-loader";
    static final String TARGET = "5.1.0";
    static final Set<String> SOURCES = Set.of("3.1.1", "3.1.2");
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    private static final Set<String> OVERRIDE_SECTIONS = Set.of("overrides", "resolutions");
    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", "bower_components", "vendor", "target", "build", "dist", "out", "coverage",
            "report", "reports", "test-results", "storybook-static", ".output",
            "tmp", "temp", "install", "generated", ".git", ".idea", ".vscode", ".cache", ".next",
            ".nuxt", ".yarn", ".pnpm", ".pnpm-store", ".npm", ".gradle", ".mvn", ".m2",
            ".turbo", ".nx", ".parcel-cache", ".vite", ".angular");

    private NgxTranslateModuleLoaderSupport() {
    }

    static boolean isProjectPath(Path path) {
        Path normalized = path.normalize();
        for (int index = 0; index < normalized.getNameCount() - 1; index++) {
            String name = normalized.getName(index).toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(name) || name.startsWith("generated") || name.startsWith("install")) {
                return false;
            }
        }
        return true;
    }

    static boolean isPackageJson(Path path) {
        return isProjectPath(path) && path.getFileName() != null &&
               "package.json".equals(path.getFileName().toString());
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal && literal.getValue() instanceof String value) return value;
        return member.getKey() instanceof Json.Identifier identifier ? identifier.getName() : "";
    }

    static String stringValue(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }

    static String directDependencySection(Cursor cursor) {
        Cursor object = cursor.getParent();
        Cursor section = object == null ? null : object.getParent();
        Cursor root = section == null ? null : section.getParent();
        Cursor document = root == null ? null : root.getParent();
        if (section != null && section.getValue() instanceof Json.Member member &&
            document != null && document.getValue() instanceof Json.Document) {
            String name = key(member);
            return DEPENDENCY_SECTIONS.contains(name) ? name : "";
        }
        return "";
    }

    static boolean underOverrideSection(Cursor cursor) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (!(current.getValue() instanceof Json.Member member) || !OVERRIDE_SECTIONS.contains(key(member))) {
                continue;
            }
            if (isRootMember(current)) return true;
            Cursor object = current.getParent();
            Cursor pnpm = object == null ? null : object.getParent();
            if ("overrides".equals(key(member)) && pnpm != null && pnpm.getValue() instanceof Json.Member pnpmMember &&
                "pnpm".equals(key(pnpmMember)) && isRootMember(pnpm)) return true;
        }
        return false;
    }

    static boolean overrideSelector(String key) {
        if (PACKAGE.equals(key) || key.startsWith(PACKAGE + "@")) return true;
        int parent = key.lastIndexOf('>');
        if (parent >= 0) {
            String selected = key.substring(parent + 1);
            if (PACKAGE.equals(selected) || selected.startsWith(PACKAGE + "@")) return true;
        }
        return false;
    }

    private static boolean isRootMember(Cursor member) {
        Cursor object = member.getParent();
        Cursor document = object == null ? null : object.getParent();
        return document != null && document.getValue() instanceof Json.Document;
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

    static Json.Member replaceStringValue(Json.Member member, String replacement) {
        if (!(member.getValue() instanceof Json.Literal literal)) return member;
        String quote = literal.getSource().startsWith("'") ? "'" : "\"";
        return member.withValue(literal.withValue(replacement).withSource(quote + replacement + quote));
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal &&
               literal.getValue() instanceof String value ? value : "";
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

    static String expressionName(Object expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.FieldAccess field) return field.getSimpleName();
        if (expression instanceof JS.TypeTreeExpression type) return expressionName(type.getExpression());
        if (expression instanceof JS.TypeInfo type) return expressionName(type.getTypeIdentifier());
        return "";
    }

    static J.Literal replaceString(J.Literal literal, String replacement) {
        String source = literal.getValueSource();
        String quote = source != null && source.startsWith("\"") ? "\"" :
                       source != null && source.startsWith("`") ? "`" : "'";
        return literal.withValue(replacement).withValueSource(quote + replacement + quote);
    }
}
