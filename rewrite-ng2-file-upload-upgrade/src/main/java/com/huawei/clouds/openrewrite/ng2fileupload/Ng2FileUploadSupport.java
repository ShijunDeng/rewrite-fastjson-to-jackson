package com.huawei.clouds.openrewrite.ng2fileupload;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class Ng2FileUploadSupport {
    static final String PACKAGE = "ng2-file-upload";
    static final String TARGET = "10.0.0";
    static final Set<String> SOURCES = Set.of("2.0.0-3", "3.0.0", "4.0.0");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    static final Set<String> ROOT_EXPORTS = Set.of(
            "Headers", "ParsedResponseHeaders", "FilterFunction", "FileUploaderOptions", "FileUploader",
            "FileItem", "FileLikeObject", "FileDropDirective", "FileSelectDirective", "FileUploadModule");
    static final Map<String, Set<String>> KNOWN_DEEP_EXPORTS = Map.of(
            PACKAGE + "/file-upload/file-uploader.class", Set.of(
                    "Headers", "ParsedResponseHeaders", "FilterFunction", "FileUploaderOptions", "FileUploader"),
            PACKAGE + "/file-upload/file-item.class", Set.of("FileItem"),
            PACKAGE + "/file-upload/file-like-object.class", Set.of("FileLikeObject"),
            PACKAGE + "/file-upload/file-drop.directive", Set.of("FileDropDirective"),
            PACKAGE + "/file-upload/file-select.directive", Set.of("FileSelectDirective"),
            PACKAGE + "/file-upload/file-upload.module", Set.of("FileUploadModule"));
    private static final Set<String> EXCLUDED_PARENTS = Set.of(
            "node_modules", ".pnpm", ".yarn", ".npm", "bower_components", "target", "build", "dist",
            "out", ".next", ".angular", ".nx", "coverage", "generated", "vendor", ".git", ".idea",
            ".gradle", ".mvn", ".m2", ".cache", ".turbo", ".output", "tmp", "temp",
            "storybook-static", "reports", "test-results");

    private Ng2FileUploadSupport() {
    }

    static boolean projectPath(Path path) {
        Path parent = path.normalize().getParent();
        if (parent == null) return true;
        for (Path part : parent) {
            String value = part.toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED_PARENTS.contains(value) || value.startsWith("generated") || value.startsWith("install")) {
                return false;
            }
        }
        return true;
    }

    static boolean packageJson(Path path) {
        return projectPath(path) && path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    static boolean template(Path path) {
        return projectPath(path) && path.getFileName() != null &&
               path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".html");
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) return String.valueOf(literal.getValue());
        if (member.getKey() instanceof Json.Identifier identifier) return identifier.getName();
        return "";
    }

    static String string(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String value
                ? value : null;
    }

    static String directSection(Cursor cursor) {
        Cursor object = cursor.getParentTreeCursor();
        Cursor owner = object == null ? null : object.getParentTreeCursor();
        if (owner == null || !(owner.getValue() instanceof Json.Member member)) return "";
        Cursor root = owner.getParentTreeCursor();
        Cursor document = root == null ? null : root.getParentTreeCursor();
        return root != null && root.getValue() instanceof Json.JsonObject &&
               document != null && document.getValue() instanceof Json.Document ? key(member) : "";
    }

    static boolean overrideOwner(Cursor cursor) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null; current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Json.Member member &&
                Set.of("overrides", "resolutions").contains(key(member))) return true;
            if (current.getValue() instanceof Json.Document) break;
        }
        return false;
    }

    static boolean selected(String declaration) {
        return singleVersion(declaration, SOURCES);
    }

    static boolean target(String declaration) {
        return singleVersion(declaration, Set.of(TARGET));
    }

    private static boolean singleVersion(String declaration, Set<String> versions) {
        if (declaration == null || !declaration.matches("[~^]?\\d+[.]\\d+[.]\\d+(?:-\\d+)?")) return false;
        String normalized = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return versions.contains(normalized);
    }

    static String targetDeclaration(String declaration) {
        return declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(0, 1) + TARGET : TARGET;
    }

    static Json.Member replaceString(Json.Member member, String value) {
        if (!(member.getValue() instanceof Json.Literal literal)) return member;
        String quote = literal.getSource().startsWith("'") ? "'" : "\"";
        return member.withValue(literal.withValue(value).withSource(quote + value + quote));
    }

    static boolean containsPackageReference(String source) {
        int from = 0;
        while (source != null && (from = source.indexOf(PACKAGE, from)) >= 0) {
            int before = from - 1;
            int after = from + PACKAGE.length();
            boolean left = before < 0 || !packageCharacter(source.charAt(before));
            boolean right = after >= source.length() || !packageCharacter(source.charAt(after));
            if (left && right) return true;
            from = after;
        }
        return false;
    }

    private static boolean packageCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '-' || value == '_' || value == '.';
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : "";
    }

    static boolean packageModule(String module) {
        return PACKAGE.equals(module) || module != null && module.startsWith(PACKAGE + "/");
    }

    static String requireModule(J.MethodInvocation invocation) {
        if (invocation.getSelect() != null || !"require".equals(invocation.getSimpleName()) ||
            invocation.getArguments().size() != 1 || !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String value)) return "";
        return value;
    }

    static String importedName(JS.ImportSpecifier specifier) {
        Expression expression = specifier.getSpecifier();
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        return expression instanceof JS.Alias alias ? alias.getPropertyName().getSimpleName() : "";
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

    static String propertyName(JS.PropertyAssignment property) {
        Expression name = property.getName();
        if (name instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (name instanceof J.Literal literal && literal.getValue() != null) return String.valueOf(literal.getValue());
        return "";
    }

    static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
