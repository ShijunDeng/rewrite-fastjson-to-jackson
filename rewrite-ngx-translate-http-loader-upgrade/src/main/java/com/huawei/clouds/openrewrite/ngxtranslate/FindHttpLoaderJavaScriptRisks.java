package com.huawei.clouds.openrewrite.ngxtranslate;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Locate source constructs whose HTTP loader 17 migration requires application context. */
public final class FindHttpLoaderJavaScriptRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find ngx-translate HTTP loader 17 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark unresolved constructor/provider shapes, functional provider behavior, custom loader " +
               "contracts, dynamic paths, cache/interceptor switches, HTTP registration and deep imports.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private boolean loaderFile;
            private boolean customLoader;
            private Set<String> loaderTypes = Set.of();
            private Set<String> loaderInterfaces = Set.of();
            private Set<String> providerFunctions = Set.of();
            private Set<String> httpProviderFunctions = Set.of();
            private Set<String> httpModules = Set.of();

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!HttpLoaderSupport.isProjectPath(cu.getSourcePath())) {
                    return cu;
                }
                boolean previousFile = loaderFile;
                boolean previousCustom = customLoader;
                Set<String> previousTypes = loaderTypes;
                Set<String> previousInterfaces = loaderInterfaces;
                Set<String> previousProviders = providerFunctions;
                Set<String> previousHttpProviders = httpProviderFunctions;
                Set<String> previousHttpModules = httpModules;
                loaderFile = false;
                customLoader = false;
                loaderTypes = new HashSet<>();
                loaderInterfaces = new HashSet<>();
                providerFunctions = new HashSet<>();
                httpProviderFunctions = new HashSet<>();
                httpModules = new HashSet<>();
                scan(cu, ctx);
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                loaderFile = previousFile;
                customLoader = previousCustom;
                loaderTypes = previousTypes;
                loaderInterfaces = previousInterfaces;
                providerFunctions = previousProviders;
                httpProviderFunctions = previousHttpProviders;
                httpModules = previousHttpModules;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = HttpLoaderSupport.moduleName(visited);
                if (module.startsWith(HttpLoaderSupport.HTTP_LOADER + "/")) {
                    return mark(visited, "Private @ngx-translate/http-loader deep imports are unstable; use the root public entry point");
                }
                if (loaderFile && HttpLoaderSupport.ANGULAR_HTTP.equals(module) &&
                    containsLocal(visited, httpModules)) {
                    return mark(visited, "Verify that HTTP is registered exactly once in the root environment and that loader requests retain interceptors, transfer cache and SSR behavior");
                }
                if (customLoader && HttpLoaderSupport.CORE.equals(module) &&
                    containsLocal(visited, loaderInterfaces)) {
                    return mark(visited, "Custom TranslateLoader implementations must satisfy the v17 TranslationObject Observable and error/fallback contract under strict typecheck");
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (loaderTypes.contains(HttpLoaderSupport.compact(visited.getClazz()))) {
                    return mark(visited, "TranslateHttpLoader v17 has an injection-only zero-argument constructor; replace direct construction with provideTranslateHttpLoader in an Angular provider context");
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (visited.getSelect() == null && providerFunctions.contains(visited.getSimpleName())) {
                    return mark(visited, "Verify provideTranslateHttpLoader nesting and provider order; later TranslateLoader providers can overwrite this configuration");
                }
                if (loaderFile && visited.getSelect() == null &&
                    httpProviderFunctions.contains(visited.getSimpleName())) {
                    return mark(visited, "Verify root provideHttpClient features, interceptor ordering, SSR transfer cache and duplicate HTTP registration");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!loaderFile) return visited;
                String name = HttpLoaderSupport.propertyName(visited.getName());
                if ("loader".equals(name) && rawLoaderProvider(visited)) {
                    return mark(visited, "Raw TranslateLoader providers require an explicit v17 provider-helper migration while preserving factory deps, scope and initialization order");
                }
                if (Set.of("useHttpBackend", "enforceLoading").contains(name) &&
                    trueLiteral(visited.getInitializer()) && insideHttpLoaderProvider()) {
                    String effect = "useHttpBackend".equals(name)
                            ? "This bypasses HttpClient interceptors; verify authentication, tenant headers, retries, tracing and error handling"
                            : "This adds a timestamp to every request; verify browser/CDN caching, traffic and offline behavior";
                    return mark(visited, effect);
                }
                if (Set.of("prefix", "suffix").contains(name) && insideHttpLoaderProvider() &&
                    !staticString(visited.getInitializer())) {
                    return mark(visited, "Dynamic translation URL configuration requires deployment-base, SSR, escaping and cache-key review");
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                             ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                if (customLoader && implementsLoader(visited)) {
                    return mark(visited, "Compile this custom TranslateLoader against v17 and verify TranslationObject, Observable completion and error/fallback behavior");
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                               ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                if (customLoader && "getTranslation".equals(visited.getSimpleName())) {
                    return mark(visited, "TranslateLoader.getTranslation must return Observable<TranslationObject>; review any, unknown, null and error branches");
                }
                return visited;
            }

            private void scan(JS.CompilationUnit cu, ExecutionContext ctx) {
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        String module = HttpLoaderSupport.moduleName(visited);
                        if (HttpLoaderSupport.HTTP_LOADER.equals(module) ||
                            module.startsWith(HttpLoaderSupport.HTTP_LOADER + "/")) loaderFile = true;
                        if (visited.getImportClause() == null ||
                            !(visited.getImportClause().getNamedBindings() instanceof JS.NamedImports named)) {
                            return visited;
                        }
                        for (JS.ImportSpecifier specifier : named.getElements()) {
                            String imported = HttpLoaderSupport.importedName(specifier);
                            String local = HttpLoaderSupport.localName(specifier);
                            if (HttpLoaderSupport.HTTP_LOADER.equals(module)) {
                                if ("TranslateHttpLoader".equals(imported)) loaderTypes.add(local);
                                if ("provideTranslateHttpLoader".equals(imported)) providerFunctions.add(local);
                            }
                            if (HttpLoaderSupport.CORE.equals(module) && "TranslateLoader".equals(imported)) {
                                loaderInterfaces.add(local);
                            }
                            if (HttpLoaderSupport.ANGULAR_HTTP.equals(module)) {
                                if ("provideHttpClient".equals(imported)) httpProviderFunctions.add(local);
                                if ("HttpClientModule".equals(imported)) httpModules.add(local);
                            }
                        }
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                                     ExecutionContext scanCtx) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, scanCtx);
                        if (implementsLoader(visited)) customLoader = true;
                        return visited;
                    }
                }.visit(cu, ctx);
            }

            private boolean implementsLoader(J.ClassDeclaration declaration) {
                for (String type : loaderInterfaces) {
                    if (declaration.getExtends() != null &&
                        type.equals(HttpLoaderSupport.compact(declaration.getExtends()))) return true;
                    if (declaration.getImplements() != null && declaration.getImplements().stream()
                            .anyMatch(implemented -> type.equals(HttpLoaderSupport.compact(implemented)))) return true;
                }
                return false;
            }

            private boolean rawLoaderProvider(JS.PropertyAssignment property) {
                String compact = property.printTrimmed(getCursor()).replaceAll("\\s+", "");
                for (String type : loaderInterfaces) {
                    if (compact.contains("provide:" + type) &&
                        (compact.contains("useFactory:") || compact.contains("useClass:") ||
                         compact.contains("useValue:") || compact.contains("useExisting:"))) return true;
                }
                return false;
            }

            private boolean insideHttpLoaderProvider() {
                return getCursor().getPathAsStream().anyMatch(value ->
                        value instanceof J.MethodInvocation invocation && invocation.getSelect() == null &&
                        providerFunctions.contains(invocation.getSimpleName()));
            }
        };
    }

    private static boolean containsLocal(JS.Import declaration, Set<String> names) {
        return declaration.getImportClause() != null &&
               declaration.getImportClause().getNamedBindings() instanceof JS.NamedImports named &&
               named.getElements().stream().anyMatch(specifier ->
                       names.contains(HttpLoaderSupport.localName(specifier)));
    }

    private static boolean trueLiteral(Expression expression) {
        return expression instanceof J.Literal literal && Boolean.TRUE.equals(literal.getValue());
    }

    private static boolean staticString(Expression expression) {
        if (expression instanceof J.Literal literal && literal.getValue() instanceof String) return true;
        if (expression instanceof JS.TemplateExpression template && template.getSpans().isEmpty()) return true;
        String rendered = expression.toString().trim();
        return rendered.matches("'(?:[^'\\\\]|\\\\.)*'") ||
               rendered.matches("\"(?:[^\"\\\\]|\\\\.)*\"") ||
               rendered.matches("`[^`$]*`");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return SearchResult.found(tree, message);
    }
}
