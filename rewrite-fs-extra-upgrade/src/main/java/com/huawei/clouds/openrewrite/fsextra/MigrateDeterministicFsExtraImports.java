package com.huawei.clouds.openrewrite.fsextra;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

/** Routes only provably supported named fs-extra APIs to the documented v11 ESM entry. */
public final class MigrateDeterministicFsExtraImports extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic fs-extra ESM imports";
    }

    @Override
    public String getDescription() {
        return "Changes a pure named import from fs-extra to fs-extra/esm only when every binding is a documented fs-extra-specific export.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                return FsExtraSupport.isProjectPath(cu.getSourcePath()) ? super.visitJsCompilationUnit(cu, ctx) : cu;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!FsExtraSupport.PACKAGE.equals(FsExtraSupport.moduleName(visited)) ||
                    visited.getImportClause() == null || visited.getImportClause().getName() != null ||
                    !(visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named) ||
                    named.getElements().isEmpty() || named.getElements().stream()
                            .map(FsExtraSupport::importedName).anyMatch(name -> !FsExtraSupport.EXTRA_EXPORTS.contains(name))) {
                    return visited;
                }
                J.Literal literal = (J.Literal) visited.getModuleSpecifier();
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                        ? "\"" : "'";
                return visited.withModuleSpecifier(literal.withValue(FsExtraSupport.ESM_PACKAGE)
                        .withValueSource(quote + FsExtraSupport.ESM_PACKAGE + quote));
            }
        };
    }
}
