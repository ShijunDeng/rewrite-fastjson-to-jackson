package com.huawei.clouds.openrewrite.angularelements;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

/** Reduced fixtures from immutable official and real-repository revisions documented in README.md. */
class AngularElementsRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.angularelements.MigrateAngularElementsTo20_3_25";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(AngularElementsDependencyTest.environment().activateRecipes(RECOMMENDED));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void strictUpgradeRunsBeforeManifestAndSourceAudit() {
        rewriteRun(
                json("{\"dependencies\":{\"@angular/elements\":\"^15.2.9\",\"@angular/core\":\"15.2.9\"}}",
                        "{\"dependencies\":{\"@angular/elements\":\"^20.3.25\",\"@angular/core\":/*~~(Angular framework packages must be on the same 20.3.25 patch as @angular/elements; run every intervening major's official ng update migrations before final lockstep alignment)~~>*/\"15.2.9\"}}",
                        source -> source.path("package.json")),
                typescript("import { createCustomElement } from '@angular/elements';\nconst Ctor = createCustomElement(Widget, { injector });",
                        source -> source.path("src/main.ts").after(actual -> actual).afterRecipe(cu ->
                                assertTrue(cu.printAll().contains("createCustomElement boundary")))));
    }

    @Test
    void evaluatesMal90PinnedNgModuleFixture() {
        rewriteRun(typescript(
                """
                import { NgModule, Injector } from '@angular/core';
                import { createCustomElement } from '@angular/elements';
                import { SampleElementComponent } from './sample-element/sample-element.component';
                export class AppModule {
                  constructor(private injector: Injector) {
                    const customButton = createCustomElement(SampleElementComponent, { injector });
                    customElements.define('sample-element', customButton);
                  }
                  ngDoBootstrap() {}
                }
                """, source -> source.path("src/app/app.module.ts").after(actual -> actual).afterRecipe(cu -> {
                    String printed = cu.printAll();
                    assertTrue(printed.contains("createCustomElement boundary"));
                    assertTrue(printed.contains("duplicate define calls"));
                })));
    }

    @Test
    void evaluatesGiacomoStandaloneZonelessFixture() {
        rewriteRun(typescript(
                """
                import { createApplication } from '@angular/platform-browser';
                import { provideZonelessChangeDetection } from '@angular/core';
                import { createCustomElement } from '@angular/elements';
                createApplication({ providers: [provideZonelessChangeDetection()] }).then((appRef) => {
                  const name = 'my-custom-widget';
                  const element = createCustomElement(Example, { injector: appRef.injector });
                  if (!customElements.get(name)) customElements.define(name, element);
                });
                """, source -> source.path("src/main.ts").after(actual -> actual).afterRecipe(cu -> {
                    String printed = cu.printAll();
                    assertTrue(printed.contains("standalone versus NgModule"));
                    assertTrue(printed.contains("disconnect/reconnect"));
                    assertTrue(printed.contains("prevent duplicate define"));
                })));
    }

    @Test
    void evaluatesAngularOfficialStrategyAndUpgradeFixture() {
        rewriteRun(typescript(
                """
                import { createCustomElement, NgElementStrategyFactory } from '@angular/elements';
                const ElementCtor = createCustomElement(TestComponent, { injector, strategyFactory });
                customElements.define(selector, ElementCtor);
                customElements.upgrade(element);
                strategyFactory.create(injector).connect(element);
                """, source -> source.path("packages/elements/test/create-custom-element_spec.ts")
                        .after(actual -> actual).afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertTrue(printed.contains("Custom NgElement strategy"));
                            assertTrue(printed.contains("lookup/upgrade lifecycle"));
                        })));
    }
}
