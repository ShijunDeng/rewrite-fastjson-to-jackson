package com.huawei.clouds.openrewrite.ng2fileupload;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrade only direct declarations selected by the workbook. */
public final class UpgradeSelectedNg2FileUploadDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade selected ng2-file-upload dependencies to 10.0.0";
    }

    @Override
    public String getDescription() {
        return "Changes direct exact, caret, or tilde ng2-file-upload declarations only at workbook versions 2.0.0-3, 3.0.0, or 4.0.0.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                String declaration = Ng2FileUploadSupport.string(visited);
                if (!Ng2FileUploadSupport.packageJson(document.getSourcePath()) ||
                    !Ng2FileUploadSupport.PACKAGE.equals(Ng2FileUploadSupport.key(visited)) ||
                    !Ng2FileUploadSupport.SECTIONS.contains(Ng2FileUploadSupport.directSection(getCursor())) ||
                    !Ng2FileUploadSupport.selected(declaration)) return visited;
                return Ng2FileUploadSupport.replaceString(
                        visited, Ng2FileUploadSupport.targetDeclaration(declaration));
            }
        };
    }
}
