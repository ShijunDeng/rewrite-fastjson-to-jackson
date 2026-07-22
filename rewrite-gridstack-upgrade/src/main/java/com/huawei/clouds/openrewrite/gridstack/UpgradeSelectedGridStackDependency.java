package com.huawei.clouds.openrewrite.gridstack;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only XLSX-visible exact, caret, or tilde single GridStack declarations. */
public final class UpgradeSelectedGridStackDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected GridStack dependencies to 12.3.3";
    }

    @Override
    public String getDescription() {
        return "Changes only direct gridstack declarations anchored to an XLSX-visible source version; " +
               "complex ranges, protocols, decorated declarations, and unlisted versions remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!GridStackManifestSupport.isPackageJson(document.getSourcePath()) ||
                    !GridStackManifestSupport.PACKAGE.equals(GridStackManifestSupport.key(visited)) ||
                    GridStackManifestSupport.directSection(getCursor()).isEmpty() ||
                    !GridStackManifestSupport.selected(GridStackManifestSupport.stringValue(visited))) {
                    return visited;
                }
                Json.Literal literal = (Json.Literal) visited.getValue();
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(GridStackManifestSupport.TARGET)
                        .withSource(quote + GridStackManifestSupport.TARGET + quote));
            }
        };
    }
}
