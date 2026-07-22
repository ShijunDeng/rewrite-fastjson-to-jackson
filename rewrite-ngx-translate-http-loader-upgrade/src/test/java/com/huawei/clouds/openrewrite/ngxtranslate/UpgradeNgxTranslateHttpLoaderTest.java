package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeNgxTranslateHttpLoaderTest implements RewriteTest {
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.ngxtranslate.MigrateNgxTranslateHttpLoaderTo17";
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateHttpLoaderTo17";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.ngxtranslate.MigrateDeterministicHttpLoaderFactoriesTo17";
    private static final String AUDIT_RECIPE =
            "com.huawei.clouds.openrewrite.ngxtranslate.FindManualHttpLoader17MigrationRisks";
    private static final String COMPANION_RECIPE =
            "com.huawei.clouds.openrewrite.ngxtranslate.FindHttpLoader17CompanionDependencies";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(MIGRATION_RECIPE));
    }

    @ParameterizedTest(name = "upgrades {0} declaration {1}")
    @MethodSource("selectedDeclarations")
    void upgradesEverySelectedDirectDeclaration(String section, String declaration) {
        rewriteRun(
                spec -> spec.recipe(recipe(DEPENDENCY_RECIPE)),
                json(
                        "{\"" + section + "\":{\"@ngx-translate/http-loader\":\"" + declaration + "\"}}",
                        "{\"" + section + "\":{\"@ngx-translate/http-loader\":\"17.0.0\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"=4.0.0", "v4.0.0", "^v4.0.0", "=8.0.0", "v8.0.0", "^v8.0.0"})
    void upgradesExplicitRegistryPrefixes(String declaration) {
        rewriteRun(
                spec -> spec.recipe(recipe(DEPENDENCY_RECIPE)),
                json(
                        "{\"dependencies\":{\"@ngx-translate/http-loader\":\"" + declaration + "\"}}",
                        "{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void upgradesESyncMateRealPackageDeclaration() {
        // Reduced from ShahidBaig/eSyncMate_V2 at 8478a96267fb692985c70e32f5dde0544209d6a5.
        // https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/package.json
        rewriteRun(
                spec -> spec.recipe(recipe(DEPENDENCY_RECIPE)),
                json(
                        "{\"dependencies\":{\"@ngx-translate/core\":\"^15.0.0\",\"@ngx-translate/http-loader\":\"^8.0.0\",\"rxjs\":\"~7.8.0\"}}",
                        "{\"dependencies\":{\"@ngx-translate/core\":\"^15.0.0\",\"@ngx-translate/http-loader\":\"17.0.0\",\"rxjs\":\"~7.8.0\"}}",
                        source -> source.path("UI/package.json")
                )
        );
    }

    @ParameterizedTest(name = "preserves unsupported declaration {0}")
    @ValueSource(strings = {
            "3.0.1", "4.0.1", "5.0.0", "6.0.1", "7.0.1", "8.0.1", "9.0.0", "16.0.0",
            "17.0.0", "^17.0.0", "18.0.0", "4.x", ">=4 <9", "^6.0.0 || ^8.0.0", "8.0.0-beta.1", "latest"
    })
    void preservesUnsupportedDeclarations(String declaration) {
        rewriteRun(
                spec -> spec.recipe(recipe(DEPENDENCY_RECIPE)),
                json(
                        "{\"dependencies\":{\"@ngx-translate/http-loader\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "workspace:^8.0.0", "npm:@company/http-loader@8.0.0", "file:../http-loader",
            "git+https://github.com/ngx-translate/http-loader.git#8.0.0", "https://example.test/http-loader.tgz"
    })
    void preservesProtocolsAliasesAndExternalReferences(String declaration) {
        rewriteRun(
                spec -> spec.recipe(recipe(DEPENDENCY_RECIPE)),
                json(
                        "{\"dependencies\":{\"@ngx-translate/http-loader\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void preservesNestedMetadataNonScalarsLockfilesAndOtherJsonFiles() {
        rewriteRun(
                spec -> spec.recipe(recipe(DEPENDENCY_RECIPE)),
                json(
                        "{\"overrides\":{\"@ngx-translate/http-loader\":\"8.0.0\"},\"dependencies\":{\"@ngx-translate/http-loader\":{\"version\":\"8.0.0\"}},\"devDependencies\":{\"@ngx-translate/http-loader\":[\"8.0.0\"]}}",
                        source -> source.path("package.json")
                ),
                json(
                        "{\"dependencies\":{\"@ngx-translate/http-loader\":\"8.0.0\"}}",
                        source -> source.path("package-lock.json")
                ),
                json(
                        "{\"dependencies\":{\"@ngx-translate/http-loader\":\"8.0.0\"}}",
                        source -> source.path("config/dependencies.json")
                )
        );
    }

    @Test
    void migratesESyncMateFactoryAndNgModuleProvider() {
        // Reduced from ShahidBaig/eSyncMate_V2 at 8478a96267fb692985c70e32f5dde0544209d6a5.
        // https://github.com/ShahidBaig/eSyncMate_V2/blob/8478a96267fb692985c70e32f5dde0544209d6a5/UI/src/app/app.module.ts
        rewriteRun(
                spec -> spec.recipe(recipe(SOURCE_RECIPE)),
                text(
                        """
                        import { HTTP_INTERCEPTORS, HttpClientModule, HttpClient } from '@angular/common/http';
                        import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';

                        export function HttpLoaderFactory(http: HttpClient) {
                          return new TranslateHttpLoader(http, './assets/translations/', '.json');
                        }
                        @NgModule({
                          imports: [HttpClientModule, TranslateModule.forRoot({
                            loader: {
                              provide: TranslateLoader,
                              useFactory: HttpLoaderFactory,
                              deps: [HttpClient]
                            }
                          })],
                          providers: [{ provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }]
                        })
                        export class AppModule {}
                        """,
                        """
                        import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
                        import { TranslateModule } from '@ngx-translate/core';
                        import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';

                        @NgModule({
                          imports: [HttpClientModule, TranslateModule.forRoot({
                            loader: provideTranslateHttpLoader({ prefix: './assets/translations/', suffix: '.json' })
                          })],
                          providers: [{ provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }]
                        })
                        export class AppModule {}
                        """,
                        source -> source.path("UI/src/app/app.module.ts")
                )
        );
    }

    @Test
    void migratesGenshinCalcLiteralPrefixFactory() {
        // Reduced from Kurarion/Genshin-Calc at c7dd4d850db8523e33302e98d71d9e180605bd4e.
        // https://github.com/Kurarion/Genshin-Calc/blob/c7dd4d850db8523e33302e98d71d9e180605bd4e/src/app/app.module.ts
        rewriteRun(
                spec -> spec.recipe(recipe(SOURCE_RECIPE)),
                text(
                        """
                        import {HttpClientModule, HttpClient, HttpClientJsonpModule} from '@angular/common/http';
                        import {TranslateModule, TranslateLoader} from '@ngx-translate/core';
                        import {TranslateHttpLoader} from '@ngx-translate/http-loader';

                        export function HttpLoaderFactory(http: HttpClient) {
                          return new TranslateHttpLoader(http, `assets/i18n/`);
                        }

                        TranslateModule.forRoot({
                          fallbackLang: environment.defaultLang,
                          loader: {
                            provide: TranslateLoader,
                            useFactory: HttpLoaderFactory,
                            deps: [HttpClient],
                          },
                        });
                        """,
                        """
                        import { HttpClientModule, HttpClientJsonpModule } from '@angular/common/http';
                        import { TranslateModule } from '@ngx-translate/core';
                        import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';

                        TranslateModule.forRoot({
                          fallbackLang: environment.defaultLang,
                          loader: provideTranslateHttpLoader({ prefix: `assets/i18n/` }),
                        });
                        """,
                        source -> source.path("src/app/app.module.ts")
                )
        );
    }

    @Test
    void migratesDefaultConstructorAndKeepsImportsUsedElsewhere() {
        rewriteRun(
                spec -> spec.recipe(recipe(SOURCE_RECIPE)),
                text(
                        """
                        import { HttpClient } from '@angular/common/http';
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function loaderFactory(http: HttpClient): TranslateHttpLoader {
                          return new TranslateHttpLoader(http);
                        }
                        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: loaderFactory, deps: [HttpClient] } });
                        const keepHttp: HttpClient = inject(HttpClient);
                        class CustomLoader implements TranslateLoader { getTranslation() { return of({}); } }
                        """,
                        """
                        import { HttpClient } from '@angular/common/http';
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
                        TranslateModule.forRoot({ loader: provideTranslateHttpLoader() });
                        const keepHttp: HttpClient = inject(HttpClient);
                        class CustomLoader implements TranslateLoader { getTranslation() { return of({}); } }
                        """,
                        source -> source.path("src/app/i18n.module.ts")
                )
        );
    }

    @Test
    void doesNotGuessCrossFileOrDynamicFactories() {
        rewriteRun(
                spec -> spec.recipe(recipe(SOURCE_RECIPE)),
                text(
                        """
                        import { HttpClient } from '@angular/common/http';
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function translatePartialLoader(http: HttpClient): TranslateLoader {
                          return new TranslateHttpLoader(http, environment.i18nPrefix, `.json?hash=${I18N_HASH}`);
                        }
                        """,
                        source -> source.path("src/app/config/translation.config.ts")
                ),
                text(
                        """
                        import { HttpClient } from '@angular/common/http';
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        TranslateModule.forRoot({
                          loader: { provide: TranslateLoader, useFactory: translatePartialLoader, deps: [HttpClient] }
                        });
                        """,
                        source -> source.path("src/app/shared/language/translation.module.ts")
                )
        );
    }

    @Test
    void doesNotRewriteCommentsStringsOrMultipleFactoryCandidates() {
        rewriteRun(
                spec -> spec.recipe(recipe(SOURCE_RECIPE)),
                text(
                        """
                        import { HttpClient } from '@angular/common/http';
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        // export function fake(http: HttpClient) { return new TranslateHttpLoader(http); }
                        const docs = "loader: { provide: TranslateLoader, useFactory: fake, deps: [HttpClient] }";
                        export function first(http: HttpClient) { return new TranslateHttpLoader(http); }
                        export function second(http: HttpClient) { return new TranslateHttpLoader(http); }
                        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: first, deps: [HttpClient] } });
                        """,
                        source -> source.path("src/app/docs.ts")
                )
        );
    }

    @Test
    void marksTuniSalesGatewayCrossFileFactoryAndConstructor() {
        // Reduced from FeDi20-03/TuniSalesGateway at 40e99bedf123169767fbcf7200f4e2e0a94eb402.
        // https://github.com/FeDi20-03/TuniSalesGateway/blob/40e99bedf123169767fbcf7200f4e2e0a94eb402/src/main/webapp/app/config/translation.config.ts
        rewriteRun(
                spec -> spec.recipe(recipe(AUDIT_RECIPE)),
                text(
                        """
                        import { TranslateLoader } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function translatePartialLoader(http: HttpClient): TranslateLoader {
                          return new TranslateHttpLoader(http, 'i18n/', `.json?_=${I18N_HASH}`);
                        }
                        """,
                        """
                        import { TranslateLoader } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function translatePartialLoader(http: HttpClient): TranslateLoader {
                          return ~~>new TranslateHttpLoader(http, 'i18n/', `.json?_=${I18N_HASH}`);
                        }
                        """,
                        source -> source.path("src/main/webapp/app/config/translation.config.ts")
                ),
                text(
                        """
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import '@ngx-translate/http-loader';
                        TranslateModule.forRoot({
                          loader: { provide: TranslateLoader, useFactory: translatePartialLoader, deps: [HttpClient] }
                        });
                        """,
                        """
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import '@ngx-translate/http-loader';
                        TranslateModule.forRoot({
                          ~~>loader: { provide: TranslateLoader, useFactory: translatePartialLoader, deps: [HttpClient] }
                        });
                        """,
                        source -> source.path("src/main/webapp/app/shared/language/translation.module.ts")
                )
        );
    }

    @Test
    void marksCustomContractHttpRegistrationBehaviorAndDynamicPathRisks() {
        rewriteRun(
                spec -> spec.recipe(recipe(AUDIT_RECIPE)),
                text(
                        """
                        import { TranslateLoader } from '@ngx-translate/core';
                        import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
                        import { HttpClientModule, provideHttpClient } from '@angular/common/http';
                        class TenantLoader implements TranslateLoader {
                          getTranslation(lang: string): Observable<any> { return load(lang); }
                        }
                        const loader = provideTranslateHttpLoader({ prefix: environment.i18n, useHttpBackend: true, enforceLoading: true });
                        const http = provideHttpClient();
                        """,
                        """
                        import { TranslateLoader } from '@ngx-translate/core';
                        import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
                        import { ~~>HttpClientModule, ~~>provideHttpClient } from '@angular/common/http';
                        class TenantLoader ~~>implements TranslateLoader {
                          ~~>getTranslation(lang: string): Observable<any> { return load(lang); }
                        }
                        const loader = ~~>provideTranslateHttpLoader({ prefix: environment.i18n, ~~>useHttpBackend: true, ~~>enforceLoading: true });
                        const http = ~~>provideHttpClient();
                        """,
                        source -> source.path("src/app/i18n.ts")
                )
        );
    }

    @Test
    void marksCoreAndAngularCompanionDeclarations() {
        rewriteRun(
                spec -> spec.recipe(recipe(COMPANION_RECIPE)),
                json(
                        "{\"dependencies\":{\"@ngx-translate/core\":\"15.0.0\",\"@angular/core\":\"15.2.0\"},\"devDependencies\":{\"@angular/common\":\"15.2.0\"}}",
                        "{\"dependencies\":{/*~~>*/\"@ngx-translate/core\":\"15.0.0\",/*~~>*/\"@angular/core\":\"15.2.0\"},\"devDependencies\":{/*~~>*/\"@angular/common\":\"15.2.0\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void fullMigrationChangesSourceThenMarksOnlyRemainingReviewSites() {
        rewriteRun(
                text(
                        """
                        import { HttpClientModule, HttpClient } from '@angular/common/http';
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function factory(http: HttpClient) { return new TranslateHttpLoader(http, '/assets/i18n/', '.json'); }
                        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: factory, deps: [HttpClient] } });
                        """,
                        """
                        import { ~~>HttpClientModule } from '@angular/common/http';
                        import { TranslateModule } from '@ngx-translate/core';
                        import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
                        TranslateModule.forRoot({ loader: provideTranslateHttpLoader({ prefix: '/assets/i18n/', suffix: '.json' }) });
                        """,
                        source -> source.path("src/app/app.module.ts")
                )
        );
    }

    @Test
    void discoversAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{
                MIGRATION_RECIPE, DEPENDENCY_RECIPE, SOURCE_RECIPE, AUDIT_RECIPE, COMPANION_RECIPE
        }) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> name + ": " + recipe.validate().failures());
        }
    }

    private static Stream<Arguments> selectedDeclarations() {
        return Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                .flatMap(section -> Stream.of("4.0.0", "6.0.0", "7.0.0", "8.0.0")
                        .flatMap(version -> Stream.of("", "^", "~")
                                .map(prefix -> Arguments.of(section, prefix + version))));
    }

    private static Recipe recipe(String name) {
        return environment().activateRecipes(name);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxtranslate")
                .scanYamlResources()
                .build();
    }
}
