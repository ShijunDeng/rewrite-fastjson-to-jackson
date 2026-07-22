package com.huawei.clouds.openrewrite.gridstack;

import org.openrewrite.Cursor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

final class GridStackManifestSupport {
    static final String PACKAGE = "gridstack";
    static final String TARGET = "12.3.3";
    static final Set<String> SOURCES = Set.of("4.2.6", "5.1.1", "6.0.0", "6.0.3", "7.1.1");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");

    private GridStackManifestSupport() {
    }

    static boolean isPackageJson(Path path) {
        return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
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

    static boolean selected(String declaration) {
        if (declaration == null) {
            return false;
        }
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return SOURCES.contains(candidate) &&
               (declaration.equals(candidate) || declaration.equals("^" + candidate) ||
                declaration.equals("~" + candidate));
    }

    static boolean target(String declaration) {
        return TARGET.equals(declaration) || ("^" + TARGET).equals(declaration) ||
               ("~" + TARGET).equals(declaration);
    }
}
