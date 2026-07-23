package com.huawei.clouds.openrewrite.angularelements;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks exact manifest values that require coordinated Angular Elements migration decisions. */
public final class FindAngularElements20ManifestRisks extends Recipe {
    static final String OWNER =
            "This @angular/elements declaration is complex, protocol-based, unlisted, or centrally owned; select the " +
            "actual owner deliberately, align it to 20.3.25, and regenerate the package-manager lockfile";
    static final String FRAMEWORK =
            "Angular framework packages must be on the same 20.3.25 patch as @angular/elements; run every intervening " +
            "major's official ng update migrations before final lockstep alignment";
    static final String TOOLCHAIN =
            "Angular Elements 20.3.25 requires the Angular 20 toolchain baseline; verify Node, TypeScript, builder/CLI, " +
            "test runner, package manager, CI image, production bundler and differential/ESM output together";
    static final String RUNTIME =
            "Angular Elements runtime dependency detected; verify RxJS/zone.js or zoneless scheduling, event/change " +
            "detection, disconnect/reconnect destruction, error handling and cross-framework host behavior";
    static final String POLYFILL =
            "Legacy Custom Elements/Web Components polyfill detected; all Angular 20 supported browsers provide native " +
            "custom elements, so remove it only after checking unsupported embeds, load order and duplicate registry patches";
    static final String NGCC =
            "ngcc/View Engine tooling is removed in Angular 20; replace every View Engine dependency and delete this " +
            "script in the project that owns the install/build lifecycle";

    private static final Set<String> SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies");
    private static final Set<String> FRAMEWORK_PACKAGES = Set.of(
            "@angular/animations", "@angular/common", "@angular/compiler", "@angular/core", "@angular/forms",
            "@angular/platform-browser", "@angular/platform-browser-dynamic", "@angular/platform-server",
            "@angular/router", "@angular/service-worker");
    private static final Set<String> TOOL_PACKAGES = Set.of(
            "@angular/cli", "@angular/compiler-cli", "@angular-devkit/build-angular", "@angular/build", "typescript");
    private static final Set<String> RUNTIME_PACKAGES = Set.of("rxjs", "zone.js");
    private static final Set<String> POLYFILLS = Set.of(
            "@webcomponents/custom-elements", "@webcomponents/webcomponentsjs", "document-register-element");

    @Override
    public String getDisplayName() {
        return "Find Angular Elements 20 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact package.json values for dependency ownership, Angular lockstep, tooling, runtime, polyfills, " +
               "ngcc, Node, SSR/builders, and lockfile regeneration decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!AngularElementsSupport.isPackageJson(document.getSourcePath()) ||
                    !containsDirectPackage(document.getValue())) return visited;
                String name = AngularElementsSupport.key(visited);
                String value = AngularElementsSupport.stringValue(visited);
                String parent = parentKey();
                if (value == null) return visited;

                if (SECTIONS.contains(parent) && AngularElementsSupport.PACKAGE.equals(name) &&
                    !targetDeclaration(value)) return markValue(visited, OWNER);
                if (SECTIONS.contains(parent) && FRAMEWORK_PACKAGES.contains(name) && !exactTarget(value)) {
                    return markValue(visited, FRAMEWORK);
                }
                if (SECTIONS.contains(parent) && TOOL_PACKAGES.contains(name)) return markValue(visited, TOOLCHAIN);
                if (SECTIONS.contains(parent) && RUNTIME_PACKAGES.contains(name)) return markValue(visited, RUNTIME);
                if (SECTIONS.contains(parent) && POLYFILLS.contains(name)) return markValue(visited, POLYFILL);
                if ("engines".equals(parent) && "node".equals(name)) return markValue(visited, TOOLCHAIN);
                if ("scripts".equals(parent) && value.contains("ngcc")) return markValue(visited, NGCC);
                if (AngularElementsSupport.PACKAGE.equals(name) && centralOwner()) return markValue(visited, OWNER);
                return visited;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member member
                        ? AngularElementsSupport.key(member) : "";
            }

            private boolean centralOwner() {
                for (Cursor cursor = getCursor().getParent(); cursor != null; cursor = cursor.getParent()) {
                    if (cursor.getValue() instanceof Json.Member member &&
                        Set.of("overrides", "resolutions", "catalog", "catalogs", "pnpm").contains(
                                AngularElementsSupport.key(member))) return true;
                }
                return false;
            }
        };
    }

    private static boolean targetDeclaration(String declaration) {
        String version = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        return AngularElementsSupport.TARGET_VERSION.equals(version);
    }

    private static boolean exactTarget(String declaration) {
        return AngularElementsSupport.TARGET_VERSION.equals(declaration);
    }

    private static Json.Member markValue(Json.Member member, String message) {
        return member.withValue(mark(member.getValue(), message));
    }

    private static boolean containsDirectPackage(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        for (Json entry : root.getMembers()) {
            if (!(entry instanceof Json.Member section) || !SECTIONS.contains(AngularElementsSupport.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject dependencies)) continue;
            if (dependencies.getMembers().stream().filter(Json.Member.class::isInstance).map(Json.Member.class::cast)
                    .anyMatch(member -> AngularElementsSupport.PACKAGE.equals(AngularElementsSupport.key(member)))) {
                return true;
            }
        }
        return false;
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
