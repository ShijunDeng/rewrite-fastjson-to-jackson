package com.huawei.clouds.openrewrite.ngdynamicforms;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.Set;

/** Marks package values that must be coordinated with @ng-dynamic-forms/core 18.0.0. */
public final class FindNgDynamicFormsJsonRisks extends Recipe {
    private static final Set<String> ANGULAR_PACKAGES = Set.of(
            "@angular/animations", "@angular/cdk", "@angular/cli", "@angular/common", "@angular/compiler",
            "@angular/compiler-cli", "@angular/core", "@angular/forms", "@angular/material", "@angular/platform-browser",
            "@angular/platform-browser-dynamic", "@angular/platform-server", "@angular/router"
    );
    private static final Set<String> UI_RUNTIME_PACKAGES = Set.of(
            "@ionic/angular", "@ng-bootstrap/ng-bootstrap", "bootstrap", "foundation-sites", "ngx-bootstrap",
            "ngx-mask", "primeng", "primeicons", "quill"
    );
    private static final Set<String> CENTRAL_OWNERS = Set.of(
            "overrides", "resolutions", "catalog", "catalogs", "pnpm"
    );

    @Override
    public String getDisplayName() {
        return "Find NG Dynamic Forms 18 package migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unselected core declarations, Angular 16/RxJS/core-js/toolchain mismatches, UI renderer " +
               "companions, removed Kendo support, and central version owners in package.json.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!isPackageJson(document) || !containsManagedCore(document.getValue())) return visited;
                String name = UpgradeSelectedNgDynamicFormsCoreDependency.key(visited);
                if (!(visited.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration)) return visited;
                String parent = parentKey();
                boolean direct = UpgradeSelectedNgDynamicFormsCoreDependency.SECTIONS.contains(parent);
                if (direct && UpgradeSelectedNgDynamicFormsCoreDependency.PACKAGE.equals(name) &&
                    !UpgradeSelectedNgDynamicFormsCoreDependency.TARGET_VERSION.equals(declaration)) {
                    return markValue(visited, "@ng-dynamic-forms/core is unlisted, patched, newer, ranged, tagged, variable-based, or protocol-based; choose the constraint explicitly and regenerate the lockfile");
                }
                if (direct && ANGULAR_PACKAGES.contains(name) && !major(declaration, 16)) {
                    return markValue(visited, name + " must be aligned to Angular 16 for @ng-dynamic-forms/core 18.0.0; run official Angular migrations one major at a time and keep framework/compiler/CDK/UI packages coherent");
                }
                if (direct && "rxjs".equals(name) && !atLeastWithinMajor(declaration, 7, 5, 7)) {
                    return markValue(visited, "@ng-dynamic-forms/core 18.0.0 peers on RxJS ^7.5.7; verify relation streams, async validators, teardown, schedulers, and removed RxJS 6 APIs");
                }
                if (direct && "core-js".equals(name) && !atLeastWithinMajor(declaration, 3, 31, 0)) {
                    return markValue(visited, "@ng-dynamic-forms/core 18.0.0 peers on core-js ^3.31.0; align browser polyfills and validate the supported browser matrix");
                }
                if (direct && "typescript".equals(name) && !supportedTypeScript(declaration)) {
                    return markValue(visited, "Angular 16.1 compiler supports TypeScript >=4.9.3 and <5.2; align compiler-cli, IDE, test, and build TypeScript resolution");
                }
                if ("engines".equals(parent) && "node".equals(name) && !supportedNode(declaration)) {
                    return markValue(visited, "Angular 16.1 requires Node ^16.14.0 or >=18.10.0; align local, CI, images, SSR, and deployment runtimes");
                }
                if (direct && name.startsWith("@ng-dynamic-forms/ui-")) {
                    if ("@ng-dynamic-forms/ui-kendo".equals(name)) {
                        return markValue(visited, "NG Dynamic Forms 18 removed the Kendo renderer; select another renderer or own the custom control layer before deleting this package");
                    }
                    if (!major(declaration, 18)) {
                        return markValue(visited, name + " must move to its 18.x standalone release with the core; replace its removed UIModule and verify renderer-specific peers, styles, templates, and tests");
                    }
                }
                if (direct && UI_RUNTIME_PACKAGES.contains(name) && containsUiRenderer(document.getValue())) {
                    return markValue(visited, name + " is a renderer-specific peer; verify the exact NG Dynamic Forms 18 UI manifest instead of applying one shared version rule");
                }
                if (UpgradeSelectedNgDynamicFormsCoreDependency.PACKAGE.equals(name) && hasCentralOwner()) {
                    return markValue(visited, "Central package-manager ownership detected; update the catalog/override/resolution and all consumers atomically, then regenerate every lockfile");
                }
                return visited;
            }

            private boolean isPackageJson(Json.Document document) {
                Path path = document.getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString());
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member
                        ? UpgradeSelectedNgDynamicFormsCoreDependency.key((Json.Member) parent.getValue()) : "";
            }

            private boolean hasCentralOwner() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof Json.Member ancestor &&
                        CENTRAL_OWNERS.contains(UpgradeSelectedNgDynamicFormsCoreDependency.key(ancestor))) return true;
                    cursor = cursor.getParent();
                }
                return false;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.withValue(SearchResult.found(member.getValue(), message));
            }
        };
    }

    private static boolean containsManagedCore(Json tree) {
        return containsKey(tree, UpgradeSelectedNgDynamicFormsCoreDependency.PACKAGE);
    }

    private static boolean containsUiRenderer(Json tree) {
        if (tree instanceof Json.Member member) {
            return UpgradeSelectedNgDynamicFormsCoreDependency.key(member).startsWith("@ng-dynamic-forms/ui-") ||
                   containsUiRenderer(member.getValue());
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindNgDynamicFormsJsonRisks::containsUiRenderer);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindNgDynamicFormsJsonRisks::containsUiRenderer);
        }
        return false;
    }

    private static boolean containsKey(Json tree, String expected) {
        if (tree instanceof Json.Member member) {
            return expected.equals(UpgradeSelectedNgDynamicFormsCoreDependency.key(member)) ||
                   containsKey(member.getValue(), expected);
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(value -> containsKey(value, expected));
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(value -> containsKey(value, expected));
        }
        return false;
    }

    private static boolean major(String declaration, int expected) {
        int[] version = scalar(declaration);
        return version != null && version[0] == expected;
    }

    private static boolean atLeastWithinMajor(String declaration, int major, int minor, int patch) {
        int[] version = scalar(declaration);
        return version != null && version[0] == major &&
               (version[1] > minor || version[1] == minor && version[2] >= patch);
    }

    private static boolean supportedTypeScript(String declaration) {
        int[] version = scalar(declaration);
        return version != null && (version[0] == 4 &&
                (version[1] > 9 || version[1] == 9 && version[2] >= 3) ||
                version[0] == 5 && version[1] < 2);
    }

    private static boolean supportedNode(String declaration) {
        if ("^16.14.0 || >=18.10.0".equals(declaration)) return true;
        int[] version = scalar(declaration);
        return version != null && (version[0] == 16 &&
                (version[1] > 14 || version[1] == 14 && version[2] >= 0) ||
                version[0] >= 18 && (version[0] > 18 || version[1] >= 10));
    }

    private static int[] scalar(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) candidate = candidate.substring(1);
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
