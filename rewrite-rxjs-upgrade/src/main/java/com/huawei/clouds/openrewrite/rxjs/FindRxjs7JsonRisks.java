package com.huawei.clouds.openrewrite.rxjs;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks package declarations that cannot be safely coordinated by the strict dependency recipe. */
public final class FindRxjs7JsonRisks extends Recipe {
    private static final Pattern SIMPLE_VERSION = Pattern.compile("^[~^]?(?<major>\\d+)(?:\\.|$).*");

    @Override
    public String getDisplayName() {
        return "Find RxJS 7 package compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Marks unresolved RxJS constraints, rxjs-compat, central version owners, and legacy TypeScript " +
               "3 declarations in package.json with a specific migration reason.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!isPackageJson()) return visited;

                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!containsRxjs(document.getValue())) return visited;
                if (!(visited.getValue() instanceof Json.Literal literal) ||
                    !(literal.getValue() instanceof String declaration)) {
                    return visited;
                }

                String name = UpgradeSelectedRxjsDependency.key(visited);
                String owner = UpgradeSelectedRxjsDependency.directDependencySection(getCursor());
                if (owner != null && "rxjs".equals(name)) {
                    if (UpgradeSelectedRxjsDependency.TARGET_VERSION.equals(declaration)) return visited;
                    if (UpgradeSelectedRxjsDependency.isSelected(declaration)) {
                        return markValue(visited,
                                "Spreadsheet-listed RxJS declaration still needs the strict upgrade to 7.8.2 and lockfile regeneration");
                    }
                    return markValue(visited,
                            "RxJS declaration is a range, protocol, alias, unlisted, or newer version; choose the intended 7.8.2 constraint and regenerate the lockfile");
                }
                if (owner != null && "rxjs-compat".equals(name)) {
                    return markValue(visited,
                            "rxjs-compat is not an RxJS 7 migration strategy; remove it after replacing every legacy patch import and API");
                }
                if (owner != null && "typescript".equals(name) &&
                    isLegacyTypeScript(declaration)) {
                    return markValue(visited,
                            "TypeScript 3 predates the RxJS 7 declaration surface; align compiler, editor, test runner, and CI before upgrading");
                }
                if ("rxjs".equals(name) && isCentralVersionOwner()) {
                    return markValue(visited,
                            "Central RxJS version ownership detected; update the catalog, resolution, or override and every workspace consumer atomically");
                }
                return visited;
            }

            private boolean isPackageJson() {
                Path path = getCursor().firstEnclosingOrThrow(Json.Document.class).getSourcePath();
                return path.getFileName() != null && "package.json".equals(path.getFileName().toString()) &&
                       !RxjsSourceText.isGenerated(path);
            }

            private boolean isCentralVersionOwner() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof Json.Member ancestor) {
                        String name = UpgradeSelectedRxjsDependency.key(ancestor);
                        if (("overrides".equals(name) || "resolutions".equals(name) || "catalog".equals(name) ||
                             "catalogs".equals(name) || "pnpm".equals(name)) &&
                            UpgradeSelectedRxjsDependency.isTopLevelMember(cursor)) {
                            return true;
                        }
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }
        };
    }

    private static Json.Member markValue(Json.Member member, String message) {
        return member.withValue(SearchResult.found(member.getValue(), message));
    }

    private static boolean isLegacyTypeScript(String declaration) {
        Matcher matcher = SIMPLE_VERSION.matcher(declaration.trim());
        return matcher.matches() && Integer.parseInt(matcher.group("major")) <= 3;
    }

    private static boolean containsRxjs(Json tree) {
        if (!(tree instanceof Json.JsonObject root)) return false;
        for (Json memberTree : root.getMembers()) {
            if (!(memberTree instanceof Json.Member member)) continue;
            String owner = UpgradeSelectedRxjsDependency.key(member);
            if (UpgradeSelectedRxjsDependency.SECTIONS.contains(owner) &&
                containsDirectRxjs(member.getValue())) {
                return true;
            }
            if (("overrides".equals(owner) || "resolutions".equals(owner) || "catalog".equals(owner) ||
                 "catalogs".equals(owner) || "pnpm".equals(owner)) && containsNestedRxjs(member.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDirectRxjs(Json tree) {
        if (!(tree instanceof Json.JsonObject dependencies)) return false;
        return dependencies.getMembers().stream()
                .filter(Json.Member.class::isInstance)
                .map(Json.Member.class::cast)
                .anyMatch(member -> "rxjs".equals(UpgradeSelectedRxjsDependency.key(member)) ||
                                    "rxjs-compat".equals(UpgradeSelectedRxjsDependency.key(member)));
    }

    private static boolean containsNestedRxjs(Json tree) {
        if (tree instanceof Json.Member member) {
            return "rxjs".equals(UpgradeSelectedRxjsDependency.key(member)) ||
                   containsNestedRxjs(member.getValue());
        }
        if (tree instanceof Json.JsonObject object) {
            return object.getMembers().stream().anyMatch(FindRxjs7JsonRisks::containsNestedRxjs);
        }
        if (tree instanceof Json.Array array) {
            return array.getValues().stream().anyMatch(FindRxjs7JsonRisks::containsNestedRxjs);
        }
        return false;
    }
}
