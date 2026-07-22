package com.huawei.clouds.openrewrite.ngxtranslate;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mark ngx-translate constructs whose migration depends on Angular/provider/runtime semantics. */
public final class FindNgxTranslateJavaScriptRisks extends Recipe {
    private static final Set<String> EVENTS = Set.of(
            "onLangChange", "onTranslationChange", "onFallbackLangChange", "onDefaultLangChange"
    );
    private static final Set<String> PLUGINS = Set.of(
            "TranslateLoader", "TranslateCompiler", "TranslateParser", "MissingTranslationHandler"
    );
    private static final Set<String> RAW_PROVIDER_KEYS = Set.of(
            "loader", "compiler", "parser", "missingTranslationHandler"
    );
    private static final Set<String> BROWSER_GLOBALS = Set.of("window", "document", "navigator");

    @Override
    public String getDisplayName() {
        return "Find ngx-translate 17 JavaScript and TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact provider/module configuration, HTTP loader construction, deprecated getters/loading, " +
               "event writes, custom plugin contracts, concurrent language switching, SSR globals and deep imports.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean ngxFile;
            private boolean standaloneModule;
            private boolean customPlugin;
            private Set<String> serviceTypes = Set.of();
            private Set<String> runtimeServiceTypes = Set.of();
            private Set<String> bareServiceReceivers = Set.of();
            private Set<String> fieldServiceReceivers = Set.of();
            private Set<String> moduleBindings = Set.of();
            private Set<String> providerFunctions = Set.of();
            private Set<String> httpLoaderTypes = Set.of();
            private Set<String> injectFunctions = Set.of();
            private Set<String> componentBindings = Set.of();
            private Set<String> browserHelpers = Set.of();
            private Map<String, String> pluginBindings = Map.of();
            private Map<String, Integer> declarationCounts = Map.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!NgxTranslateSupport.isProjectPath(cu.getSourcePath())) return cu;
                State previous = state();
                ngxFile = false;
                standaloneModule = false;
                customPlugin = false;
                serviceTypes = new HashSet<>();
                runtimeServiceTypes = new HashSet<>();
                bareServiceReceivers = new HashSet<>();
                fieldServiceReceivers = new HashSet<>();
                moduleBindings = new HashSet<>();
                providerFunctions = new HashSet<>();
                httpLoaderTypes = new HashSet<>();
                injectFunctions = new HashSet<>();
                componentBindings = new HashSet<>();
                browserHelpers = new HashSet<>();
                pluginBindings = new HashMap<>();
                declarationCounts = new HashMap<>();
                scan(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                restore(previous);
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = NgxTranslateSupport.moduleName(visited);
                if (module.startsWith(NgxTranslateSupport.PACKAGE + "/")) {
                    return visited.withModuleSpecifier(mark(visited.getModuleSpecifier(),
                            "Private @ngx-translate/core deep imports are not stable; import the public symbol from the package root and run strict typecheck"));
                }
                if (!NgxTranslateSupport.PACKAGE.equals(module) || visited.getImportClause() == null ||
                    !(visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named)) return visited;
                JS.NamedImports marked = named.withElements(ListUtils.map(named.getElements(), specifier -> {
                    String imported = NgxTranslateSupport.importedName(specifier);
                    String local = NgxTranslateSupport.localBinding(specifier);
                    if (NgxTranslateSupport.PUBLIC_RENAMES.containsKey(imported)) {
                        return mark(specifier, "Deprecated public fallback/default implementation names in this import are renamed automatically to their v17 names");
                    }
                    if (standaloneModule && "TranslateModule".equals(imported) && moduleBindings.contains(local)) {
                        return mark(specifier, "This standalone component imports TranslateModule; replace it with exactly the TranslatePipe/TranslateDirective capabilities its template uses");
                    }
                    if (customPlugin && PLUGINS.contains(imported) && imported.equals(pluginBindings.get(local))) {
                        return mark(specifier, "v16+ tightened custom loader/compiler/parser/handler types; compile the implementation and verify Observable/error/fallback contracts");
                    }
                    return specifier;
                }));
                return visited.withImportClause(visited.getImportClause().withNamedBindings(marked));
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String clazz = NgxTranslateSupport.typeName(visited.getClazz());
                if (httpLoaderTypes.contains(clazz) && declarationCounts.getOrDefault(clazz, 0) == 0) {
                    return mark(visited, "ngx-translate 17 HTTP loader uses injection/provider configuration; preserve prefix, suffix, backend, interceptor and SSR behavior when replacing this constructor");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (isModuleConfiguration(visited)) {
                    return mark(visited, "TranslateModule configuration remains supported, but v17 functional providers change provider ordering, root/child scope, isolate/extend and initialization choices");
                }
                if (isProviderInvocation(visited)) {
                    return mark(visited, "Verify provideTranslateService provider ordering and nested loader/compiler/parser/missing-handler helpers; later duplicate providers can overwrite earlier configuration");
                }
                if (ownedReceiver(visited.getSelect()) && "use".equals(visited.getSimpleName())) {
                    return mark(visited, "ngx-translate 16 made rapid use() calls deterministic with the last request winning; test startup, route changes and concurrent language switches");
                }
                if (ownedReceiver(visited.getSelect()) && "getTranslation".equals(visited.getSimpleName())) {
                    return mark(visited, "getTranslation() is deprecated; choose get/instant/stream or a loader boundary according to caching and Observable semantics");
                }
                if (ownedReceiver(visited.getSelect()) && Set.of("setDefaultLang", "getDefaultLang").contains(visited.getSimpleName())) {
                    return mark(visited, "This proven TranslateService legacy method is renamed automatically to the v17 fallback-language API");
                }
                if ("emit".equals(visited.getSimpleName()) && eventSelect(visited.getSelect())) {
                    return mark(visited, "ngx-translate events are readonly Observables in v17; application code must not emit directly and should own a separate Subject if mutation is required");
                }
                if (ngxFile && visited.getSelect() == null && browserHelpers.contains(visited.getSimpleName()) &&
                    declarationCounts.getOrDefault(visited.getSimpleName(), 0) == 0) {
                    return mark(visited, "Browser-only globals/render hooks in ngx-translate initialization require an explicit Angular SSR/hydration execution boundary");
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall functionCall, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(functionCall, ctx);
                if (visited.getFunction() instanceof J.Identifier function && providerFunctions.contains(function.getSimpleName()) &&
                    declarationCounts.getOrDefault(function.getSimpleName(), 0) == 0) {
                    return mark(visited, "Verify provideTranslateService provider ordering and nested loader/compiler/parser/missing-handler helpers; later duplicate providers can overwrite earlier configuration");
                }
                if (ngxFile && visited.getFunction() instanceof J.Identifier function &&
                    browserHelpers.contains(function.getSimpleName()) &&
                    declarationCounts.getOrDefault(function.getSimpleName(), 0) == 0) {
                    return mark(visited, "Browser-only globals/render hooks in ngx-translate initialization require an explicit Angular SSR/hydration execution boundary");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!isDirectProviderOption()) return visited;
                String name = propertyName(visited.getName());
                if (Set.of("defaultLanguage", "useDefaultLang").contains(name)) {
                    return mark(visited, "Legacy default-language configuration needs fallbackLang/useFallbackLang migration at the owning forRoot/forChild/provider boundary");
                }
                if (RAW_PROVIDER_KEYS.contains(name)) {
                    return mark(visited, "Raw provider objects require v17 provider-order and helper review; preserve dependency injection, factory deps, isolate/extend and SSR semantics");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                if (ownedReceiver(visited.getTarget()) && Set.of("defaultLang", "currentLang", "langs").contains(visited.getSimpleName())) {
                    return mark(visited, "This TranslateService property is deprecated; use getFallbackLang(), getCurrentLang() or getLangs() while preserving null/initialization expectations");
                }
                if (ownedReceiver(visited.getTarget()) && "onDefaultLangChange".equals(visited.getSimpleName())) {
                    return mark(visited, "This proven TranslateService event is renamed automatically to onFallbackLangChange");
                }
                return visited;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (!ngxFile || isSyntaxName(visited) || declarationCounts.getOrDefault(visited.getSimpleName(), 0) != 0) {
                    return visited;
                }
                if (BROWSER_GLOBALS.contains(visited.getSimpleName()) || browserHelpers.contains(visited.getSimpleName())) {
                    return mark(visited, "Browser-only globals/render hooks in ngx-translate initialization require an explicit Angular SSR/hydration execution boundary");
                }
                return visited;
            }

            private void scan(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = NgxTranslateSupport.moduleName(visited);
                        if (module.startsWith("@ngx-translate/")) ngxFile = true;
                        if (visited.getImportClause() == null ||
                            !(visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named)) return visited;
                        for (JS.ImportSpecifier specifier : named.getElements()) {
                            String imported = NgxTranslateSupport.importedName(specifier);
                            String local = NgxTranslateSupport.localBinding(specifier);
                            if (NgxTranslateSupport.PACKAGE.equals(module)) {
                                if ("TranslateService".equals(imported)) {
                                    serviceTypes.add(local);
                                    if (!visited.getImportClause().isTypeOnly()) runtimeServiceTypes.add(local);
                                }
                                if ("TranslateModule".equals(imported)) moduleBindings.add(local);
                                if ("provideTranslateService".equals(imported)) providerFunctions.add(local);
                                if (PLUGINS.contains(imported)) pluginBindings.put(local, imported);
                            }
                            if (NgxTranslateSupport.HTTP_LOADER.equals(module) &&
                                "TranslateHttpLoader".equals(imported)) httpLoaderTypes.add(local);
                            if ("@angular/core".equals(module)) {
                                if ("inject".equals(imported) && !visited.getImportClause().isTypeOnly()) injectFunctions.add(local);
                                if ("Component".equals(imported)) componentBindings.add(local);
                                if (Set.of("afterNextRender", "isPlatformBrowser").contains(imported)) browserHelpers.add(local);
                            }
                            if ("@angular/common".equals(module) && "isPlatformBrowser".equals(imported)) {
                                browserHelpers.add(local);
                            }
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

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                                    ExecutionContext scanCtx) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                        declarationCounts.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                                    ExecutionContext scanCtx) {
                        JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, scanCtx);
                        declarationCounts.merge(visited.getName().getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration declaration,
                                                                       ExecutionContext scanCtx) {
                        J.MethodDeclaration visited = super.visitMethodDeclaration(declaration, scanCtx);
                        declarationCounts.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);

                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        if (declarationCounts.getOrDefault(visited.getSimpleName(), 0) != 1) return visited;
                        J.VariableDeclarations declarations = getCursor().firstEnclosing(J.VariableDeclarations.class);
                        boolean typed = declarations != null &&
                                        serviceTypes.contains(NgxTranslateSupport.typeName(declarations.getTypeExpression()));
                        boolean injected = isServiceInjection(visited.getInitializer());
                        if (typed || injected) {
                            if (isClassField(declarations)) fieldServiceReceivers.add(visited.getSimpleName());
                            else bareServiceReceivers.add(visited.getSimpleName());
                        }
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                                    ExecutionContext scanCtx) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                        if (isPluginType(visited.getExtends()) || visited.getImplements() != null &&
                            visited.getImplements().stream().anyMatch(this::isPluginType)) {
                            customPlugin = true;
                        }
                        for (J.Annotation annotation : visited.getLeadingAnnotations()) {
                            if (componentBindings.contains(annotation.getSimpleName()) &&
                                declarationCounts.getOrDefault(annotation.getSimpleName(), 0) == 0 &&
                                isStandaloneModuleComponent(annotation)) {
                                standaloneModule = true;
                            }
                        }
                        return visited;
                    }

                    private boolean isPluginType(TypeTree type) {
                        String local = NgxTranslateSupport.typeName(type);
                        String imported = pluginBindings.get(local);
                        return imported != null && PLUGINS.contains(imported) &&
                               declarationCounts.getOrDefault(local, 0) == 0;
                    }
                }.visit(cu, ctx);
            }

            private boolean isStandaloneModuleComponent(J.Annotation annotation) {
                for (Expression argument : annotation.getArguments()) {
                    if (!(argument instanceof J.NewClass object) || object.getClazz() != null || object.getBody() == null) continue;
                    boolean standalone = false;
                    boolean importsModule = false;
                    for (Statement statement : object.getBody().getStatements()) {
                        if (!(statement instanceof JS.PropertyAssignment property)) continue;
                        String name = propertyName(property.getName());
                        if ("standalone".equals(name) && property.getInitializer() instanceof J.Literal literal &&
                            Boolean.TRUE.equals(literal.getValue())) standalone = true;
                        if ("imports".equals(name) && containsModuleBinding(property.getInitializer())) importsModule = true;
                    }
                    if (standalone && importsModule) return true;
                }
                return false;
            }

            private boolean containsModuleBinding(Expression expression) {
                boolean[] found = {false};
                new JavaScriptIsoVisitor<Integer>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Integer ignored) {
                        J.Identifier visited = super.visitIdentifier(identifier, ignored);
                        if (moduleBindings.contains(visited.getSimpleName()) &&
                            declarationCounts.getOrDefault(visited.getSimpleName(), 0) == 0) found[0] = true;
                        return visited;
                    }
                }.visit(expression, 0);
                return found[0];
            }

            private boolean isServiceInjection(Expression initializer) {
                if (initializer instanceof J.MethodInvocation call) {
                    return call.getSelect() == null && injectFunctions.contains(call.getSimpleName()) &&
                           declarationCounts.getOrDefault(call.getSimpleName(), 0) == 0 &&
                           hasRuntimeServiceArgument(call.getArguments());
                }
                if (initializer instanceof JS.FunctionCall call && call.getFunction() instanceof J.Identifier function) {
                    return injectFunctions.contains(function.getSimpleName()) &&
                           declarationCounts.getOrDefault(function.getSimpleName(), 0) == 0 &&
                           hasRuntimeServiceArgument(call.getArguments());
                }
                return false;
            }

            private boolean hasRuntimeServiceArgument(List<Expression> arguments) {
                return !arguments.isEmpty() && runtimeServiceTypes.contains(NgxTranslateSupport.typeName(arguments.get(0)));
            }

            private boolean isClassField(J.VariableDeclarations declarations) {
                if (declarations == null) return false;
                if (!declarations.getModifiers().isEmpty()) return true;
                return getCursor().firstEnclosing(J.ClassDeclaration.class) != null &&
                       getCursor().firstEnclosing(J.MethodDeclaration.class) == null;
            }

            private boolean isModuleConfiguration(J.MethodInvocation invocation) {
                if (!Set.of("forRoot", "forChild").contains(invocation.getSimpleName()) ||
                    !(invocation.getSelect() instanceof J.Identifier module)) return false;
                return moduleBindings.contains(module.getSimpleName()) &&
                       declarationCounts.getOrDefault(module.getSimpleName(), 0) == 0;
            }

            private boolean isProviderInvocation(J.MethodInvocation invocation) {
                return invocation.getSelect() == null && providerFunctions.contains(invocation.getSimpleName()) &&
                       declarationCounts.getOrDefault(invocation.getSimpleName(), 0) == 0;
            }

            private boolean isDirectProviderOption() {
                Cursor cursor = getCursor().getParent();
                while (cursor != null && !(cursor.getValue() instanceof J.NewClass)) {
                    if (cursor.getValue() instanceof JS.PropertyAssignment ||
                        cursor.getValue() instanceof JS.CompilationUnit) return false;
                    cursor = cursor.getParent();
                }
                if (cursor == null || ((J.NewClass) cursor.getValue()).getClazz() != null) return false;
                J.NewClass options = (J.NewClass) cursor.getValue();
                cursor = cursor.getParent();
                while (cursor != null &&
                       (cursor.getValue() instanceof JRightPadded<?> || cursor.getValue() instanceof JContainer<?>)) {
                    cursor = cursor.getParent();
                }
                if (cursor == null) return false;
                if (cursor.getValue() instanceof J.MethodInvocation invocation &&
                    invocation.getArguments().stream().anyMatch(argument -> argument == options)) {
                    return isModuleConfiguration(invocation) || isProviderInvocation(invocation);
                }
                if (cursor.getValue() instanceof JS.FunctionCall call &&
                    call.getArguments().stream().anyMatch(argument -> argument == options) &&
                    call.getFunction() instanceof J.Identifier function) {
                    return providerFunctions.contains(function.getSimpleName()) &&
                           declarationCounts.getOrDefault(function.getSimpleName(), 0) == 0;
                }
                return false;
            }

            private boolean ownedReceiver(Expression receiver) {
                if (receiver instanceof J.Identifier identifier) {
                    return bareServiceReceivers.contains(identifier.getSimpleName());
                }
                return receiver instanceof J.FieldAccess access && access.getTarget() instanceof J.Identifier target &&
                       "this".equals(target.getSimpleName()) && fieldServiceReceivers.contains(access.getSimpleName());
            }

            private boolean eventSelect(Expression select) {
                return select instanceof J.FieldAccess access && EVENTS.contains(access.getSimpleName()) &&
                       ownedReceiver(access.getTarget());
            }

            private boolean isSyntaxName(J.Identifier identifier) {
                if (getCursor().firstEnclosing(JS.ImportSpecifier.class) != null) return true;
                Cursor parent = getCursor().getParentTreeCursor();
                if (parent == null) return false;
                Object value = parent.getValue();
                return value instanceof JS.PropertyAssignment property && property.getName() == identifier ||
                       value instanceof J.FieldAccess access && access.getName() == identifier ||
                       value instanceof J.MethodInvocation invocation && invocation.getName() == identifier ||
                       value instanceof J.VariableDeclarations.NamedVariable variable && variable.getName() == identifier ||
                       value instanceof J.MethodDeclaration method && method.getName() == identifier ||
                       value instanceof J.ClassDeclaration declaration && declaration.getName() == identifier ||
                       value instanceof JS.TypeDeclaration declaration && declaration.getName() == identifier;
            }

            private State state() {
                return new State(ngxFile, standaloneModule, customPlugin, serviceTypes, runtimeServiceTypes,
                        bareServiceReceivers, fieldServiceReceivers, moduleBindings, providerFunctions,
                        httpLoaderTypes, injectFunctions, componentBindings, browserHelpers, pluginBindings,
                        declarationCounts);
            }

            private void restore(State state) {
                ngxFile = state.ngxFile;
                standaloneModule = state.standaloneModule;
                customPlugin = state.customPlugin;
                serviceTypes = state.serviceTypes;
                runtimeServiceTypes = state.runtimeServiceTypes;
                bareServiceReceivers = state.bareServiceReceivers;
                fieldServiceReceivers = state.fieldServiceReceivers;
                moduleBindings = state.moduleBindings;
                providerFunctions = state.providerFunctions;
                httpLoaderTypes = state.httpLoaderTypes;
                injectFunctions = state.injectFunctions;
                componentBindings = state.componentBindings;
                browserHelpers = state.browserHelpers;
                pluginBindings = state.pluginBindings;
                declarationCounts = state.declarationCounts;
            }
        };
    }

    private record State(boolean ngxFile, boolean standaloneModule, boolean customPlugin,
                         Set<String> serviceTypes, Set<String> runtimeServiceTypes,
                         Set<String> bareServiceReceivers, Set<String> fieldServiceReceivers,
                         Set<String> moduleBindings, Set<String> providerFunctions,
                         Set<String> httpLoaderTypes, Set<String> injectFunctions,
                         Set<String> componentBindings, Set<String> browserHelpers,
                         Map<String, String> pluginBindings, Map<String, Integer> declarationCounts) { }

    private static String propertyName(Expression expression) {
        if (expression instanceof J.Identifier identifier) return identifier.getSimpleName();
        if (expression instanceof J.Literal literal) return String.valueOf(literal.getValue());
        return "";
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return SearchResult.found(tree, message);
    }
}
