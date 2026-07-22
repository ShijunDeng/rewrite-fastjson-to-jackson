package com.huawei.clouds.openrewrite.vuei18n;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

/** Marks concrete removed or stricter message syntax in locale JSON resources. */
public final class FindVueI18nJsonMessageRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Vue I18n 11 JSON locale-message risks";
    }

    @Override
    public String getDescription() {
        return "Marks legacy modulo interpolation, removed linked-message grouping, and unescaped email @ " +
               "characters only in locale-shaped JSON resource paths.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean localeResource;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean previous = localeResource;
                localeResource = VueI18nLocaleMessageSupport.isLocaleResource(document.getSourcePath());
                Json.Document visited = localeResource ? super.visitDocument(document, ctx) : document;
                localeResource = previous;
                return visited;
            }

            @Override
            public Json.Literal visitLiteral(Json.Literal literal, ExecutionContext ctx) {
                Json.Literal visited = super.visitLiteral(literal, ctx);
                if (!localeResource || !(visited.getValue() instanceof String value) || isMemberKey(visited)) {
                    return visited;
                }
                String risk = VueI18nLocaleMessageSupport.risk(value);
                return risk == null ? visited : SearchResult.found(visited, risk);
            }

            private boolean isMemberKey(Json.Literal literal) {
                org.openrewrite.Cursor parent = getCursor().getParent();
                return parent != null && parent.getValue() instanceof Json.Member member &&
                       member.getKey().getId().equals(literal.getId());
            }
        };
    }
}
