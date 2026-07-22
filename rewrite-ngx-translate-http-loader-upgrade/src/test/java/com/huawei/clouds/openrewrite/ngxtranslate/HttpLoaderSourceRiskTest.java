package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;

class HttpLoaderSourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "marks source risk {0}")
    @MethodSource("riskCases")
    void marksEveryDocumentedSourceRisk(String label, String sourceCode, String message) {
        rewriteRun(
                spec -> spec.recipe(new FindHttpLoaderJavaScriptRisks()),
                typescript(sourceCode, source -> source.path("src/" + label + ".ts")
                        .after(actual -> actual)
                        .afterRecipe(cu -> assertTrue(cu.printAll().contains(message), cu::printAll)))
        );
    }

    static Stream<Arguments> riskCases() {
        String imports = "import { HttpClient } from '@angular/common/http';\n" +
                         "import { TranslateLoader, TranslateModule } from '@ngx-translate/core';\n" +
                         "import { TranslateHttpLoader } from '@ngx-translate/http-loader';\n";
        return Stream.of(
                Arguments.of("deep-lib", "import { TranslateHttpLoader } from '@ngx-translate/http-loader/lib/http-loader';\n", "deep imports are unstable"),
                Arguments.of("deep-src", "import { TranslateHttpLoader } from '@ngx-translate/http-loader/src/lib/http-loader';\n", "deep imports are unstable"),
                Arguments.of("constructor-three", imports + "const loader = new TranslateHttpLoader(http, './i18n/', '.json');\n", "injection-only zero-argument constructor"),
                Arguments.of("constructor-one", imports + "const loader = new TranslateHttpLoader(http);\n", "injection-only zero-argument constructor"),
                Arguments.of("constructor-zero", imports + "const loader = new TranslateHttpLoader();\n", "replace direct construction"),
                Arguments.of("constructor-alias", "import { TranslateHttpLoader as Loader } from '@ngx-translate/http-loader';\nconst loader = new Loader(http);\n", "injection-only zero-argument constructor"),
                Arguments.of("raw-factory", imports + "TranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: factory, deps: [HttpClient] } });\n", "Raw TranslateLoader providers require"),
                Arguments.of("raw-class", imports + "TranslateModule.forRoot({ loader: { provide: TranslateLoader, useClass: CustomLoader } });\n", "Raw TranslateLoader providers require"),
                Arguments.of("raw-value", imports + "TranslateModule.forRoot({ loader: { provide: TranslateLoader, useValue: loader } });\n", "Raw TranslateLoader providers require"),
                Arguments.of("raw-existing", imports + "TranslateModule.forRoot({ loader: { provide: TranslateLoader, useExisting: ExistingLoader } });\n", "Raw TranslateLoader providers require"),
                Arguments.of("raw-alias", "import { TranslateLoader as LoaderToken, TranslateModule } from '@ngx-translate/core';\nimport '@ngx-translate/http-loader';\nTranslateModule.forRoot({ loader: { provide: LoaderToken, useFactory: factory } });\n", "Raw TranslateLoader providers require"),
                Arguments.of("helper-default", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader();\n", "provider order"),
                Arguments.of("helper-alias", "import { provideTranslateHttpLoader as provideLoader } from '@ngx-translate/http-loader';\nprovideLoader({ prefix: './i18n/' });\n", "provider order"),
                Arguments.of("backend", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader({ useHttpBackend: true });\n", "bypasses HttpClient interceptors"),
                Arguments.of("loading", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader({ enforceLoading: true });\n", "timestamp to every request"),
                Arguments.of("environment-prefix", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader({ prefix: environment.i18n });\n", "Dynamic translation URL"),
                Arguments.of("window-prefix", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader({ prefix: window.baseUrl });\n", "Dynamic translation URL"),
                Arguments.of("location-prefix", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader({ prefix: location.origin + '/i18n/' });\n", "Dynamic translation URL"),
                Arguments.of("function-prefix", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader({ prefix: resolvePrefix() });\n", "Dynamic translation URL"),
                Arguments.of("identifier-suffix", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader({ suffix: I18N_SUFFIX });\n", "Dynamic translation URL"),
                Arguments.of("template-suffix", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader({ suffix: `.json?hash=${HASH}` });\n", "Dynamic translation URL"),
                Arguments.of("http-module", "import { HttpClientModule } from '@angular/common/http';\nimport { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader();\n", "HTTP is registered exactly once"),
                Arguments.of("http-provider", "import { provideHttpClient } from '@angular/common/http';\nimport { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideHttpClient();\n", "root provideHttpClient features"),
                Arguments.of("http-provider-alias", "import { provideHttpClient as provideHttp } from '@angular/common/http';\nimport { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideHttp();\n", "root provideHttpClient features")
        );
    }

    @Test
    void marksCustomLoaderContractAtAllUsefulNodes() {
        rewriteRun(
                spec -> spec.recipe(new FindHttpLoaderJavaScriptRisks()),
                typescript(
                        """
                        import { TranslateLoader } from '@ngx-translate/core';
                        class TenantLoader implements TranslateLoader {
                          getTranslation(lang: string): Observable<any> { return load(lang); }
                        }
                        """,
                        source -> source.path("src/tenant-loader.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Custom TranslateLoader implementations"));
                                    assertTrue(printed.contains("Compile this custom TranslateLoader"));
                                    assertTrue(printed.contains("Observable<TranslationObject>"));
                                }))
        );
    }

    @Test
    void marksTuniSalesGatewayCrossFileBoundariesAtPinnedCommit() {
        // FeDi20-03/TuniSalesGateway@40e99bedf123169767fbcf7200f4e2e0a94eb402.
        rewriteRun(
                spec -> spec.recipe(new FindHttpLoaderJavaScriptRisks()),
                typescript(
                        """
                        import { TranslateLoader } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function translatePartialLoader(http: HttpClient): TranslateLoader {
                          return new TranslateHttpLoader(http, 'i18n/', `.json?_=${I18N_HASH}`);
                        }
                        """,
                        source -> source.path("src/main/webapp/app/config/translation.config.ts")
                                .after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("injection-only zero-argument constructor")))),
                typescript(
                        """
                        import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
                        import '@ngx-translate/http-loader';
                        TranslateModule.forRoot({
                          loader: { provide: TranslateLoader, useFactory: translatePartialLoader, deps: [HttpClient] }
                        });
                        """,
                        source -> source.path("src/main/webapp/app/shared/language/translation.module.ts")
                                .after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("Raw TranslateLoader providers require"))))
        );
    }

    @ParameterizedTest(name = "no false positive {0}")
    @MethodSource("safeCases")
    void leavesUnownedAndUnrelatedSameNamedSourceUnmarked(String label, String sourceCode) {
        rewriteRun(
                spec -> spec.recipe(new FindHttpLoaderJavaScriptRisks()),
                typescript(sourceCode, source -> source.path("src/safe-" + label + ".ts"))
        );
    }

    static Stream<Arguments> safeCases() {
        return Stream.of(
                Arguments.of("constructor-no-import", "const loader = new TranslateHttpLoader(http);\n"),
                Arguments.of("other-package", "import { TranslateHttpLoader } from '@vendor/i18n';\nnew TranslateHttpLoader(http);\n"),
                Arguments.of("ordinary-loader", "const config = { loader: { provide: TranslateLoader, useFactory: factory } };\n"),
                Arguments.of("ordinary-backend", "const config = { useHttpBackend: true, enforceLoading: true };\n"),
                Arguments.of("ordinary-prefix", "const config = { prefix: environment.i18n, suffix: extension };\n"),
                Arguments.of("http-without-loader", "import { HttpClientModule, provideHttpClient } from '@angular/common/http';\nprovideHttpClient();\n"),
                Arguments.of("custom-unrelated", "interface TranslateLoader {}\nclass Loader implements TranslateLoader { getTranslation() {} }\n"),
                Arguments.of("root-public-import", "import { TranslateHttpLoaderConfig } from '@ngx-translate/http-loader';\nconst config: TranslateHttpLoaderConfig = { prefix: './i18n/', suffix: '.json', enforceLoading: false, useHttpBackend: false };\n"),
                Arguments.of("side-effect-only", "import '@ngx-translate/http-loader';\nconst loader = registry.loader;\n"),
                Arguments.of("helper-other-package", "import { provideTranslateHttpLoader } from '@vendor/i18n';\nprovideTranslateHttpLoader({ prefix: environment.i18n });\n")
        );
    }

    @ParameterizedTest(name = "static provider option {0}")
    @MethodSource("staticOptions")
    void doesNotMisclassifyStaticPathsOrFalseBehaviorFlags(String label, String options) {
        rewriteRun(
                spec -> spec.recipe(new FindHttpLoaderJavaScriptRisks()),
                typescript(
                        "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader(" + options + ");\n",
                        source -> source.path("src/static-" + label + ".ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("provider order"));
                                    assertFalse(printed.contains("Dynamic translation URL"));
                                    assertFalse(printed.contains("bypasses HttpClient interceptors"));
                                    assertFalse(printed.contains("timestamp to every request"));
                                }))
        );
    }

    static Stream<Arguments> staticOptions() {
        return Stream.of(
                Arguments.of("single", "{ prefix: './assets/i18n/' }"),
                Arguments.of("double", "{ prefix: \"/i18n/\", suffix: \".json\" }"),
                Arguments.of("template", "{ prefix: `assets/i18n/`, suffix: `.json` }"),
                Arguments.of("false-flags", "{ useHttpBackend: false, enforceLoading: false }")
        );
    }

    @Test
    void sourceRiskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindHttpLoaderJavaScriptRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { TranslateHttpLoader } from '@ngx-translate/http-loader';\nnew TranslateHttpLoader(http);\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual)),
                typescript("import { TranslateHttpLoader } from '@ngx-translate/http-loader';\nnew TranslateHttpLoader(http);\n",
                        source -> source.path("dist/generated.ts"))
        );
    }
}
