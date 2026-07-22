package com.huawei.clouds.openrewrite.vuei18n;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only XLSX-visible exact, caret, or tilde single Vue I18n declarations. */
public final class UpgradeSelectedVueI18nDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected Vue I18n dependencies to 11.3.0";
    }

    @Override
    public String getDescription() {
        return "Changes only direct vue-i18n declarations anchored to an XLSX-visible source version; " +
               "complex ranges, protocols, decorated declarations, and unlisted versions remain unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!VueI18nManifestSupport.isPackageJson(document.getSourcePath()) ||
                    !VueI18nManifestSupport.PACKAGE.equals(VueI18nManifestSupport.key(visited)) ||
                    VueI18nManifestSupport.directSection(getCursor()).isEmpty() ||
                    VueI18nManifestSupport.selectedVersion(VueI18nManifestSupport.stringValue(visited)) == null) {
                    return visited;
                }
                Json.Literal literal = (Json.Literal) visited.getValue();
                String quote = literal.getSource().startsWith("'") ? "'" : "\"";
                return visited.withValue(literal.withValue(VueI18nManifestSupport.TARGET)
                        .withSource(quote + VueI18nManifestSupport.TARGET + quote));
            }
        };
    }
}
