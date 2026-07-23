package com.huawei.clouds.openrewrite.angularelements;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;

import java.util.HashSet;
import java.util.Set;

/** Marks Angular Elements contracts whose correct migration depends on the host application. */
public final class FindAngularElements20SourceRisks extends Recipe {
    static final String CREATION =
            "Angular createCustomElement boundary detected; verify EnvironmentInjector ownership, standalone versus " +
            "NgModule providers, input/output metadata, zone or zoneless scheduling, error handling, and destroy/recreate behavior";
    static final String REGISTRATION =
            "Custom-element registry boundary detected; use a hyphenated stable tag that is not the Angular component " +
            "selector, prevent duplicate define calls across bundles/HMR/microfrontends, and guard browser globals during SSR";
    static final String LIFECYCLE =
            "Custom-element lookup/upgrade lifecycle detected; verify whenDefined timing, nodes created before registration, " +
            "disconnect/reconnect destruction, moved nodes, MutationObserver timing, and registry ownership across realms";
    static final String INPUT =
            "Angular Element attribute/input boundary detected; input names map to dash-separated lowercase attributes and " +
            "attribute values are strings, so test coercion, aliases, required inputs, reflection, sanitization, and update timing";
    static final String OUTPUT =
            "Angular Element output/event boundary detected; outputs are CustomEvent names and the emitted payload is in " +
            "event.detail, so verify aliases, casing, bubbling/composed expectations, typing, teardown, and cross-framework adapters";
    static final String STRATEGY =
            "Custom NgElement strategy contract detected; recompile connect/disconnect/input/events implementations against " +
            "20.3.25 and verify injector lifetime, scheduling, teardown, reattachment, error propagation, and protected/internal API use";
    static final String TYPING =
            "Angular Element DOM typing detected; preserve NgElement & WithProperties and HTMLElementTagNameMap declarations, " +
            "then type-check attribute strings, property inputs, CustomEvent.detail outputs, and custom tag lookup on the target";
    static final String SCHEMA =
            "CUSTOM_ELEMENTS_SCHEMA boundary detected; keep it only where Angular templates intentionally consume custom " +
            "elements, and test misspelled elements/properties because the schema weakens template diagnostics";
    static final String DEEP_IMPORT =
            "This @angular/elements deep import is outside the documented package-root API; migrate to public root exports " +
            "and verify Angular package-format, ESM bundling, declarations, and any custom strategy dependency";
    static final String SHADOW_DOM =
            "Shadow DOM boundary on an Angular Element detected; verify style encapsulation, CSS custom properties, slots, " +
            "focus/event composed paths, hydration/SSR, and host-framework cleanup in every supported browser";

    private static final Set<String> STRATEGY_IMPORTS = Set.of(
            "NgElementStrategy", "NgElementStrategyFactory", "NgElementConstructor");
    private static final Set<String> TYPE_IMPORTS = Set.of("NgElement", "WithProperties");
    private static final Set<String> STRATEGY_METHODS = Set.of(
            "connect", "disconnect", "getInputValue", "setInputValue");

    @Override
    public String getDisplayName() {
        return "Find Angular Elements 20 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks owned createCustomElement, registry, attribute/input, event/output, custom strategy, DOM typing, " +
               "schema, SSR/lifecycle, and Shadow DOM nodes that require application-specific Angular 20 decisions.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean ownsElements;
            private boolean customStrategy;
            private Set<String> creators = Set.of();
            private Set<String> namespaces = Set.of();
            private Set<String> elementBindings = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!AngularElementsSupport.isSource(cu.getSourcePath())) return cu;
                boolean oldOwns = ownsElements;
                boolean oldStrategy = customStrategy;
                Set<String> oldCreators = creators;
                Set<String> oldNamespaces = namespaces;
                Set<String> oldElementBindings = elementBindings;
                ownsElements = false;
                customStrategy = false;
                creators = new HashSet<>();
                namespaces = new HashSet<>();
                elementBindings = new HashSet<>();
                scanOwnership(cu, ctx);
                JS.CompilationUnit visited = ownsElements ? super.visitJsCompilationUnit(cu, ctx) : cu;
                ownsElements = oldOwns;
                customStrategy = oldStrategy;
                creators = oldCreators;
                namespaces = oldNamespaces;
                elementBindings = oldElementBindings;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = AngularElementsSupport.moduleName(visited);
                if (module.startsWith(AngularElementsSupport.PACKAGE + "/")) {
                    return AngularElementsSupport.mark(visited, DEEP_IMPORT);
                }
                if (AngularElementsSupport.PACKAGE.equals(module)) {
                    if (importsAny(visited, STRATEGY_IMPORTS)) return AngularElementsSupport.mark(visited, STRATEGY);
                    if (importsAny(visited, TYPE_IMPORTS)) return AngularElementsSupport.mark(visited, TYPING);
                }
                if ("@angular/core".equals(module) &&
                    AngularElementsSupport.importedAlias(visited, "CUSTOM_ELEMENTS_SCHEMA") != null) {
                    return AngularElementsSupport.mark(visited, SCHEMA);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String required = AngularElementsSupport.requireModule(visited);
                if (required.startsWith(AngularElementsSupport.PACKAGE + "/")) {
                    return AngularElementsSupport.mark(visited, DEEP_IMPORT);
                }
                if (ownedCreator(visited)) return AngularElementsSupport.mark(visited, CREATION);
                if (registryCall(visited)) {
                    return AngularElementsSupport.mark(visited,
                            "define".equals(visited.getSimpleName()) ? REGISTRATION : LIFECYCLE);
                }
                String root = AngularElementsSupport.rootIdentifier(visited.getSelect());
                String first = visited.getArguments().isEmpty() ? null :
                        AngularElementsSupport.literal(visited.getArguments().get(0));
                if ("document".equals(root) && Set.of("createElement", "querySelector", "querySelectorAll")
                        .contains(visited.getSimpleName()) && customTagReference(first)) {
                    return AngularElementsSupport.mark(visited, INPUT);
                }
                if (Set.of("setAttribute", "toggleAttribute", "removeAttribute")
                        .contains(visited.getSimpleName()) && first != null && knownElement(visited.getSelect())) {
                    return AngularElementsSupport.mark(visited, INPUT);
                }
                if (Set.of("addEventListener", "removeEventListener", "dispatchEvent")
                        .contains(visited.getSimpleName()) && first != null && knownElement(visited.getSelect())) {
                    return AngularElementsSupport.mark(visited, OUTPUT);
                }
                if (Set.of("attachShadow", "getRootNode").contains(visited.getSimpleName()) &&
                    knownElement(visited.getSelect())) {
                    return AngularElementsSupport.mark(visited, SHADOW_DOM);
                }
                if (customStrategy && STRATEGY_METHODS.contains(visited.getSimpleName())) {
                    return AngularElementsSupport.mark(visited, STRATEGY);
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                return customStrategy && Set.of("connectedCallback", "disconnectedCallback", "attributeChangedCallback")
                        .contains(visited.getSimpleName()) ? AngularElementsSupport.mark(visited, STRATEGY) : visited;
            }

            private boolean ownedCreator(J.MethodInvocation invocation) {
                if (!"createCustomElement".equals(invocation.getSimpleName()) && invocation.getSelect() != null) {
                    return false;
                }
                if (invocation.getSelect() == null) return creators.contains(invocation.getSimpleName());
                return "createCustomElement".equals(invocation.getSimpleName()) &&
                       invocation.getSelect() instanceof J.Identifier identifier &&
                       namespaces.contains(identifier.getSimpleName());
            }

            private boolean registryCall(J.MethodInvocation invocation) {
                if (!Set.of("define", "get", "whenDefined", "upgrade").contains(invocation.getSimpleName())) return false;
                Expression select = invocation.getSelect();
                if (select instanceof J.Identifier identifier) {
                    return "customElements".equals(identifier.getSimpleName());
                }
                return select instanceof J.FieldAccess field &&
                       "customElements".equals(field.getSimpleName()) &&
                       Set.of("window", "globalThis").contains(AngularElementsSupport.rootIdentifier(field));
            }

            private boolean customTagReference(String value) {
                return value != null && value.contains("-");
            }

            private boolean knownElement(Expression select) {
                if (select instanceof J.Identifier identifier) {
                    return elementBindings.contains(identifier.getSimpleName());
                }
                return select instanceof J.MethodInvocation invocation && documentCustomElementCall(invocation);
            }

            private boolean documentCustomElementCall(J.MethodInvocation invocation) {
                String root = AngularElementsSupport.rootIdentifier(invocation.getSelect());
                String first = invocation.getArguments().isEmpty() ? null :
                        AngularElementsSupport.literal(invocation.getArguments().get(0));
                return "document".equals(root) &&
                       Set.of("createElement", "querySelector", "querySelectorAll")
                               .contains(invocation.getSimpleName()) && customTagReference(first);
            }

            private boolean importsAny(JS.Import declaration, Set<String> names) {
                return names.stream().anyMatch(name -> AngularElementsSupport.importedAlias(declaration, name) != null);
            }

            private void scanOwnership(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = AngularElementsSupport.moduleName(visited);
                        if (AngularElementsSupport.PACKAGE.equals(module) ||
                            module.startsWith(AngularElementsSupport.PACKAGE + "/")) ownsElements = true;
                        if (!AngularElementsSupport.PACKAGE.equals(module)) return visited;
                        String creator = AngularElementsSupport.importedAlias(visited, "createCustomElement");
                        if (creator != null) creators.add(creator);
                        String namespace = AngularElementsSupport.namespaceAlias(visited);
                        if (namespace != null) namespaces.add(namespace);
                        if (importsAny(visited, STRATEGY_IMPORTS)) customStrategy = true;
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (visited.getInitializer() instanceof J.MethodInvocation require &&
                            AngularElementsSupport.PACKAGE.equals(AngularElementsSupport.requireModule(require))) {
                            ownsElements = true;
                            namespaces.add(visited.getSimpleName());
                        }
                        if (visited.getInitializer() instanceof J.MethodInvocation invocation &&
                            documentCustomElementCall(invocation)) elementBindings.add(visited.getSimpleName());
                        return visited;
                    }
                }.visit(cu, ctx);
            }
        };
    }
}
