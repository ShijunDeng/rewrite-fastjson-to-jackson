package com.huawei.clouds.openrewrite.vuei18n;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

/** Marks concrete removed or stricter message syntax in locale YAML resources. */
public final class FindVueI18nYamlMessageRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Vue I18n 11 YAML locale-message risks";
    }

    @Override
    public String getDescription() {
        return "Marks legacy modulo interpolation, removed linked-message grouping, and unescaped email @ " +
               "characters only in locale-shaped YAML resource paths.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            private boolean localeResource;

            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                boolean previous = localeResource;
                localeResource = VueI18nLocaleMessageSupport.isLocaleResource(documents.getSourcePath());
                Yaml.Documents visited = localeResource ? super.visitDocuments(documents, ctx) : documents;
                localeResource = previous;
                return visited;
            }

            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                Yaml.Scalar visited = super.visitScalar(scalar, ctx);
                if (!localeResource || isMappingKey(visited)) {
                    return visited;
                }
                String risk = VueI18nLocaleMessageSupport.risk(visited.getValue());
                return risk == null ? visited : SearchResult.found(visited, risk);
            }

            private boolean isMappingKey(Yaml.Scalar scalar) {
                org.openrewrite.Cursor parent = getCursor().getParent();
                return parent != null && parent.getValue() instanceof Yaml.Mapping.Entry entry &&
                       entry.getKey().getId().equals(scalar.getId());
            }
        };
    }
}
