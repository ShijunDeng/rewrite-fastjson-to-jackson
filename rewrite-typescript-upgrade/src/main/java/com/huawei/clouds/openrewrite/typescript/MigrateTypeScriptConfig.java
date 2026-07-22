package com.huawei.clouds.openrewrite.typescript;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonRightPadded;
import org.openrewrite.json.tree.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Applies only configuration simplifications guaranteed by TypeScript 6. */
public final class MigrateTypeScriptConfig extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic TypeScript 6 configuration";
    }

    @Override
    public String getDescription() {
        return "Remove redundant dom.iterable and dom.asynciterable lib entries only from direct compilerOptions.lib arrays that also contain dom.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean config;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean previous = config;
                String file = document.getSourcePath().getFileName() == null ? "" :
                        document.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
                config = TypeScriptSupport.isProjectPath(document.getSourcePath()) &&
                         ((file.startsWith("tsconfig") || file.startsWith("jsconfig")) && file.endsWith(".json"));
                Json.Document visited = config ? super.visitDocument(document, ctx) : document;
                config = previous;
                return visited;
            }

            @Override
            public Json.Array visitArray(Json.Array array, ExecutionContext ctx) {
                Json.Array visited = super.visitArray(array, ctx);
                if (!config || !isDirectCompilerLib()) return visited;
                boolean hasDom = visited.getValues().stream().filter(Json.Literal.class::isInstance)
                        .map(Json.Literal.class::cast).map(Json.Literal::getValue)
                        .filter(String.class::isInstance).map(String.class::cast)
                        .anyMatch(value -> "dom".equalsIgnoreCase(value));
                if (!hasDom) return visited;
                List<JsonRightPadded<JsonValue>> values = visited.getPadding().getValues();
                List<JsonRightPadded<JsonValue>> retained = new ArrayList<>(values.size());
                for (JsonRightPadded<JsonValue> padded : values) {
                    JsonValue value = padded.getElement();
                    if (!(value instanceof Json.Literal literal) || !(literal.getValue() instanceof String text) ||
                        !("dom.iterable".equalsIgnoreCase(text) || "dom.asynciterable".equalsIgnoreCase(text))) {
                        retained.add(padded);
                    }
                }
                return retained.size() == values.size() ? visited : visited.getPadding().withValues(retained);
            }

            private boolean isDirectCompilerLib() {
                Cursor memberCursor = getCursor().getParent();
                if (memberCursor == null || !(memberCursor.getValue() instanceof Json.Member member) ||
                    !"lib".equals(TypeScriptSupport.key(member))) return false;
                Cursor compilerObject = memberCursor.getParent();
                Cursor compilerMember = compilerObject == null ? null : compilerObject.getParent();
                Cursor rootObject = compilerMember == null ? null : compilerMember.getParent();
                Cursor document = rootObject == null ? null : rootObject.getParent();
                return compilerMember != null && compilerMember.getValue() instanceof Json.Member compiler &&
                       "compilerOptions".equals(TypeScriptSupport.key(compiler)) &&
                       document != null && document.getValue() instanceof Json.Document;
            }
        };
    }
}
