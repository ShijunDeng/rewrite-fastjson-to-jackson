package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class AngularRouterRiskMarkerTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksPinnedNgRxRouterTestingModuleFixture() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularRouterTypeScriptRisks()),
                typescript(
                        "import { RouterTestingModule } from '@angular/router/testing';\n",
                        "/*~~(RouterTestingModule is deprecated; use provideRouter/RouterTestingHarness and verify real async navigation, redirects, guards, resolvers, and location behavior)~~>*/import { RouterTestingModule } from '@angular/router/testing';\n",
                        source -> source.path("ngrx/platform/modules/router-store/spec/utils.ts")
                )
        );
    }

    @Test
    void marksPinnedWebDbRouterLinkWithHrefFixture() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularRouterTypeScriptRisks()),
                typescript(
                        "import { RouterLink, RouterLinkWithHref } from '@angular/router';\n",
                        "/*~~(RouterLinkWithHref was merged into RouterLink; migrate the imported type/directive and verify wrapper tests and href behavior)~~>*/import { RouterLink, RouterLinkWithHref } from '@angular/router';\n",
                        source -> source.path("WebDB-App/app/front/src/shared/shared.module.ts")
                )
        );
    }

    @Test
    void marksPinnedCanLoadGuardImportAndRouteProperty() {
        assertTypeScriptMarker(
                "import { CanLoad, Route, Router } from '@angular/router';\n",
                "guard/resolver first-emission",
                "hantsy/spring-microservice-sample/ui/src/app/core/load-guard.ts"
        );
        assertTypeScriptMarker(
                "import { Routes } from '@angular/router';\nconst routes: Routes = [{path: 'admin', canLoad: [AuthGuard]}];\n",
                "canLoad requires deliberate",
                "src/app.routes.ts"
        );
    }

    @Test
    void marksPinnedLegacyStringLazyLoadingAtTheProperty() {
        assertTypeScriptMarker(
                "import { Route } from '@angular/router';\nexport const routes: Route[] = [{ loadChildren: 'app/dashboard/dashboard.module#DashboardModule', path: 'dashboard' }];\n",
                "String loadChildren syntax was removed",
                "vladotesanovic/angular2-express-starter/src/app/app.router.ts"
        );
    }

    @Test
    void marksDynamicLazyLoadingAndRouteProviders() {
        assertTypeScriptMarker(
                "import { Routes } from '@angular/router';\nconst routes: Routes = [{path: 'admin', loadChildren: () => import('./admin.routes'), providers: [AdminService]}];\n",
                "runs in the route injection context",
                "src/app.routes.ts"
        );
        assertTypeScriptMarker(
                "import { Routes } from '@angular/router';\nconst routes: Routes = [{path: 'admin', providers: [AdminService]}];\n",
                "Route-level providers define lazy injector lifetime",
                "src/providers.routes.ts"
        );
    }

    @Test
    void marksRedirectAndPathMatchChoices() {
        assertTypeScriptMarker(
                "import { Routes } from '@angular/router';\nconst routes: Routes = [{path: '', redirectTo: '/home', pathMatch: 'full'}];\n",
                "prevent loops",
                "src/redirect.routes.ts"
        );
        assertTypeScriptMarker(
                "import { Routes } from '@angular/router';\nconst routes: Routes = [{path: '', pathMatch: 'full'}];\n",
                "Route.pathMatch typing is stricter",
                "src/path.routes.ts"
        );
    }

    @Test
    void marksUrlTreeRedirectCommandAndReuseStrategyImports() {
        assertTypeScriptMarker(
                "import { UrlTree, RedirectCommand } from '@angular/router';\n",
                "redirect/URL behavior",
                "src/redirect.guard.ts"
        );
        assertTypeScriptMarker(
                "import { RouteReuseStrategy, DetachedRouteHandle } from '@angular/router';\n",
                "route state lifetime",
                "src/reuse.ts"
        );
    }

    @Test
    void marksNavigationEventsAndCurrentNavigationLifecycle() {
        assertTypeScriptMarker(
                "import { NavigationStart, NavigationCancel, NavigationSkipped } from '@angular/router';\n",
                "navigation lifecycle handling",
                "src/navigation-events.ts"
        );
        assertTypeScriptMarker(
                "import { Router } from '@angular/router';\nfunction read(router: any) { return router.getCurrentNavigation(); }\n",
                "Current navigation exists only during an active navigation",
                "aitboudad/ngx-loading-bar/packages/router/src/router.service.ts"
        );
    }

    @Test
    void marksNavigationAndUrlCreationCalls() {
        assertTypeScriptMarker(
                "import { Router } from '@angular/router';\nfunction go(router: Router) { return router.navigate(['team', id], {relativeTo: route}); }\n",
                "relative URL, history, redirect",
                "src/navigation.ts"
        );
        assertTypeScriptMarker(
                "import { Router } from '@angular/router';\nfunction url(router: Router) { return router.createUrlTree([], {queryParamsHandling: 'merge'}); }\n",
                "Router.createUrlTree",
                "src/url.ts"
        );
    }

    @Test
    void marksRemovedWritableRouterProperties() {
        assertTypeScriptMarker(
                "import { Router } from '@angular/router';\nfunction setup(router: Router) { router.errorHandler = handleError; }\n",
                "removed/deprecated as a writable property",
                "src/router-options.ts"
        );
    }

    @Test
    void marksPinnedRelativeLinkResolutionAndRouterModuleProviderChoices() {
        assertTypeScriptMarker(
                "import { Routes, RouterModule } from '@angular/router';\nconst routes: Routes = [];\nRouterModule.forRoot(routes, {relativeLinkResolution: 'legacy'});\n",
                "RouterModule.forRoot configuration",
                "mathisGarberg/angular-folder-structure/src/app/app-routing.module.ts"
        );
        assertTypeScriptMarker(
                "import { RouterModule } from '@angular/router';\nRouterModule.forRoot(routes, {relativeLinkResolution: 'legacy'});\n",
                "relativeLinkResolution Router option changed",
                "src/legacy-options.ts"
        );
    }

    @Test
    void marksProvideRouterFeaturesAndTestProviderOrder() {
        assertTypeScriptMarker(
                "import { provideRouter, withEnabledBlockingInitialNavigation, withInMemoryScrolling } from '@angular/router';\nconst providers = [provideRouter(routes, withEnabledBlockingInitialNavigation(), withInMemoryScrolling({scrollPositionRestoration: 'enabled'}))];\n",
                "affects Router provider ordering",
                "src/app.config.ts"
        );
        assertTypeScriptMarker(
                "import { provideRouter } from '@angular/router';\nconst providers = [provideRouter(routes)];\n",
                "in a test must preserve provider order",
                "src/app.spec.ts"
        );
    }

    @Test
    void marksServerRoutingAndHydrationInteractions() {
        assertTypeScriptMarker(
                "import { provideRouter } from '@angular/router';\nexport const config = {providers: [provideRouter(routes)]};\n",
                "Server/client provideRouter configuration",
                "src/main.server.ts"
        );
        assertTypeScriptMarker(
                "import { Router } from '@angular/router';\nimport { provideClientHydration, withEventReplay } from '@angular/platform-browser';\nconst providers = [provideClientHydration(withEventReplay())];\n",
                "Hydration/event replay interacts with initial navigation",
                "src/app.config.ts"
        );
    }

    @Test
    void marksComplexConstraintCentralOwnerAndLockstepToolchain() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularRouterJsonRisks()),
                json(
                        """
                        {
                          "engines":{"node":"18.x"},
                          "dependencies":{"@angular/router":">=10 <14","@angular/core":"12.2.17","@angular/common":"12.2.17","rxjs":"6.4.0"},
                          "devDependencies":{"typescript":"4.3.5"},
                          "pnpm":{"overrides":{"@angular/router":"10.0.14"}}
                        }
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("complex range"));
                                    assertTrue(printed.contains("framework packages must be aligned"));
                                    assertTrue(printed.contains("requires TypeScript"));
                                    assertTrue(printed.contains("supports RxJS"));
                                    assertTrue(printed.contains("requires Node"));
                                    assertTrue(printed.contains("Central package-manager"));
                                })
                )
        );
    }

    @Test
    void doesNotMarkSupportedSingleDeclarationsOrUnrelatedNodePackages() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularRouterJsonRisks()),
                json("{\"engines\":{\"node\":\"^20.19.0 || ^22.12.0 || >=24.0.0\"},\"dependencies\":{\"@angular/router\":\"^10.0.14\",\"rxjs\":\"^7.8.2\"},\"devDependencies\":{\"typescript\":\"5.9.2\"}}",
                        source -> source.path("package.json")),
                json("{\"engines\":{\"node\":\"18.x\"},\"devDependencies\":{\"typescript\":\"4.3.5\"}}",
                        source -> source.path("services/api/package.json")),
                json("{\"fixtures\":{\"@angular/router\":\"10.0.14\"},\"dependencies\":{\"@angular/core\":\"12.2.17\"}}",
                        source -> source.path("fixtures/package.json"))
        );
    }

    @Test
    void marksWorkspaceBuilderDeploymentAndSsrTargets() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularRouterJsonRisks()),
                json(
                        "{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@nrwl/angular:webpack-browser\",\"options\":{\"baseHref\":\"/portal/\",\"deployUrl\":\"/assets/\"}},\"server\":{},\"prerender\":{}}}}}",
                        source -> source.path("angular.json").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("Custom builder detected"));
                                    assertTrue(printed.contains("Deployment URL affects Router links"));
                                    assertTrue(printed.contains("SSR/prerender routing"));
                                })
                )
        );
    }

    @Test
    void recommendedRecipeCombinesDependencyAutoMigrationAndMarkers() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources()
                .build();
        rewriteRun(
                spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.angular.MigrateAngularRouterTo20_3_26")),
                json(
                        "{\"dependencies\":{\"@angular/router\":\"^12.2.17\"}}",
                        "{\"dependencies\":{\"@angular/router\":\"20.3.26\"}}",
                        source -> source.path("package.json")
                ),
                typescript(
                        "import { Router } from '@angular/router';\nfunction read(router: Router) { return router.getCurrentNavigation(); }\n",
                        "import { Router } from '@angular/router';\nfunction read(router: Router) { return /*~~(Current navigation exists only during an active navigation; update signal reads/state capture and verify redirect, cancellation, reload, and page-refresh lifecycles)~~>*/router.currentNavigation(); }\n",
                        source -> source.path("src/navigation.ts")
                                .afterRecipe(after -> assertTrue(after.printAll().contains("Current navigation exists only")))
                ),
                typescript(
                        "import { RouterModule } from '@angular/router';\nRouterModule.forRoot(routes, {relativeLinkResolution: 'legacy'});\n",
                        "import { RouterModule } from '@angular/router';\n/*~~(RouterModule.forRoot configuration needs provider-feature, initial navigation, scrolling, preloading, error, and lazy-injector review)~~>*/RouterModule.forRoot(routes, {});\n",
                        source -> source.path("src/app-routing.module.ts")
                                .afterRecipe(after -> assertTrue(after.printAll().contains("RouterModule.forRoot configuration")))
                )
        );
    }

    private void assertTypeScriptMarker(String before, String message, String path) {
        rewriteRun(
                spec -> spec.recipe(new FindAngularRouterTypeScriptRisks()),
                typescript(before, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> assertTrue(after.printAll().contains(message), after.printAll())))
        );
    }
}
