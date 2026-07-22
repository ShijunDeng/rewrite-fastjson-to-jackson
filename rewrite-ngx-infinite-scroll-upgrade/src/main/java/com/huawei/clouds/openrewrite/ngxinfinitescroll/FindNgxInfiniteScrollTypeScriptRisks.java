package com.huawei.clouds.openrewrite.ngxinfinitescroll;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Marks exact TypeScript nodes requiring application-specific standalone or runtime decisions. */
public final class FindNgxInfiniteScrollTypeScriptRisks extends Recipe {
    private static final Set<String> RUNTIME_PROPERTIES = Set.of(
            "infiniteScrollDistance", "infiniteScrollUpDistance", "infiniteScrollThrottle",
            "infiniteScrollDisabled", "infiniteScrollContainer", "scrollWindow", "immediateCheck",
            "horizontal", "alwaysCallback", "fromRoot", "scrolled", "scrolledUp"
    );

    @Override
    public String getDisplayName() {
        return "Find ngx-infinite-scroll 17 TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks deprecated module references, private deep imports, standalone directive declarations, " +
               "inline templates, and runtime-sensitive directive properties on exact TypeScript AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> rootImports = Map.of();
            private Set<String> deprecatedAliases = Set.of();
            private Set<String> directiveInstances = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();
            private boolean usesPackage;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!NgxInfiniteScrollSourceSupport.isProjectSource(cu.getSourcePath())) return cu;
                Map<String, String> oldImports = rootImports;
                Set<String> oldDeprecated = deprecatedAliases;
                Set<String> oldInstances = directiveInstances;
                Map<String, Integer> oldDeclarations = declarationCounts;
                boolean oldUses = usesPackage;
                rootImports = new HashMap<>();
                deprecatedAliases = new HashSet<>();
                directiveInstances = new HashSet<>();
                declarationCounts = new HashMap<>();
                usesPackage = collectImports(cu, ctx);
                collectDirectiveInstances(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                rootImports = oldImports;
                deprecatedAliases = oldDeprecated;
                directiveInstances = oldInstances;
                declarationCounts = oldDeclarations;
                usesPackage = oldUses;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = NgxInfiniteScrollSourceSupport.moduleName(visited);
                if (module.startsWith(UpgradeSelectedNgxInfiniteScrollDependency.PACKAGE + "/")) {
                    return visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                            "Private ngx-infinite-scroll deep import detected; 17.0.1 exports only the root entry, so select a documented public symbol or own the internal implementation"));
                }
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                if (declaration != null && UpgradeSelectedNgxInfiniteScrollDependency.PACKAGE.equals(
                        NgxInfiniteScrollSourceSupport.moduleName(declaration)) &&
                    "InfiniteScrollModule".equals(NgxInfiniteScrollSourceSupport.importedName(visited))) {
                    return SearchResult.found(visited,
                            "InfiniteScrollModule is deprecated in 17.0.1; migrate deterministic Angular imports/exports scopes to the standalone InfiniteScrollDirective and review any wrapper API");
                }
                return visited;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (deprecatedAliases.contains(visited.getSimpleName()) &&
                    declarationCounts.getOrDefault(visited.getSimpleName(), 0) == 0 &&
                    getCursor().firstEnclosing(JS.ImportSpecifier.class) == null) {
                    return SearchResult.found(visited, "InfiniteScrollModule is deprecated in 17.0.1; this reference was not safely reducible to the standalone InfiniteScrollDirective and needs explicit scope/API review");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                if (RUNTIME_PROPERTIES.contains(visited.getSimpleName()) &&
                    visited.getTarget() instanceof J.Identifier target &&
                    directiveInstances.contains(target.getSimpleName()) &&
                    declarationCounts.getOrDefault(target.getSimpleName(), 0) == 1) {
                    return visited.withName(SearchResult.found(visited.getName(), runtimeMessage(visited.getSimpleName())));
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = NgxInfiniteScrollSourceSupport.propertyName(visited);
                if ("declarations".equals(name) && visited.getInitializer() instanceof J.NewArray array &&
                    array.getInitializer() != null) {
                    return visited.withInitializer(array.withInitializer(ListUtils.map(array.getInitializer(), expression -> {
                        if (expression instanceof J.Identifier identifier &&
                            "InfiniteScrollDirective".equals(rootImports.get(identifier.getSimpleName()))) {
                            return SearchResult.found(identifier, "InfiniteScrollDirective is standalone in 17.0.1; move this exact declaration entry to imports and verify NgModule/TestBed/lazy scope");
                        }
                        return expression;
                    })));
                }
                if (usesPackage && "template".equals(name) && visited.getInitializer() instanceof J.Literal literal &&
                    literal.getValue() instanceof String value && containsTemplateRisk(value)) {
                    return visited.withInitializer(SearchResult.found(literal,
                            "Inline ngx-infinite-scroll template detected; verify standalone scope, container/window selection, thresholds, throttling, event idempotency, SSR/hydration, Zone, and teardown"));
                }
                return visited;
            }

            private boolean collectImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean[] found = {false};
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = NgxInfiniteScrollSourceSupport.moduleName(visited);
                        if (!module.equals(UpgradeSelectedNgxInfiniteScrollDependency.PACKAGE) &&
                            !module.startsWith(UpgradeSelectedNgxInfiniteScrollDependency.PACKAGE + "/")) return visited;
                        found[0] = true;
                        if (!module.equals(UpgradeSelectedNgxInfiniteScrollDependency.PACKAGE)) return visited;
                        JS.ImportClause clause = visited.getImportClause();
                        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return visited;
                        for (JS.ImportSpecifier element : named.getElements()) {
                            String imported = NgxInfiniteScrollSourceSupport.importedName(element);
                            String local = NgxInfiniteScrollSourceSupport.localName(element);
                            if (!local.isEmpty()) rootImports.put(local, imported);
                            if ("InfiniteScrollModule".equals(imported) && !local.isEmpty()) deprecatedAliases.add(local);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                return found[0];
            }

            private void collectDirectiveInstances(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        declarationCounts.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(
                            J.VariableDeclarations declarations, ExecutionContext scanCtx) {
                        J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, scanCtx);
                        String type = NgxInfiniteScrollSourceSupport.expressionName(visited.getTypeExpression());
                        if ("InfiniteScrollDirective".equals(rootImports.get(type))) {
                            for (J.VariableDeclarations.NamedVariable variable : visited.getVariables()) {
                                if (declarationCounts.getOrDefault(variable.getSimpleName(), 0) == 1) {
                                    directiveInstances.add(variable.getSimpleName());
                                }
                            }
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }

    private static String runtimeMessage(String property) {
        if ("scrolled".equals(property) || "scrolledUp".equals(property)) {
            return property + " emits IInfiniteScrollEvent outside the scroll subscription's Angular zone boundary; verify request idempotency, leading/trailing throttle timing, OnPush/zoneless updates, teardown, and duplicate pages";
        }
        if ("infiniteScrollContainer".equals(property) || "fromRoot".equals(property) ||
            "scrollWindow".equals(property)) {
            return property + " controls window/element/query-root selection; verify CSS height/overflow, selector uniqueness, overlays/Shadow DOM, SSR/hydration timing, route reuse, and container replacement";
        }
        return property + " changes scroll thresholds, throttling, direction, setup, or disabled callback behavior; verify runtime changes, event counts, OnPush/zoneless rendering, pagination idempotency, and teardown";
    }

    private static boolean containsTemplateRisk(String value) {
        return value.matches("(?s).*\\b(?:infiniteScroll|infinite-scroll|data-infinite-scroll|scrolled|scrolledUp|" +
                             "infiniteScrollDistance|infiniteScrollUpDistance|infiniteScrollThrottle|" +
                             "infiniteScrollDisabled|infiniteScrollContainer|scrollWindow|immediateCheck|" +
                             "horizontal|alwaysCallback|fromRoot)\\b.*");
    }
}
