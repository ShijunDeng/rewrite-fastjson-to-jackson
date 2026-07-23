package com.huawei.clouds.openrewrite.mermaid;

import org.openrewrite.Cursor;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class MermaidSupport {
    static final String PACKAGE = "mermaid";
    static final String TARGET_VERSION = "11.15.0";
    static final Set<String> SOURCE_VERSIONS = Set.of("9.1.1", "9.1.3", "9.1.6", "9.4.3");
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    private static final Set<String> EXCLUDED_PARENTS = Set.of(
            "node_modules", "vendor", "dist", "build", "out", "target", ".next", ".nuxt",
            ".svelte-kit", ".angular", ".cache", ".yarn", ".pnpm", ".npm", "coverage",
            "reports", "test-results", "storybook-static");

    private MermaidSupport() {
    }

    static boolean isProjectPath(Path path) {
        int index = 0;
        int count = path.getNameCount();
        for (Path component : path) {
            String name = component.toString().toLowerCase(Locale.ROOT);
            if (index++ < count - 1 && (EXCLUDED_PARENTS.contains(name) ||
                                       name.startsWith("generated") || name.startsWith("install"))) {
                return false;
            }
        }
        return true;
    }

    static boolean isPackageJson(Path path) {
        return isProjectPath(path) && path.getFileName() != null &&
               "package.json".equals(path.getFileName().toString());
    }

    static boolean isSource(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".mjs") ||
               name.endsWith(".cjs") || name.endsWith(".ts") || name.endsWith(".tsx");
    }

    static boolean isMarkupOrDiagram(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".md") ||
               name.endsWith(".mdx") || name.endsWith(".mmd") || name.endsWith(".mermaid") ||
               name.endsWith(".vue") || name.endsWith(".svelte") || name.endsWith(".astro") ||
               name.endsWith(".css") || name.endsWith(".scss") || name.endsWith(".less");
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

    static String directDependencySection(Cursor memberCursor) {
        Cursor object = memberCursor.getParent();
        Cursor section = object == null ? null : object.getParent();
        Cursor root = section == null ? null : section.getParent();
        Cursor document = root == null ? null : root.getParent();
        if (section != null && section.getValue() instanceof Json.Member sectionMember &&
            document != null && document.getValue() instanceof Json.Document) {
            String name = key(sectionMember);
            return DEPENDENCY_SECTIONS.contains(name) ? name : "";
        }
        return "";
    }

    static String replacement(String declaration) {
        if (declaration == null) return null;
        String operator = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(0, 1) : "";
        String version = operator.isEmpty() ? declaration : declaration.substring(1);
        if (!SOURCE_VERSIONS.contains(version) || !declaration.equals(operator + version)) return null;
        return operator + TARGET_VERSION;
    }
}
