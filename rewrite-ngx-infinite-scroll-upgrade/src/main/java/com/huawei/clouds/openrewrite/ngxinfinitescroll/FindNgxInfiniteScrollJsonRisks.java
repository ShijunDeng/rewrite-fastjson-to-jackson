package com.huawei.clouds.openrewrite.ngxinfinitescroll;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks package declarations that must be coordinated with ngx-infinite-scroll 17.0.1. */
public final class FindNgxInfiniteScrollJsonRisks extends Recipe {
    private static final Set<String> ANGULAR_PACKAGES = Set.of(
            "@angular/animations", "@angular/cdk", "@angular/cli", "@angular/common", "@angular/compiler",
            "@angular/compiler-cli", "@angular/core", "@angular/forms", "@angular/material", "@angular/platform-browser",
            "@angular/platform-browser-dynamic", "@angular/platform-server", "@angular/router"
    );
    private static final Set<String> CENTRAL_OWNERS = Set.of(
            "overrides", "resolutions", "catalog", "catalogs", "pnpm"
    );

    @Override
    public String getDisplayName() {
        return "Find ngx-infinite-scroll 17 package migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unselected dependency declarations, non-Angular-17 framework/toolchain values, tslib, " +
               "and centrally owned constraints in package.json files that use ngx-infinite-scroll.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!isPackageJson(document) || !containsManagedPackage(document.getValue())) return visited;
                String name = UpgradeSelectedNgxInfiniteScrollDependency.key(visited);
                if (!(visited.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration)) return visited;
                String parent = parentKey();
                boolean direct = UpgradeSelectedNgxInfiniteScrollDependency.SECTIONS.contains(parent);
                if (direct && UpgradeSelectedNgxInfiniteScrollDependency.PACKAGE.equals(name) &&
                    !UpgradeSelectedNgxInfiniteScrollDependency.TARGET_VERSION.equals(declaration)) {
                    String reason = UpgradeSelectedNgxInfiniteScrollDependency.selected(declaration)
                            ? "This spreadsheet-selected legacy ngx-infinite-scroll declaration remains; run the dependency migration before compatibility review"
                            : "ngx-infinite-scroll is unlisted, patched, newer, ranged, tagged, variable-based, or protocol-based; choose the 17.0.1 constraint explicitly";
                    return markValue(visited, reason + " and regenerate the lockfile");
                }
                if (direct && ANGULAR_PACKAGES.contains(name) && !major(declaration, 17)) {
                    return markValue(visited, name + " must be aligned to Angular 17 because ngx-infinite-scroll 17.0.1 peers on @angular/core and @angular/common >=17 <18; run official Angular migrations one major at a time");
                }
                if (direct && "typescript".equals(name) && !supportedTypeScript(declaration)) {
                    return markValue(visited, "Angular 17 requires TypeScript >=5.2 and <5.5 (with a narrower upper bound for early 17.x); align compiler-cli, builders, tests, and IDE resolution");
                }
                if (direct && "rxjs".equals(name) && !supportedRxjs(declaration)) {
                    return markValue(visited, "Angular 17 supports RxJS ^6.5.3 or ^7.4.0; align the actual resolved range and verify scroll subscription teardown and EventEmitter observer behavior");
                }
                if (direct && "tslib".equals(name) && !atLeastWithinMajor(declaration, 2, 3, 0)) {
                    return markValue(visited, "ngx-infinite-scroll 17.0.1 depends on tslib ^2.3.0; align the runtime helper package and verify that bundling does not retain an older central resolution");
                }
                if ("engines".equals(parent) && "node".equals(name) && !supportedNode(declaration)) {
                    return markValue(visited, "Angular 17 supports Node ^18.13.0 or ^20.9.0; align local, CI, SSR, container, and deployment runtimes");
                }
                if (UpgradeSelectedNgxInfiniteScrollDependency.PACKAGE.equals(name) && hasCentralOwner()) {
                    return markValue(visited, "Central package-manager ownership detected; update this catalog/override/resolution and every consumer atomically, then regenerate all lockfiles");
                }
                return visited;
            }

            private boolean isPackageJson(Json.Document document) {
                return UpgradeSelectedNgxInfiniteScrollDependency.isEditablePackageJson(document.getSourcePath());
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member
                        ? UpgradeSelectedNgxInfiniteScrollDependency.key((Json.Member) parent.getValue()) : "";
            }

            private boolean hasCentralOwner() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof Json.Member ancestor &&
                        CENTRAL_OWNERS.contains(UpgradeSelectedNgxInfiniteScrollDependency.key(ancestor))) return true;
                    cursor = cursor.getParent();
                }
                return false;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.withValue(SearchResult.found(member.getValue(), message));
            }
        };
    }

    private static boolean containsManagedPackage(Json tree) {
        if (tree instanceof Json.Member member) {
            return UpgradeSelectedNgxInfiniteScrollDependency.PACKAGE.equals(
                    UpgradeSelectedNgxInfiniteScrollDependency.key(member)) || containsManagedPackage(member.getValue());
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindNgxInfiniteScrollJsonRisks::containsManagedPackage);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindNgxInfiniteScrollJsonRisks::containsManagedPackage);
        }
        return false;
    }

    private static boolean major(String declaration, int expected) {
        int[] version = scalar(declaration);
        return version != null && version[0] == expected;
    }

    private static boolean supportedTypeScript(String declaration) {
        int[] version = scalar(declaration);
        return version != null && version[0] == 5 && version[1] >= 2 && version[1] < 5;
    }

    private static boolean supportedRxjs(String declaration) {
        int[] version = scalar(declaration);
        return version != null && (version[0] == 6 &&
                (version[1] > 5 || version[1] == 5 && version[2] >= 3) ||
                version[0] == 7 &&
                (version[1] > 4 || version[1] == 4 && version[2] >= 0));
    }

    private static boolean supportedNode(String declaration) {
        if ("^18.13.0 || ^20.9.0".equals(declaration)) return true;
        int[] version = scalar(declaration);
        return version != null && (version[0] == 18 &&
                (version[1] > 13 || version[1] == 13 && version[2] >= 0) ||
                version[0] == 20 &&
                (version[1] > 9 || version[1] == 9 && version[2] >= 0));
    }

    private static boolean atLeastWithinMajor(String declaration, int major, int minor, int patch) {
        int[] version = scalar(declaration);
        return version != null && version[0] == major &&
               (version[1] > minor || version[1] == minor && version[2] >= patch);
    }

    private static int[] scalar(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) candidate = candidate.substring(1);
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
