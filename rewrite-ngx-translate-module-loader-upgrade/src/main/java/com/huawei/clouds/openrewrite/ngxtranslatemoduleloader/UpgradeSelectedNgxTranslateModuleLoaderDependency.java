package com.huawei.clouds.openrewrite.ngxtranslatemoduleloader;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only direct workbook-selected package.json declarations. */
public final class UpgradeSelectedNgxTranslateModuleLoaderDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected ngx-translate-module-loader dependencies to 5.1.0";
    }

    @Override
    public String getDescription() {
        return "Change direct @larscom/ngx-translate-module-loader declarations only when an exact, caret, or " +
               "tilde single version is anchored to workbook source 3.1.1 or 3.1.2.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!NgxTranslateModuleLoaderSupport.isPackageJson(document.getSourcePath()) ||
                    !NgxTranslateModuleLoaderSupport.PACKAGE.equals(NgxTranslateModuleLoaderSupport.key(visited)) ||
                    NgxTranslateModuleLoaderSupport.directDependencySection(getCursor()).isEmpty()) return visited;
                String declaration = NgxTranslateModuleLoaderSupport.stringValue(visited);
                return NgxTranslateModuleLoaderSupport.selected(declaration)
                        ? NgxTranslateModuleLoaderSupport.replaceStringValue(
                                visited, NgxTranslateModuleLoaderSupport.targetDeclaration(declaration)) : visited;
            }
        };
    }
}
