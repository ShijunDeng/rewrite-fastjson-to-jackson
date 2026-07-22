package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.javascript.Assertions.typescript;

class HttpLoaderSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "literal factory shape {0}")
    @MethodSource("literalFactoryShapes")
    void migratesExactSameFileLiteralFactories(String label, String constructorArgs, String providerCall) {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicHttpLoaderFactory()),
                typescript(
                        """
                        import { HttpClient } from '@angular/common/http';
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function loaderFactory(http: HttpClient) {
                          return new TranslateHttpLoader(http%s);
                        }
                        TranslateModule.forRoot({
                          loader: { provide: TranslateLoader, useFactory: loaderFactory, deps: [HttpClient] }
                        });
                        """.formatted(constructorArgs),
                        """
                        import { TranslateModule } from '@ngx-translate/core';
                        import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
                        TranslateModule.forRoot({
                          loader: %s
                        });
                        """.formatted(providerCall),
                        source -> source.path("src/" + label + ".ts"))
        );
    }

    static Stream<Arguments> literalFactoryShapes() {
        return Stream.of(
                Arguments.of("defaults", "", "provideTranslateHttpLoader()"),
                Arguments.of("single-quote-prefix", ", './assets/i18n/'", "provideTranslateHttpLoader({ prefix: './assets/i18n/' })"),
                Arguments.of("double-quote-prefix", ", \"/translations/\"", "provideTranslateHttpLoader({ prefix: \"/translations/\" })"),
                Arguments.of("template-prefix", ", `assets/i18n/`", "provideTranslateHttpLoader({ prefix: `assets/i18n/` })"),
                Arguments.of("single-quote-both", ", './i18n/', '.json'", "provideTranslateHttpLoader({ prefix: './i18n/', suffix: '.json' })"),
                Arguments.of("double-quote-both", ", \"/locale/\", \".lang.json\"", "provideTranslateHttpLoader({ prefix: \"/locale/\", suffix: \".lang.json\" })"),
                Arguments.of("template-both", ", `i18n/`, `.json`", "provideTranslateHttpLoader({ prefix: `i18n/`, suffix: `.json` })")
        );
    }

    @Test
    void migratesESyncMateFactoryAtPinnedCommit() {
        // Reduced from ShahidBaig/eSyncMate_V2@8478a96267fb692985c70e32f5dde0544209d6a5.
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicHttpLoaderFactory()),
                typescript(
                        """
                        import { HTTP_INTERCEPTORS, HttpClientModule, HttpClient } from '@angular/common/http';
                        import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function HttpLoaderFactory(http: HttpClient) {
                          return new TranslateHttpLoader(http, './assets/translations/', '.json');
                        }
                        @NgModule({
                          imports: [HttpClientModule, TranslateModule.forRoot({
                            loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory, deps: [HttpClient] }
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
                        source -> source.path("UI/src/app/app.module.ts"))
        );
    }

    @Test
    void migratesGenshinCalcFactoryAtPinnedCommit() {
        // Reduced from Kurarion/Genshin-Calc@c7dd4d850db8523e33302e98d71d9e180605bd4e.
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicHttpLoaderFactory()),
                typescript(
                        """
                        import {HttpClientModule, HttpClient, HttpClientJsonpModule} from '@angular/common/http';
                        import {TranslateModule, TranslateLoader} from '@ngx-translate/core';
                        import {TranslateHttpLoader} from '@ngx-translate/http-loader';
                        export function HttpLoaderFactory(http: HttpClient) {
                          return new TranslateHttpLoader(http, `assets/i18n/`);
                        }
                        TranslateModule.forRoot({
                          fallbackLang: environment.defaultLang,
                          loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory, deps: [HttpClient], },
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
                        source -> source.path("src/app/app.module.ts"))
        );
    }

    @Test
    void prunesOnlyImportsProvenUnused() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicHttpLoaderFactory()),
                typescript(
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
                        source -> source.path("src/keep-imports.ts"))
        );
    }

    @ParameterizedTest(name = "conservative no-op {0}")
    @MethodSource("unsafeSources")
    void preservesFactoriesWhoseEquivalenceIsNotProven(String label, String sourceCode) {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicHttpLoaderFactory()),
                typescript(sourceCode, source -> source.path("src/no-op-" + label + ".ts"))
        );
    }

    static Stream<Arguments> unsafeSources() {
        String imports = "import { HttpClient } from '@angular/common/http';\n" +
                         "import { TranslateLoader, TranslateModule } from '@ngx-translate/core';\n" +
                         "import { TranslateHttpLoader } from '@ngx-translate/http-loader';\n";
        return Stream.of(
                Arguments.of("dynamic-prefix", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http, environment.prefix); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n"),
                Arguments.of("dynamic-suffix", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http, 'i18n/', environment.suffix); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n"),
                Arguments.of("template-interpolation", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http, `/${tenant}/i18n/`); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n"),
                Arguments.of("two-factories", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nexport function g(http: HttpClient) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n"),
                Arguments.of("two-providers", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nconst a = { loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } };\nconst b = { loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } };\n"),
                Arguments.of("cross-file-provider", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http); }\n"),
                Arguments.of("cross-file-factory", imports + "TranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: externalFactory, deps: [HttpClient] } });\n"),
                Arguments.of("extra-statement", imports + "export function f(http: HttpClient) { audit(); return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n"),
                Arguments.of("wrong-parameter-type", imports + "export function f(http: HttpBackend) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n"),
                Arguments.of("wrong-constructor-receiver", imports + "export function f(client: HttpClient) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n"),
                Arguments.of("missing-deps", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f } });\n"),
                Arguments.of("deps-extra", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient, TOKEN] } });\n"),
                Arguments.of("provider-order", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { useFactory: f, provide: TranslateLoader, deps: [HttpClient] } });\n"),
                Arguments.of("factory-alias", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: alias, deps: [HttpClient] } });\n"),
                Arguments.of("import-alias", "import { HttpClient } from '@angular/common/http';\nimport { TranslateLoader, TranslateModule } from '@ngx-translate/core';\nimport { TranslateHttpLoader as Loader } from '@ngx-translate/http-loader';\nexport function f(http: HttpClient) { return new Loader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n"),
                Arguments.of("namespace-import", "import { HttpClient } from '@angular/common/http';\nimport { TranslateLoader, TranslateModule } from '@ngx-translate/core';\nimport * as httpLoader from '@ngx-translate/http-loader';\nexport function f(http: HttpClient) { return new httpLoader.TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n"),
                Arguments.of("constructor-in-comment", imports + "// export function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n"),
                Arguments.of("provider-in-string", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nconst docs = 'loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] }';\n"),
                Arguments.of("nested-module-config", imports + "export function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ nested: { loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } } });\n"),
                Arguments.of("target-helper", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nconst providers = provideTranslateHttpLoader({ prefix: './i18n/', suffix: '.json' });\n"),
                Arguments.of("no-loader-import", "import { HttpClient } from '@angular/common/http';\nimport { TranslateLoader, TranslateModule } from '@ngx-translate/core';\nexport function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n")
        );
    }

    @Test
    void deterministicFactoryMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicHttpLoaderFactory())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import { HttpClient } from '@angular/common/http';\nimport { TranslateLoader, TranslateModule } from '@ngx-translate/core';\nimport { TranslateHttpLoader } from '@ngx-translate/http-loader';\nexport function f(http: HttpClient) { return new TranslateHttpLoader(http, './i18n/', '.json'); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n",
                        "import { TranslateModule } from '@ngx-translate/core';\nimport { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nTranslateModule.forRoot({ loader: provideTranslateHttpLoader({ prefix: './i18n/', suffix: '.json' }) });\n",
                        source -> source.path("src/idempotent.ts")),
                typescript(
                        "import { HttpClient } from '@angular/common/http';\nimport { TranslateLoader, TranslateModule } from '@ngx-translate/core';\nimport { TranslateHttpLoader } from '@ngx-translate/http-loader';\nexport function f(http: HttpClient) { return new TranslateHttpLoader(http); }\nTranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: f, deps: [HttpClient] } });\n",
                        source -> source.path("node_modules/generated.ts"))
        );
    }
}
