package com.huawei.clouds.openrewrite.typescript;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;

/** Applies syntax migrations explicitly defined as equivalent by TypeScript 6. */
public final class MigrateTypeScriptSource extends Recipe {
    @Override
    public String getDisplayName() { return "Migrate deterministic TypeScript 6 source syntax"; }

    @Override
    public String getDescription() {
        return "Replace non-ambient legacy internal module declarations with namespace and import assertion tokens with import attributes, excluding generated trees.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                return TypeScriptSupport.isProjectPath(cu.getSourcePath()) ? super.visitJsCompilationUnit(cu, ctx) : cu;
            }

            @Override
            public JS.NamespaceDeclaration visitNamespaceDeclaration(JS.NamespaceDeclaration namespace, ExecutionContext ctx) {
                JS.NamespaceDeclaration visited = super.visitNamespaceDeclaration(namespace, ctx);
                return visited.getKeywordType() == JS.NamespaceDeclaration.KeywordType.Module &&
                       !isQuotedExternalModule(visited)
                        // A fresh identity makes the RPC printer serialize this enum-only token replacement.
                        ? visited.withKeywordType(JS.NamespaceDeclaration.KeywordType.Namespace)
                                 .withId(Tree.randomId()) : visited;
            }

            @Override
            public JS.ImportAttributes visitImportAttributes(JS.ImportAttributes attributes, ExecutionContext ctx) {
                JS.ImportAttributes visited = super.visitImportAttributes(attributes, ctx);
                return visited.getToken() == JS.ImportAttributes.Token.Assert
                        ? visited.withToken(JS.ImportAttributes.Token.With) : visited;
            }

            private boolean isQuotedExternalModule(JS.NamespaceDeclaration namespace) {
                if (!(namespace.getName() instanceof J.Literal literal)) return false;
                String source = literal.getValueSource();
                return source != null && source.length() >= 2 &&
                       ((source.charAt(0) == '\'' && source.charAt(source.length() - 1) == '\'') ||
                        (source.charAt(0) == '"' && source.charAt(source.length() - 1) == '"'));
            }
        };
    }
}
