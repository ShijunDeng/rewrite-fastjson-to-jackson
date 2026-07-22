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

/** Marks Router package and workspace values which require coordinated application decisions. */
public final class FindAngularRouterJsonRisks extends Recipe {
    private static final Set<String> DEPENDENCY_SECTIONS = Set.of(
            "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );
    private static final Set<String> ANGULAR_PACKAGE_GROUP = Set.of(
            "@angular/animations", "@angular/common", "@angular/core", "@angular/compiler",
            "@angular/compiler-cli", "@angular/forms", "@angular/platform-browser",
            "@angular/platform-browser-dynamic", "@angular/platform-server", "@angular/service-worker"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Router 20 JSON migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks complex Router constraints, lockstep Angular/toolchain declarations, central version owners, " +
               "custom builders, deployment URL settings, and SSR/prerender targets.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                Path path = document.getSourcePath();
                String file = path.getFileName() == null ? "" : path.getFileName().toString();
                if ("package.json".equals(file)) {
                    return containsManagedRouter(document.getValue()) ? inspectPackage(visited) : visited;
                }
                if ("angular.json".equals(file) || "workspace.json".equals(file)) {
                    return inspectWorkspace(visited);
                }
                return visited;
            }

            private Json.Member inspectPackage(Json.Member member) {
                String name = UpgradeSelectedAngularRouterDependency.key(member);
                if (!(member.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration)) {
                    return member;
                }
                String parent = parentKey();
                if (DEPENDENCY_SECTIONS.contains(parent) && "@angular/router".equals(name) &&
                    !UpgradeSelectedAngularRouterDependency.TARGET_VERSION.equals(declaration) &&
                    !UpgradeSelectedAngularRouterDependency.selected(declaration)) {
                    return markValue(member, "@angular/router uses a complex range, protocol, variable, tag, unlisted, or newer declaration; choose the intended constraint and regenerate the lockfile");
                }
                if (DEPENDENCY_SECTIONS.contains(parent) && ANGULAR_PACKAGE_GROUP.contains(name) &&
                    !UpgradeSelectedAngularRouterDependency.TARGET_VERSION.equals(declaration)) {
                    return markValue(member, "Angular Router peers and framework packages must be aligned at 20.3.26 after each major's official ng update migrations");
                }
                if (DEPENDENCY_SECTIONS.contains(parent) && "typescript".equals(name) &&
                    !supportedTypeScript(declaration)) {
                    return markValue(member, "Angular 20 compiler tooling requires TypeScript >=5.8 and <6.0 across build, editor, and CI");
                }
                if (DEPENDENCY_SECTIONS.contains(parent) && "rxjs".equals(name) && !supportedRxJs(declaration)) {
                    return markValue(member, "Angular Router 20.3.26 supports RxJS ^6.5.3 or ^7.4.0; verify guard/resolver first-emission, empty, error, and cancellation behavior");
                }
                if ("engines".equals(parent) && "node".equals(name) && !supportedNode(declaration)) {
                    return markValue(member, "Angular Router 20.3.26 requires Node ^20.19.0, ^22.12.0, or >=24.0.0 in local, CI, image, and deployment environments");
                }
                if ("@angular/router".equals(name) && hasCentralOwner()) {
                    return markValue(member, "Central package-manager version ownership detected; update the catalog/override/resolution and every workspace consumer atomically");
                }
                return member;
            }

            private Json.Member inspectWorkspace(Json.Member member) {
                String name = UpgradeSelectedAngularRouterDependency.key(member);
                if ("builder".equals(name) && member.getValue() instanceof Json.Literal literal &&
                    literal.getValue() instanceof String declaration &&
                    !declaration.startsWith("@angular-devkit/build-angular:") &&
                    !declaration.startsWith("@angular/build:")) {
                    return markValue(member, "Custom builder detected; select an Angular 20-compatible release and validate route discovery, lazy chunks, dev server fallback, and SSR options");
                }
                if ("server".equals(name) || "ssr".equals(name) || "prerender".equals(name)) {
                    return SearchResult.found(member, "SSR/prerender routing needs identical server/client routes, blocking initial navigation, redirects, base URL, transfer state, and hydration timing");
                }
                if (("baseHref".equals(name) || "deployUrl".equals(name)) &&
                    member.getValue() instanceof Json.Literal) {
                    return markValue(member, "Deployment URL affects Router links, browser history, lazy chunk loading, service worker navigation fallback, and SSR request URLs; verify the deployed sub-path");
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
                        ? UpgradeSelectedAngularRouterDependency.key((Json.Member) parent.getValue()) : "";
            }

            private boolean hasCentralOwner() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof Json.Member ancestor) {
                        String name = UpgradeSelectedAngularRouterDependency.key(ancestor);
                        if ("overrides".equals(name) || "resolutions".equals(name) || "catalog".equals(name) ||
                            "catalogs".equals(name) || "pnpm".equals(name)) {
                            return true;
                        }
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }
        };
    }

    private static boolean containsManagedRouter(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) {
            return false;
        }
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member section)) {
                continue;
            }
            String name = UpgradeSelectedAngularRouterDependency.key(section);
            if (DEPENDENCY_SECTIONS.contains(name) && directObjectContains(section.getValue(), "@angular/router")) {
                return true;
            }
            if (("overrides".equals(name) || "resolutions".equals(name) || "catalog".equals(name) ||
                 "catalogs".equals(name) || "pnpm".equals(name)) && containsKey(section.getValue(), "@angular/router")) {
                return true;
            }
        }
        return false;
    }

    private static boolean directObjectContains(Json tree, String wanted) {
        if (!(tree instanceof Json.JsonObject object)) {
            return false;
        }
        return object.getMembers().stream().anyMatch(value -> value instanceof Json.Member member &&
                wanted.equals(UpgradeSelectedAngularRouterDependency.key(member)));
    }

    private static boolean containsKey(Json tree, String wanted) {
        if (tree instanceof Json.Member member) {
            return wanted.equals(UpgradeSelectedAngularRouterDependency.key(member)) ||
                   containsKey(member.getValue(), wanted);
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(value -> containsKey(value, wanted));
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(value -> containsKey(value, wanted));
        }
        return false;
    }

    private static boolean supportedTypeScript(String declaration) {
        int[] version = scalarVersion(declaration);
        return version != null && version[0] == 5 && version[1] >= 8;
    }

    private static boolean supportedRxJs(String declaration) {
        if ("^6.5.3 || ^7.4.0".equals(declaration)) {
            return true;
        }
        int[] version = scalarVersion(declaration);
        return version != null && ((version[0] == 6 && (version[1] > 5 || version[1] == 5 && version[2] >= 3)) ||
                                   (version[0] == 7 && version[1] >= 4));
    }

    private static boolean supportedNode(String declaration) {
        if ("^20.19.0 || ^22.12.0 || >=24.0.0".equals(declaration)) {
            return true;
        }
        int[] version = scalarVersion(declaration);
        return version != null && ((version[0] == 20 && version[1] >= 19) ||
                                   (version[0] == 22 && version[1] >= 12) || version[0] >= 24);
    }

    private static int[] scalarVersion(String declaration) {
        String candidate = declaration.startsWith("^") || declaration.startsWith("~")
                ? declaration.substring(1) : declaration;
        if (!candidate.matches("\\d+\\.\\d+\\.\\d+")) {
            return null;
        }
        String[] parts = candidate.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
