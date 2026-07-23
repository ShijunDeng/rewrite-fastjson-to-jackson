package com.huawei.clouds.openrewrite.angularelements;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only the eight exact spreadsheet-selected @angular/elements version families. */
public final class UpgradeSelectedAngularElementsDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected @angular/elements dependencies to 20.3.25";
    }

    @Override
    public String getDescription() {
        return "Changes only direct Angular Elements declarations whose exact, caret, or tilde version is one of " +
               "the eight workbook-selected Angular Elements sources, preserving the declared operator.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!AngularElementsSupport.isPackageJson(document.getSourcePath()) ||
                    !AngularElementsSupport.PACKAGE.equals(AngularElementsSupport.key(visited)) ||
                    AngularElementsSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String replacement = AngularElementsSupport.replacement(AngularElementsSupport.stringValue(visited));
                if (replacement == null || !(visited.getValue() instanceof Json.Literal literal)) return visited;
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(replacement).withSource(quote + replacement + quote));
            }
        };
    }
}
