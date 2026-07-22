package com.huawei.clouds.openrewrite.i18next;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

/** Marks remaining legacy plural keys in locale-shaped JSON resources. */
public final class FindI18nextJsonLocaleRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find i18next JSON v4 locale migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks remaining _plural and numeric plural suffix keys only in locale-shaped JSON paths after the " +
               "safe English conversion, so language-specific cardinal/ordinal/context conversion is reviewed.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean locale;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean previous = locale;
                locale = I18nextLocaleSupport.isLocaleResource(document.getSourcePath());
                Json.Document visited = locale ? super.visitDocument(document, ctx) : document;
                locale = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String key = I18nextManifestSupport.key(visited);
                if (locale && (key.endsWith("_plural") || key.matches(".*_[0-9]+$"))) {
                    return visited.withKey(SearchResult.found(visited.getKey(),
                            "Legacy i18next plural suffix remains; convert this locale with its exact CLDR cardinal/ordinal rules, custom separator, context keys, and collision review"));
                }
                return visited;
            }
        };
    }
}
