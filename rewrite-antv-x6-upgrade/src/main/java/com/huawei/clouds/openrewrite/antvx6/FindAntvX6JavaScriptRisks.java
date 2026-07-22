package com.huawei.clouds.openrewrite.antvx6;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Marks source and executable-config contracts that are not safe to infer automatically. */
public final class FindAntvX6JavaScriptRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find AntV X6 3 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unsafe consolidated imports, internal paths, default panning, removed transition, React Provider, dynamic loading, and config aliases.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> graphTypes = Set.of();
            private Set<String> portalTypes = Set.of();
            private Set<String> graphVariables = Set.of();
            private Set<String> cellVariables = Set.of();
            private boolean x6File;
            private boolean config;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!AntvX6Support.isProjectPath(cu.getSourcePath())) return cu;
                Set<String> oldGraphs = graphTypes;
                Set<String> oldPortals = portalTypes;
                Set<String> oldGraphVariables = graphVariables;
                Set<String> oldCellVariables = cellVariables;
                boolean oldX6 = x6File;
                boolean oldConfig = config;
                graphTypes = new HashSet<>();
                portalTypes = new HashSet<>();
                graphVariables = new HashSet<>();
                cellVariables = new HashSet<>();
                x6File = false;
                config = AntvX6Support.isConfig(cu.getSourcePath());
                scanImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                graphTypes = oldGraphs;
                portalTypes = oldPortals;
                graphVariables = oldGraphVariables;
                cellVariables = oldCellVariables;
                x6File = oldX6;
                config = oldConfig;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = AntvX6Support.moduleName(visited);
                if (AntvX6Support.internalPath(module)) {
                    return SearchResult.found(visited,
                            "This import couples the application to X6 lib/es/src internals; use the public @antv/x6 API and verify the exported symbol and bundle format");
                }
                if (AntvX6Support.CONSOLIDATED.contains(module) && !AntvX6Support.namedOnly(visited)) {
                    return SearchResult.found(visited,
                            "Only named imports have a documented direct 3.x mapping; manually replace this default, namespace, or side-effect import and remove the old package");
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                Expression initializer = visited.getInitializer();
                if (initializer instanceof J.NewClass created && isGraphType(created.getClazz())) {
                    graphVariables.add(visited.getSimpleName());
                }
                if (initializer instanceof J.MethodInvocation call &&
                    Set.of("addNode", "addEdge", "createNode", "createEdge").contains(call.getSimpleName()) &&
                    isGraph(call.getSelect())) {
                    cellVariables.add(visited.getSimpleName());
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (isGraphType(visited.getClazz()) && !hasExplicitPanning(visited)) {
                    return SearchResult.found(visited,
                            "X6 3 enables Graph panning by default; set panning:false to preserve disabled behavior or explicitly test pan/Selection/Scroller pointer interactions");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if ("getProvider".equals(visited.getSimpleName()) && isPortal(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "X6 React Shape 3 replaces Portal.getProvider() with the named getProvider() export; update the import and call, then verify React root, HMR, SSR, and cleanup");
                }
                if ("transition".equals(visited.getSimpleName()) && isCell(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "X6 3 removes the 2.x transition API in favor of animate; translate timing, easing, callbacks, cancellation, and concurrent animations with behavior tests");
                }
                if (("require".equals(visited.getSimpleName()) || "import".equals(visited.getSimpleName())) &&
                    visited.getArguments().stream().anyMatch(this::x6Literal)) {
                    return SearchResult.found(visited,
                            "Dynamic X6 loading is not rewritten because export shape and runtime resolution are contextual; migrate to @antv/x6 and test chunking, ESM/CJS, SSR, and failures");
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (config && visited.getValue() instanceof String value &&
                    (AntvX6Support.internalReference(value) || AntvX6Support.CONSOLIDATED.contains(value))) {
                    return SearchResult.found(visited,
                            "This executable-config alias targets an X6 2 package or internal directory; resolve from public @antv/x6 and verify bundler and test-runner behavior");
                }
                return visited;
            }

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = AntvX6Support.moduleName(visited);
                        if (AntvX6Support.PACKAGE.equals(module)) {
                            x6File = true;
                            collectNamed(visited, "Graph", graphTypes);
                        }
                        if ("@antv/x6-react-shape".equals(module)) collectNamed(visited, "Portal", portalTypes);
                        if (AntvX6Support.CONSOLIDATED.contains(module) || AntvX6Support.internalPath(module)) x6File = true;
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private void collectNamed(JS.Import declaration, String imported, Set<String> aliases) {
                JS.ImportClause clause = declaration.getImportClause();
                if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return;
                for (JS.ImportSpecifier specifier : named.getElements()) {
                    Expression expression = specifier.getSpecifier();
                    if (expression instanceof J.Identifier identifier && imported.equals(identifier.getSimpleName())) {
                        aliases.add(identifier.getSimpleName());
                    } else if (expression instanceof JS.Alias alias &&
                               imported.equals(alias.getPropertyName().getSimpleName()) &&
                               alias.getAlias() instanceof J.Identifier identifier) {
                        aliases.add(identifier.getSimpleName());
                    }
                }
            }

            private boolean isGraphType(TypeTree expression) {
                return expression instanceof J.Identifier identifier && graphTypes.contains(identifier.getSimpleName());
            }

            private boolean isGraph(Expression expression) {
                return expression instanceof J.Identifier identifier && graphVariables.contains(identifier.getSimpleName());
            }

            private boolean isCell(Expression expression) {
                return expression instanceof J.Identifier identifier && cellVariables.contains(identifier.getSimpleName());
            }

            private boolean isPortal(Expression expression) {
                return expression instanceof J.Identifier identifier && portalTypes.contains(identifier.getSimpleName());
            }

            private boolean x6Literal(Expression expression) {
                return expression instanceof J.Literal literal && literal.getValue() instanceof String value &&
                       (AntvX6Support.PACKAGE.equals(value) || AntvX6Support.CONSOLIDATED.contains(value) ||
                        AntvX6Support.internalPath(value));
            }

            private boolean hasExplicitPanning(J.NewClass graph) {
                for (Expression argument : graph.getArguments()) {
                    if (!(argument instanceof J.NewClass object) || object.getClazz() != null || object.getBody() == null) continue;
                    for (J statement : object.getBody().getStatements()) {
                        if (statement instanceof JS.PropertyAssignment property &&
                            property.getName() instanceof J.Identifier identifier &&
                            "panning".equals(identifier.getSimpleName())) return true;
                        if (statement instanceof JS.PropertyAssignment property &&
                            property.getName() instanceof J.Literal literal && "panning".equals(literal.getValue())) return true;
                    }
                }
                return false;
            }
        };
    }
}
