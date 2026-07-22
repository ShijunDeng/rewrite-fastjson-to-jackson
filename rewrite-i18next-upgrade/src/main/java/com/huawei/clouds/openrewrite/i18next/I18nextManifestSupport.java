package com.huawei.clouds.openrewrite.i18next;

import org.openrewrite.Cursor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Set;

final class I18nextManifestSupport {
    static final String PACKAGE = "i18next";
    static final String TARGET = "25.10.10";
    static final Set<String> SOURCES = Set.of("21.10.0", "21.6.14", "22.4.10", "22.4.9", "22.5.1");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");

    private I18nextManifestSupport() {
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
