package com.huawei.clouds.openrewrite.fsextra;

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.json.tree.Json;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FsExtraSupport {
    static final String PACKAGE = "fs-extra";
    static final String ESM_PACKAGE = "fs-extra/esm";
    static final String TARGET = "11.3.4";
    static final Set<String> SOURCES = Set.of("8.1.0", "9.1.0", "10.0.0", "10.0.1", "10.1.0", "11.1.1");
    static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    static final Set<String> EXTRA_EXPORTS = Set.of(
            "copy", "copySync", "emptyDir", "emptyDirSync", "emptydir", "emptydirSync",
            "createFile", "createFileSync", "ensureFile", "ensureFileSync", "createLink",
            "createLinkSync", "ensureLink", "ensureLinkSync", "createSymlink", "createSymlinkSync",
            "ensureSymlink", "ensureSymlinkSync", "readJson", "readJSON", "readJsonSync",
            "readJSONSync", "writeJson", "writeJSON", "writeJsonSync", "writeJSONSync",
            "outputJson", "outputJSON", "outputJsonSync", "outputJSONSync", "mkdirs", "mkdirsSync",
            "mkdirp", "mkdirpSync", "ensureDir", "ensureDirSync", "move", "moveSync",
            "outputFile", "outputFileSync", "pathExists", "pathExistsSync", "remove", "removeSync");
    private static final Set<String> EXCLUDED = Set.of(
            "node_modules", "vendor", "dist", "build", "out", "generated", "install", ".next",
            ".nuxt", ".cache", ".mvn", ".m2", ".yarn", "coverage", "target");
    private static final Pattern SIMPLE_NODE_RANGE = Pattern.compile(
            "^(?:>=|\\^|~)?\\s*(\\d+)(?:\\.(\\d+|[xX*]))?(?:\\.(\\d+|[xX*]))?\\s*$");

    private FsExtraSupport() {
    }

    static boolean isProjectPath(Path path) {
        for (Path component : path) {
            if (EXCLUDED.contains(component.toString().toLowerCase(Locale.ROOT))) return false;
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

    static String key(Json.Member member) {
        if (member.getKey() instanceof Json.Literal literal && literal.getValue() instanceof String value) return value;
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

    static String containerName(Cursor cursor) {
        Cursor object = cursor.getParent();
        Cursor member = object == null ? null : object.getParent();
        return member != null && member.getValue() instanceof Json.Member section ? key(section) : "";
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

    static boolean clearlySupportsNode14_14(String range) {
        if (range == null || range.isBlank()) return false;
        for (String alternative : range.split("\\|\\|")) {
            String candidate = alternative.trim();
            Matcher matcher = SIMPLE_NODE_RANGE.matcher(candidate);
            if (!matcher.matches()) return false;
            int major = Integer.parseInt(matcher.group(1));
            String minorToken = matcher.group(2);
            int minor = minorToken == null || minorToken.equalsIgnoreCase("x") || "*".equals(minorToken)
                    ? 0 : Integer.parseInt(minorToken);
            if (major < 14 || (major == 14 && minor < 14)) return false;
        }
        return true;
    }

    static boolean insideTopLevelContainer(Cursor cursor, Set<String> containers) {
        for (Cursor current = cursor; current != null; current = current.getParent()) {
            if (!(current.getValue() instanceof Json.Member member) || !containers.contains(key(member))) continue;
            Cursor root = current.getParent();
            Cursor document = root == null ? null : root.getParent();
            return document != null && document.getValue() instanceof Json.Document;
        }
        return false;
    }

    static boolean npmLockPackageKey(String key) {
        return PACKAGE.equals(key) || ("node_modules/" + PACKAGE).equals(key) ||
               key.endsWith("/node_modules/" + PACKAGE);
    }

    static String moduleName(JS.Import declaration) {
        return declaration.getModuleSpecifier() instanceof J.Literal literal &&
               literal.getValue() instanceof String value ? value : "";
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

    static String stringLiteral(Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String value ? value : null;
    }

    static boolean isFsExtraModule(String module) {
        return PACKAGE.equals(module) || ESM_PACKAGE.equals(module) || module.startsWith(PACKAGE + "/lib/") ||
               (PACKAGE + "/lib").equals(module);
    }
}
