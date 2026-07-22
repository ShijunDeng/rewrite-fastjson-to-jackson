package com.huawei.clouds.openrewrite.markdownit;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class MarkdownItSupport {
    static final String PACKAGE = "markdown-it";
    static final String TARGET = "14.3.0";
    static final Set<String> SOURCES = Set.of(
            "11.0.0", "12.2.0", "12.3.2", "13.0.1", "13.0.2", "14.0.0", "14.1.0");
    static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    static final Set<String> OVERRIDE_SECTIONS = Set.of("overrides", "resolutions");
    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", "bower_components", "vendor", "target", "build", "dist", "out", "coverage",
            "tmp", "temp", "install",
            "generated", "generated-sources", ".git", ".idea", ".vscode", ".cache", ".next", ".nuxt",
            ".yarn", ".pnpm", ".pnpm-store", ".npm", ".gradle", ".mvn", ".m2", ".turbo", ".nx",
            ".parcel-cache", ".vite");
    private static final Set<String> TARGET_DEEP_MODULES = Set.of(
            "common/html_blocks", "common/html_re", "common/utils", "helpers/index",
            "helpers/parse_link_destination", "helpers/parse_link_label", "helpers/parse_link_title",
            "index", "parser_block", "parser_core", "parser_inline", "renderer", "ruler", "token",
            "presets/commonmark", "presets/default", "presets/zero",
            "rules_core/block", "rules_core/inline", "rules_core/linkify", "rules_core/normalize",
            "rules_core/replacements", "rules_core/smartquotes", "rules_core/state_core", "rules_core/text_join",
            "rules_block/blockquote", "rules_block/code", "rules_block/fence", "rules_block/heading",
            "rules_block/hr", "rules_block/html_block", "rules_block/lheading", "rules_block/list",
            "rules_block/paragraph", "rules_block/reference", "rules_block/state_block", "rules_block/table",
            "rules_inline/autolink", "rules_inline/backticks", "rules_inline/balance_pairs",
            "rules_inline/emphasis", "rules_inline/entity", "rules_inline/escape", "rules_inline/fragments_join",
            "rules_inline/html_inline", "rules_inline/image", "rules_inline/link", "rules_inline/linkify",
            "rules_inline/newline", "rules_inline/state_inline", "rules_inline/strikethrough", "rules_inline/text");

    private MarkdownItSupport() {
    }

    static boolean isProjectPath(Path path) {
        Path normalized = path.normalize();
        // Only directory components are filtered. A business source leaf such as install.js or dist.ts is valid.
        for (int index = 0; index < normalized.getNameCount() - 1; index++) {
            String name = normalized.getName(index).toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(name) || name.startsWith("generated") || name.startsWith("install")) {
                return false;
            }
        }
        return true;
    }

    static boolean isPackageJson(Path path) {
        return isProjectPath(path) && path.getFileName() != null && "package.json".equals(path.getFileName().toString());
    }

    static boolean isExecutableConfig(Path path) {
        if (!isProjectPath(path) || path.getFileName() == null) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("webpack.config") || name.startsWith("vite.config") ||
               name.startsWith("rollup.config") || name.startsWith("esbuild.config") ||
               name.startsWith("jest.config") || name.startsWith("vitest.config");
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
            // pnpm keeps its package-manager override map at the root-level pnpm.overrides path.
            Cursor object = current.getParent();
            Cursor pnpm = object == null ? null : object.getParent();
            if ("overrides".equals(key(member)) && pnpm != null && pnpm.getValue() instanceof Json.Member pnpmMember &&
                "pnpm".equals(key(pnpmMember)) && isRootMember(pnpm)) {
                return true;
            }
        }
        return false;
    }

    static boolean overrideSelector(String key) {
        if (PACKAGE.equals(key) || key.startsWith(PACKAGE + "@")) return true;
        int parentSelector = key.lastIndexOf('>');
        if (parentSelector >= 0) {
            String target = key.substring(parentSelector + 1);
            if (PACKAGE.equals(target) || target.startsWith(PACKAGE + "@")) return true;
        }
        int slash = key.lastIndexOf('/');
        if (slash < 0) return false;
        String target = key.substring(slash + 1);
        if (!(PACKAGE.equals(target) || target.startsWith(PACKAGE + "@"))) return false;
        String ownerPath = key.substring(0, slash);
        // @scope/markdown-it is a different scoped package, while tool/markdown-it and
        // **/markdown-it are Yarn selective-resolution paths to the unscoped package.
        return !ownerPath.startsWith("@") || ownerPath.contains("/");
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

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal &&
               literal.getValue() instanceof String value ? value : "";
    }

    static String normalizedRoot(String module) {
        return "markdown-it/index.js".equals(module) || "markdown-it/index".equals(module) ? PACKAGE : module;
    }

    static String migratedStaticModule(String module) {
        String normalized = normalizedRoot(module);
        if (!normalized.startsWith(PACKAGE + "/lib/")) return normalized;
        String tail = normalized.substring((PACKAGE + "/lib/").length());
        boolean mjs = tail.endsWith(".mjs");
        if (mjs) tail = tail.substring(0, tail.length() - 4);
        else if (tail.endsWith(".js")) tail = tail.substring(0, tail.length() - 3);
        if ("rules_inline/text_collapse".equals(tail)) {
            return PACKAGE + "/lib/rules_inline/fragments_join.mjs";
        }
        return !mjs && TARGET_DEEP_MODULES.contains(tail) ? PACKAGE + "/lib/" + tail + ".mjs" : normalized;
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

    static boolean deepModule(String module) {
        return module.startsWith(PACKAGE + "/lib/");
    }

    static String requireModule(J.MethodInvocation invocation) {
        if (!"require".equals(invocation.getSimpleName()) || invocation.getSelect() != null ||
            invocation.getArguments().size() != 1 || !(invocation.getArguments().get(0) instanceof J.Literal literal) ||
            !(literal.getValue() instanceof String value)) return "";
        return value;
    }

    static J.Literal replaceString(J.Literal literal, String replacement) {
        String source = literal.getValueSource();
        String quote = source != null && source.startsWith("\"") ? "\"" :
                       source != null && source.startsWith("`") ? "`" : "'";
        return literal.withValue(replacement).withValueSource(quote + replacement + quote);
    }

    static String rootIdentifier(Expression expression) {
        Expression current = expression;
        while (current instanceof J.FieldAccess field) current = field.getTarget();
        return current instanceof J.Identifier identifier ? identifier.getSimpleName() : "";
    }
}
