package com.huawei.clouds.openrewrite.i18next;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

/** Marks TypeScript compiler options that violate the i18next 23+ type baseline. */
public final class FindI18nextJsonConfigRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find i18next TypeScript configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks explicit strict:false or strictNullChecks:false compiler options in tsconfig variants; " +
               "the recipe does not enable them because resulting application errors need owner decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean tsconfig;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                String file = document.getSourcePath().getFileName() == null ? "" :
                        document.getSourcePath().getFileName().toString();
                boolean previous = tsconfig;
                tsconfig = file.startsWith("tsconfig") && file.endsWith(".json");
                Json.Document visited = tsconfig ? super.visitDocument(document, ctx) : document;
                tsconfig = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String key = I18nextManifestSupport.key(visited);
                if (tsconfig && ("strict".equals(key) || "strictNullChecks".equals(key)) &&
                    visited.getValue() instanceof Json.Literal literal && Boolean.FALSE.equals(literal.getValue()) &&
                    "compilerOptions".equals(parentKey())) {
                    return visited.withValue(SearchResult.found(visited.getValue(),
                            "i18next 23+ types require strict or at least strictNullChecks with TypeScript 5; enable it and resolve every application/declaration error"));
                }
                return visited;
            }

            private String parentKey() {
                org.openrewrite.Cursor object = getCursor().getParent();
                org.openrewrite.Cursor parent = object == null ? null : object.getParent();
                return parent != null && parent.getValue() instanceof Json.Member member
                        ? I18nextManifestSupport.key(member) : "";
            }
        };
    }
}
