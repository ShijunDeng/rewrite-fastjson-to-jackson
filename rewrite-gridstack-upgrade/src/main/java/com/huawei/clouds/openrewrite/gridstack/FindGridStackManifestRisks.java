package com.huawei.clouds.openrewrite.gridstack;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks package and framework integration decisions not safe to infer from a GridStack version. */
public final class FindGridStackManifestRisks extends Recipe {
    private static final Set<String> FRAMEWORKS = Set.of(
            "react", "react-dom", "next", "vue", "nuxt", "@angular/core",
            "gridstack-angular", "react-gridstack", "gridstack-wrapper");

    @Override
    public String getDisplayName() {
        return "Find GridStack 12 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped GridStack declarations and framework or wrapper packages whose lifecycle, " +
               "rendering, hydration, and peer compatibility require application tests.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean manifest;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!GridStackManifestSupport.isPackageJson(document.getSourcePath())) {
                    return document;
                }
                boolean previous = manifest;
                manifest = containsGridStack(document);
                Json.Document visited = super.visitDocument(document, ctx);
                manifest = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!manifest || GridStackManifestSupport.directSection(getCursor()).isEmpty()) {
                    return visited;
                }
                String key = GridStackManifestSupport.key(visited);
                String value = GridStackManifestSupport.stringValue(visited);
                if (GridStackManifestSupport.PACKAGE.equals(key) && !GridStackManifestSupport.target(value)) {
                    return mark(visited, "Strict migration skipped this complex range, protocol, decorated, dynamic, or unlisted GridStack declaration; select and test a 12.x constraint, then regenerate the lockfile");
                }
                if (FRAMEWORKS.contains(key)) {
                    return mark(visited, key + " integration must be verified against GridStack 12 rendering callbacks, mount/unmount ownership, SSR/hydration, nested grids, drag helpers, and wrapper peer ranges");
                }
                return visited;
            }

            private Json.Member mark(Json.Member member, String message) {
                return member.getValue() instanceof Json.Literal
                        ? member.withValue(SearchResult.found(member.getValue(), message))
                        : SearchResult.found(member, message);
            }
        };
    }

    private static boolean containsGridStack(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) {
            return false;
        }
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member section) ||
                !GridStackManifestSupport.SECTIONS.contains(GridStackManifestSupport.key(section)) ||
                !(section.getValue() instanceof Json.JsonObject dependencies)) {
                continue;
            }
            for (Json entry : dependencies.getMembers()) {
                if (entry instanceof Json.Member member &&
                    GridStackManifestSupport.PACKAGE.equals(GridStackManifestSupport.key(member))) {
                    return true;
                }
            }
        }
        return false;
    }
}
