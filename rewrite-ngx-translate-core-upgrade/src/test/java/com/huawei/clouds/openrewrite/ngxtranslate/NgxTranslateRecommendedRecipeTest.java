package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class NgxTranslateRecommendedRecipeTest implements RewriteTest {
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateCoreTo17";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(NgxTranslateProjectRiskTest.environment().activateRecipes(MIGRATE));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesESyncMateManifestAndMarksItsPinnedModuleBoundaries() {
        // ShahidBaig/eSyncMate_V2@8478a96267fb692985c70e32f5dde0544209d6a5.
        rewriteRun(
                json("{\"dependencies\":{\"@angular/core\":\"^16.2.0\",\"@ngx-translate/core\":\"^15.0.0\",\"@ngx-translate/http-loader\":\"^8.0.0\"}}",
                        source -> source.path("UI/package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"@ngx-translate/core\":\"17.0.0\""));
                                    assertTrue(printed.contains("Align @ngx-translate/http-loader"));
                                })),
                typescript(
                        """
                        import { TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        const loader = new TranslateHttpLoader(http, './assets/translations/', '.json');
                        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory } });
                        """,
                        source -> source.path("UI/src/app/app.module.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("HTTP loader uses injection/provider configuration"));
                                    assertTrue(printed.contains("Raw provider objects require"));
                                })),
                typescript(
                        """
                        import { Component } from '@angular/core';
                        import { TranslateModule } from '@ngx-translate/core';
                        @Component({ standalone: true, imports: [TranslateModule] })
                        export class AlertConfigurationComponent {}
                        """,
                        source -> source.path("UI/src/app/alert-configuration/alert-configuration.component.ts")
                                .after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("standalone component imports TranslateModule"))))
        );
    }

    @Test
    void migratesGenshinCalcAtPinnedCommitAndKeepsAmbiguousGetterVisible() {
        // Kurarion/Genshin-Calc@c7dd4d850db8523e33302e98d71d9e180605bd4e.
        rewriteRun(
                json("{\"dependencies\":{\"@angular/core\":\"^15.2.0\",\"@ngx-translate/core\":\"^14.0.0\",\"@ngx-translate/http-loader\":\"^7.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"@ngx-translate/core\":\"17.0.0\""));
                                    assertTrue(printed.contains("declares Angular common/core >=16"));
                                    assertTrue(printed.contains("Align @ngx-translate/http-loader"));
                                })),
                typescript(
                        """
                        import { TranslateService } from '@ngx-translate/core';
                        export class LanguageService {
                          constructor(private translateService: TranslateService) {}
                          initialize() { this.translateService.setDefaultLang('en'); }
                          current() { return this.translateService.currentLang; }
                        }
                        """,
                        source -> source.path("src/app/shared/service/language.service.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("setFallbackLang('en')"));
                                    assertTrue(printed.contains("property is deprecated"));
                                }))
        );
    }

    @Test
    void migratesTuniSalesGatewayAtPinnedCommitAndAuditsChildScope() {
        // FeDi20-03/TuniSalesGateway@40e99bedf123169767fbcf7200f4e2e0a94eb402.
        rewriteRun(
                json("{\"dependencies\":{\"@angular/common\":\"15.2.10\",\"@ngx-translate/core\":\"14.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("\"@ngx-translate/core\":\"17.0.0\"")))),
                typescript(
                        """
                        import { TranslateModule, TranslateService } from '@ngx-translate/core';
                        export class TranslationModule {
                          constructor(private translateService: TranslateService) {
                            this.translateService.setDefaultLang('fr');
                          }
                        }
                        TranslateModule.forChild({ missingTranslationHandler: { provide: Handler, useClass: MissingHandler } });
                        """,
                        source -> source.path("src/main/webapp/app/shared/language/translation.module.ts")
                                .after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("setFallbackLang('fr')"));
                                    assertTrue(printed.contains("TranslateModule configuration remains supported"));
                                    assertTrue(printed.contains("Raw provider objects require"));
                                }))
        );
    }

    @Test
    void officialCore17TargetManifestAndModernSourceAreNoOp() {
        // ngx-translate/core v17 peeled commit 4500e0b8aa7d8d8320b74b7a5ac3b2ffcf192d53.
        rewriteRun(
                json("{\"name\":\"@ngx-translate/core\",\"version\":\"17.0.0\",\"peerDependencies\":{\"@angular/common\":\">=16\",\"@angular/core\":\">=16\"}}",
                        source -> source.path("vendor/ngx-translate-core/package.json")),
                typescript(
                        "import { TranslateService } from '@ngx-translate/core';\nclass App { constructor(private t: TranslateService) {} run() { this.t.setFallbackLang('en'); return this.t.getFallbackLang(); } }\n",
                        source -> source.path("src/modern.ts"))
        );
    }

    @Test
    void recommendedRecipeAppliesAutoChangesThenLeavesManualMarkers() {
        rewriteRun(
                json("{\"dependencies\":{\"@ngx-translate/core\":\"~13.0.0\",\"@angular/core\":\"15.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("\"@ngx-translate/core\":\"17.0.0\""));
                                    assertTrue(document.printAll().contains("declares Angular common/core >=16"));
                                })),
                typescript(
                        "import { DefaultLangChangeEvent, TranslateService } from '@ngx-translate/core';\nclass X { constructor(private i18n: TranslateService) {} run() { this.i18n.setDefaultLang('en'); return this.i18n.currentLang; } }\n",
                        source -> source.path("src/i18n.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("FallbackLangChangeEvent"));
                                    assertTrue(printed.contains("setFallbackLang('en')"));
                                    assertTrue(printed.contains("property is deprecated"));
                                }))
        );
    }

    @Test
    void backwardCompatibleAggregateAuditsBothSourceAndProject() {
        Recipe aggregate = NgxTranslateProjectRiskTest.environment().activateRecipes(
                "com.huawei.clouds.openrewrite.ngxtranslate.FindManualNgxTranslate17MigrationRisks");
        rewriteRun(
                spec -> spec.recipe(aggregate),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"16.0.0\",\"@angular/core\":\"15.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("was not changed")))),
                typescript("import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ defaultLanguage: 'en' });\n",
                        source -> source.path("src/app.module.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("Legacy default-language configuration"))))
        );
    }

    @Test
    void recommendedRecipeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"^11.0.1\",\"@angular/core\":\"15.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript("import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(private t: TranslateService) {} run() { this.t.setDefaultLang('en'); return this.t.currentLang; } }\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual))
        );
    }

    @Test
    void discoversAndValidatesAllPublicRecipes() {
        Environment environment = NgxTranslateProjectRiskTest.environment();
        Map<String, String> recipes = Map.of(
                "com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateCoreTo17",
                "Upgrade spreadsheet-selected @ngx-translate/core declarations to 17.0.0",
                "com.huawei.clouds.openrewrite.ngxtranslate.MigrateDeterministicNgxTranslateSourceTo17",
                "Apply deterministic ngx-translate 17 source API migrations",
                "com.huawei.clouds.openrewrite.ngxtranslate.AuditNgxTranslate17Source",
                "Audit ngx-translate 17 JavaScript and TypeScript compatibility",
                "com.huawei.clouds.openrewrite.ngxtranslate.AuditNgxTranslate17Project",
                "Audit ngx-translate 17 manifests and JSON configuration",
                "com.huawei.clouds.openrewrite.ngxtranslate.FindManualNgxTranslate17MigrationRisks",
                "Find all ngx-translate 17 migration risks requiring manual review",
                MIGRATE,
                "Migrate spreadsheet-selected ngx-translate applications to core 17.0.0"
        );
        for (Map.Entry<String, String> entry : recipes.entrySet()) {
            Recipe recipe = environment.activateRecipes(entry.getKey());
            assertEquals(entry.getValue(), recipe.getDisplayName());
            assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> entry.getKey().equals(candidate.getName())));
        }
    }
}
