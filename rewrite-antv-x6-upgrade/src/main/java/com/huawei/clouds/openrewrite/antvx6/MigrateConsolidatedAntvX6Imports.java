package com.huawei.clouds.openrewrite.antvx6;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

/** Applies the import-path equivalence documented by the X6 3.x upgrade guide. */
public final class MigrateConsolidatedAntvX6Imports extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate consolidated AntV X6 3 imports";
    }

    @Override
    public String getDescription() {
        return "Moves named imports from the documented X6 2 plugin, common, and geometry packages to @antv/x6.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                return AntvX6Support.isProjectPath(cu.getSourcePath())
                        ? super.visitJsCompilationUnit(cu, ctx) : cu;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (!AntvX6Support.CONSOLIDATED.contains(AntvX6Support.moduleName(visited)) ||
                    !AntvX6Support.namedOnly(visited) || !(visited.getModuleSpecifier() instanceof J.Literal literal)) {
                    return visited;
                }
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                        ? "\"" : "'";
                return visited.withModuleSpecifier(literal.withValue(AntvX6Support.PACKAGE)
                        .withValueSource(quote + AntvX6Support.PACKAGE + quote));
            }
        };
    }
}
