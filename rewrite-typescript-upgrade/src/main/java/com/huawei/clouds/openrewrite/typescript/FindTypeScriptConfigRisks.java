package com.huawei.clouds.openrewrite.typescript;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

import java.util.Locale;
import java.util.Set;

/** Marks TypeScript 6 compiler option changes that require runtime or project-layout intent. */
public final class FindTypeScriptConfigRisks extends Recipe {
    private static final Set<String> DEFAULT_SENSITIVE = Set.of(
            "strict", "module", "target", "noUncheckedSideEffectImports", "libReplacement", "types", "rootDir"
    );

    @Override
    public String getDisplayName() { return "Find TypeScript 6 configuration risks"; }

    @Override
    public String getDescription() {
        return "Mark changed defaults, project-layout inference, global type discovery, deprecated compiler options, and unsupported legacy module/emit choices at exact tsconfig/jsconfig nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean config;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean previous = config;
                String file = document.getSourcePath().getFileName() == null ? "" :
                        document.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
                boolean active = TypeScriptSupport.isProjectPath(document.getSourcePath()) &&
                                 (file.startsWith("tsconfig") || file.startsWith("jsconfig")) && file.endsWith(".json");
                config = active;
                Json.Document visited = active ? super.visitDocument(document, ctx) : document;
                config = previous;
                if (active && visited.getValue() instanceof Json.JsonObject root &&
                    root.getMembers().stream().filter(Json.Member.class::isInstance).map(Json.Member.class::cast)
                            .noneMatch(member -> "compilerOptions".equals(TypeScriptSupport.key(member)))) {
                    return TypeScriptSupport.mark(visited,
                            "TypeScript 6 changes defaults for strict/module/target/noUncheckedSideEffectImports/libReplacement/types/rootDir; add compilerOptions and choose these values after checking runtime, globals, emit layout, and side-effect imports");
                }
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!config) return visited;
                String key = TypeScriptSupport.key(visited);
                if ("compilerOptions".equals(key) && isRootMember(getCursor()) &&
                    visited.getValue() instanceof Json.JsonObject options) {
                    java.util.Set<String> present = options.getMembers().stream().filter(Json.Member.class::isInstance)
                            .map(Json.Member.class::cast).map(TypeScriptSupport::key)
                            .collect(java.util.stream.Collectors.toSet());
                    java.util.Set<String> missing = new java.util.TreeSet<>(DEFAULT_SENSITIVE);
                    missing.removeAll(present);
                    if (!missing.isEmpty()) {
                        return TypeScriptSupport.mark(visited,
                                "TypeScript 6 changes defaults for strict/module/target/noUncheckedSideEffectImports/libReplacement/types/rootDir; explicitly choose the missing values after checking runtime, globals, emit layout, and side-effect imports: " + missing);
                    }
                    return visited;
                }
                if (!isDirectCompilerOption(getCursor())) return visited;
                String value = scalar(visited);
                if ("target".equals(key) && "es5".equalsIgnoreCase(value)) {
                    return TypeScriptSupport.markValue(visited, "TypeScript 6 deprecates ES5 emit; raise target to at least ES2015 or retain legacy output in an external transpiler");
                }
                if ("downlevelIteration".equals(key)) {
                    return TypeScriptSupport.markValue(visited, "downlevelIteration is deprecated because TypeScript 6 no longer supports ES5 emit; remove it only with the target/runtime decision");
                }
                if ("moduleResolution".equals(key) && Set.of("classic", "node", "node10").contains(value.toLowerCase(Locale.ROOT))) {
                    return TypeScriptSupport.markValue(visited, "Legacy moduleResolution is deprecated/removed; choose bundler, node16, or nodenext from the actual runtime and package-exports contract");
                }
                if ("module".equals(key) && Set.of("amd", "umd", "system", "systemjs").contains(value.toLowerCase(Locale.ROOT))) {
                    return TypeScriptSupport.markValue(visited, "TypeScript 6 deprecates this legacy module emitter; select an ESM/CommonJS boundary and external bundler deliberately");
                }
                if ("baseUrl".equals(key)) {
                    return TypeScriptSupport.markValue(visited, "baseUrl is deprecated as a module-resolution root; rewrite each paths target/import owner before removing it");
                }
                if (("esModuleInterop".equals(key) || "allowSyntheticDefaultImports".equals(key) ||
                     "alwaysStrict".equals(key)) && "false".equalsIgnoreCase(value)) {
                    return TypeScriptSupport.markValue(visited, "TypeScript 6 no longer supports this false legacy interop/strictness behavior; verify imports, this semantics, and emitted runtime behavior");
                }
                if ("outFile".equals(key)) {
                    return TypeScriptSupport.markValue(visited, "outFile was removed; move concatenation and chunk/output naming to a supported bundler");
                }
                if ("ignoreDeprecations".equals(key) && value.startsWith("6.")) {
                    return TypeScriptSupport.markValue(visited, "ignoreDeprecations suppresses TypeScript 6 migration diagnostics; remove it after resolving every marked option before TypeScript 7");
                }
                if ("types".equals(key) && containsString(visited.getValue(), "*")) {
                    return TypeScriptSupport.markValue(visited, "types:[\"*\"] restores broad ambient discovery; replace it with the exact node/test/runtime global packages to avoid hidden coupling");
                }
                return visited;
            }

            private boolean isDirectCompilerOption(Cursor cursor) {
                Cursor compilerObject = cursor.getParentTreeCursor();
                Cursor compilerMember = compilerObject == null ? null : compilerObject.getParentTreeCursor();
                if (compilerMember == null || !(compilerMember.getValue() instanceof Json.Member member) ||
                    !"compilerOptions".equals(TypeScriptSupport.key(member))) return false;
                Cursor rootObject = compilerMember == null ? null : compilerMember.getParentTreeCursor();
                Cursor document = rootObject == null ? null : rootObject.getParentTreeCursor();
                return document != null && document.getValue() instanceof Json.Document;
            }

            private boolean isRootMember(Cursor cursor) {
                Cursor rootObject = cursor.getParentTreeCursor();
                Cursor document = rootObject == null ? null : rootObject.getParentTreeCursor();
                return document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static String scalar(Json.Member member) {
        if (!(member.getValue() instanceof Json.Literal literal) || literal.getValue() == null) return "";
        return literal.getValue().toString();
    }

    private static boolean containsString(Json tree, String wanted) {
        if (tree instanceof Json.Literal literal) return wanted.equals(literal.getValue());
        if (tree instanceof Json.Array array) return array.getValues().stream().anyMatch(value -> containsString(value, wanted));
        return false;
    }
}
