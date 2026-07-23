package com.huawei.clouds.openrewrite.angularelements;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class AngularElementsSupport {
    static final String PACKAGE = "@angular/elements";
    static final String TARGET_VERSION = "20.3.25";
    static final Set<String> SOURCE_VERSIONS = Set.of("12.2.13", "12.2.16", "13.1.3", "13.3.12", "14.2.12", "15.1.5", "15.2.1", "15.2.9");
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    private static final Set<String> EXCLUDED_PARENTS = Set.of(
            "node_modules", "vendor", "dist", "build", "out", "target", ".next", ".nuxt",
            ".svelte-kit", ".angular", ".cache", ".yarn", ".pnpm", ".npm", "coverage",
            "reports", "test-results", "storybook-static");

    private AngularElementsSupport() {
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

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal &&
               literal.getValue() instanceof String value ? value : "";
    }

    static String importedAlias(JS.Import declaration, String importedName) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return null;
        for (JS.ImportSpecifier element : named.getElements()) {
            Expression expression = element.getSpecifier();
            if (expression instanceof J.Identifier identifier && importedName.equals(identifier.getSimpleName())) {
                return identifier.getSimpleName();
            }
            if (expression instanceof JS.Alias alias && importedName.equals(alias.getPropertyName().getSimpleName()) &&
                alias.getAlias() instanceof J.Identifier identifier) return identifier.getSimpleName();
        }
        return null;
    }

    static String namespaceAlias(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.Alias alias) ||
            !(alias.getAlias() instanceof J.Identifier identifier)) return null;
        return identifier.getSimpleName();
    }

    static String requireModule(J.MethodInvocation invocation) {
        if (invocation.getSelect() != null || !"require".equals(invocation.getSimpleName()) ||
            invocation.getArguments().size() != 1 ||
            !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String value)) return "";
        return value;
    }

    static String rootIdentifier(Expression expression) {
        Expression current = expression;
        while (current instanceof J.FieldAccess field) current = field.getTarget();
        return current instanceof J.Identifier identifier ? identifier.getSimpleName() : null;
    }

    static String literal(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value ? value : null;
    }

    static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
