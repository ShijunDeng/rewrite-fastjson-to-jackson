package com.huawei.clouds.openrewrite.ngxcolorpicker;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks manifest values that need a coordinated Angular 19+ migration. */
public final class FindNgxColorPickerManifestRisks extends Recipe {
    private static final Set<String> ANGULAR_PACKAGES = Set.of(
            "@angular/animations", "@angular/cdk", "@angular/cli", "@angular/common", "@angular/compiler",
            "@angular/compiler-cli", "@angular/core", "@angular/forms", "@angular/material",
            "@angular/platform-browser", "@angular/platform-browser-dynamic", "@angular/platform-server",
            "@angular/router", "@angular-devkit/build-angular"
    );
    private static final Set<String> CENTRAL_OWNERS = Set.of(
            "overrides", "resolutions", "catalog", "catalogs", "pnpm"
    );

    @Override
    public String getDisplayName() {
        return "Find ngx-color-picker 20.1.1 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks the exact package.json values for unselected ngx-color-picker constraints, Angular below 19, " +
               "obsolete TypeScript/Node baselines, and central package-manager ownership.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!UpgradeSelectedNgxColorPickerDependency.isEditablePackageJson(document.getSourcePath()) ||
                    !containsManagedPackage(document.getValue())) return visited;
                if (!(visited.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration)) return visited;
                String name = UpgradeSelectedNgxColorPickerDependency.key(visited);
                String parent = parentKey();
                boolean direct = UpgradeSelectedNgxColorPickerDependency.SECTIONS.contains(parent);
                if (direct && UpgradeSelectedNgxColorPickerDependency.PACKAGE.equals(name) &&
                    !UpgradeSelectedNgxColorPickerDependency.TARGET_VERSION.equals(declaration)) {
                    String reason = UpgradeSelectedNgxColorPickerDependency.selected(declaration)
                            ? "The spreadsheet-selected legacy ngx-color-picker declaration remains; run the dependency migration"
                            : "This ngx-color-picker constraint is not one of the spreadsheet-selected exact/caret/tilde values; choose the 20.1.1 constraint explicitly";
                    return markValue(visited, reason + " and regenerate the lockfile");
                }
                if (direct && ANGULAR_PACKAGES.contains(name) && !atLeastMajor(declaration, 19)) {
                    return markValue(visited, name + " must be migrated as part of an Angular 19+ compatible set because ngx-color-picker 20.1.1 peers on @angular/common, @angular/core, and @angular/forms >=19.0.0");
                }
                if (direct && "typescript".equals(name) && !atLeast(declaration, 5, 5, 0)) {
                    return markValue(visited, "Angular 19 starts at TypeScript 5.5; select the exact compiler range required by the chosen Angular 19/20 minor and align compiler-cli, builders, tests, and editor tooling");
                }
                if ("engines".equals(parent) && "node".equals(name) && !atLeast(declaration, 18, 19, 1)) {
                    return markValue(visited, "Angular 19 starts at Node 18.19.1; select a Node line supported by the chosen Angular 19/20 minor across local, CI, SSR, container, and deployment runtimes");
                }
                if (UpgradeSelectedNgxColorPickerDependency.PACKAGE.equals(name) && hasCentralOwner()) {
                    return markValue(visited, "Central package-manager ownership detected; update this catalog/override/resolution and every consumer atomically, then regenerate every lockfile");
                }
                return visited;
            }

            private String parentKey() {
                Cursor object = getCursor().getParentTreeCursor();
                Cursor parent = object == null ? null : object.getParentTreeCursor();
                return parent != null && parent.getValue() instanceof Json.Member
                        ? UpgradeSelectedNgxColorPickerDependency.key((Json.Member) parent.getValue()) : "";
            }

            private boolean hasCentralOwner() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof Json.Member ancestor &&
                        CENTRAL_OWNERS.contains(UpgradeSelectedNgxColorPickerDependency.key(ancestor))) return true;
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
            return UpgradeSelectedNgxColorPickerDependency.PACKAGE.equals(
                    UpgradeSelectedNgxColorPickerDependency.key(member)) || containsManagedPackage(member.getValue());
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindNgxColorPickerManifestRisks::containsManagedPackage);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindNgxColorPickerManifestRisks::containsManagedPackage);
        }
        return false;
    }

    private static boolean atLeastMajor(String declaration, int minimum) {
        int[] version = scalar(declaration);
        return version != null && version[0] >= minimum;
    }

    private static boolean atLeast(String declaration, int major, int minor, int patch) {
        int[] version = scalar(declaration);
        if (version == null) return false;
        if (version[0] != major) return version[0] > major;
        if (version[1] != minor) return version[1] > minor;
        return version[2] >= patch;
    }

    private static int[] scalar(String declaration) {
        String candidate = declaration;
        if (candidate.startsWith("^") || candidate.startsWith("~")) candidate = candidate.substring(1);
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) return null;
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
