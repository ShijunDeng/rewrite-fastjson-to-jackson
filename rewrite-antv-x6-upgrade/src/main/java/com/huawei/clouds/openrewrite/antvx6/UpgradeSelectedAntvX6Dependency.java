package com.huawei.clouds.openrewrite.antvx6;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only XLSX-visible direct @antv/x6 declarations. */
public final class UpgradeSelectedAntvX6Dependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected AntV X6 dependencies to 3.1.7";
    }

    @Override
    public String getDescription() {
        return "Changes only exact, caret, or tilde direct @antv/x6 declarations anchored to an XLSX-visible source version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                return AntvX6Support.isPackageJson(document.getSourcePath())
                        ? super.visitDocument(document, ctx) : document;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String declaration = AntvX6Support.stringValue(visited);
                if (!AntvX6Support.PACKAGE.equals(AntvX6Support.key(visited)) ||
                    AntvX6Support.directSection(getCursor()).isEmpty() || !AntvX6Support.selected(declaration)) {
                    return visited;
                }
                Json.Literal literal = (Json.Literal) visited.getValue();
                String replacement = AntvX6Support.targetDeclaration(declaration);
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(replacement).withSource(quote + replacement + quote));
            }
        };
    }
}
