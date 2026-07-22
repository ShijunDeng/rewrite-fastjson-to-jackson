package com.huawei.clouds.openrewrite.ngdynamicforms;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.JavaScriptVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Removes the obsolete no-argument core module forRoot wrapper using its official replacement. */
public final class MigrateDeterministicNgDynamicFormsSource extends Recipe {
    @Override
    public String getDisplayName() {
        return "Remove obsolete DynamicFormsCoreModule.forRoot() calls";
    }

    @Override
    public String getDescription() {
        return "Replaces an import-resolved, no-argument DynamicFormsCoreModule.forRoot() expression with the module " +
               "identifier; NG Dynamic Forms removed this wrapper after adopting tree-shakeable root providers.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptVisitor<ExecutionContext>() {
            private Set<String> moduleAliases = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();

            @Override
            public J visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> oldAliases = moduleAliases;
                Map<String, Integer> oldDeclarations = declarationCounts;
                moduleAliases = new HashSet<>();
                collect(cu, ctx);
                declarationCounts = NgDynamicFormsSourceSupport.declarations(cu, ctx).variables();
                J visited = super.visitJsCompilationUnit(cu, ctx);
                moduleAliases = oldAliases;
                declarationCounts = oldDeclarations;
                return visited;
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J visited = super.visitMethodInvocation(invocation, ctx);
                if (!(visited instanceof J.MethodInvocation call) || !"forRoot".equals(call.getSimpleName()) ||
                    !(call.getSelect() instanceof J.Identifier identifier) ||
                    !moduleAliases.contains(identifier.getSimpleName()) ||
                    declarationCounts.getOrDefault(identifier.getSimpleName(), 0) != 0 ||
                    !noArguments(call)) return visited;
                return identifier.withPrefix(call.getPrefix());
            }

            private void collect(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (UpgradeSelectedNgDynamicFormsCoreDependency.PACKAGE.equals(
                                NgDynamicFormsSourceSupport.moduleName(visited))) {
                            String alias = NgDynamicFormsSourceSupport.importedAlias(visited, "DynamicFormsCoreModule");
                            if (alias != null) moduleAliases.add(alias);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }

    private static boolean noArguments(J.MethodInvocation call) {
        return call.getArguments().isEmpty() ||
               call.getArguments().size() == 1 && call.getArguments().get(0) instanceof J.Empty;
    }
}
