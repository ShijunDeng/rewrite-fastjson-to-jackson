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

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Marks Router nodes whose migration depends on application navigation semantics. */
public final class FindAngularRouterTypeScriptRisks extends Recipe {
    private static final Set<String> GUARD_RESOLVER_APIS = Set.of(
            "CanActivate", "CanActivateChild", "CanDeactivate", "CanLoad", "CanMatch", "Resolve",
            "CanActivateFn", "CanActivateChildFn", "CanDeactivateFn", "CanLoadFn", "CanMatchFn", "ResolveFn",
            "DeprecatedGuard", "DeprecatedResolve", "GuardResult", "MaybeAsync"
    );
    private static final Set<String> NAVIGATION_APIS = Set.of(
            "Navigation", "NavigationStart", "NavigationEnd", "NavigationCancel", "NavigationError",
            "NavigationSkipped", "NavigationCancellationCode", "NavigationSkippedCode", "Scroll", "Event"
    );
    private static final Set<String> URL_REDIRECT_APIS = Set.of(
            "UrlTree", "RedirectCommand", "RedirectFunction", "NavigationBehaviorOptions", "UrlSerializer"
    );
    private static final Set<String> REUSE_APIS = Set.of(
            "RouteReuseStrategy", "BaseRouteReuseStrategy", "DetachedRouteHandle", "UrlHandlingStrategy",
            "TitleStrategy"
    );
    private static final Set<String> ROUTER_OPTION_KEYS = Set.of(
            "initialNavigation", "onSameUrlNavigation", "paramsInheritanceStrategy", "urlUpdateStrategy",
            "canceledNavigationResolution", "errorHandler", "malformedUriErrorHandler", "relativeLinkResolution"
    );
    private static final Set<String> REMOVED_ROUTER_PROPERTIES = Set.of(
            "errorHandler", "urlHandlingStrategy", "canceledNavigationResolution", "paramsInheritanceStrategy",
            "titleStrategy", "urlUpdateStrategy", "malformedUriErrorHandler", "routeReuseStrategy"
    );
    private static final Set<String> NAVIGATION_METHODS = Set.of(
            "navigate", "navigateByUrl", "createUrlTree", "resetConfig", "initialNavigation"
    );
    private static final Set<String> ROUTER_PROVIDER_APIS = Set.of(
            "provideRouter", "provideRoutes", "withEnabledBlockingInitialNavigation",
            "withDisabledInitialNavigation", "withHashLocation", "withInMemoryScrolling", "withPreloading",
            "withComponentInputBinding", "withRouterConfig", "withNavigationErrorHandler",
            "withViewTransitions", "withDebugTracing"
    );

    @Override
    public String getDisplayName() {
        return "Find Angular Router 20 TypeScript migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks navigation lifecycle, guards/resolvers, redirects, lazy loading, UrlTree, route reuse, " +
               "provider configuration, SSR/hydration, and testing decisions at exact TypeScript AST nodes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            private Set<String> routerTypes = Set.of();
            private Set<String> routerVariables = Set.of();
            private Set<String> routerModules = Set.of();
            private Set<String> routerProviderFunctions = Set.of();
            private Set<String> hydrationFunctions = Set.of();
            private boolean hasRoutesImport;
            private boolean hasRouterImport;

            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                Set<String> previousTypes = routerTypes;
                Set<String> previousVariables = routerVariables;
                Set<String> previousModules = routerModules;
                Set<String> previousProviders = routerProviderFunctions;
                Set<String> previousHydration = hydrationFunctions;
                boolean previousRoutes = hasRoutesImport;
                boolean previousRouter = hasRouterImport;
                routerTypes = new HashSet<>();
                routerVariables = new HashSet<>();
                routerModules = new HashSet<>();
                routerProviderFunctions = new HashSet<>();
                hydrationFunctions = new HashSet<>();
                hasRoutesImport = false;
                hasRouterImport = false;
                JS.CompilationUnit visited = super.visitJsCompilationUnit(cu, ctx);
                routerTypes = previousTypes;
                routerVariables = previousVariables;
                routerModules = previousModules;
                routerProviderFunctions = previousProviders;
                hydrationFunctions = previousHydration;
                hasRoutesImport = previousRoutes;
                hasRouterImport = previousRouter;
                return visited;
            }

            @Override
            public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext ctx) {
                JS.Import visited = super.visitImportDeclaration(declaration, ctx);
                String module = MigrateDeterministicAngularRouterSource.moduleName(visited);
                if ("@angular/router".equals(module)) {
                    addAlias(visited, "Router", routerTypes);
                    addAlias(visited, "RouterModule", routerModules);
                    hasRouterImport |= imported(visited, "Router");
                    hasRoutesImport |= imported(visited, "Routes") || imported(visited, "Route");
                    for (String api : ROUTER_PROVIDER_APIS) {
                        addAlias(visited, api, routerProviderFunctions);
                    }
                    if (imported(visited, "RouterLinkWithHref")) {
                        return SearchResult.found(visited, "RouterLinkWithHref was merged into RouterLink; migrate the imported type/directive and verify wrapper tests and href behavior");
                    }
                    String guard = firstImported(visited, GUARD_RESOLVER_APIS);
                    if (guard != null) {
                        return SearchResult.found(visited, guard + " participates in guard/resolver first-emission, empty completion, cancellation, DI context, and UrlTree/RedirectCommand semantics; verify every route outcome");
                    }
                    String navigation = firstImported(visited, NAVIGATION_APIS);
                    if (navigation != null) {
                        return SearchResult.found(visited, navigation + " navigation lifecycle handling must distinguish end, cancel, error, skipped, redirect, and concurrent navigation outcomes");
                    }
                    String redirect = firstImported(visited, URL_REDIRECT_APIS);
                    if (redirect != null) {
                        return SearchResult.found(visited, redirect + " redirect/URL behavior requires explicit replaceUrl, skipLocationChange, query/matrix params, relative outlets, and error handling decisions");
                    }
                    String reuse = firstImported(visited, REUSE_APIS);
                    if (reuse != null) {
                        return SearchResult.found(visited, reuse + " changes route state lifetime and provider/component reuse; verify detach handles, params, titles, lazy injectors, and cleanup");
                    }
                }
                if ("@angular/router/testing".equals(module) && imported(visited, "RouterTestingModule")) {
                    return SearchResult.found(visited, "RouterTestingModule is deprecated; use provideRouter/RouterTestingHarness and verify real async navigation, redirects, guards, resolvers, and location behavior");
                }
                if ("@angular/platform-browser".equals(module)) {
                    addAlias(visited, "provideClientHydration", hydrationFunctions);
                    addAlias(visited, "withEventReplay", hydrationFunctions);
                    addAlias(visited, "withIncrementalHydration", hydrationFunctions);
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                    ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(declarations, ctx);
                if (routerTypes.contains(typeName(visited.getTypeExpression()))) {
                    for (J.VariableDeclarations.NamedVariable variable : visited.getVariables()) {
                        routerVariables.add(variable.getSimpleName());
                    }
                }
                return visited;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
                    J.VariableDeclarations.NamedVariable variable, ExecutionContext ctx) {
                J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, ctx);
                if (isInjectRouter(visited.getInitializer())) {
                    routerVariables.add(visited.getSimpleName());
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                String name = visited.getSimpleName();
                if (visited.getSelect() == null && routerProviderFunctions.contains(name)) {
                    return SearchResult.found(visited, routerProviderMessage(name));
                }
                if (visited.getSelect() == null && hydrationFunctions.contains(name) && hasRouterImport) {
                    return SearchResult.found(visited, "Hydration/event replay interacts with initial navigation, redirects, router events, scroll restoration, and lazy routes; verify server/client parity");
                }
                if (("getCurrentNavigation".equals(name) || "currentNavigation".equals(name)) &&
                    isLikelyRouter(visited.getSelect())) {
                    return SearchResult.found(visited, "Current navigation exists only during an active navigation; update signal reads/state capture and verify redirect, cancellation, reload, and page-refresh lifecycles");
                }
                if (NAVIGATION_METHODS.contains(name) && isLikelyRouter(visited.getSelect())) {
                    return SearchResult.found(visited, "Router." + name + " needs application-specific relative URL, history, redirect, cancellation, concurrent navigation, and promise rejection tests");
                }
                if (("forRoot".equals(name) || "forChild".equals(name)) &&
                    visited.getSelect() instanceof J.Identifier identifier &&
                    routerModules.contains(identifier.getSimpleName())) {
                    String message = isServerEntryPoint()
                            ? "Server RouterModule configuration must match client routes and blocking initial navigation to avoid redirects or hydration mismatch"
                            : "RouterModule." + name + " configuration needs provider-feature, initial navigation, scrolling, preloading, error, and lazy-injector review";
                    return SearchResult.found(visited, message);
                }
                return visited;
            }

            @Override
            public JS.FunctionCall visitFunctionCall(JS.FunctionCall call, ExecutionContext ctx) {
                JS.FunctionCall visited = super.visitFunctionCall(call, ctx);
                String name = MigrateDeterministicAngularRouterSource.expressionName(visited.getFunction());
                if (routerProviderFunctions.contains(name)) {
                    return SearchResult.found(visited, routerProviderMessage(name));
                }
                if (hydrationFunctions.contains(name) && hasRouterImport) {
                    return SearchResult.found(visited, "Hydration/event replay interacts with initial navigation, redirects, router events, scroll restoration, and lazy routes; verify server/client parity");
                }
                return visited;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess field, ExecutionContext ctx) {
                J.FieldAccess visited = super.visitFieldAccess(field, ctx);
                String name = visited.getSimpleName();
                if (("getCurrentNavigation".equals(name) || "currentNavigation".equals(name)) &&
                    isLikelyRouter(visited.getTarget())) {
                    return SearchResult.found(visited, "Current navigation property/function reference changes to a signal; preserve invocation/reactivity and active-navigation lifetime");
                }
                if (REMOVED_ROUTER_PROPERTIES.contains(name) && isLikelyRouter(visited.getTarget())) {
                    return SearchResult.found(visited, "Router." + name + " is removed/deprecated as a writable property; configure it through DI, provideRouter features, or RouterModule options and verify behavior");
                }
                return visited;
            }

            @Override
            public JS.PropertyAssignment visitPropertyAssignment(JS.PropertyAssignment property,
                                                                  ExecutionContext ctx) {
                JS.PropertyAssignment visited = super.visitPropertyAssignment(property, ctx);
                if (!hasRoutesImport && routerModules.isEmpty()) {
                    return visited;
                }
                String name = MigrateDeterministicAngularRouterSource.expressionName(visited.getName());
                if ("loadChildren".equals(name) && visited.getInitializer() instanceof J.Literal literal &&
                    literal.getValue() instanceof String) {
                    return SearchResult.found(visited, "String loadChildren syntax was removed; choose a dynamic import target/export and verify lazy chunk failures, preloading, base href, and deployment paths");
                }
                if ("loadChildren".equals(name) || "loadComponent".equals(name)) {
                    return SearchResult.found(visited, name + " runs in the route injection context; verify export shape, lazy providers, preloading, chunk errors, SSR discovery, and deployment paths");
                }
                if (name.startsWith("can") || "resolve".equals(name) || "runGuardsAndResolvers".equals(name)) {
                    return SearchResult.found(visited, name + " requires deliberate functional/class guard semantics, DI context, first emission, empty/error/cancel handling, and redirect behavior");
                }
                if ("redirectTo".equals(name)) {
                    return SearchResult.found(visited, "redirectTo may be absolute, relative, wildcard, parameterized, or a RedirectFunction; prevent loops and verify query/matrix params, outlets, replaceUrl, SSR, and authorization");
                }
                if ("providers".equals(name) && hasRoutesImport) {
                    return SearchResult.found(visited, "Route-level providers define lazy injector lifetime and no longer inherit through RouterOutlet component providers; verify service scope and cleanup");
                }
                if ("pathMatch".equals(name)) {
                    return SearchResult.found(visited, "Route.pathMatch typing is stricter; keep Routes/Route attribution and verify empty-path, prefix/full, wildcard, child, and redirect matching");
                }
                if (ROUTER_OPTION_KEYS.contains(name)) {
                    return SearchResult.found(visited, name + " Router option changed or moved to provider features; select the Angular 20 behavior and test history, errors, initial navigation, and relative links");
                }
                return visited;
            }

            private boolean isInjectRouter(Expression initializer) {
                if (initializer instanceof JS.FunctionCall call) {
                    return "inject".equals(MigrateDeterministicAngularRouterSource.expressionName(call.getFunction())) &&
                           hasRouterArgument(call.getArguments());
                }
                if (initializer instanceof J.MethodInvocation invocation) {
                    return "inject".equals(invocation.getSimpleName()) && invocation.getSelect() == null &&
                           hasRouterArgument(invocation.getArguments());
                }
                return false;
            }

            private boolean hasRouterArgument(java.util.List<Expression> arguments) {
                return !arguments.isEmpty() &&
                       routerTypes.contains(MigrateDeterministicAngularRouterSource.expressionName(arguments.get(0)));
            }

            private boolean isLikelyRouter(Expression select) {
                if (select instanceof J.Identifier identifier) {
                    String name = identifier.getSimpleName();
                    return routerVariables.contains(name) || hasRouterImport && "router".equalsIgnoreCase(name);
                }
                if (select instanceof J.FieldAccess access) {
                    String name = access.getSimpleName();
                    return routerVariables.contains(name) || hasRouterImport && "router".equalsIgnoreCase(name);
                }
                return false;
            }

            private Path sourcePath() {
                return getCursor().firstEnclosingOrThrow(JS.CompilationUnit.class).getSourcePath();
            }

            private boolean isServerEntryPoint() {
                Path file = sourcePath().getFileName();
                return file != null && "main.server.ts".equals(file.toString());
            }

            private boolean isServerLikePath() {
                String path = sourcePath().toString().toLowerCase();
                return path.contains("server") || path.contains("ssr");
            }

            private boolean isTestFile() {
                String path = sourcePath().toString();
                return path.endsWith(".spec.ts") || path.endsWith(".test.ts");
            }

            private String routerProviderMessage(String name) {
                if (isTestFile()) {
                    return name + " in a test must preserve provider order and exercise real asynchronous guards, resolvers, redirects, and location state";
                }
                if (isServerEntryPoint() || "provideRouter".equals(name) && isServerLikePath()) {
                    return "Server/client provideRouter configuration and blocking initial navigation must agree before hydration";
                }
                return name + " affects Router provider ordering, initial navigation, history, scrolling, preloading, errors, or component inputs; verify the selected feature combination";
            }
        };
    }

    private static boolean imported(JS.Import declaration, String name) {
        return MigrateDeterministicAngularRouterSource.importedAlias(declaration, name) != null;
    }

    private static void addAlias(JS.Import declaration, String name, Set<String> aliases) {
        String alias = MigrateDeterministicAngularRouterSource.importedAlias(declaration, name);
        if (alias != null) {
            aliases.add(alias);
        }
    }

    private static String firstImported(JS.Import declaration, Set<String> candidates) {
        for (String candidate : candidates) {
            if (imported(declaration, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String typeName(TypeTree type) {
        if (type instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (type instanceof J.FieldAccess access) {
            return access.getSimpleName();
        }
        if (type instanceof JS.TypeTreeExpression expression) {
            return MigrateDeterministicAngularRouterSource.expressionName(expression.getExpression());
        }
        if (type instanceof JS.TypeInfo typeInfo) {
            return typeName(typeInfo.getTypeIdentifier());
        }
        return "";
    }
}
