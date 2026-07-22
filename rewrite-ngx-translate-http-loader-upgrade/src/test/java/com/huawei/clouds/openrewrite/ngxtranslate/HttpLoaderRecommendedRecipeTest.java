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

class HttpLoaderRecommendedRecipeTest implements RewriteTest {
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateHttpLoaderTo17";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(HttpLoaderDependencyTest.environment().activateRecipes(MIGRATE));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesESyncMateAtPinnedCommitAndMarksRemainingDecisions() {
        // ShahidBaig/eSyncMate_V2@8478a96267fb692985c70e32f5dde0544209d6a5.
        rewriteRun(
                json("{\"dependencies\":{\"@angular/core\":\"^16.2.4\",\"@ngx-translate/core\":\"^15.0.0\",\"@ngx-translate/http-loader\":\"^8.0.0\"},\"devDependencies\":{\"typescript\":\"~4.9.4\"}}",
                        source -> source.path("UI/package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"@ngx-translate/http-loader\":\"17.0.0\""));
                                    assertTrue(printed.contains("Align @ngx-translate/core"));
                                })),
                typescript(
                        """
                        import { HttpClientModule, HttpClient } from '@angular/common/http';
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function HttpLoaderFactory(http: HttpClient) {
                          return new TranslateHttpLoader(http, './assets/translations/', '.json');
                        }
                        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory, deps: [HttpClient] } });
                        """,
                        source -> source.path("UI/src/app/app.module.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("provideTranslateHttpLoader({ prefix: './assets/translations/', suffix: '.json' })"));
                                    assertTrue(printed.contains("provider order"));
                                    assertTrue(printed.contains("HTTP is registered exactly once"));
                                }))
        );
    }

    @Test
    void migratesGenshinCalcAtPinnedCommitAndExposesOldMatrix() {
        // Kurarion/Genshin-Calc@c7dd4d850db8523e33302e98d71d9e180605bd4e.
        rewriteRun(
                json("{\"dependencies\":{\"@angular/core\":\"^14.2.12\",\"@ngx-translate/core\":\"^14.0.0\",\"@ngx-translate/http-loader\":\"^7.0.0\"},\"devDependencies\":{\"typescript\":\"~4.6.2\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"@ngx-translate/http-loader\":\"17.0.0\""));
                                    assertTrue(printed.contains("Align @ngx-translate/core"));
                                    assertTrue(printed.contains("requires Angular common/core >=16"));
                                    assertTrue(printed.contains("exact TypeScript range"));
                                })),
                typescript(
                        """
                        import { HttpClient, HttpClientModule } from '@angular/common/http';
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function HttpLoaderFactory(http: HttpClient) {
                          return new TranslateHttpLoader(http, `assets/i18n/`);
                        }
                        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory, deps: [HttpClient] } });
                        """,
                        source -> source.path("src/app/app.module.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("provideTranslateHttpLoader({ prefix: `assets/i18n/` })"))))
        );
    }

    @Test
    void preservesAndMarksTuniSalesGatewayDynamicFactoryAtPinnedCommit() {
        // FeDi20-03/TuniSalesGateway@40e99bedf123169767fbcf7200f4e2e0a94eb402.
        rewriteRun(
                json("{\"dependencies\":{\"@angular/core\":\"14.2.0\",\"@ngx-translate/core\":\"14.0.0\",\"@ngx-translate/http-loader\":\"7.0.0\"},\"devDependencies\":{\"typescript\":\"4.8.2\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("\"@ngx-translate/http-loader\":\"17.0.0\"")))),
                typescript(
                        """
                        import { HttpClient } from '@angular/common/http';
                        import { TranslateLoader } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function translatePartialLoader(http: HttpClient): TranslateLoader {
                          return new TranslateHttpLoader(http, 'i18n/', `.json?_=${I18N_HASH}`);
                        }
                        """,
                        source -> source.path("src/main/webapp/app/config/translation.config.ts")
                                .after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("new TranslateHttpLoader(http, 'i18n/'"));
                                    assertTrue(printed.contains("injection-only zero-argument constructor"));
                                }))
        );
    }

    @Test
    void officialV17ManifestAndPublicTypesAreNoOp() {
        // ngx-translate/core http-loader v17 peeled commit e7b83e9a495b127ed63378fdd77b458d3caffcc0.
        rewriteRun(
                json("{\"name\":\"@ngx-translate/http-loader\",\"version\":\"17.0.0\",\"peerDependencies\":{\"@angular/common\":\">=16\",\"@angular/core\":\">=16\"},\"sideEffects\":false}",
                        source -> source.path("vendor/http-loader/package.json")),
                typescript("import { TranslateHttpLoaderConfig } from '@ngx-translate/http-loader';\nconst config: Partial<TranslateHttpLoaderConfig> = { prefix: './i18n/', suffix: '.json' };\n",
                        source -> source.path("src/config.ts"))
        );
    }

    @Test
    void legacyAuditEntryPointsRemainFunctional() {
        Environment environment = HttpLoaderDependencyTest.environment();
        rewriteRun(
                spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.ngxtranslate.FindManualHttpLoader17MigrationRisks")),
                typescript("import { TranslateHttpLoader } from '@ngx-translate/http-loader';\nnew TranslateHttpLoader(http);\n",
                        source -> source.path("src/legacy-source-audit.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("injection-only zero-argument constructor"))))
        );
        rewriteRun(
                spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.ngxtranslate.FindHttpLoader17CompanionDependencies")),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\",\"@ngx-translate/core\":\"15.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("Align @ngx-translate/core"))))
        );
    }

    @Test
    void recommendedRecipeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"^6.0.0\",\"@ngx-translate/core\":\"13.0.0\",\"@angular/core\":\"15.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript("import { HttpClient } from '@angular/common/http';\nimport { TranslateLoader, TranslateModule } from '@ngx-translate/core';\nimport { TranslateHttpLoader } from '@ngx-translate/http-loader';\nexport function f(http: HttpClient) { return new TranslateHttpLoader(http, './i18n/'); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual))
        );
    }

    @Test
    void discoversAndValidatesAllPublicRecipes() {
        Environment environment = HttpLoaderDependencyTest.environment();
        Map<String, String> recipes = Map.of(
                "com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateHttpLoaderTo17",
                "Upgrade spreadsheet-selected @ngx-translate/http-loader declarations to 17.0.0",
                "com.huawei.clouds.openrewrite.ngxtranslate.MigrateDeterministicHttpLoaderFactoriesTo17",
                "Apply deterministic ngx-translate HTTP loader 17 factory migrations",
                "com.huawei.clouds.openrewrite.ngxtranslate.AuditNgxTranslateHttpLoader17Source",
                "Audit ngx-translate HTTP loader 17 source compatibility",
                "com.huawei.clouds.openrewrite.ngxtranslate.AuditNgxTranslateHttpLoader17Project",
                "Audit ngx-translate HTTP loader 17 project compatibility",
                "com.huawei.clouds.openrewrite.ngxtranslate.FindManualHttpLoader17MigrationRisks",
                "Find ngx-translate HTTP loader 17 source risks requiring review",
                "com.huawei.clouds.openrewrite.ngxtranslate.FindHttpLoader17CompanionDependencies",
                "Find ngx-translate HTTP loader 17 project and companion risks",
                MIGRATE,
                "Migrate spreadsheet-selected ngx-translate HTTP loader applications to 17.0.0"
        );
        for (Map.Entry<String, String> entry : recipes.entrySet()) {
            Recipe recipe = environment.activateRecipes(entry.getKey());
            assertEquals(entry.getValue(), recipe.getDisplayName());
            assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> entry.getKey().equals(candidate.getName())));
        }
    }
}
