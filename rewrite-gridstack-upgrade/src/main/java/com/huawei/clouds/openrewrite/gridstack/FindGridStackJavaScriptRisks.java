package com.huawei.clouds.openrewrite.gridstack;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Marks exact GridStack runtime, rendering, D&D, serialization, nested-grid, and SSR boundaries. */
public final class FindGridStackJavaScriptRisks extends Recipe {
    private static final Set<String> INTERNAL_EXPORTS = Set.of(
            "DDGridStack", "DDElement", "DDManager", "GridStackEngine", "Utils");
    private static final Set<String> REMOVED_METHODS = Set.of(
            "locked", "maxWidth", "minWidth", "maxHeight", "minHeight", "move", "resize",
            "getGridHeight", "verticalMargin");

    @Override
    public String getDisplayName() {
        return "Find GridStack 12 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks exact initialization, widget mutation, event, serialization, drag/drop, nested-grid, " +
               "internal API, rendering callback, and browser-global boundaries that require application decisions.";
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
                if (module.startsWith("gridstack/") &&
                    !Set.of("gridstack/dist/gridstack.css", "gridstack/dist/gridstack.min.css").contains(module)) {
                    return SearchResult.found(visited,
                            "Deep GridStack distribution or implementation imports changed across v6-v12; use the public gridstack entry point and separately include only the target base CSS");
                }
                return visited;
            }

            @Override
            public JS.ImportSpecifier visitImportSpecifier(JS.ImportSpecifier specifier, ExecutionContext ctx) {
                JS.ImportSpecifier visited = super.visitImportSpecifier(specifier, ctx);
                JS.Import declaration = getCursor().firstEnclosing(JS.Import.class);
                String imported = GridStackJavaScriptSupport.importedName(visited);
                if (declaration != null && "gridstack".equals(GridStackJavaScriptSupport.moduleName(declaration)) &&
                    INTERNAL_EXPORTS.contains(imported)) {
                    return SearchResult.found(visited,
                            imported + " exposes GridStack engine or drag/drop internals; verify target declarations, subclass contracts, private fields, pointer/touch behavior, and cleanup instead of patching internals");
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
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!gridStackFile) {
                    return visited;
                }
                String name = visited.getSimpleName();
                if (Set.of("init", "initAll", "addGrid").contains(name) && isClass(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "GridStack initialization crosses ES2020/CSS-variable, responsive-column, existing-DOM, nested-grid, framework lifecycle, and SSR boundaries; verify after mount and destroy on unmount");
                }
                if ("setupDragIn".equals(name) && isClass(visited.getSelect())) {
                    return SearchResult.found(visited,
                            "GridStack 11 rewrote side-panel drag-in helper cloning and widget association; verify pointer/touch, helper DOM, accept/removable rules, nested targets, and framework ownership");
                }
                if (!isGrid(visited.getSelect())) {
                    return visited;
                }
                if ("addWidget".equals(name)) {
                    return SearchResult.found(visited,
                            "GridStack 11 accepts only a GridStackWidget object and no longer injects HTML content; use renderCB/createWidgetDivs/makeWidget with trusted rendering and verify framework ownership");
                }
                if (Set.of("removeWidget", "removeAll", "update").contains(name)) {
                    return SearchResult.found(visited,
                            "Widget mutation affects DOM removal, addRemoveCB/updateCB, component disposal, event emission, layout constraints, and nested grids; verify target signatures and lifecycle ordering");
                }
                if (Set.of("save", "load").contains(name)) {
                    return SearchResult.found(visited,
                            "GridStack serialization changed for subGridOpts, string ids, content rendering, custom fields, callbacks, column layouts, and v12.3 save(column); verify round-trip data and backend DTOs");
                }
                if ("on".equals(name) || "off".equals(name)) {
                    return SearchResult.found(visited,
                            "GridStack event payloads and propagation require review, especially add/remove/change ordering, drag/resize element arguments, dropped nodes, v12.1 nested events, and listener teardown");
                }
                if (Set.of("makeSubGrid", "removeAsSubGrid").contains(name)) {
                    return SearchResult.found(visited,
                            "Nested-grid ownership and v12.1 event propagation changed; verify dynamic nesting, parent/child drag, auto columns, save/load, empty-grid removal, and duplicate event workarounds");
                }
                if ("destroy".equals(name)) {
                    return SearchResult.found(visited,
                            "destroy(removeDOM) controls GridStack DOM and listener ownership; verify route remount, framework teardown, nested grids, drag helpers, and removeDOM=false reuse");
                }
                if (REMOVED_METHODS.contains(name) || name.startsWith("_")) {
                    return SearchResult.found(visited,
                            "This removed or internal GridStack method is not a stable v12 contract; migrate through public update/options/engine APIs after verifying collision and layout semantics");
                }
                return visited;
            }

            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, ExecutionContext ctx) {
                J.Assignment visited = super.visitAssignment(assignment, ctx);
                if (createsGrid(visited.getAssignment()) && visited.getVariable() instanceof J.FieldAccess gridField) {
                    gridVariables.add(gridField.getSimpleName());
                }
                if (!gridStackFile || !(visited.getVariable() instanceof J.FieldAccess field) ||
                    !isClass(field.getTarget())) {
                    return visited;
                }
                if (Set.of("addRemoveCB", "renderCB", "updateCB", "saveCB").contains(field.getSimpleName())) {
                    return SearchResult.found(visited,
                            "GridStack callback is global process state; coordinate registration, trusted rendering, framework component creation/disposal, updates, tests, HMR, SSR concurrency, and teardown");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(fieldAccess, ctx);
                if (!gridStackFile) {
                    return visited;
                }
                if ("engine".equals(visited.getSimpleName()) && isGrid(visited.getTarget())) {
                    return SearchResult.found(visited,
                            "Direct grid.engine access binds to collision/layout internals rewritten since v4; use public GridStack APIs or a registered engineClass and verify every overridden contract");
                }
                if ("gridstackNode".equals(visited.getSimpleName())) {
                    return SearchResult.found(visited,
                            "gridstackNode is live runtime state, not a stable serialized DTO; distinguish GridStackNode from GridStackWidget and avoid persisting DOM/engine/subGrid fields");
                }
                if ("subGrid".equals(visited.getSimpleName())) {
                    return SearchResult.found(visited,
                            "Runtime GridStackNode.subGrid differs from serializable subGridOpts; verify nested-grid ownership, save/load, event propagation, and teardown");
                }
                if ("innerHTML".equals(visited.getSimpleName()) && insideRenderCallback()) {
                    return SearchResult.found(visited,
                            "Direct innerHTML in a GridStack integration is an XSS and ownership boundary after v11; sanitize trusted content and prefer renderCB or framework rendering with lifecycle tests");
                }
                return visited;
            }

            private boolean insideRenderCallback() {
                org.openrewrite.Cursor cursor = getCursor().getParent();
                while (cursor != null) {
                    if (cursor.getValue() instanceof J.Assignment assignment &&
                        assignment.getVariable() instanceof J.FieldAccess field &&
                        "renderCB".equals(field.getSimpleName()) && isClass(field.getTarget())) {
                        return true;
                    }
                    cursor = cursor.getParent();
                }
                return false;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (gridStackFile && Set.of("window", "document").contains(visited.getSimpleName())) {
                    return SearchResult.found(visited,
                            "Browser global access in a GridStack integration must be delayed until client mount; verify SSR import safety, hydration, route remount, tests, and listener cleanup");
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
                if (Set.of("minWidth", "oneColumnSize", "disableOneColumnMode", "oneColumnModeDomSort")
                        .contains(name)) {
                    return SearchResult.found(visited,
                            "Legacy one-column options were removed by v10; define and test explicit columnOpts breakpoints, layout policy, DOM order, nested behavior, and resize restoration");
                }
                if (Set.of("addRemoveCB", "dragInOptions").contains(name)) {
                    return SearchResult.found(visited,
                            "This option moved to a global callback or setupDragIn API; choose lifecycle ownership explicitly and verify side-panel/framework creation, removal, HMR, and teardown");
                }
                if (Set.of("subGrid", "subGridOpts", "subGridDynamic", "children").contains(name)) {
                    return SearchResult.found(visited,
                            "Nested-grid configuration affects dynamic nesting, auto columns, parent-child drag, serialization, and v12.1 top-level event propagation; verify recursively");
                }
                if (Set.of("acceptWidgets", "removable", "draggable", "resizable", "staticGrid").contains(name)) {
                    return SearchResult.found(visited,
                            "GridStack v6/v11 rewrote drag/drop and side-panel behavior; verify mouse/touch/Safari, handles, clone/helper DOM, accept/trash rules, nested grids, and cleanup");
                }
                if ("content".equals(name)) {
                    return SearchResult.found(visited,
                            "GridStack 11 no longer writes widget content as innerHTML; define trusted renderCB/framework rendering and test add/load/update without introducing XSS or duplicate ownership");
                }
                if ("id".equals(name) && visited.getInitializer() instanceof J.Literal literal &&
                    literal.getValue() instanceof Number) {
                    return SearchResult.found(visited,
                            "GridStackWidget.id is string in the target; convert numeric IDs consistently across DOM attributes, keys, save/load DTOs, lookup, and persistence");
                }
                return visited;
            }

            private boolean isOwnedOptions(JS.PropertyAssignment property) {
                J.NewClass object = getCursor().firstEnclosing(J.NewClass.class);
                if (object == null || object.getClazz() != null || object.getBody() == null ||
                    object.getBody().getStatements().stream().noneMatch(statement -> statement.getId().equals(property.getId()))) {
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

            private void scanImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = GridStackJavaScriptSupport.moduleName(visited);
                        if ("gridstack".equals(module) || module.startsWith("gridstack/") &&
                            !module.endsWith(".css")) {
                            gridStackFile = true;
                            GridStackJavaScriptSupport.collectNamed(visited, "GridStack", classAliases);
                            GridStackJavaScriptSupport.collectNamed(visited, "GridStackOptions", optionAliases);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
