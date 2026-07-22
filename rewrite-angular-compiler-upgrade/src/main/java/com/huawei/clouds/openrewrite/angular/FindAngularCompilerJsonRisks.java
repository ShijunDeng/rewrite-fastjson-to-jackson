package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Marks compiler package, toolchain, tsconfig, and workspace values requiring coordinated decisions. */
public final class FindAngularCompilerJsonRisks extends Recipe {
    private static final Set<String> ANGULAR_GROUP = Set.of(
            "@angular/animations", "@angular/common", "@angular/core", "@angular/compiler-cli",
            "@angular/platform-browser", "@angular/platform-browser-dynamic", "@angular/platform-server"
    );
    private static final Set<String> BUILD_TOOLS = Set.of(
            "@angular/cli", "@angular-devkit/build-angular", "@angular/build", "ng-packagr"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Compiler 20 JSON migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks complex constraints, lockstep Angular/build toolchain declarations, residual ngcc/View Engine " +
               "configuration, template compiler flags, custom builders, AOT disablement, and SSR targets.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(file)) return inspectPackage(visited);
                if (file.startsWith("tsconfig") && file.endsWith(".json")) return inspectTsConfig(visited);
                if ("angular.json".equals(file) || "workspace.json".equals(file)) return inspectWorkspace(visited);
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                if (!containsManagedCompiler(getCursor().firstEnclosingOrThrow(Json.Document.class).getValue())) return member;
                String name = UpgradeSelectedAngularCompilerDependency.key(member);
                String parent = parentKey();
                if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String value)) return member;
                if (UpgradeSelectedAngularCompilerDependency.SECTIONS.contains(parent) && "@angular/compiler".equals(name) &&
                    !UpgradeSelectedAngularCompilerDependency.TARGET_VERSION.equals(value) &&
                    !UpgradeSelectedAngularCompilerDependency.selected(value)) {
                    return markValue(member, "@angular/compiler uses a complex range, protocol, variable, tag, unlisted, or newer declaration; choose the constraint and regenerate the lockfile");
                }
                if (UpgradeSelectedAngularCompilerDependency.SECTIONS.contains(parent) &&
                    (ANGULAR_GROUP.contains(name) || BUILD_TOOLS.contains(name)) &&
                    !UpgradeSelectedAngularCompilerDependency.TARGET_VERSION.equals(value)) {
                    return markValue(member, "Angular compiler/core/compiler-cli and CLI/build/linker packages must be aligned to a mutually compatible 20.3.26 toolchain after each major's migrations");
                }
                if (UpgradeSelectedAngularCompilerDependency.SECTIONS.contains(parent) && "typescript".equals(name) && !supportedTypeScript(value)) {
                    return markValue(member, "Angular 20 compiler-cli requires TypeScript >=5.8 and <6.0; old schematics may require an earlier compiler during staged major upgrades");
                }
                if (UpgradeSelectedAngularCompilerDependency.SECTIONS.contains(parent) && "tslib".equals(name) && !supportedTslib(value)) {
                    return markValue(member, "Angular Compiler 20 requires tslib ^2.3.0 or newer; align emitted helpers across libraries, tests, SSR, and bundlers");
                }
                if ("engines".equals(parent) && "node".equals(name) && !supportedNode(value)) {
                    return markValue(member, "Angular Compiler 20.3.26 requires Node ^20.19.0, ^22.12.0, or >=24.0.0 in local, CI, package, image, and SSR environments");
                }
                if ("scripts".equals(parent) && value.contains("ngcc")) {
                    return markValue(member, "ngcc was removed with View Engine support; split this chained script, remove ngcc, and verify every dependency ships Ivy/APF-compatible output");
                }
                if ("@angular/compiler".equals(name) && hasCentralOwner()) {
                    return markValue(member, "Central package-manager version ownership detected; update catalog/override/resolution and every compiler consumer atomically");
                }
                return member;
            }

            private Json.Member inspectTsConfig(Json.Member member) {
                String name = UpgradeSelectedAngularCompilerDependency.key(member);
                if (!"angularCompilerOptions".equals(parentKey())) return member;
                if ("compliationMode".equals(name)) {
                    return markValue(member, "Misspelled compliationMode remains; resolve any duplicate compilationMode key and select the intended library/application compilation mode");
                }
                if ("enableIvy".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Boolean.FALSE.equals(literal.getValue())) {
                    return markValue(member, "enableIvy:false requests removed View Engine behavior; remove it and replace every View Engine-only dependency before Angular 20 compilation");
                }
                if (Set.of("strictTemplates", "strictStandalone", "strictInjectionParameters").contains(name) &&
                    member.getValue() instanceof Json.Literal literal && Boolean.FALSE.equals(literal.getValue())) {
                    return markValue(member, name + " is disabled and can hide Angular 20 template, standalone scope, host binding, input/output, nullability, or DI incompatibilities");
                }
                if ("fullTemplateTypeCheck".equals(name)) {
                    return markValue(member, "fullTemplateTypeCheck is superseded by strictTemplates; select strict template sub-flags deliberately and fix diagnostics instead of globally suppressing them");
                }
                if ("compilationMode".equals(name) && member.getValue() instanceof Json.Literal) {
                    return markValue(member, "compilationMode must be partial for published libraries and full for final applications; verify linker order, APF exports, sideEffects, and consumer compatibility");
                }
                if ("preserveWhitespaces".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    Boolean.TRUE.equals(literal.getValue())) {
                    return markValue(member, "preserveWhitespaces affects rendered layout, source spans, i18n message IDs, hydration, snapshots, and payload size; retain only with explicit tests");
                }
                if (Set.of("extendedDiagnostics", "diagnostics", "annotationsAs", "disableExpressionLowering", "enableResourceInlining").contains(name)) {
                    return SearchResult.found(member, name + " changes compiler diagnostics or emitted library metadata; verify public API, tree shaking, resources, source maps, linker, and CI policy");
                }
                return member;
            }

            private Json.Member inspectWorkspace(Json.Member member) {
                String name = UpgradeSelectedAngularCompilerDependency.key(member);
                if ("builder".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    literal.getValue() instanceof String builder &&
                    !builder.startsWith("@angular-devkit/build-angular:") && !builder.startsWith("@angular/build:")) {
                    return markValue(member, "Custom builder detected; select an Angular 20-compatible compiler/linker integration and test AOT, resources, source maps, i18n, libraries, SSR, and optimization");
                }
                if ("aot".equals(name) && member.getValue() instanceof Json.Literal literal && Boolean.FALSE.equals(literal.getValue())) {
                    return markValue(member, "AOT is disabled; runtime JIT/platform-browser-dynamic is deprecated in Angular 20, so migrate bootstrap/templates and verify production CSP");
                }
                if (Set.of("server", "ssr", "prerender").contains(name)) {
                    return SearchResult.found(member, "SSR/prerender compilation must share compiler options, template resources, providers, i18n, hydration output, and target conditions with the client build");
                }
                return member;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.withValue(SearchResult.found(member.getValue(), message));
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member
                        ? UpgradeSelectedAngularCompilerDependency.key((Json.Member) parent.getValue()) : "";
            }

            private boolean hasCentralOwner() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof Json.Member member &&
                        Set.of("overrides", "resolutions", "catalog", "catalogs", "pnpm").contains(
                                UpgradeSelectedAngularCompilerDependency.key(member))) return true;
                    cursor = cursor.getParent();
                }
                return false;
            }
        };
    }

    private static boolean containsManagedCompiler(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member member)) continue;
            String key = UpgradeSelectedAngularCompilerDependency.key(member);
            if (UpgradeSelectedAngularCompilerDependency.SECTIONS.contains(key) &&
                member.getValue() instanceof Json.JsonObject dependencies &&
                dependencies.getMembers().stream().anyMatch(entry -> entry instanceof Json.Member dependency &&
                        "@angular/compiler".equals(UpgradeSelectedAngularCompilerDependency.key(dependency)))) {
                return true;
            }
            if (Set.of("overrides", "resolutions", "catalog", "catalogs", "pnpm").contains(key) &&
                containsCompilerAnywhere(member.getValue())) return true;
        }
        return false;
    }

    private static boolean containsCompilerAnywhere(Json tree) {
        if (tree instanceof Json.Member member) {
            return "@angular/compiler".equals(UpgradeSelectedAngularCompilerDependency.key(member)) ||
                   containsCompilerAnywhere(member.getValue());
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindAngularCompilerJsonRisks::containsCompilerAnywhere);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindAngularCompilerJsonRisks::containsCompilerAnywhere);
        }
        return false;
    }

    private static boolean supportedTypeScript(String value) {
        int[] v = scalar(value); return v != null && v[0] == 5 && v[1] >= 8;
    }
    private static boolean supportedTslib(String value) {
        int[] v = scalar(value); return v != null && v[0] >= 2 && (v[0] > 2 || v[1] >= 3);
    }
    private static boolean supportedNode(String value) {
        if ("^20.19.0 || ^22.12.0 || >=24.0.0".equals(value)) return true;
        int[] v = scalar(value); return v != null && (v[0] == 20 && v[1] >= 19 || v[0] == 22 && v[1] >= 12 || v[0] >= 24);
    }
    private static int[] scalar(String value) {
        String candidate = value.startsWith("^") || value.startsWith("~") ? value.substring(1) : value;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] p = candidate.split("\\.");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
    }
}
