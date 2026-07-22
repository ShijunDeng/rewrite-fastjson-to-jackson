package com.huawei.clouds.openrewrite.gridstack;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Applies only source migrations explicitly documented as equivalent by GridStack. */
public final class MigrateDeterministicGridStackSource extends Recipe {
    private static final Set<String> REMOVED_SIDE_EFFECT_IMPORTS = Set.of(
            "gridstack/dist/h5/gridstack-dd-native", "gridstack/dist/h5/gridstack-dd-native.js",
            "gridstack/dist/jq/gridstack-dd-jqueryui", "gridstack/dist/jq/gridstack-dd-jqueryui.js",
            "gridstack/dist/gridstack-extra.css", "gridstack/dist/gridstack-extra.min.css");

    @Override
    public String getDisplayName() {
        return "Migrate deterministic GridStack 12 source constructs";
    }

    @Override
    public String getDescription() {
        return "Normalizes the public root import, removes obsolete standalone D&D/extra-CSS imports, renames " +
               "subGrid and onParentResize, and removes disableOneColumnMode:true only where GridStack ownership is proven.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean gridStackFile;
            private Set<String> classAliases = Set.of();
            private Set<String> optionAliases = Set.of();
            private Set<String> gridVariables = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();
            private Set<String> declaredTypes = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean oldFile = gridStackFile;
                Set<String> oldClasses = classAliases;
                Set<String> oldOptions = optionAliases;
                Set<String> oldVariables = gridVariables;
                Map<String, Integer> oldDeclarations = declarationCounts;
                Set<String> oldTypes = declaredTypes;
                gridStackFile = false;
                classAliases = new HashSet<>();
                optionAliases = new HashSet<>();
                gridVariables = new HashSet<>();
                scanImports(cu, ctx);
                GridStackJavaScriptSupport.DeclarationInventory declarations =
                        GridStackJavaScriptSupport.declarations(cu, ctx);
                declarationCounts = declarations.variables();
                declaredTypes = declarations.types();
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                gridStackFile = oldFile;
                classAliases = oldClasses;
                optionAliases = oldOptions;
                gridVariables = oldVariables;
                declarationCounts = oldDeclarations;
                declaredTypes = oldTypes;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = GridStackJavaScriptSupport.moduleName(visited);
                if (REMOVED_SIDE_EFFECT_IMPORTS.contains(module) && visited.getImportClause() == null) {
                    return null;
                }
                if ("gridstack/dist/gridstack".equals(module) ||
                    "gridstack/dist/gridstack.js".equals(module)) {
                    J.Literal literal = (J.Literal) visited.getModuleSpecifier();
                    String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                            ? "\"" : "'";
                    return visited.withModuleSpecifier(literal.withValue("gridstack")
                            .withValueSource(quote + "gridstack" + quote));
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                J.VariableDeclarations declarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
                if (createsGrid(visited.getInitializer()) ||
                    declarations != null && isUnshadowedClassType(
                            GridStackJavaScriptSupport.typeName(declarations.getTypeExpression()))) {
                    gridVariables.add(visited.getSimpleName());
                }
                return visited;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment visited = super.visitAssignment(assignment, ctx);
                if (createsGrid(visited.getAssignment()) && visited.getVariable() instanceof J.FieldAccess field) {
                    gridVariables.add(field.getSimpleName());
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (gridStackFile && "onParentResize".equals(visited.getSimpleName()) &&
                    isGrid(visited.getSelect())) {
                    return visited.withName(visited.getName().withSimpleName("onResize"));
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!gridStackFile || !isOwnedOptions(property)) {
                    return visited;
                }
                String name = GridStackJavaScriptSupport.propertyName(visited.getName());
                if ("subGrid".equals(name) && !hasSibling(property, "subGridOpts")) {
                    return visited.withName(renameProperty(visited.getName(), "subGridOpts"));
                }
                if ("disableOneColumnMode".equals(name) &&
                    visited.getInitializer() instanceof J.Literal literal && Boolean.TRUE.equals(literal.getValue()) &&
                    !hasSibling(property, "disableOneColumnMode")) {
                    return null;
                }
                return visited;
            }

            private Expression renameProperty(Expression name, String replacement) {
                if (name instanceof J.Identifier identifier) {
                    return identifier.withSimpleName(replacement);
                }
                if (name instanceof J.Literal literal) {
                    String quote = literal.getValueSource() != null && literal.getValueSource().startsWith("\"")
                            ? "\"" : "'";
                    return literal.withValue(replacement).withValueSource(quote + replacement + quote);
                }
                return name;
            }

            private boolean isOwnedOptions(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(property.getId())) ||
                    object.getBody().getStatements().stream().anyMatch(JS.Spread.class::isInstance)) {
                    return false;
                }
                J.VariableDeclarations declarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
                if (declarations != null &&
                    isUnshadowedOptionType(GridStackJavaScriptSupport.typeName(declarations.getTypeExpression())) &&
                    declarations.getVariables().stream().anyMatch(variable -> variable.getInitializer() != null &&
                            variable.getInitializer().getId().equals(object.getId()))) {
                    return true;
                }
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.MethodInvocation call &&
                        call.getArguments().stream().anyMatch(argument -> argument.getId().equals(object.getId()))) {
                        String name = call.getSimpleName();
                        return (Set.of("init", "initAll", "addGrid").contains(name) && isClass(call.getSelect())) ||
                               (Set.of("updateOptions", "makeSubGrid", "addWidget").contains(name) &&
                                isGrid(call.getSelect()));
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            private boolean createsGrid(Expression expression) {
                return expression instanceof J.MethodInvocation call &&
                       Set.of("init", "addGrid").contains(call.getSimpleName()) && isClass(call.getSelect());
            }

            private boolean isClass(Expression expression) {
                return expression instanceof J.Identifier identifier &&
                       classAliases.contains(identifier.getSimpleName()) &&
                       declarationCounts.getOrDefault(identifier.getSimpleName(), 0) == 0;
            }

            private boolean isGrid(Expression expression) {
                if (expression instanceof J.Identifier identifier) {
                    return gridVariables.contains(identifier.getSimpleName()) &&
                           declarationCounts.getOrDefault(identifier.getSimpleName(), 0) <= 1;
                }
                if (expression instanceof J.FieldAccess field) {
                    return gridVariables.contains(field.getSimpleName()) &&
                           declarationCounts.getOrDefault(field.getSimpleName(), 0) <= 1;
                }
                return createsGrid(expression);
            }

            private boolean isUnshadowedOptionType(String name) {
                return optionAliases.contains(name) && !declaredTypes.contains(name);
            }

            private boolean isUnshadowedClassType(String name) {
                return classAliases.contains(name) && !declaredTypes.contains(name);
            }

            private boolean hasSibling(JS.PropertyAssignment property, String name) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                return object != null && object.getBody() != null && object.getBody().getStatements().stream()
                        .filter(statement -> !statement.getId().equals(property.getId()))
                        .filter(JS.PropertyAssignment.class::isInstance)
                        .map(JS.PropertyAssignment.class::cast)
                        .anyMatch(sibling -> name.equals(
                                GridStackJavaScriptSupport.propertyName(sibling.getName())));
            }

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = GridStackJavaScriptSupport.moduleName(visited);
                        if ("gridstack".equals(module) || "gridstack/dist/gridstack".equals(module) ||
                            "gridstack/dist/gridstack.js".equals(module)) {
                            gridStackFile = true;
                            GridStackJavaScriptSupport.collectNamed(visited, "GridStack", classAliases);
                            GridStackJavaScriptSupport.collectNamed(visited, "GridStackOptions", optionAliases);
                        } else if (module.startsWith("gridstack/")) {
                            gridStackFile = true;
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
