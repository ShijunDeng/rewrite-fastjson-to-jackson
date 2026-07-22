package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;

class AngularRouterSourceMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDeterministicAngularRouterSource());
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void followsOfficialAngularTypedConstructorCurrentNavigationMigration() {
        rewriteRun(typescript(
                """
                import { Router } from '@angular/router';
                export class Service {
                  constructor(private router: Router) {}
                  read() { return this.router.getCurrentNavigation(); }
                }
                """,
                """
                import { Router } from '@angular/router';
                export class Service {
                  constructor(private router: Router) {}
                  read() { return this.router.currentNavigation(); }
                }
                """,
                source -> source.path("packages/core/schematics/test/router_current_navigation_spec.ts")
        ));
    }

    @Test
    void migratesPropertyReferenceLikeOfficialAngularFixture() {
        rewriteRun(typescript(
                """
                import { Router } from '@angular/router';
                function capture(router: Router) {
                  return router.getCurrentNavigation;
                }
                """,
                """
                import { Router } from '@angular/router';
                function capture(router: Router) {
                  return router.currentNavigation;
                }
                """,
                source -> source.path("src/capture.ts")
        ));
    }

    @Test
    void migratesAliasedRouterType() {
        rewriteRun(typescript(
                """
                import { Router as AngularRouter } from '@angular/router';
                function read(appRouter: AngularRouter) {
                  return appRouter.getCurrentNavigation();
                }
                """,
                """
                import { Router as AngularRouter } from '@angular/router';
                function read(appRouter: AngularRouter) {
                  return appRouter.currentNavigation();
                }
                """,
                source -> source.path("src/alias.ts")
        ));
    }

    @Test
    void migratesInjectRouterFromPinnedOktaStyleFixture() {
        rewriteRun(typescript(
                """
                import { inject } from '@angular/core';
                import { Router } from '@angular/router';
                const router = inject(Router);
                const nav = router.getCurrentNavigation();
                """,
                """
                import { inject } from '@angular/core';
                import { Router } from '@angular/router';
                const router = inject(Router);
                const nav = router.currentNavigation();
                """,
                source -> source.path("okta/okta-angular/lib/src/okta/okta.guard.ts")
        ));
    }

    @Test
    void conservativelyLeavesAnyAndSameNamedNonAngularRouter() {
        rewriteRun(
                typescript(
                        "import { Router } from '@angular/router';\nfunction read(router: any) { return router.getCurrentNavigation(); }\n",
                        source -> source.path("ngx-loading-bar/router.service.ts")
                ),
                typescript(
                        "import { Router } from '@not-angular/router';\nfunction read(router: Router) { return router.getCurrentNavigation(); }\n",
                        source -> source.path("src/not-angular.ts")
                ),
                typescript(
                        "class Router { getCurrentNavigation() { return null; } }\nconst router = new Router();\nrouter.getCurrentNavigation();\n",
                        source -> source.path("src/local.ts")
                )
        );
    }

    @Test
    void removesLegacyRelativeLinkResolutionFromPinnedRealFixture() {
        rewriteRun(typescript(
                """
                import { Routes, RouterModule } from '@angular/router';
                const routes: Routes = [];
                const routing = RouterModule.forRoot(routes, {
                  useHash: true,
                  relativeLinkResolution: 'legacy'
                });
                """,
                """
                import { Routes, RouterModule } from '@angular/router';
                const routes: Routes = [];
                const routing = RouterModule.forRoot(routes, {
                  useHash: true
                });
                """,
                source -> source.path("mathisGarberg/angular-folder-structure/src/app/app-routing.module.ts")
        ));
    }

    @Test
    void removesRedundantCorrectedRelativeLinkResolution() {
        rewriteRun(typescript(
                "import { RouterModule } from '@angular/router';\nRouterModule.forRoot(routes, { relativeLinkResolution: 'corrected' });\n",
                "import { RouterModule } from '@angular/router';\nRouterModule.forRoot(routes, { });\n",
                source -> source.path("src/app-routing.module.ts")
        ));
    }

    @Test
    void doesNotRemoveSamePropertyOutsideAttributedRouterForRoot() {
        rewriteRun(
                typescript(
                        "const options = { relativeLinkResolution: 'legacy' };\n",
                        source -> source.path("src/options.ts")
                ),
                typescript(
                        "import { RouterModule } from '@angular/router';\nOtherModule.forRoot(routes, { relativeLinkResolution: 'legacy' });\n",
                        source -> source.path("src/other.ts")
                ),
                typescript(
                        "import { RouterModule } from '@angular/router';\nRouterModule.forRoot(routes, { nested: { relativeLinkResolution: 'legacy', initialNavigation: 'enabled' } });\n",
                        source -> source.path("src/nested.ts")
                )
        );
    }

    @Test
    void migratesRemovedEnabledInitialNavigationValue() {
        rewriteRun(typescript(
                "import { RouterModule } from '@angular/router';\nRouterModule.forRoot(routes, {initialNavigation: 'enabled'});\n",
                "import { RouterModule } from '@angular/router';\nRouterModule.forRoot(routes, {initialNavigation: 'enabledBlocking'});\n",
                source -> source.path("src/app-routing.module.ts")
        ));
    }

    @Test
    void migratesAliasedRouterLinkWithHrefImportWithoutTouchingLocalUses() {
        rewriteRun(typescript(
                "import { RouterLinkWithHref as LegacyLink } from '@angular/router';\nconst directive = LegacyLink;\n",
                "import { RouterLink as LegacyLink } from '@angular/router';\nconst directive = LegacyLink;\n",
                source -> source.path("src/shared.module.ts")
        ));
    }

    @Test
    void deterministicRouterMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        """
                        import { Router, RouterModule } from '@angular/router';
                        function read(router: Router) { return router.getCurrentNavigation(); }
                        RouterModule.forRoot(routes, {relativeLinkResolution: 'legacy'});
                        """,
                        """
                        import { Router, RouterModule } from '@angular/router';
                        function read(router: Router) { return router.currentNavigation(); }
                        RouterModule.forRoot(routes, {});
                        """,
                        source -> source.path("src/app-routing.module.ts")
                )
        );
    }
}
