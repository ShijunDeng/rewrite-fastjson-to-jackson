package com.huawei.clouds.openrewrite.typescript;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TypeScriptSupport {
    static final String PACKAGE = "typescript";
    static final String TARGET = "6.0.3";
    static final Set<String> SOURCES = Set.of(
            "3.8.3", "3.9.5", "4.1.2", "4.2.3", "4.4.4",
            "4.5.5", "4.6.2", "4.6.4", "4.7.4"
    );
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "node_modules", ".pnpm", ".yarn", ".npm", "bower_components", "target", "build", "dist",
            "out", ".next", ".angular", ".mvn", ".m2", "coverage", "generated", "install", "vendor"
    );
    private static final Pattern SCALAR = Pattern.compile("([~^]?)(\\d+\\.\\d+\\.\\d+)");

    private TypeScriptSupport() {
    }

    static boolean isProjectPath(Path path) {
        for (Path part : path.normalize()) {
            if (GENERATED_DIRECTORIES.contains(part.toString())) return false;
        }
        return true;
    }

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal) {
            return literal.getValue() == null ? "" : literal.getValue().toString();
        }
        return member.getKey() instanceof Json.Identifier identifier ? identifier.getName() : "";
    }

    static String directSection(Cursor cursor) {
        Cursor object = cursor.getParentTreeCursor();
        Cursor section = object == null ? null : object.getParentTreeCursor();
        Cursor root = section == null ? null : section.getParentTreeCursor();
        Cursor document = root == null ? null : root.getParentTreeCursor();
        return section != null && section.getValue() instanceof Json.Member owner &&
               document != null && document.getValue() instanceof Json.Document ? key(owner) : "";
    }

    static boolean isSelected(String declaration) {
        Matcher matcher = SCALAR.matcher(declaration);
        return matcher.matches() && SOURCES.contains(matcher.group(2));
    }

    static boolean isTarget(String declaration) {
        Matcher matcher = SCALAR.matcher(declaration);
        return matcher.matches() && TARGET.equals(matcher.group(2));
    }

    static String upgradeDeclaration(String declaration) {
        Matcher matcher = SCALAR.matcher(declaration);
        return matcher.matches() ? matcher.group(1) + TARGET : declaration;
    }

    static String literalString(Json.Member member) {
        return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String
                ? (String) literal.getValue() : null;
    }

    static Json.Member replaceString(Json.Member member, String replacement) {
        if (!(member.getValue() instanceof Json.Literal literal)) return member;
        String quote = literal.getSource().startsWith("'") ? "'" : "\"";
        return member.withValue(literal.withValue(replacement).withSource(quote + replacement + quote));
    }

    static Json.Member markValue(Json.Member member, String message) {
        if (member.getValue().getMarkers().findFirst(SearchResult.class).isPresent()) return member;
        return member.withValue(SearchResult.found(member.getValue(), message));
    }

    static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findFirst(SearchResult.class).isPresent() ? tree : SearchResult.found(tree, message);
    }
}
