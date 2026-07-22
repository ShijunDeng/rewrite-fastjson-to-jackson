package com.huawei.clouds.openrewrite.angular;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Marks exact compiler/JIT/decorator nodes whose migration requires application semantics. */
public final class FindAngularCompilerTypeScriptRisks extends Recipe {
    private static final Set<String> COMPILER_APIS = Set.of(
            "Compiler", "CompilerFactory", "JitCompilerFactory", "COMPILER_OPTIONS", "ResourceLoader",
            "parseTemplate", "Parser", "Lexer", "HtmlParser", "DomElementSchemaRegistry", "SelectorMatcher",
            "CssSelector", "InterpolationConfig", "BindingParser", "AST", "RecursiveAstVisitor", "TmplAstNode",
            "MessageBundle", "Xliff", "Xliff2", "Xmb", "Serializer", "ConstantPool", "R3TargetBinder"
    );
    private static final Set<String> DECORATORS = Set.of(
            "Component", "Directive", "NgModule", "Pipe", "HostBinding", "HostListener"
    );
    private static final Set<String> DYNAMIC_COMPONENT_APIS = Set.of(
            "createComponent", "createNgModule", "createNgModuleRef", "reflectComponentType"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Compiler 20 TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks runtime JIT/compiler APIs, compiler AST/parser tooling, dynamic component creation, and " +
               "statically evaluated decorator metadata at exact TypeScript AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Map<String, String> compilerAliases = Map.of();
            private Set<String> compilerNamespaces = Set.of();
            private Map<String, String> decoratorAliases = Map.of();
            private Set<String> platformDynamicAliases = Set.of();
            private Set<String> dynamicComponentAliases = Set.of();
            private Set<String> compilerVariables = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Map<String, String> oldCompiler = compilerAliases;
                Set<String> oldNamespaces = compilerNamespaces;
                Map<String, String> oldDecorators = decoratorAliases;
                Set<String> oldPlatform = platformDynamicAliases;
                Set<String> oldDynamic = dynamicComponentAliases;
                Set<String> oldVariables = compilerVariables;
                compilerAliases = new HashMap<>();
                compilerNamespaces = new HashSet<>();
                decoratorAliases = new HashMap<>();
                platformDynamicAliases = new HashSet<>();
                dynamicComponentAliases = new HashSet<>();
                compilerVariables = new HashSet<>();
                collect(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                compilerAliases = oldCompiler;
                compilerNamespaces = oldNamespaces;
                decoratorAliases = oldDecorators;
                platformDynamicAliases = oldPlatform;
                dynamicComponentAliases = oldDynamic;
                compilerVariables = oldVariables;
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String api = compilerApi(visited.getClazz());
                return api == null ? visited : SearchResult.found(visited, compilerMessage(api));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String name = visited.getSimpleName();
                if (visited.getSelect() == null && platformDynamicAliases.contains(name)) {
                    return SearchResult.found(visited, "platformBrowserDynamic enables runtime JIT, which is deprecated in Angular 20; migrate production bootstrap to AOT-capable platformBrowser/bootstrapApplication and verify CSP, providers, SSR, and tests");
                }
                if (visited.getSelect() == null && dynamicComponentAliases.contains(name)) {
                    return SearchResult.found(visited, "Dynamic component creation needs explicit environment injector, bindings/directives, lifecycle cleanup, lazy loading, and AOT discoverability tests");
                }
                String api = visited.getSelect() == null ? compilerAliases.get(name) : namespaceApi(visited.getSelect(), name);
                if (api != null) return SearchResult.found(visited, compilerMessage(api));
                if (isCompilerReceiver(visited.getSelect()) &&
                    (name.startsWith("compile") || name.contains("Module") || name.contains("Component"))) {
                    return SearchResult.found(visited, "Runtime Compiler." + name + " relies on deprecated JIT behavior; replace dynamic templates/modules with AOT-known code and test CSP, caching, provider scope, and errors");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String local = MigrateDeterministicAngularCompilerSource.expressionName(visited.getFunction());
                if (platformDynamicAliases.contains(local)) {
                    return SearchResult.found(visited, "platformBrowserDynamic enables runtime JIT, which is deprecated in Angular 20; migrate production bootstrap to AOT and verify CSP, providers, SSR, and tests");
                }
                if (dynamicComponentAliases.contains(local)) {
                    return SearchResult.found(visited, "Dynamic component creation needs explicit environment injector, bindings/directives, lifecycle cleanup, lazy loading, and AOT discoverability tests");
                }
                String api = compilerAliases.get(local);
                return api == null ? visited : SearchResult.found(visited, compilerMessage(api));
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                String api = namespaceApi(visited.getTarget(), visited.getSimpleName());
                return api == null ? visited : SearchResult.found(visited, compilerMessage(api));
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                    ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
                String api = compilerAliases.get(typeName(visited.getTypeExpression()));
                if (api != null && !"Compiler".equals(api)) {
                    return SearchResult.found(visited, compilerMessage(api));
                }
                return visited;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                String imported = decoratorAliases.get(visited.getSimpleName());
                if ("HostBinding".equals(imported) || "HostListener".equals(imported)) {
                    return SearchResult.found(visited, imported + " expressions/events receive stricter template type checking; verify inheritance, directive composition, event names/$event types, globals, and custom elements");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property, ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                J.Annotation annotation = getCursor().firstEnclosing(J.Annotation.class);
                if (annotation == null || !MigrateDeterministicAngularCompilerSource.isDirectAnnotationProperty(
                        getCursor(), annotation)) return visited;
                String decorator = decoratorAliases.get(annotation.getSimpleName());
                String name = MigrateDeterministicAngularCompilerSource.expressionName(visited.getName());
                if ("NgModule".equals(decorator) && "declarations".equals(name)) {
                    return SearchResult.found(visited, "Angular 19 makes components/directives/pipes standalone by default; every NgModule declaration must have explicit standalone:false from the official migration and retain correct scope/order");
                }
                if (("Component".equals(decorator) || "Directive".equals(decorator)) && "host".equals(name)) {
                    return SearchResult.found(visited, "Host metadata now participates in stricter compiler checking; verify property/event names, $event types, inheritance, directive composition, schemas, and SSR");
                }
                if ("Component".equals(decorator) && "template".equals(name)) {
                    String message = visited.getInitializer() instanceof J.Literal
                            ? "Inline template must pass Angular 20 parsing/type checking, reserved in/void operators, block-control-flow escaping, i18n, security, and whitespace tests"
                            : "Dynamic template metadata is not statically analyzable/AOT-safe; replace runtime composition with an external or literal template and verify CSP";
                    return SearchResult.found(visited, message);
                }
                if ("Component".equals(decorator) && "templateUrl".equals(name)) {
                    return SearchResult.found(visited, "External template resolution must work in Angular 20 AOT, tests, libraries, custom builders, SSR, and source-map/i18n extraction pipelines");
                }
                if ("Component".equals(decorator) && "imports".equals(name)) {
                    return SearchResult.found(visited, "Standalone template imports must exactly cover used directives/pipes and avoid hidden NgModule scope assumptions; run strict template and unused-import diagnostics");
                }
                if (Set.of("providers", "imports", "exports", "schemas").contains(name)) {
                    return SearchResult.found(visited, name + " decorator metadata must be statically evaluable and preserve provider/template scope, schemas, lazy injectors, linker output, and tree shaking");
                }
                if ("jit".equals(name) && visited.getInitializer() instanceof J.Literal literal &&
                    Boolean.TRUE.equals(literal.getValue())) {
                    return SearchResult.found(visited, "jit:true opts this declaration out of normal AOT assumptions; remove runtime JIT after validating resource loading, CSP, inheritance, and tests");
                }
                return visited;
            }

            private void collect(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = MigrateDeterministicAngularCompilerSource.moduleName(visited);
                        if ("@angular/compiler".equals(module)) {
                            for (String api : COMPILER_APIS) addNamed(visited, api, compilerAliases);
                            JS.ImportClause clause = visited.getImportClause();
                            if (clause != null && clause.getNamedBindings() instanceof JS.NamedImports named) {
                                for (JS.ImportSpecifier specifier : named.getElements()) {
                                    String imported = importedName(specifier);
                                    if (imported.startsWith("ɵ")) addNamed(visited, imported, compilerAliases);
                                }
                            }
                            String namespace = MigrateDeterministicAngularCompilerSource.namespaceAlias(visited);
                            if (namespace != null) compilerNamespaces.add(namespace);
                        }
                        if ("@angular/core".equals(module)) {
                            for (String decorator : DECORATORS) addNamed(visited, decorator, decoratorAliases);
                            for (String api : DYNAMIC_COMPONENT_APIS) {
                                String alias = MigrateDeterministicAngularCompilerSource.importedAlias(visited, api);
                                if (alias != null) dynamicComponentAliases.add(alias);
                            }
                        }
                        if ("@angular/platform-browser-dynamic".equals(module)) {
                            String alias = MigrateDeterministicAngularCompilerSource.importedAlias(visited, "platformBrowserDynamic");
                            if (alias != null) platformDynamicAliases.add(alias);
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                            ExecutionContext scanCtx) {
                        J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, scanCtx);
                        if ("Compiler".equals(compilerAliases.get(typeName(visited.getTypeExpression())))) {
                            visited.getVariables().forEach(v -> compilerVariables.add(v.getSimpleName()));
                        }
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private String compilerApi(Object expression) {
                if (expression instanceof J.Identifier identifier) return compilerAliases.get(identifier.getSimpleName());
                if (expression instanceof J.FieldAccess access) return namespaceApi(access.getTarget(), access.getSimpleName());
                return null;
            }

            private String namespaceApi(Expression target, String member) {
                return target instanceof J.Identifier identifier && compilerNamespaces.contains(identifier.getSimpleName())
                        ? member : null;
            }

            private boolean isCompilerReceiver(Expression expression) {
                if (expression instanceof J.Identifier identifier) return compilerVariables.contains(identifier.getSimpleName());
                return expression instanceof J.FieldAccess access && compilerVariables.contains(access.getSimpleName());
            }
        };
    }

    private static void addNamed(JS.Import declaration, String imported, Map<String, String> aliases) {
        String alias = MigrateDeterministicAngularCompilerSource.importedAlias(declaration, imported);
        if (alias != null) aliases.put(alias, imported);
    }

    private static String importedName(JS.ImportSpecifier specifier) {
        if (specifier.getSpecifier() instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (specifier.getSpecifier() instanceof JS.Alias alias) return alias.getPropertyName().getSimpleName();
        return "";
    }

    private static String compilerMessage(String api) {
        if (api.startsWith("ɵ")) return api + " is a private Angular compiler API with no compatibility contract; replace it with a public entry point or pin/retest the tool against Angular 20 linker and AST output";
        if (Set.of("Compiler", "CompilerFactory", "JitCompilerFactory", "COMPILER_OPTIONS", "ResourceLoader").contains(api)) {
            return api + " participates in deprecated runtime JIT/resource compilation; migrate to AOT-known templates/modules and verify CSP, caching, providers, SSR, and failures";
        }
        return api + " compiler parser/AST/i18n API is version-sensitive; pin the Angular 20 contract and test malformed syntax, source spans, blocks, expressions, entities, i18n, and diagnostics";
    }

    private static String typeName(TypeTree type) {
        if (type instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (type instanceof J.FieldAccess access) return access.getSimpleName();
        if (type instanceof J.ArrayType array) return typeName(array.getElementType());
        if (type instanceof J.ParameterizedType parameterized) {
            if (parameterized.getClazz() instanceof J.Identifier identifier) return identifier.getSimpleName();
            if (parameterized.getClazz() instanceof J.FieldAccess access) return access.getSimpleName();
        }
        if (type instanceof JS.TypeTreeExpression expression) return MigrateDeterministicAngularCompilerSource.expressionName(expression.getExpression());
        if (type instanceof JS.TypeInfo info) return typeName(info.getTypeIdentifier());
        return "";
    }
}
