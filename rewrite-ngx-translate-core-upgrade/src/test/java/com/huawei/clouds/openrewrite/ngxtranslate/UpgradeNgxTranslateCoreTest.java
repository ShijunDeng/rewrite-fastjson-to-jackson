package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeNgxTranslateCoreTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateCoreTo17";
    private static final String STANDALONE_RECIPE =
            "com.huawei.clouds.openrewrite.ngxtranslate.MigrateStandaloneTranslateModuleToPipeAndDirective";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.ngxtranslate.MigrateDeterministicNgxTranslateSourceTo17";
    private static final String RISK_RECIPE =
            "com.huawei.clouds.openrewrite.ngxtranslate.FindManualNgxTranslate17MigrationRisks";
    private static final String COMPOSITE_RECIPE =
            "com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateCoreTo17";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
    }

    @Test
    void upgradesESyncMateDependencyWithoutChangingCompanionPackages() {
        // Reduced from ShahidBaig/eSyncMate_V2 at 8478a962:
        // https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/package.json
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@angular/core": "^16.2.0",
                    "@ngx-translate/core": "^15.0.0",
                    "@ngx-translate/http-loader": "^8.0.0",
                    "rxjs": "~7.8.0"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@angular/core": "^16.2.0",
                    "@ngx-translate/core": "17.0.0",
                    "@ngx-translate/http-loader": "^8.0.0",
                    "rxjs": "~7.8.0"
                  }
                }
                """,
                source -> source.path("UI/package.json")
        ));
    }

    @Test
    void upgradesGenshinCalcDependency() {
        // Reduced from Kurarion/Genshin-Calc at c7dd4d85:
        // https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/package.json
        rewriteRun(packageVersion("package.json", "^14.0.0"));
    }

    @Test
    void upgradesTuniSalesGatewayDependency() {
        // Reduced from FeDi20-03/TuniSalesGateway at 40e99bed:
        // https://github.com/FeDi20-03/TuniSalesGateway/blob/40e99bedf123169767fbcf7200f4e2e0a94eb402/package.json
        rewriteRun(packageVersion("package.json", "14.0.0"));
    }

    @ParameterizedTest(name = "upgrades spreadsheet ngx-translate version {0}")
    @ValueSource(strings = {"11.0.1", "13.0.0", "14.0.0", "15.0.0"})
    void upgradesEverySpreadsheetVersion(String version) {
        rewriteRun(packageVersion("package.json", version));
    }

    @ParameterizedTest(name = "upgrades safe selected declaration {0}")
    @ValueSource(strings = {
            "^11.0.1", "~11.0.1", "=11.0.1", "v11.0.1", "^v11.0.1",
            "^13.0.0", "~13.0.0", "=13.0.0", "v13.0.0", "^v13.0.0",
            "^14.0.0", "~14.0.0", "=14.0.0", "v14.0.0", "^v14.0.0",
            "^15.0.0", "~15.0.0", "=15.0.0", "v15.0.0", "^v15.0.0"
    })
    void upgradesSupportedRegistryDeclarations(String declaration) {
        rewriteRun(packageVersion("package.json", declaration));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@ngx-translate/core": "11.0.1"},
                  "devDependencies": {"@ngx-translate/core": "^13.0.0"},
                  "peerDependencies": {"@ngx-translate/core": "~14.0.0"},
                  "optionalDependencies": {"@ngx-translate/core": "=15.0.0"}
                }
                """,
                """
                {
                  "dependencies": {"@ngx-translate/core": "17.0.0"},
                  "devDependencies": {"@ngx-translate/core": "17.0.0"},
                  "peerDependencies": {"@ngx-translate/core": "17.0.0"},
                  "optionalDependencies": {"@ngx-translate/core": "17.0.0"}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesWorkspaceChildrenWithoutChangingWorkspaceConfiguration() {
        rewriteRun(
                json("{\"private\":true,\"workspaces\":[\"apps/*\"]}", source -> source.path("package.json")),
                packageVersion("apps/admin/package.json", "^13.0.0"),
                packageVersion("packages/i18n/package.json", "15.0.0")
        );
    }

    @ParameterizedTest(name = "preserves unlisted declaration {0}")
    @ValueSource(strings = {
            "11.0.0", "11.0.2", "12.0.0", "13.0.1", "14.0.1", "15.0.1",
            "16.0.0", "17.0.0", "^17.0.0", "17.0.1", "18.0.0",
            ">=11.0.1", ">=11.0.1 <17", "11.0.1 || 15.0.0", "11.0.1 - 15.0.0",
            "11.0.1-beta.1", "11.0.1+vendor.1", "latest", "${ngxTranslateVersion}"
    })
    void preservesUnlistedOrAmbiguousDeclarations(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"@ngx-translate/core\":\"" + declaration + "\"}}",
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest(name = "preserves non-registry declaration {0}")
    @ValueSource(strings = {
            "workspace:^15.0.0", "npm:@vendor/core@15.0.0", "file:../core", "link:../core",
            "git+https://github.com/ngx-translate/core.git#v15.0.0",
            "https://registry.example/core-15.0.0.tgz", "catalog:", "catalog:frontend"
    })
    void preservesProtocolsAliasesAndCatalogs(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"@ngx-translate/core\":\"" + declaration + "\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void preservesOverridesResolutionsNestedMetadataAndSimilarPackages() {
        rewriteRun(json(
                """
                {
                  "overrides": {"@ngx-translate/core": "15.0.0"},
                  "resolutions": {"@ngx-translate/core": "14.0.0"},
                  "pnpm": {"overrides": {"@ngx-translate/core": "13.0.0"}},
                  "dependencies": {
                    "@ngx-translate/http-loader": "15.0.0",
                    "@vendor/ngx-translate-core": "15.0.0"
                  }
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void preservesNonScalarValuesAndArrayShapedSections() {
        rewriteRun(json(
                """
                {
                  "dependencies": ["@ngx-translate/core", "15.0.0"],
                  "devDependencies": {
                    "@ngx-translate/core": {"version": "15.0.0"}
                  },
                  "peerDependencies": {"@ngx-translate/core": null},
                  "optionalDependencies": {"@ngx-translate/core": ["15.0.0"]}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void preservesLockfilesAndOrdinaryJson() {
        rewriteRun(
                json(
                        "{\"packages\":{\"\":{\"dependencies\":{\"@ngx-translate/core\":\"15.0.0\"}}}}",
                        source -> source.path("package-lock.json")
                ),
                json(
                        "{\"dependencies\":{\"@ngx-translate/core\":\"14.0.0\"}}",
                        source -> source.path("config/dependencies.json")
                )
        );
    }

    @Test
    void migratesESyncMateStandaloneTranslateModule() {
        // Reduced from ShahidBaig/eSyncMate_V2 at 8478a962:
        // https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/src/app/alert-configuration/alert-configuration.component.ts
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(STANDALONE_RECIPE)),
                text(
                        """
                        import { Component } from '@angular/core';
                        import { CommonModule } from '@angular/common';
                        import { TranslateModule } from '@ngx-translate/core';
                        @Component({
                          standalone: true,
                          imports: [CommonModule, TranslateModule]
                        })
                        export class AlertConfigurationComponent {}
                        """,
                        """
                        import { Component } from '@angular/core';
                        import { CommonModule } from '@angular/common';
                        import { TranslatePipe, TranslateDirective } from '@ngx-translate/core';
                        @Component({
                          standalone: true,
                          imports: [CommonModule, TranslatePipe, TranslateDirective]
                        })
                        export class AlertConfigurationComponent {}
                        """,
                        source -> source.path("UI/src/app/alert-configuration/alert-configuration.component.ts")
                )
        );
    }

    @Test
    void migratesGenshinCalcFallbackConfigurationAndGetter() {
        // Reduced from Kurarion/Genshin-Calc at c7dd4d85:
        // https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/src/app/app.module.ts
        // https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/src/app/shared/service/language.service.ts
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        TranslateModule.forRoot({
                          defaultLanguage: environment.defaultLang,
                          loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory }
                        });
                        """,
                        """
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        TranslateModule.forRoot({
                          fallbackLang: environment.defaultLang,
                          loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory }
                        });
                        """,
                        source -> source.path("src/app/app.module.ts")
                ),
                text(
                        """
                        import { TranslateService } from '@ngx-translate/core';
                        const fallback = this.translateService.getDefaultLang();
                        """,
                        """
                        import { TranslateService } from '@ngx-translate/core';
                        const fallback = this.translateService.getFallbackLang();
                        """,
                        source -> source.path("src/app/shared/service/language.service.ts")
                )
        );
    }

    @Test
    void migratesTuniSalesGatewayDefaultLanguageSetter() {
        // Reduced from FeDi20-03/TuniSalesGateway at 40e99bed:
        // https://github.com/FeDi20-03/TuniSalesGateway/blob/40e99bedf123169767fbcf7200f4e2e0a94eb402/src/main/webapp/app/shared/language/translation.module.ts
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import { TranslateService } from '@ngx-translate/core';
                        translateService.setDefaultLang('fr');
                        """,
                        """
                        import { TranslateService } from '@ngx-translate/core';
                        translateService.setFallbackLang('fr');
                        """,
                        source -> source.path("src/main/webapp/app/shared/language/translation.module.ts")
                )
        );
    }

    @Test
    void migratesDeprecatedPublicNamesAndServiceProperties() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import {
                          DefaultLangChangeEvent,
                          FakeMissingTranslationHandler,
                          TranslateFakeCompiler,
                          TranslateFakeLoader,
                          TranslateService
                        } from '@ngx-translate/core';
                        const event$ = translate.onDefaultLangChange;
                        const fallback = this.translateService.defaultLang;
                        const current = translate.currentLang;
                        const languages = this.translateService.langs;
                        """,
                        """
                        import {
                          FallbackLangChangeEvent,
                          DefaultMissingTranslationHandler,
                          TranslateNoOpCompiler,
                          TranslateNoOpLoader,
                          TranslateService
                        } from '@ngx-translate/core';
                        const event$ = translate.onFallbackLangChange;
                        const fallback = this.translateService.getFallbackLang();
                        const current = translate.getCurrentLang();
                        const languages = this.translateService.getLangs();
                        """,
                        source -> source.path("src/app/i18n.ts")
                )
        );
    }

    @Test
    void migratesProvideTranslateServiceFallbackConfiguration() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import { provideTranslateService } from '@ngx-translate/core';
                        export const providers = [provideTranslateService({
                          defaultLanguage: 'en'
                        })];
                        """,
                        """
                        import { provideTranslateService } from '@ngx-translate/core';
                        export const providers = [provideTranslateService({
                          fallbackLang: 'en'
                        })];
                        """,
                        source -> source.path("src/app/app.config.ts")
                )
        );
    }

    @Test
    void sourceRecipePreservesAmbiguousAndUnrelatedSource() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import { NgModule } from '@angular/core';
                        import { TranslateModule, TranslateService } from '@ngx-translate/core';
                        const settings = { defaultLanguage: 'en' };
                        const current = config.currentLang;
                        const fallback = config.defaultLang;
                        const languages = config.langs;
                        other.setDefaultLang('fr');
                        i18n.getDefaultLang();
                        TranslateModule.forRoot({ useDefaultLang: false });
                        @NgModule({ imports: [TranslateModule] })
                        export class I18nModule {}
                        """,
                        source -> source.path("src/app/i18n.module.ts")
                ),
                text(
                        "this.translate.setDefaultLang('en');",
                        source -> source.path("src/app/no-ngx-import.ts")
                )
        );
    }

    @Test
    void sourceRecipeDoesNotTurnLegacyPropertyWritesIntoInvalidMethodAssignments() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import { TranslateService } from '@ngx-translate/core';
                        translate.defaultLang = 'en';
                        translate.currentLang = selected;
                        translate.langs = ['en', 'fr'];
                        """,
                        source -> source.path("src/app/legacy-writes.ts")
                )
        );
    }

    @Test
    void sourceRecipeIsIdempotentForTargetApis() {
        rewriteRun(
                spec -> spec.recipe(sourceRecipe()),
                text(
                        """
                        import { FallbackLangChangeEvent, TranslateService } from '@ngx-translate/core';
                        translate.setFallbackLang('en');
                        translate.getFallbackLang();
                        translate.getCurrentLang();
                        translate.getLangs();
                        translate.onFallbackLangChange.subscribe(handle);
                        """,
                        source -> source.path("src/app/modern.ts")
                )
        );
    }

    @Test
    void marksESyncMateHttpLoaderAndModuleConfiguration() {
        // Reduced from ShahidBaig/eSyncMate_V2 at 8478a962:
        // https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/src/app/app.module.ts
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import { TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        const loader = new TranslateHttpLoader(http, './assets/translations/', '.json');
                        TranslateModule.forRoot();
                        """,
                        """
                        import { TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        const loader = ~~>new TranslateHttpLoader(http, './assets/translations/', '.json');
                        ~~>TranslateModule.forRoot();
                        """,
                        source -> source.path("UI/src/app/app.module.ts")
                )
        );
    }

    @Test
    void marksGenshinCalcRawLoaderProvider() {
        // Reduced from Kurarion/Genshin-Calc at c7dd4d85.
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        TranslateModule.forRoot({
                          loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory }
                        });
                        """,
                        """
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        ~~>TranslateModule.forRoot({
                          ~~>loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory }
                        });
                        """,
                        source -> source.path("src/app/app.module.ts")
                )
        );
    }

    @Test
    void marksTuniSalesGatewayMissingTranslationProvider() {
        // Reduced from FeDi20-03/TuniSalesGateway at 40e99bed.
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import { MissingTranslationHandler, TranslateModule } from '@ngx-translate/core';
                        TranslateModule.forChild({
                          missingTranslationHandler: { provide: MissingTranslationHandler, useClass: MissingHandler }
                        });
                        """,
                        """
                        import { MissingTranslationHandler, TranslateModule } from '@ngx-translate/core';
                        ~~>TranslateModule.forChild({
                          ~~>missingTranslationHandler: { provide: MissingTranslationHandler, useClass: MissingHandler }
                        });
                        """,
                        source -> source.path("src/main/webapp/app/shared/language/translation.module.ts")
                )
        );
    }

    @Test
    void marksEventWritesDirectLoadingAndCustomPluginTypes() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import { TranslateLoader, TranslateService } from '@ngx-translate/core';
                        class Loader implements TranslateLoader {}
                        translate.onLangChange.emit(event);
                        translate.getTranslation('en');
                        """,
                        """
                        import { TranslateLoader, TranslateService } from '@ngx-translate/core';
                        class Loader ~~>implements TranslateLoader {}
                        translate~~>.onLangChange.emit(event);
                        translate~~>.getTranslation('en');
                        """,
                        source -> source.path("src/app/custom-loader.ts")
                )
        );
    }

    @Test
    void marksConcurrencySsrLegacyPropertiesAndDeepImports() {
        rewriteRun(
                spec -> spec.recipe(riskRecipe()),
                text(
                        """
                        import { TranslateService } from '@ngx-translate/core/internal/public-api';
                        this.translateService.use(nextLanguage);
                        const language = i18n.currentLang;
                        i18n.setDefaultLang('fr');
                        i18n.onDefaultLangChange.subscribe(handle);
                        if (window.localStorage) initialize();
                        """,
                        """
                        import { TranslateService } ~~>from '@ngx-translate/core/internal/public-api';
                        ~~>this.translateService.use(nextLanguage);
                        const language = i18n~~>.currentLang;
                        i18n~~>.setDefaultLang('fr');
                        i18n~~>.onDefaultLangChange.subscribe(handle);
                        if (~~>window.localStorage) initialize();
                        """,
                        source -> source.path("src/app/language-switcher.ts")
                )
        );
    }

    @Test
    void compositeUpgradesDependencyMigratesSafeSourceAndMarksRisk() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(COMPOSITE_RECIPE)),
                json(
                        "{\"dependencies\":{\"@ngx-translate/core\":\"^14.0.0\"}}",
                        "{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\"}}",
                        source -> source.path("package.json")
                ),
                text(
                        """
                        import { TranslateModule, TranslateService } from '@ngx-translate/core';
                        translateService.setDefaultLang('en');
                        translateService.use(selected);
                        """,
                        """
                        import { TranslateModule, TranslateService } from '@ngx-translate/core';
                        translateService.setFallbackLang('en');
                        ~~>translateService.use(selected);
                        """,
                        source -> source.path("src/app/i18n.ts")
                )
        );
    }

    @Test
    void discoversAndValidatesEveryRecipe() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe standalone = environment.activateRecipes(STANDALONE_RECIPE);
        Recipe source = environment.activateRecipes(SOURCE_RECIPE);
        Recipe risks = environment.activateRecipes(RISK_RECIPE);
        Recipe composite = environment.activateRecipes(COMPOSITE_RECIPE);

        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> DEPENDENCY_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> STANDALONE_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> SOURCE_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> RISK_RECIPE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> COMPOSITE_RECIPE.equals(recipe.getName())));
        assertEquals("Upgrade selected @ngx-translate/core declarations to 17.0.0", dependency.getDisplayName());
        assertEquals("Use standalone ngx-translate pipe and directive in standalone components", standalone.getDisplayName());
        assertEquals("Migrate deterministic ngx-translate source constructs to version 17", source.getDisplayName());
        assertEquals("Find ngx-translate 17 migration risks requiring manual review", risks.getDisplayName());
        assertEquals("Migrate ngx-translate applications to core 17.0.0", composite.getDisplayName());
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(standalone.validate().isValid(), () -> standalone.validate().failures().toString());
        assertTrue(source.validate().isValid(), () -> source.validate().failures().toString());
        assertTrue(risks.validate().isValid(), () -> risks.validate().failures().toString());
        assertTrue(composite.validate().isValid(), () -> composite.validate().failures().toString());
    }

    private static SourceSpecs packageVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"@ngx-translate/core\":\"" + version + "\"}}",
                "{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\"}}",
                source -> source.path(path)
        );
    }

    private static Recipe sourceRecipe() {
        return environment().activateRecipes(SOURCE_RECIPE);
    }

    private static Recipe riskRecipe() {
        return environment().activateRecipes(RISK_RECIPE);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxtranslate")
                .scanYamlResources()
                .build();
    }
}
