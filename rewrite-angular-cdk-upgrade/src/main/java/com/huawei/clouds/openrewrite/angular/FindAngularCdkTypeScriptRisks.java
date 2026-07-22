package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Mark CDK 20 source incompatibilities that require application-level decisions. */
public final class FindAngularCdkTypeScriptRisks extends Recipe {
    private static final Set<String> REMOVED_PORTAL = Set.of(
            "DomPortalHost", "PortalHost", "BasePortalHost"
    );
    private static final Set<String> REMOVED_TABLE = Set.of(
            "Constructor", "CanStickCtor", "mixinHasStickyInput", "CanStick", "CDK_TABLE_TEMPLATE",
            "StickyDirection", "StickyStyler"
    );
    private static final Set<String> SELECTION = Set.of("SelectionModel");
    private static final Set<String> SELECTION_MUTATORS = Set.of(
            "clear", "deselect", "select", "setSelection", "toggle"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular CDK 20 TypeScript compatibility risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact removed/changed portal, overlay, dialog, drag-drop, table, selection and private CDK " +
               "APIs while preserving binding-aware call-site precision.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> imported = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, String> previous = imported;
                imported = new HashMap<>();
                collectImports(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                imported = previous;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = MigrateDeterministicAngularCdkSource.moduleName(visited);
                if ("@angular/cdk/testing/protractor".equals(module)) {
                    return mark(visited, "CDK's Protractor test harness entry point was removed in v14; migrate tests to a supported WebDriver or browser runner and preserve HarnessEnvironment behavior");
                }
                if (module.startsWith("@angular/cdk/") && isPrivateEntryPoint(module)) {
                    return mark(visited, "Private or internal CDK entry point is not covered by compatibility guarantees; replace it with the public package export before upgrading");
                }
                if (MigrateDeterministicAngularCdkSource.importsAny(visited, REMOVED_PORTAL)) {
                    return mark(visited, "Portal host classes were renamed/removed; aliased imports can migrate automatically, but unaliased bindings and constructor ownership require deliberate changes to Outlet APIs");
                }
                if (MigrateDeterministicAngularCdkSource.importsAny(visited, Set.of("PortalInjector"))) {
                    return mark(visited, "PortalInjector was removed in CDK 20; replace it with Injector.create and preserve parent/provider lookup semantics");
                }
                if (MigrateDeterministicAngularCdkSource.importsAny(visited, REMOVED_TABLE)) {
                    return mark(visited, "CDK table sticky internals/mixins were removed in v20; replace subclass/internal coupling with supported CdkTable and column APIs");
                }
                if (MigrateDeterministicAngularCdkSource.importsAny(visited,
                        Set.of("DIALOG_SCROLL_STRATEGY_PROVIDER", "DIALOG_SCROLL_STRATEGY_PROVIDER_FACTORY"))) {
                    return mark(visited, "Dialog scroll-strategy provider/factory exports were removed; provide DIALOG_SCROLL_STRATEGY with an application-owned factory");
                }
                if (MigrateDeterministicAngularCdkSource.importsAny(visited, Set.of("DragDropRegistry"))) {
                    return mark(visited, "DragDropRegistry is no longer generic and its scroll method was removed; review type arguments and use scrolled while preserving viewport dispatch behavior");
                }
                if (MigrateDeterministicAngularCdkSource.importsAny(visited, Set.of("ComponentPortal", "DomPortalOutlet"))) {
                    return mark(visited, "Portal constructors changed in CDK 20 and no longer accept ComponentFactoryResolver; update construction and verify attachment/disposal ownership");
                }
                if (MigrateDeterministicAngularCdkSource.importsAny(visited, Set.of("DialogConfig"))) {
                    return mark(visited, "DialogConfig.componentFactoryResolver was removed; typed direct properties migrate automatically, while spreads/builders need manual review");
                }
                if (module.startsWith("@angular/cdk/")) {
                    return mark(visited, "CDK major upgrades can change DOM, focus, overlay, drag/drop, virtual-scroll and SSR behavior; run the official schematics and feature-level tests");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String select = MigrateDeterministicAngularCdkSource.expressionName(visited.getSelect());
                String owner = imported.get(select);
                if (owner != null && SELECTION.contains(owner) && SELECTION_MUTATORS.contains(visited.getSimpleName())) {
                    return mark(visited, "SelectionModel mutator now returns whether the selection changed; verify callers, mocks and overload assumptions against the boolean contract");
                }
                if ("DragDropRegistry".equals(owner) && "scroll".equals(visited.getSimpleName())) {
                    return mark(visited, "DragDropRegistry.scroll was removed in CDK 20; use scrolled and verify throttling/subscription cleanup");
                }
                return visited;
            }

            private void collectImports(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = MigrateDeterministicAngularCdkSource.moduleName(visited);
                        if (module.startsWith("@angular/cdk/")) {
                            for (String api : Set.of("SelectionModel", "DragDropRegistry")) {
                                String alias = MigrateDeterministicAngularCdkSource.importedAlias(visited, api);
                                if (alias != null) imported.put(alias, api);
                            }
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                              ExecutionContext scanCtx) {
                        J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, scanCtx);
                        String canonical = canonicalType(visited.getTypeExpression());
                        if (canonical != null) {
                            for (J.VariableDeclarations.NamedVariable variable : visited.getVariables()) {
                                imported.put(variable.getSimpleName(), canonical);
                            }
                        }
                        return visited;
                    }

                    private String canonicalType(org.openrewrite.java.tree.TypeTree type) {
                        String exact = imported.get(MigrateDeterministicAngularCdkSource.typeName(type));
                        if (exact != null) return exact;
                        String rendered = type == null ? "" : type.toString().trim();
                        for (Map.Entry<String, String> entry : imported.entrySet()) {
                            if (rendered.startsWith(entry.getKey() + "<")) return entry.getValue();
                        }
                        return null;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (visited.getInitializer() instanceof J.NewClass construction && construction.getClazz() != null) {
                            String canonical = imported.get(MigrateDeterministicAngularCdkSource.typeName(
                                    construction.getClazz()));
                            if (canonical != null) imported.put(visited.getSimpleName(), canonical);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                String source = cu.printAll();
                for (Map.Entry<String, String> entry : Map.copyOf(imported).entrySet()) {
                    if (!SELECTION.contains(entry.getValue()) && !"DragDropRegistry".equals(entry.getValue())) continue;
                    java.util.regex.Matcher typed = java.util.regex.Pattern.compile(
                            "(?<![$\\w])([$A-Za-z_][$\\w]*)\\s*[?!]?\\s*:\\s*" +
                            java.util.regex.Pattern.quote(entry.getKey()) + "(?:\\s*<[^;=,){}]+>)?")
                            .matcher(source);
                    while (typed.find()) imported.put(typed.group(1), entry.getValue());
                }
            }
        };
    }

    private static boolean isPrivateEntryPoint(String module) {
        return module.contains("/private") || module.contains("/_") || module.contains("/src/") ||
               module.contains("/typings/");
    }

    private static <T extends org.openrewrite.Tree> T mark(T tree, String message) {
        return SearchResult.found(tree, message);
    }
}
