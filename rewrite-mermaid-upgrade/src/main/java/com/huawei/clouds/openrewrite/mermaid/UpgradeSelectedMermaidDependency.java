package com.huawei.clouds.openrewrite.mermaid;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only the four exact spreadsheet-selected Mermaid version families. */
public final class UpgradeSelectedMermaidDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected Mermaid dependencies to 11.15.0";
    }

    @Override
    public String getDescription() {
        return "Changes only direct mermaid declarations whose exact, caret, or tilde version is one of " +
               "9.1.1, 9.1.3, 9.1.6, or 9.4.3, preserving the declared operator.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!MermaidSupport.isPackageJson(document.getSourcePath()) ||
                    !MermaidSupport.PACKAGE.equals(MermaidSupport.key(visited)) ||
                    MermaidSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String replacement = MermaidSupport.replacement(MermaidSupport.stringValue(visited));
                if (replacement == null || !(visited.getValue() instanceof Json.Literal literal)) return visited;
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(replacement).withSource(quote + replacement + quote));
            }
        };
    }
}
