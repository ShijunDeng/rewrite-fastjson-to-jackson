package com.huawei.clouds.openrewrite.typescript;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;

/** Upgrades only the nine TypeScript source releases visible in the spreadsheet. */
public final class UpgradeSelectedTypeScriptDependency extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade spreadsheet-selected TypeScript declarations to 6.0.3";
    }

    @Override
    public String getDescription() {
        return "Upgrade exact, caret, and tilde TypeScript declarations from the nine visible spreadsheet versions in direct package.json dependency sections, preserving the npm range operator.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                if (!TypeScriptSupport.isProjectPath(document.getSourcePath()) ||
                    document.getSourcePath().getFileName() == null ||
                    !"package.json".equals(document.getSourcePath().getFileName().toString()) ||
                    !TypeScriptSupport.PACKAGE.equals(TypeScriptSupport.key(visited)) ||
                    !TypeScriptSupport.DEPENDENCY_SECTIONS.contains(TypeScriptSupport.directSection(getCursor()))) {
                    return visited;
                }
                String declaration = TypeScriptSupport.literalString(visited);
                return declaration != null && TypeScriptSupport.isSelected(declaration)
                        ? TypeScriptSupport.replaceString(visited, TypeScriptSupport.upgradeDeclaration(declaration)) : visited;
            }
        };
    }
}
