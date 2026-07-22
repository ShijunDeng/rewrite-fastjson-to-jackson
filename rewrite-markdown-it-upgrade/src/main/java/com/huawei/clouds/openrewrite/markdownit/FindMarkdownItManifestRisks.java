package com.huawei.clouds.openrewrite.markdownit;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

/** Marks manifest declarations that require a compatibility or ownership decision. */
public final class FindMarkdownItManifestRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find markdown-it 14 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unselected markdown-it declarations, override ownership, type packages, and markdown-it plugin " +
               "versions that must be validated against the ESM implementation and parser internals.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!MarkdownItSupport.isPackageJson(document.getSourcePath())) return visited;
                String key = MarkdownItSupport.key(visited);
                String section = MarkdownItSupport.directDependencySection(getCursor());
                String declaration = MarkdownItSupport.stringValue(visited);
                if (MarkdownItSupport.PACKAGE.equals(key) && !section.isEmpty() && declaration != null &&
                    !MarkdownItSupport.target(declaration)) {
                    return visited.withValue(SearchResult.found(visited.getValue(),
                            "This direct markdown-it declaration is not the exact workbook target after strict migration; resolve ranges, aliases, catalogs, forks, or unlisted versions deliberately"));
                }
                if (MarkdownItSupport.overrideSelector(key) && section.isEmpty() &&
                    MarkdownItSupport.underOverrideSection(getCursor())) {
                    return SearchResult.found(visited,
                            "This override/resolution can force a markdown-it version independently of the direct declaration; align the actual package-manager graph and regenerate its lockfile");
                }
                if (!section.isEmpty() && ("@types/markdown-it".equals(key) || key.startsWith("markdown-it-"))) {
                    return visited.withValue(SearchResult.found(visited.getValue(),
                            "Validate this markdown-it type/plugin package against 14.3.0: v14 rewrote internals and bundled plugins to ESM, and markdown-it-emoji changed its signature"));
                }
                return visited;
            }
        };
    }
}
