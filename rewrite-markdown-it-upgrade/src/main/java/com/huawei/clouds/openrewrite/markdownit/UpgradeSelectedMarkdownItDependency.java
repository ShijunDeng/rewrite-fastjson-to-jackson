package com.huawei.clouds.openrewrite.markdownit;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only direct workbook-selected markdown-it npm declarations. */
public final class UpgradeSelectedMarkdownItDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected markdown-it dependencies to 14.3.0";
    }

    @Override
    public String getDescription() {
        return "Changes direct package.json markdown-it declarations only when an exact, caret, or tilde single " +
               "version is anchored to one of the seven explicit workbook sources.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!MarkdownItSupport.isPackageJson(document.getSourcePath()) ||
                    !MarkdownItSupport.PACKAGE.equals(MarkdownItSupport.key(visited)) ||
                    MarkdownItSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String declaration = MarkdownItSupport.stringValue(visited);
                if (!MarkdownItSupport.selected(declaration) || !(visited.getValue() instanceof Json.Literal literal)) {
                    return visited;
                }
                String replacement = MarkdownItSupport.targetDeclaration(declaration);
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(replacement).withSource(quote + replacement + quote));
            }
        };
    }
}
