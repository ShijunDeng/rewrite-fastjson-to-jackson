package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;

class NgxTranslateSourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "marks risk: {0}")
    @MethodSource("riskCases")
    void marksEveryDocumentedSourceRisk(String label, String sourceCode, String expectedMessage) {
        rewriteRun(
                spec -> spec.recipe(new FindNgxTranslateJavaScriptRisks()),
                typescript(sourceCode, source -> source.path("src/" + label + ".ts")
                        .after(actual -> actual)
                        .afterRecipe(cu -> assertTrue(cu.printAll().contains(expectedMessage), cu::printAll)))
        );
    }

    static Stream<Arguments> riskCases() {
        String servicePrefix = "import { TranslateService } from '@ngx-translate/core';\n" +
                               "class App { constructor(private translate: TranslateService) {} run() { ";
        String serviceSuffix = " } }\n";
        return Stream.of(
                Arguments.of("deep-import", "import { TranslateService } from '@ngx-translate/core/internal/public-api';\n", "Private @ngx-translate/core deep imports"),
                Arguments.of("standalone", "import { Component } from '@angular/core';\nimport { TranslateModule } from '@ngx-translate/core';\n@Component({ standalone: true, imports: [TranslateModule] }) class App {}\n", "standalone component imports TranslateModule"),
                Arguments.of("old-event-type", "import { DefaultLangChangeEvent } from '@ngx-translate/core';\nconst event: DefaultLangChangeEvent | undefined = undefined;\n", "renamed automatically"),
                Arguments.of("old-handler", "import { FakeMissingTranslationHandler } from '@ngx-translate/core';\n", "renamed automatically"),
                Arguments.of("old-compiler", "import { TranslateFakeCompiler } from '@ngx-translate/core';\n", "renamed automatically"),
                Arguments.of("old-loader", "import { TranslateFakeLoader } from '@ngx-translate/core';\n", "renamed automatically"),
                Arguments.of("custom-loader", "import { TranslateLoader } from '@ngx-translate/core';\nclass Loader implements TranslateLoader {}\n", "tightened custom loader/compiler/parser/handler types"),
                Arguments.of("custom-compiler", "import { TranslateCompiler } from '@ngx-translate/core';\nclass Compiler implements TranslateCompiler {}\n", "tightened custom loader/compiler/parser/handler types"),
                Arguments.of("custom-parser", "import { TranslateParser } from '@ngx-translate/core';\nclass Parser extends TranslateParser {}\n", "tightened custom loader/compiler/parser/handler types"),
                Arguments.of("custom-handler", "import { MissingTranslationHandler } from '@ngx-translate/core';\nclass Handler implements MissingTranslationHandler {}\n", "tightened custom loader/compiler/parser/handler types"),
                Arguments.of("custom-loader-alias", "import { TranslateLoader as LoaderContract } from '@ngx-translate/core';\nclass Loader implements LoaderContract {}\n", "tightened custom loader/compiler/parser/handler types"),
                Arguments.of("http-loader", "import { TranslateHttpLoader } from '@ngx-translate/http-loader';\nconst loader = new TranslateHttpLoader(http, './assets/i18n/', '.json');\n", "HTTP loader uses injection/provider configuration"),
                Arguments.of("for-root", "import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({});\n", "TranslateModule configuration remains supported"),
                Arguments.of("for-child", "import { TranslateModule as I18nModule } from '@ngx-translate/core';\nI18nModule.forChild({ isolate: true });\n", "TranslateModule configuration remains supported"),
                Arguments.of("functional-provider", "import { provideTranslateService } from '@ngx-translate/core';\nprovideTranslateService({ fallbackLang: 'en' });\n", "provider ordering and nested loader/compiler/parser"),
                Arguments.of("language-use", servicePrefix + "this.translate.use(selected);" + serviceSuffix, "rapid use() calls deterministic"),
                Arguments.of("direct-loading", servicePrefix + "this.translate.getTranslation('en');" + serviceSuffix, "getTranslation() is deprecated"),
                Arguments.of("legacy-setter", servicePrefix + "this.translate.setDefaultLang('en');" + serviceSuffix, "renamed automatically"),
                Arguments.of("legacy-getter", servicePrefix + "this.translate.getDefaultLang();" + serviceSuffix, "renamed automatically"),
                Arguments.of("event-lang-emit", servicePrefix + "this.translate.onLangChange.emit(event);" + serviceSuffix, "events are readonly Observables"),
                Arguments.of("event-translation-emit", servicePrefix + "this.translate.onTranslationChange.emit(event);" + serviceSuffix, "events are readonly Observables"),
                Arguments.of("event-default-emit", servicePrefix + "this.translate.onDefaultLangChange.emit(event);" + serviceSuffix, "events are readonly Observables"),
                Arguments.of("event-fallback-emit", servicePrefix + "this.translate.onFallbackLangChange.emit(event);" + serviceSuffix, "events are readonly Observables"),
                Arguments.of("default-property", servicePrefix + "return this.translate.defaultLang;" + serviceSuffix, "property is deprecated"),
                Arguments.of("current-property", servicePrefix + "return this.translate.currentLang;" + serviceSuffix, "property is deprecated"),
                Arguments.of("languages-property", servicePrefix + "return this.translate.langs;" + serviceSuffix, "property is deprecated"),
                Arguments.of("legacy-event", servicePrefix + "return this.translate.onDefaultLangChange;" + serviceSuffix, "event is renamed automatically"),
                Arguments.of("default-language", "import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ defaultLanguage: 'en' });\n", "Legacy default-language configuration"),
                Arguments.of("use-default", "import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ useDefaultLang: false });\n", "Legacy default-language configuration"),
                Arguments.of("raw-loader", "import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ loader: { provide: Loader, useClass: Custom } });\n", "Raw provider objects require"),
                Arguments.of("raw-compiler", "import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ compiler: { provide: Compiler, useClass: Custom } });\n", "Raw provider objects require"),
                Arguments.of("raw-parser", "import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ parser: { provide: Parser, useClass: Custom } });\n", "Raw provider objects require"),
                Arguments.of("raw-handler", "import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ missingTranslationHandler: { provide: Handler, useClass: Custom } });\n", "Raw provider objects require"),
                Arguments.of("ssr-window", "import { TranslateService } from '@ngx-translate/core';\nwindow.localStorage.getItem('lang');\n", "Browser-only globals/render hooks"),
                Arguments.of("ssr-document", "import { TranslateService } from '@ngx-translate/core';\ndocument.documentElement.lang = 'en';\n", "Browser-only globals/render hooks"),
                Arguments.of("ssr-navigator", "import { TranslateService } from '@ngx-translate/core';\nconst language = navigator.language;\n", "Browser-only globals/render hooks"),
                Arguments.of("ssr-platform", "import { isPlatformBrowser } from '@angular/common';\nimport { TranslateService } from '@ngx-translate/core';\nif (isPlatformBrowser(platformId)) initialize();\n", "Browser-only globals/render hooks"),
                Arguments.of("ssr-render", "import { afterNextRender } from '@angular/core';\nimport { TranslateService } from '@ngx-translate/core';\nafterNextRender(() => initialize());\n", "Browser-only globals/render hooks")
        );
    }

    @ParameterizedTest(name = "no false positive: {0}")
    @MethodSource("safeCases")
    void leavesModernAndUnownedSourceUnmarked(String label, String sourceCode) {
        rewriteRun(
                spec -> spec.recipe(new FindNgxTranslateJavaScriptRisks()),
                typescript(sourceCode, source -> source.path("src/safe-" + label + ".ts"))
        );
    }

    static Stream<Arguments> safeCases() {
        return Stream.of(
                Arguments.of("same-method-no-import", "translate.use('en'); translate.getTranslation('en');\n"),
                Arguments.of("same-method-unowned", "import { TranslateService } from '@ngx-translate/core';\nrouter.use('en'); registry.getTranslation('en');\n"),
                Arguments.of("untyped-receiver", "import { TranslateService } from '@ngx-translate/core';\nconst translate: any = service; translate.setDefaultLang('en');\n"),
                Arguments.of("modern-methods", "import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(private t: TranslateService) {} run() { this.t.setFallbackLang('en'); this.t.getFallbackLang(); this.t.getCurrentLang(); this.t.getLangs(); } }\n"),
                Arguments.of("event-subscribe", "import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(private t: TranslateService) {} run() { this.t.onLangChange.subscribe(handle); } }\n"),
                Arguments.of("safe-read-apis", "import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(private t: TranslateService) {} run() { this.t.get('key'); this.t.instant('key'); this.t.stream('key'); } }\n"),
                Arguments.of("ordinary-properties", "const value = config.defaultLang + config.currentLang + config.langs;\n"),
                Arguments.of("ordinary-config", "const config = { defaultLanguage: 'en', loader: Loader };\n"),
                Arguments.of("shadowed-window", "import { TranslateService } from '@ngx-translate/core';\nfunction run(window: Storage) { return window.localStorage; }\n"),
                Arguments.of("unimported-render-hook", "import { TranslateService } from '@ngx-translate/core';\nafterNextRender(() => initialize());\n"),
                Arguments.of("unowned-plugin", "import { TranslateLoader } from '@ngx-translate/core';\nclass Loader implements OtherLoader {}\n"),
                Arguments.of("non-standalone-module", "import { Component } from '@angular/core';\nimport { TranslateModule } from '@ngx-translate/core';\n@Component({ standalone: false, imports: [TranslateModule] }) class App {}\n"),
                Arguments.of("shadowed-module", "import { TranslateModule } from '@ngx-translate/core';\nfunction configure(TranslateModule: any) { TranslateModule.forRoot({}); }\n"),
                Arguments.of("shadowed-provider", "import { provideTranslateService } from '@ngx-translate/core';\nfunction configure(provideTranslateService: any) { provideTranslateService({}); }\n"),
                Arguments.of("browser-without-ngx", "window.localStorage.clear(); document.title = 'x'; const lang = navigator.language;\n"),
                Arguments.of("target-names", "import { FallbackLangChangeEvent, DefaultMissingTranslationHandler, TranslateNoOpCompiler, TranslateNoOpLoader } from '@ngx-translate/core';\n"),
                Arguments.of("root-import", "import { TranslatePipe, TranslateDirective } from '@ngx-translate/core';\n"),
                Arguments.of("http-helper", "import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';\nprovideTranslateHttpLoader({ prefix: './i18n/', suffix: '.json' });\n")
        );
    }

    @Test
    void marksPinnedRealRepositoryBoundaries() {
        // Reduced from ShahidBaig/eSyncMate_V2@8478a96267fb692985c70e32f5dde0544209d6a5.
        rewriteRun(
                spec -> spec.recipe(new FindNgxTranslateJavaScriptRisks()),
                typescript(
                        """
                        import { TranslateModule } from '@ngx-translate/core';
                        import { TranslateHttpLoader } from '@ngx-translate/http-loader';
                        export function HttpLoaderFactory(http: HttpClient) {
                          return new TranslateHttpLoader(http, './assets/translations/', '.json');
                        }
                        TranslateModule.forRoot({ loader: { provide: TranslateLoader, useFactory: HttpLoaderFactory } });
                        """,
                        source -> source.path("UI/src/app/app.module.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("HTTP loader uses injection/provider configuration"));
                                    assertTrue(printed.contains("TranslateModule configuration remains supported"));
                                    assertTrue(printed.contains("Raw provider objects require"));
                                }))
        );
    }

    @Test
    void marksOnlyDirectProviderOptionKeys() {
        rewriteRun(
                spec -> spec.recipe(new FindNgxTranslateJavaScriptRisks()),
                typescript(
                        "import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ nested: { loader: Loader, defaultLanguage: 'en' } });\n",
                        source -> source.path("src/nested-options.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("TranslateModule configuration remains supported"));
                                    assertTrue(!printed.contains("Raw provider objects require"), printed);
                                    assertTrue(!printed.contains("Legacy default-language configuration"), printed);
                                }))
        );
    }

    @Test
    void ignoresGeneratedSourcesDuringRiskAudit() {
        rewriteRun(
                spec -> spec.recipe(new FindNgxTranslateJavaScriptRisks()),
                typescript(
                        "import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ defaultLanguage: 'en' });\n",
                        source -> source.path("dist/generated.ts"))
        );
    }

    @Test
    void sourceRiskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindNgxTranslateJavaScriptRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ defaultLanguage: 'en' });\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual))
        );
    }
}
