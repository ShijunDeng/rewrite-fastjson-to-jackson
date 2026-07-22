package com.huawei.clouds.openrewrite.diagramjsminimap;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Mark source behavior that cannot be migrated without application and diagram-js context. */
public final class FindDiagramJsMinimapJavaScriptRisks extends Recipe {
    private static final Set<String> TOUCH_EVENTS = Set.of(
            "touchstart", "touchmove", "touchend", "touchcancel", "gesturestart", "gesturechange",
            "gestureend"
    );

    @Override
    public String getDisplayName() {
        return "Find diagram-js-minimap 5 JavaScript compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark unpublished/deep entry points, module registration, minimap service lifecycle, removed touch " +
               "assumptions, legacy keyboard events and DOM/SVG coupling at exact JavaScript or TypeScript nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean minimapFile;
            private Set<String> minimapBindings = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                boolean previousFile = minimapFile;
                Set<String> previousBindings = minimapBindings;
                minimapFile = false;
                minimapBindings = new HashSet<>();
                scan(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                minimapFile = previousFile;
                minimapBindings = previousBindings;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = DiagramJsMinimapSupport.moduleName(visited);
                if ((DiagramJsMinimapSupport.PACKAGE + "/assets/diagram-js-minimap.css").equals(module)) {
                    return mark(visited, "The public minimap stylesheet must survive production tree-shaking, CSS isolation, SSR and micro-frontend asset processing");
                }
                if (module.startsWith(DiagramJsMinimapSupport.PACKAGE + "/lib/")) {
                    return mark(visited, "The target package publishes only dist and assets; replace lib/private deep imports with the public root module or an application-owned extension");
                }
                if (module.startsWith(DiagramJsMinimapSupport.PACKAGE + "/dist/")) {
                    return mark(visited, "Direct dist/UMD entry points bypass package main/module selection; deterministic index imports normalize automatically, while UMD/global interop needs manual review");
                }
                if (DiagramJsMinimapSupport.PACKAGE.equals(module)) {
                    if (visited.getImportClause() == null || visited.getImportClause().getName() == null) {
                        return mark(visited, "The public root exports the minimap module as default; named, namespace or side-effect-only imports need manual correction");
                    }
                    return mark(visited, "Verify the owning bpmn-js/diagram-js matrix, one registration per modeler, multi-instance isolation, SVG IDs, focus and removed touch behavior");
                }
                if ("hammerjs".equals(module) && minimapFile) {
                    return mark(visited, "diagram-js-minimap 5 and diagram-js 14 removed broken HammerJS touch support; retain HammerJS only for application-owned gestures and provide explicit minimap touch UX");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if ("additionalModules".equals(DiagramJsMinimapSupport.propertyName(visited.getName())) &&
                    containsMinimapBinding(visited.getInitializer())) {
                    return mark(visited, "Register minimapModule exactly once per modeler; verify instance-local DI lifecycle, destroy/recreate and same-page SVG ID isolation");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (!minimapFile) return visited;
                if ("get".equals(visited.getSimpleName()) && hasLiteralArgument(visited, "minimap")) {
                    return mark(visited, "Minimap service open/close/toggle calls must run after modeler creation/import and before destroy; verify focus, hidden containers and multiple modelers");
                }
                if (Set.of("addEventListener", "removeEventListener", "on", "off").contains(visited.getSimpleName()) &&
                    firstLiteral(visited.getArguments()) != null &&
                    TOUCH_EVENTS.contains(firstLiteral(visited.getArguments()))) {
                    return mark(visited, "Target minimap no longer provides HammerJS/broken touch support; define and test application-owned touch/pinch/pan behavior on real devices");
                }
                if (Set.of("querySelector", "querySelectorAll", "getElementById").contains(visited.getSimpleName()) &&
                    firstLiteral(visited.getArguments()) != null && selectorRisk(firstLiteral(visited.getArguments()))) {
                    return mark(visited, "DOM/SVG lookup depends on minimap internals; v5.1 prefixes copied graphic IDs and supports multiple same-page instances");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if (minimapFile && "keyCode".equals(visited.getSimpleName())) {
                    return mark(visited, "diagram-js keyboard handling moved from deprecated keyCode to code and focus-bound canvas behavior; update custom shortcuts and focus restoration");
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                if (!(visited.getValue() instanceof String value)) return visited;
                if (value.contains("node_modules/diagram-js-minimap") ||
                    value.contains("vendor/diagram-js-minimap")) {
                    return mark(visited, "Copied/vendor minimap assets depend on node_modules layout and public paths; prefer a public CSS import or verify copy/cache behavior in every build");
                }
                if (value.startsWith(DiagramJsMinimapSupport.PACKAGE + "/lib/")) {
                    return mark(visited, "The target npm package does not publish lib; replace require/dynamic deep access with the public root module");
                }
                return visited;
            }

            private void scan(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = DiagramJsMinimapSupport.moduleName(visited);
                        if (module.equals(DiagramJsMinimapSupport.PACKAGE) ||
                            module.startsWith(DiagramJsMinimapSupport.PACKAGE + "/")) {
                            minimapFile = true;
                            if (DiagramJsMinimapSupport.isRootOrDistribution(module)) {
                                String binding = DiagramJsMinimapSupport.defaultBinding(visited);
                                if (binding != null) minimapBindings.add(binding);
                            }
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean containsMinimapBinding(Expression initializer) {
                if (!(initializer instanceof J.NewArray array)) return false;
                return array.getInitializer().stream().anyMatch(expression -> expression instanceof J.Identifier id &&
                        minimapBindings.contains(id.getSimpleName()));
            }
        };
    }

    private static boolean hasLiteralArgument(J.MethodInvocation invocation, String wanted) {
        return invocation.getArguments().stream().anyMatch(argument -> argument instanceof J.Literal literal &&
                wanted.equals(literal.getValue()));
    }

    private static String firstLiteral(java.util.List<Expression> arguments) {
        return !arguments.isEmpty() && arguments.get(0) instanceof J.Literal literal &&
               literal.getValue() instanceof String ? (String) literal.getValue() : null;
    }

    private static boolean selectorRisk(String selector) {
        return selector.contains("djs-minimap") || selector.contains(".viewport-dom") ||
               selector.contains(".djs-minimap") || selector.contains("url(#djs-minimap");
    }

    private static <T extends org.openrewrite.Tree> T mark(T tree, String message) {
        return SearchResult.found(tree, message);
    }
}
