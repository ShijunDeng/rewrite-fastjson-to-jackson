package com.huawei.clouds.openrewrite.ngdynamicforms;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Marks exact NG Dynamic Forms source nodes requiring application-specific Angular 16 decisions. */
public final class FindNgDynamicFormsTypeScriptRisks extends Recipe {
    private static final Set<String> SERVICE_METHODS = Set.of(
            "createFormArray", "createFormGroup", "addFormGroupControl", "insertFormGroupControl",
            "removeFormGroupControl", "addFormArrayGroup", "insertFormArrayGroup", "removeFormArrayGroup",
            "clearFormArray", "findById", "detectChanges", "fromJSON"
    );
    private static final Set<String> CORE_BASE_TYPES = Set.of(
            "DynamicFormComponent", "DynamicFormControlComponent", "DynamicFormControlContainerComponent",
            "DynamicFormControlWithTemplateComponent"
    );
    private static final Set<String> STANDALONE_DIRECTIVES = Set.of(
            "DynamicListDirective", "DynamicTemplateDirective"
    );

    @Override
    public String getDisplayName() {
        return "Find NG Dynamic Forms 18 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed UI modules/Kendo imports, deep entries, untyped form-service calls, dynamic model " +
               "construction, custom base-class extensions, standalone directive declarations, and nonstandard forRoot calls.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> coreImports = Map.of();
            private Set<String> serviceVariables = Set.of();
            private Map<String, Integer> declarationCounts = Map.of();
            private Set<String> declaredTypes = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, String> oldImports = coreImports;
                Set<String> oldServices = serviceVariables;
                Map<String, Integer> oldDeclarations = declarationCounts;
                Set<String> oldTypes = declaredTypes;
                coreImports = new HashMap<>();
                serviceVariables = new HashSet<>();
                collect(cu, ctx);
                NgDynamicFormsSourceSupport.DeclarationInventory declarations =
                        NgDynamicFormsSourceSupport.declarations(cu, ctx);
                declarationCounts = declarations.variables();
                declaredTypes = declarations.types();
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                coreImports = oldImports;
                serviceVariables = oldServices;
                declarationCounts = oldDeclarations;
                declaredTypes = oldTypes;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = NgDynamicFormsSourceSupport.moduleName(visited);
                if (module.startsWith("@ng-dynamic-forms/core/")) {
                    return visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                            "Private @ng-dynamic-forms/core deep import detected; switch to the public root entry and verify that the symbol remains exported by 18.0.0"));
                }
                if ("@ng-dynamic-forms/ui-kendo".equals(module) || module.startsWith("@ng-dynamic-forms/ui-kendo/")) {
                    return visited.withModuleSpecifier(SearchResult.found(visited.getModuleSpecifier(),
                            "NG Dynamic Forms 18 removed the Kendo renderer; replace it with a supported renderer or an owned custom-control implementation"));
                }
                if (module.startsWith("@ng-dynamic-forms/ui-")) {
                    return markRemovedUiModules(visited);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if ("forRoot".equals(visited.getSimpleName()) &&
                    visited.getSelect() instanceof J.Identifier identifier &&
                    isUnshadowedImportedValue(identifier.getSimpleName(), "DynamicFormsCoreModule") &&
                    !noArguments(visited)) {
                    return SearchResult.found(visited, "DynamicFormsCoreModule.forRoot with arguments/nonstandard shape cannot be reduced automatically; core services are root-provided, so migrate custom providers explicitly");
                }
                if (isServiceSelect(visited.getSelect()) && SERVICE_METHODS.contains(visited.getSimpleName())) {
                    String method = visited.getSimpleName();
                    if (Set.of("createFormArray", "createFormGroup", "addFormGroupControl", "insertFormGroupControl",
                            "removeFormGroupControl", "addFormArrayGroup", "insertFormArrayGroup",
                            "removeFormArrayGroup", "clearFormArray").contains(method)) {
                        return SearchResult.found(visited, method + " crosses the v16 UntypedFormGroup/Array migration boundary; verify casts, disabled/raw values, validators, updateOn, arrays, and strict templates");
                    }
                    if ("detectChanges".equals(method)) {
                        return SearchResult.found(visited, "DynamicFormService.detectChanges is required for OnPush-visible model metadata changes; verify value/disabled versus label/layout/options updates and destroyed/lazy components");
                    }
                    if ("fromJSON".equals(method)) {
                        return SearchResult.found(visited, "Dynamic form JSON revival constructs runtime model classes and dates; validate untrusted schema, validators, masks, relations, custom model decorators, and round trips");
                    }
                    return SearchResult.found(visited, method + " returns or resolves dynamic model/control state; verify nullability, generic assumptions, nested paths, and array mutations under Angular 16 strict typing");
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String local = NgDynamicFormsSourceSupport.expressionName(visited.getClazz());
                String imported = coreImports.get(local);
                if (isUnshadowedImportedType(local) && imported != null && imported.startsWith("Dynamic") &&
                    imported.endsWith("Model")) {
                    return SearchResult.found(visited, imported + " configuration crosses Angular 16 untyped forms and strict-mode boundaries; verify value shape, validators, relations, disabled state, additional renderer config, and JSON serialization");
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
                String local = NgDynamicFormsSourceSupport.typeName(visited.getExtends());
                String base = coreImports.get(local);
                if (isUnshadowedImportedType(local) && base != null && CORE_BASE_TYPES.contains(base)) {
                    return SearchResult.found(visited, "Custom class extends " + base + "; verify v18 abstract members, standalone imports, content/view queries, untyped FormGroup inputs, events, OnPush change detection, and DI scope");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!"declarations".equals(NgDynamicFormsSourceSupport.expressionName(visited.getName()))) return visited;
                String source = visited.getInitializer().printTrimmed(getCursor());
                for (Map.Entry<String, String> entry : coreImports.entrySet()) {
                    if (STANDALONE_DIRECTIVES.contains(entry.getValue()) &&
                        isUnshadowedImportedValue(entry.getKey(), entry.getValue()) &&
                        source.matches("(?s).*\\b" + java.util.regex.Pattern.quote(entry.getKey()) + "\\b.*")) {
                        return SearchResult.found(visited, entry.getValue() + " is standalone in v18; move it from NgModule/TestBed declarations to imports while preserving the rest of this metadata array");
                    }
                }
                return visited;
            }

            private void collect(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (UpgradeSelectedNgDynamicFormsCoreDependency.PACKAGE.equals(
                                NgDynamicFormsSourceSupport.moduleName(visited))) collectCoreImports(visited, coreImports);
                        return visited;
                    }
                }.visit(cu, ctx);
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(
                            J.VariableDeclarations declarations, ExecutionContext scanCtx) {
                        J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, scanCtx);
                        String local = NgDynamicFormsSourceSupport.typeName(visited.getTypeExpression());
                        if (isUnshadowedImportedType(local) &&
                            "DynamicFormService".equals(coreImports.get(local))) {
                            visited.getVariables().forEach(variable -> serviceVariables.add(variable.getSimpleName()));
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (visited.getInitializer() instanceof J.MethodInvocation call &&
                            "inject".equals(call.getSimpleName()) && call.getArguments().stream().anyMatch(argument ->
                                    isUnshadowedImportedValue(NgDynamicFormsSourceSupport.expressionName(argument),
                                            "DynamicFormService"))) {
                            serviceVariables.add(visited.getSimpleName());
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean isServiceSelect(Expression select) {
                if (select instanceof J.Identifier identifier) {
                    return serviceVariables.contains(identifier.getSimpleName()) &&
                           declarationCounts.getOrDefault(identifier.getSimpleName(), 0) <= 1;
                }
                if (select instanceof J.FieldAccess field) {
                    return serviceVariables.contains(field.getSimpleName()) &&
                           declarationCounts.getOrDefault(field.getSimpleName(), 0) <= 1;
                }
                return false;
            }

            private boolean isUnshadowedImportedValue(String local, String imported) {
                return imported.equals(coreImports.get(local)) && declarationCounts.getOrDefault(local, 0) == 0 &&
                       !declaredTypes.contains(local);
            }

            private boolean isUnshadowedImportedType(String local) {
                return coreImports.containsKey(local) && !declaredTypes.contains(local);
            }
        };
    }

    private static boolean noArguments(J.MethodInvocation call) {
        return call.getArguments().isEmpty() ||
               call.getArguments().size() == 1 && call.getArguments().get(0) instanceof J.Empty;
    }

    private static void collectCoreImports(JS.Import declaration, Map<String, String> imports) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return;
        for (JS.ImportSpecifier element : named.getElements()) {
            Expression specifier = element.getSpecifier();
            if (specifier instanceof J.Identifier identifier) {
                imports.put(identifier.getSimpleName(), identifier.getSimpleName());
            } else if (specifier instanceof JS.Alias alias && alias.getAlias() instanceof J.Identifier identifier) {
                imports.put(identifier.getSimpleName(), alias.getPropertyName().getSimpleName());
            }
        }
    }

    private static JS.Import markRemovedUiModules(JS.Import declaration) {
        JS.ImportClause clause = declaration.getImportClause();
        if (clause == null || !(clause.getNamedBindings() instanceof JS.NamedImports named)) return declaration;
        java.util.List<JS.ImportSpecifier> elements = org.openrewrite.internal.ListUtils.map(named.getElements(), element -> {
            Expression specifier = element.getSpecifier();
            String imported = specifier instanceof J.Identifier identifier ? identifier.getSimpleName() :
                    specifier instanceof JS.Alias alias ? alias.getPropertyName().getSimpleName() : "";
            return imported.matches("DynamicForms.*UIModule")
                    ? SearchResult.found(element, imported + " was removed in v18; import the actually used standalone form/container/control components and update NgModule/TestBed/standalone scopes")
                    : element;
        });
        return declaration.withImportClause(clause.withNamedBindings(named.withElements(elements)));
    }
}
