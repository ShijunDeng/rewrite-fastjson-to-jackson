package com.huawei.clouds.openrewrite.ng2fileupload;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.Set;

/** Move only proven named imports from old physical files to the target root export. */
public final class NormalizeNg2FileUploadPublicImports extends Recipe {
    @Override
    public String getDisplayName() {
        return "Normalize proven ng2-file-upload public imports";
    }

    @Override
    public String getDescription() {
        return "Rewrites a known physical-file import to ng2-file-upload only when every named symbol is exported by that old file and by the 10.0.0 root entry.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                return Ng2FileUploadSupport.projectPath(cu.getSourcePath()) ? super.visitJsCompilationUnit(cu, ctx) : cu;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = Ng2FileUploadSupport.moduleName(visited);
                Set<String> exports = Ng2FileUploadSupport.KNOWN_DEEP_EXPORTS.get(module);
                JS.ImportClause clause = visited.getImportClause();
                if (exports == null || clause == null || clause.getName() != null ||
                    !(clause.getNamedBindings() instanceof JS.NamedImports named) || named.getElements().isEmpty() ||
                    named.getElements().stream().anyMatch(specifier ->
                            !exports.contains(Ng2FileUploadSupport.importedName(specifier)))) return visited;
                if (!(visited.getModuleSpecifier() instanceof J.Literal literal)) return visited;
                String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"") ? "\"" : "'";
                return visited.withModuleSpecifier(literal.withValue(Ng2FileUploadSupport.PACKAGE)
                        .withValueSource(quote + Ng2FileUploadSupport.PACKAGE + quote));
            }
        };
    }
}
