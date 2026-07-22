package com.huawei.clouds.openrewrite.typescript;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks package ownership and toolchain choices that cannot be inferred from a version string. */
public final class FindTypeScriptManifestRisks extends Recipe {
    private static final Set<String> TOOLING = Set.of(
            "ts-node", "ts-jest", "tsx", "vue-tsc", "typedoc", "typescript-eslint",
            "@typescript-eslint/parser", "@typescript-eslint/eslint-plugin", "@typescript-eslint/utils",
            "@angular/compiler-cli", "fork-ts-checker-webpack-plugin", "awesome-typescript-loader"
    );
    private static final Pattern NODE_MINIMUM = Pattern.compile(
            "^(?:>=|>|\\^|~)?\\s*(\\d+)(?:\\.(\\d+|x|\\*))?.*", Pattern.CASE_INSENSITIVE);

    @Override
    public String getDisplayName() { return "Find TypeScript 6 package and script risks"; }

    @Override
    public String getDescription() {
        return "Mark unowned TypeScript declarations, central owners, compiler-integrated tools, unsupported Node engines, and tsc scripts affected by TypeScript 6 CLI/config behavior.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!TypeScriptSupport.isProjectPath(document.getSourcePath()) ||
                    document.getSourcePath().getFileName() == null ||
                    !"package.json".equals(document.getSourcePath().getFileName().toString())) return visited;
                String name = TypeScriptSupport.key(visited);
                String parent = parentKey();
                boolean dependency = TypeScriptSupport.DEPENDENCY_SECTIONS.contains(parent);
                if (dependency && TypeScriptSupport.PACKAGE.equals(name)) {
                    String declaration = TypeScriptSupport.literalString(visited);
                    if (declaration == null) return TypeScriptSupport.markValue(visited,
                            "Non-string TypeScript declaration is not owned by this recipe; resolve its workspace/catalog source to 6.0.3");
                    if (!TypeScriptSupport.isTarget(declaration)) return TypeScriptSupport.markValue(visited, declarationRisk(declaration));
                }
                if (TypeScriptSupport.PACKAGE.equals(name) && hasCentralOwner()) {
                    return TypeScriptSupport.markValue(visited,
                            "Central TypeScript ownership detected; update the override/resolution/catalog and every workspace consumer atomically");
                }
                if (dependency && TOOLING.contains(name)) {
                    return TypeScriptSupport.markValue(visited,
                            "This tool loads TypeScript compiler/language-service APIs or constrains its peer range; select a release explicitly supporting TypeScript 6 and rerun its tests/editor integration");
                }
                String value = TypeScriptSupport.literalString(visited);
                if ("node".equals(name) && "engines".equals(parent) && value != null && !supportsNode14_17(value)) {
                    return TypeScriptSupport.markValue(visited,
                            "TypeScript 6.0.3 requires Node >=14.17; align local, CI, container, editor, and package-manager runtimes");
                }
                if ("type".equals(name) && "commonjs".equals(value)) {
                    return TypeScriptSupport.markValue(visited,
                            "TypeScript 6 defaults module to esnext; keep CommonJS only with an explicit compiler module/moduleResolution and verified runtime boundary");
                }
                if ("scripts".equals(parent) && value != null && riskyTscScript(value)) {
                    return TypeScriptSupport.markValue(visited,
                            "This tsc command uses file arguments or TypeScript 6-deprecated CLI options; use the intended project config (or --ignoreConfig deliberately) and migrate the option owner");
                }
                return visited;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor owner = object == null ? null : object.getParentTreeCursor();
                return owner != null && owner.getValue() instanceof Json.Member member
                        ? TypeScriptSupport.key(member) : "";
            }

            private boolean hasCentralOwner() {
                for (Cursor current = getCursor().getParent(); current != null; current = current.getParent()) {
                    if (current.getValue() instanceof Json.Member ancestor &&
                        Set.of("overrides", "resolutions", "catalog", "catalogs", "pnpm").contains(TypeScriptSupport.key(ancestor))) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private static String declarationRisk(String declaration) {
        String lower = declaration.toLowerCase(Locale.ROOT);
        if (lower.startsWith("workspace:") || lower.startsWith("catalog:") || lower.startsWith("npm:") ||
            lower.startsWith("file:") || lower.startsWith("link:") || lower.startsWith("git") ||
            lower.startsWith("http:") || lower.startsWith("https:") || lower.equals("latest") || lower.equals("next") ||
            lower.contains("${") || lower.contains("{{")) {
            return "Protocol, alias, tag, or dynamic TypeScript declaration is not rewritten; migrate its actual catalog/workspace/source owner to 6.0.3";
        }
        if (lower.matches("[~^]?\\d+\\.\\d+\\.\\d+-.*")) {
            return "Prerelease TypeScript declaration is not rewritten; select a stable 6.0.3 constraint only after validating prerelease-specific behavior";
        }
        if (lower.matches("[~^]?\\d+\\.\\d+\\.\\d+")) {
            return "Unlisted TypeScript scalar is intentionally not rewritten; confirm its real migration path instead of inferring hidden spreadsheet versions";
        }
        return "Complex TypeScript comparator, OR, hyphen, wildcard, or non-semver declaration is not rewritten; choose an owned npm constraint manually";
    }

    private static boolean supportsNode14_17(String declaration) {
        if (declaration.contains("||") || declaration.contains(" - ")) return false;
        Matcher matcher = NODE_MINIMUM.matcher(declaration.trim());
        if (!matcher.matches()) return false;
        int major = Integer.parseInt(matcher.group(1));
        String minorGroup = matcher.group(2);
        int minor = minorGroup == null || "x".equalsIgnoreCase(minorGroup) || "*".equals(minorGroup)
                ? 0 : Integer.parseInt(minorGroup);
        return major > 14 || major == 14 && minor >= 17;
    }

    private static boolean riskyTscScript(String script) {
        String lower = script.toLowerCase(Locale.ROOT);
        if (!lower.matches(".*(?:^|[;&| ]|npx |npm exec )tsc(?:\\.cmd)?(?: |$).*$")) return false;
        return lower.matches(".*(?:^| )[^- ][^ ]*\\.tsx?(?: |$).*") ||
               lower.matches(".*--(?:target(?:=| )es5|downleveliteration|moduleresolution(?:=| )(?:classic|node|node10)|module(?:=| )(?:amd|umd|system|systemjs)|baseurl|outfile)(?: |$|=).*" );
    }
}
