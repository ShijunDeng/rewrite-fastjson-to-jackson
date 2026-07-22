package com.huawei.clouds.openrewrite.fsextra;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only XLSX-visible direct fs-extra declarations. */
public final class UpgradeSelectedFsExtraDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected fs-extra dependencies to 11.3.4";
    }

    @Override
    public String getDescription() {
        return "Changes only exact, caret, or tilde direct fs-extra declarations anchored to an XLSX-visible source version.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                return FsExtraSupport.isPackageJson(document.getSourcePath())
                        ? super.visitDocument(document, ctx) : document;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String declaration = FsExtraSupport.stringValue(visited);
                if (!FsExtraSupport.PACKAGE.equals(FsExtraSupport.key(visited)) ||
                    FsExtraSupport.directSection(getCursor()).isEmpty() || !FsExtraSupport.selected(declaration)) {
                    return visited;
                }
                Json.Literal literal = (Json.Literal) visited.getValue();
                String replacement = FsExtraSupport.targetDeclaration(declaration);
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(replacement).withSource(quote + replacement + quote));
            }
        };
    }
}
