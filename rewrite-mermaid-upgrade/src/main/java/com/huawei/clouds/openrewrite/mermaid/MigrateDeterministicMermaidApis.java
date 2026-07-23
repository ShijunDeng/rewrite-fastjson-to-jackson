package com.huawei.clouds.openrewrite.mermaid;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Set;

/** Replaces removed v9 async aliases only when the v11 async API is signature-equivalent. */
public final class MigrateDeterministicMermaidApis extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Mermaid 11 APIs";
    }

    @Override
    public String getDescription() {
        return "Renames imported Mermaid parseAsync(text) and renderAsync(id, text) calls to the Promise-based " +
               "parse and render APIs; callback/container overloads remain unchanged for explicit review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> aliases = Set.of();
            private Set<String> declared = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!MermaidSupport.isSource(cu.getSourcePath())) return cu;
                Set<String> oldAliases = aliases;
                Set<String> oldDeclared = declared;
                aliases = new HashSet<>();
                declared = declarations(cu);
                scanImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                aliases = oldAliases;
                declared = oldDeclared;
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!MermaidJavaScriptSupport.isOwnedSelect(visited.getSelect(), aliases)) return visited;
                if ("parseAsync".equals(visited.getSimpleName()) && visited.getArguments().size() == 1) {
                    return visited.withName(visited.getName().withSimpleName("parse"));
                }
                if ("renderAsync".equals(visited.getSimpleName()) && visited.getArguments().size() == 2) {
                    return visited.withName(visited.getName().withSimpleName("render"));
                }
                return visited;
            }

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ignored) {
                        if (MermaidSupport.PACKAGE.equals(MermaidJavaScriptSupport.moduleName(declaration)) &&
                            declaration.getImportClause() != null && declaration.getImportClause().getName() != null) {
                            String alias = declaration.getImportClause().getName().getSimpleName();
                            if (!declared.contains(alias)) aliases.add(alias);
                        }
                        return declaration;
                    }
                }.visit(cu, ctx);
            }

            private Set<String> declarations(JS.CompilationUnit cu) {
                Set<String> names = new HashSet<>();
                new JavaScriptIsoVisitor<Set<String>>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, Set<String> accumulator) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, accumulator);
                        accumulator.add(visited.getSimpleName());
                        return visited;
                    }
                }.visit(cu, names);
                return names;
            }
        };
    }
}
