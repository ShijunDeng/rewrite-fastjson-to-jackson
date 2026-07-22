package com.huawei.clouds.openrewrite.vuei18n;

import org.openrewrite.Cursor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

final class VueI18nManifestSupport {
    static final String PACKAGE = "vue-i18n";
    static final String TARGET = "11.3.0";
    static final Set<String> SOURCES = Set.of(
            "7.3.2", "8.11.2", "8.20.0", "8.22.1", "8.22.4",
            "8.24.3", "8.24.4", "8.25.0", "8.26.7", "8.27.1");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");

    private VueI18nManifestSupport() {
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

    static String directSection(Cursor memberCursor) {
        Cursor object = memberCursor.getParent();
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

    static String selectedVersion(String declaration) {
        if (declaration == null) {
            return null;
        }
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return SOURCES.contains(candidate) &&
               (declaration.equals(candidate) || declaration.equals("^" + candidate) || declaration.equals("~" + candidate))
                ? candidate : null;
    }

    static boolean targetDeclaration(String declaration) {
        return TARGET.equals(declaration) || ("^" + TARGET).equals(declaration) || ("~" + TARGET).equals(declaration);
    }
}
