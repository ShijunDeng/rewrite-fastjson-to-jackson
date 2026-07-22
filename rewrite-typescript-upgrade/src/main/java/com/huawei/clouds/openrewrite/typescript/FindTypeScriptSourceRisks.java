package com.huawei.clouds.openrewrite.typescript;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.regex.Pattern;

/** Marks compiler API and directive behavior that requires source-owner review. */
public final class FindTypeScriptSourceRisks extends Recipe {
    private static final Pattern NO_DEFAULT_LIB = Pattern.compile(
            "(?m)^\\s*///\\s*<reference\\s+no-default-lib\\s*=\\s*(['\"])true\\1[^>]*>");
    private static final Pattern AMD_DIRECTIVE = Pattern.compile(
            "(?m)^\\s*///\\s*<amd-(?:module|dependency)\\b[^>]*>");

    @Override
    public String getDisplayName() { return "Find TypeScript 6 source and compiler API risks"; }

    @Override
    public String getDescription() {
        return "Mark direct TypeScript compiler/language-service imports, require calls, removed no-default-lib directives, and AMD directives at exact project source nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!TypeScriptSupport.isProjectPath(cu.getSourcePath())) return cu;
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                String source = visited.printAll();
                if (NO_DEFAULT_LIB.matcher(source).find()) {
                    return TypeScriptSupport.mark(visited,
                            "TypeScript 6 no longer supports the no-default-lib reference directive; choose noLib/libReplacement and verify every global declaration source");
                }
                if (AMD_DIRECTIVE.matcher(source).find()) {
                    return TypeScriptSupport.mark(visited,
                            "AMD directives accompany a TypeScript 6-deprecated module format; move module naming/loading to the selected runtime or bundler");
                }
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                if (isTypeScriptModule(visited.getModuleSpecifier()) || isTypeScriptModule(visited.getInitializer())) {
                    return TypeScriptSupport.mark(visited,
                            "This source loads TypeScript compiler/language-service APIs across a large version jump; verify factory, transform, printer, host, diagnostics, and plugin contracts against 6.0.3");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                if ("require".equals(visited.getSimpleName()) && visited.getArguments().size() == 1 &&
                    isTypeScriptModule(visited.getArguments().get(0))) {
                    return TypeScriptSupport.mark(visited,
                            "This require call loads TypeScript compiler APIs; verify the complete compiler/language-service integration against 6.0.3");
                }
                return visited;
            }
        };
    }

    private static boolean isTypeScriptModule(org.openrewrite.java.tree.Expression expression) {
        return expression instanceof J.Literal literal && literal.getValue() instanceof String module &&
               (TypeScriptSupport.PACKAGE.equals(module) || module.startsWith(TypeScriptSupport.PACKAGE + "/"));
    }
}
