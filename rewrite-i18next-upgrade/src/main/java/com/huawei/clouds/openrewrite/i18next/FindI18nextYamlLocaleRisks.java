package com.huawei.clouds.openrewrite.i18next;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

/** Marks legacy plural keys in locale-shaped YAML resources for language-aware conversion. */
public final class FindI18nextYamlLocaleRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find i18next YAML v4 locale migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks _plural and numeric plural suffix mapping keys only in locale-shaped YAML paths for " +
               "language-specific CLDR cardinal, ordinal, context, and separator review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            private boolean locale;

            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                boolean previous = locale;
                locale = I18nextLocaleSupport.isLocaleResource(documents.getSourcePath());
                Yaml.Documents visited = locale ? super.visitDocuments(documents, ctx) : documents;
                locale = previous;
                return visited;
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ctx);
                String key = visited.getKey().getValue();
                if (locale && (key.endsWith("_plural") || key.matches(".*_[0-9]+$"))) {
                    return visited.withKey(SearchResult.found(visited.getKey(),
                            "Legacy i18next plural suffix remains; convert this locale with its exact CLDR cardinal/ordinal rules, custom separator, context keys, and collision review"));
                }
                return visited;
            }
        };
    }
}
