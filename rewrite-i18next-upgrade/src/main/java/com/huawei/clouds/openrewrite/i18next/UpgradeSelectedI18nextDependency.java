package com.huawei.clouds.openrewrite.i18next;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only XLSX-visible exact, caret, or tilde single i18next declarations. */
public final class UpgradeSelectedI18nextDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected i18next dependencies to 25.10.10";
    }

    @Override
    public String getDescription() {
        return "Changes only direct i18next declarations anchored to an XLSX-visible source version; " +
               "complex ranges, protocols, decorated declarations, and unlisted versions remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!I18nextManifestSupport.isPackageJson(document.getSourcePath()) ||
                    !I18nextManifestSupport.PACKAGE.equals(I18nextManifestSupport.key(visited)) ||
                    I18nextManifestSupport.directSection(getCursor()).isEmpty() ||
                    I18nextManifestSupport.selectedVersion(I18nextManifestSupport.stringValue(visited)) == null) {
                    return visited;
                }
                Json.Literal literal = (Json.Literal) visited.getValue();
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(I18nextManifestSupport.TARGET)
                        .withSource(quote + I18nextManifestSupport.TARGET + quote));
            }
        };
    }
}
