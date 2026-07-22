package com.huawei.clouds.openrewrite.ngxcolorpicker;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Marks exact TypeScript nodes that need application-specific standalone or removed-API decisions. */
public final class FindNgxColorPickerSourceRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find ngx-color-picker 20.1.1 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks residual ColorPickerModule references, private deep imports, standalone declarations, " +
               "and legacy SliderDirective bindings on exact TypeScript AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> rootImports = Map.of();
            private Set<String> moduleAliases = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();
            private boolean usesPackage;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!NgxColorPickerSupport.isProjectSource(cu.getSourcePath())) return cu;
                Map<String, String> previousImports = rootImports;
                Set<String> previousAliases = moduleAliases;
                Map<String, Integer> previousCounts = declarationCounts;
                boolean previousUses = usesPackage;
                rootImports = new HashMap<>();
                moduleAliases = new HashSet<>();
                declarationCounts = new HashMap<>();
                usesPackage = collect(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                rootImports = previousImports;
                moduleAliases = previousAliases;
                declarationCounts = previousCounts;
                usesPackage = previousUses;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = NgxColorPickerSupport.moduleName(visited);
                if (module.startsWith(UpgradeSelectedNgxColorPickerDependency.PACKAGE + "/")) {
                    return visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                            "Private ngx-color-picker deep import detected; use an exported root symbol or explicitly own the internal implementation before upgrading"));
                }
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                if (declaration != null && UpgradeSelectedNgxColorPickerDependency.PACKAGE.equals(
                        NgxColorPickerSupport.moduleName(declaration)) &&
                    "ColorPickerModule".equals(NgxColorPickerSupport.importedName(visited))) {
                    return SearchResult.found(visited,
                            "ColorPickerModule was removed in ngx-color-picker 19; replace deterministic imports/exports scopes with standalone ColorPickerDirective and review wrapper APIs");
                }
                return visited;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (moduleAliases.contains(visited.getSimpleName()) &&
                    declarationCounts.getOrDefault(visited.getSimpleName(), 0) == 0 &&
                    getCursor().firstEnclosing(JS.ImportSpecifier.class) == null) {
                    return SearchResult.found(visited,
                            "This ColorPickerModule reference was not safely reducible to the standalone ColorPickerDirective and needs an explicit Angular scope/API decision");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                String name = NgxColorPickerSupport.propertyName(visited);
                if ("declarations".equals(name) && visited.getInitializer() instanceof J.NewArray array &&
                    array.getInitializer() != null) {
                    return visited.withInitializer(array.withInitializer(ListUtils.map(array.getInitializer(), expression -> {
                        if (expression instanceof J.Identifier identifier &&
                            isStandaloneRootImport(identifier.getSimpleName())) {
                            return SearchResult.found(identifier,
                                    rootImports.get(identifier.getSimpleName()) + " is standalone in the target; move this exact declaration entry to imports and verify NgModule/TestBed/lazy scope");
                        }
                        return expression;
                    })));
                }
                if (usesPackage && "template".equals(name) && visited.getInitializer() instanceof J.Literal literal &&
                    literal.getValue() instanceof String value && legacySliderBinding(value)) {
                    return visited.withInitializer(SearchResult.found(literal,
                            "The exported SliderDirective no longer declares a slider input; replace bound [slider]/bind-slider syntax with the bare slider selector after confirming this is ngx-color-picker's directive"));
                }
                return visited;
            }

            private boolean isStandaloneRootImport(String local) {
                String imported = rootImports.get(local);
                return "ColorPickerDirective".equals(imported) || "ColorPickerComponent".equals(imported) ||
                       "SliderDirective".equals(imported) || "TextDirective".equals(imported);
            }

            private boolean collect(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean[] found = {false};
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = NgxColorPickerSupport.moduleName(visited);
                        if (!module.equals(UpgradeSelectedNgxColorPickerDependency.PACKAGE) &&
                            !module.startsWith(UpgradeSelectedNgxColorPickerDependency.PACKAGE + "/")) return visited;
                        found[0] = true;
                        if (!module.equals(UpgradeSelectedNgxColorPickerDependency.PACKAGE)) return visited;
                        JS.ImportClause clause = visited.getImportClause();
                        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return visited;
                        for (JS.ImportSpecifier element : named.getElements()) {
                            String imported = NgxColorPickerSupport.importedName(element);
                            String local = NgxColorPickerSupport.localName(element);
                            if (!local.isEmpty()) rootImports.put(local, imported);
                            if ("ColorPickerModule".equals(imported) && !local.isEmpty()) moduleAliases.add(local);
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        declarationCounts.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
                return found[0];
            }
        };
    }

    private static boolean legacySliderBinding(String value) {
        return value.matches("(?s).*(?:\\[slider]|bind-slider)\\s*=.*");
    }
}
