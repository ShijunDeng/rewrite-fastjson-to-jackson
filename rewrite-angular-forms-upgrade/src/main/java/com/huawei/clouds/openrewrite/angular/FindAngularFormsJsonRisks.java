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

/** Marks package, compiler, and workspace values requiring coordinated Angular Forms decisions. */
public final class FindAngularFormsJsonRisks extends Recipe {
    private static final Set<String> ANGULAR_PEERS = Set.of(
            "@angular/common", "@angular/core", "@angular/platform-browser", "@angular/compiler-cli"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Forms 20 JSON migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks complex Forms constraints, Angular/toolchain peers, central owners, template compiler flags, " +
               "custom builders, SSR, and package-manager environment requirements.";
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
                if ("angular.json".equals(file) || "workspace.json".equals(file)) return inspectWorkspace(visited);
                if (file.startsWith("tsconfig") && file.endsWith(".json")) return inspectTsConfig(visited);
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                if (!containsManagedForms(getCursor().firstEnclosingOrThrow(Json.Document.class).getValue())) return member;
                String name = UpgradeSelectedAngularFormsDependency.key(member);
                if (!(member.getValue() instanceof Json.Literal literal) || !(literal.getValue() instanceof String value)) return member;
                String parent = parentKey();
                if (UpgradeSelectedAngularFormsDependency.SECTIONS.contains(parent) && "@angular/forms".equals(name) &&
                    !UpgradeSelectedAngularFormsDependency.TARGET_VERSION.equals(value) &&
                    !UpgradeSelectedAngularFormsDependency.selected(value)) {
                    return markValue(member, "@angular/forms uses a complex range, protocol, variable, tag, unlisted, or newer declaration; choose the constraint and regenerate the lockfile");
                }
                if (UpgradeSelectedAngularFormsDependency.SECTIONS.contains(parent) && ANGULAR_PEERS.contains(name) &&
                    !UpgradeSelectedAngularFormsDependency.TARGET_VERSION.equals(value)) {
                    return markValue(member, "Angular Forms peers must be aligned at 20.3.26 after every major's official ng update migrations");
                }
                if (UpgradeSelectedAngularFormsDependency.SECTIONS.contains(parent) && "typescript".equals(name) && !supportedTypeScript(value)) {
                    return markValue(member, "Angular 20 compiler tooling requires TypeScript >=5.8 and <6.0; typed forms and strict template diagnostics depend on the aligned compiler");
                }
                if (UpgradeSelectedAngularFormsDependency.SECTIONS.contains(parent) && "rxjs".equals(name) && !supportedRxJs(value)) {
                    return markValue(member, "Angular Forms 20.3.26 supports RxJS ^6.5.3 or ^7.4.0; verify value/status events and async-validator cancellation");
                }
                if ("engines".equals(parent) && "node".equals(name) && !supportedNode(value)) {
                    return markValue(member, "Angular 20.3.26 requires Node ^20.19.0, ^22.12.0, or >=24.0.0 in local, CI, images, and deployment");
                }
                if ("@angular/forms".equals(name) && hasCentralOwner()) {
                    return markValue(member, "Central package-manager version ownership detected; update the catalog/override/resolution and all consumers atomically");
                }
                return member;
            }

            private Json.Member inspectWorkspace(Json.Member member) {
                String name = UpgradeSelectedAngularFormsDependency.key(member);
                if ("builder".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    literal.getValue() instanceof String builder &&
                    !builder.startsWith("@angular-devkit/build-angular:") && !builder.startsWith("@angular/build:")) {
                    return markValue(member, "Custom builder detected; select an Angular 20-compatible release and verify template compilation, assets, tests, SSR, and hydration");
                }
                if (Set.of("server", "ssr", "prerender").contains(name)) {
                    return SearchResult.found(member, "SSR/prerender forms need identical initial values, disabled state, validation, DOM attributes, and hydration/event replay behavior");
                }
                return member;
            }

            private Json.Member inspectTsConfig(Json.Member member) {
                String name = UpgradeSelectedAngularFormsDependency.key(member);
                if (Set.of("strictTemplates", "strictInputTypes", "strictNullInputTypes").contains(name) &&
                    member.getValue() instanceof Json.Literal literal && Boolean.FALSE.equals(literal.getValue())) {
                    return markValue(member, name + " is disabled; Angular 20 typed forms/template diagnostics may hide incompatible value, nullability, validator, and directive bindings");
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
                        ? UpgradeSelectedAngularFormsDependency.key((Json.Member) parent.getValue()) : "";
            }

            private boolean hasCentralOwner() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof Json.Member ancestor &&
                        Set.of("overrides", "resolutions", "catalog", "catalogs", "pnpm").contains(
                                UpgradeSelectedAngularFormsDependency.key(ancestor))) return true;
                    cursor = cursor.getParent();
                }
                return false;
            }
        };
    }

    private static boolean containsManagedForms(Json tree) {
        if (tree instanceof Json.Member member) {
            return "@angular/forms".equals(UpgradeSelectedAngularFormsDependency.key(member)) ||
                   containsManagedForms(member.getValue());
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindAngularFormsJsonRisks::containsManagedForms);
        }
        if (tree instanceof Json.Array array) return array.getValues().stream().anyMatch(FindAngularFormsJsonRisks::containsManagedForms);
        return false;
    }

    private static boolean supportedTypeScript(String value) {
        int[] v = scalar(value); return v != null && v[0] == 5 && v[1] >= 8;
    }
    private static boolean supportedRxJs(String value) {
        if ("^6.5.3 || ^7.4.0".equals(value)) return true;
        int[] v = scalar(value); return v != null && (v[0] == 6 && (v[1] > 5 || v[1] == 5 && v[2] >= 3) || v[0] == 7 && v[1] >= 4);
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
