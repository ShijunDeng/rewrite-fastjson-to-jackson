package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.javascript.Assertions.typescript;

class NgxTranslateSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "public symbol {0} -> {1}")
    @MethodSource("publicRenames")
    void renamesImportedPublicSymbolsAndTheirReferences(String beforeName, String afterName) {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { " + beforeName + " } from '@ngx-translate/core';\nconst value: " + beforeName + " | undefined = undefined;\n",
                        "import { " + afterName + " } from '@ngx-translate/core';\nconst value: " + afterName + " | undefined = undefined;\n",
                        source -> source.path("src/public-name.ts"))
        );
    }

    @ParameterizedTest(name = "aliased public symbol {0} -> {1}")
    @MethodSource("publicRenames")
    void renamesOnlyTheImportedSideOfAliases(String beforeName, String afterName) {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { " + beforeName + " as Local } from '@ngx-translate/core';\nconst value: Local | undefined = undefined;\n",
                        "import { " + afterName + " as Local } from '@ngx-translate/core';\nconst value: Local | undefined = undefined;\n",
                        source -> source.path("src/aliased-public-name.ts"))
        );
    }

    static Stream<Arguments> publicRenames() {
        return NgxTranslateSupport.PUBLIC_RENAMES.entrySet().stream()
                .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    @ParameterizedTest(name = "constructor receiver modifier: {0}")
    @ValueSource(strings = {"private ", "public ", "protected ", "readonly ", "private readonly "})
    void renamesLegacyMethodsAndEventOnConstructorInjectedService(String modifier) {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        """
                        import { TranslateService } from '@ngx-translate/core';
                        class LanguageService {
                          constructor(%stranslate: TranslateService) {}
                          configure() {
                            this.translate.setDefaultLang('en');
                            const fallback = this.translate.getDefaultLang();
                            return this.translate.onDefaultLangChange;
                          }
                        }
                        """.formatted(modifier),
                        """
                        import { TranslateService } from '@ngx-translate/core';
                        class LanguageService {
                          constructor(%stranslate: TranslateService) {}
                          configure() {
                            this.translate.setFallbackLang('en');
                            const fallback = this.translate.getFallbackLang();
                            return this.translate.onFallbackLangChange;
                          }
                        }
                        """.formatted(modifier),
                        source -> source.path("src/app/language.service.ts"))
        );
    }

    @Test
    void migratesBareCallsOnAnOrdinaryConstructorParameter() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(translate: TranslateService) { translate.setDefaultLang('en'); } }\n",
                        "import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(translate: TranslateService) { translate.setFallbackLang('en'); } }\n",
                        source -> source.path("src/constructor-parameter.ts"))
        );
    }

    @Test
    void doesNotTreatAnOrdinaryConstructorParameterAsAClassField() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(translate: TranslateService) { this.translate.setDefaultLang('en'); } }\n",
                        source -> source.path("src/not-a-field.ts"))
        );
    }

    @ParameterizedTest(name = "inject receiver declaration: {0}")
    @ValueSource(strings = {"translate", "private translate", "readonly translate", "private readonly translate"})
    void renamesLegacyApisOnInjectProvenService(String declaration) {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { inject } from '@angular/core';\nimport { TranslateService } from '@ngx-translate/core';\nclass App { " + declaration + " = inject(TranslateService); run() { this.translate.setDefaultLang('en'); return this.translate.getDefaultLang(); } }\n",
                        "import { inject } from '@angular/core';\nimport { TranslateService } from '@ngx-translate/core';\nclass App { " + declaration + " = inject(TranslateService); run() { this.translate.setFallbackLang('en'); return this.translate.getFallbackLang(); } }\n",
                        source -> source.path("src/app/app.ts"))
        );
    }

    @Test
    void supportsAliasedTranslateServiceType() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { TranslateService as I18n } from '@ngx-translate/core';\nclass App { constructor(private i18n: I18n) {} run() { this.i18n.setDefaultLang('en'); } }\n",
                        "import { TranslateService as I18n } from '@ngx-translate/core';\nclass App { constructor(private i18n: I18n) {} run() { this.i18n.setFallbackLang('en'); } }\n",
                        source -> source.path("src/app/alias.ts"))
        );
    }

    @Test
    void supportsAliasedAngularInjectBinding() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { inject as di } from '@angular/core';\nimport { TranslateService } from '@ngx-translate/core';\nclass App { private i18n = di(TranslateService); run() { this.i18n.setDefaultLang('en'); } }\n",
                        "import { inject as di } from '@angular/core';\nimport { TranslateService } from '@ngx-translate/core';\nclass App { private i18n = di(TranslateService); run() { this.i18n.setFallbackLang('en'); } }\n",
                        source -> source.path("src/app/alias-inject.ts"))
        );
    }

    @Test
    void preservesObjectKeysWhenRenamingAnImportedPublicType() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { FakeMissingTranslationHandler } from '@ngx-translate/core';\nconst registry = { FakeMissingTranslationHandler: true };\nconst handler: FakeMissingTranslationHandler | undefined = undefined;\n",
                        "import { DefaultMissingTranslationHandler } from '@ngx-translate/core';\nconst registry = { FakeMissingTranslationHandler: true };\nconst handler: DefaultMissingTranslationHandler | undefined = undefined;\n",
                        source -> source.path("src/object-key.ts"))
        );
    }

    @Test
    void skipsPublicRenameWhenTheReplacementIsAlreadyImported() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { DefaultLangChangeEvent, FallbackLangChangeEvent } from '@ngx-translate/core';\nconst oldEvent: DefaultLangChangeEvent | undefined = undefined;\n",
                        source -> source.path("src/import-collision.ts"))
        );
    }

    @Test
    void skipsPublicRenameWhenTheOldBindingIsShadowed() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { DefaultLangChangeEvent } from '@ngx-translate/core';\nfunction inspect(DefaultLangChangeEvent: unknown) { return DefaultLangChangeEvent; }\n",
                        source -> source.path("src/shadowed-public-name.ts"))
        );
    }

    @Test
    void usesATypeOnlyServiceImportForConstructorReceiverAttribution() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import type { TranslateService } from '@ngx-translate/core';\nclass App { constructor(private i18n: TranslateService) {} run() { this.i18n.setDefaultLang('en'); } }\n",
                        "import type { TranslateService } from '@ngx-translate/core';\nclass App { constructor(private i18n: TranslateService) {} run() { this.i18n.setFallbackLang('en'); } }\n",
                        source -> source.path("src/type-only-service.ts"))
        );
    }

    @Test
    void skipsLocallyShadowedInjectFunction() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { inject } from '@angular/core';\nimport { TranslateService } from '@ngx-translate/core';\nfunction factory(inject: any) { const i18n = inject(TranslateService); i18n.setDefaultLang('en'); }\n",
                        source -> source.path("src/shadowed-inject.ts"))
        );
    }

    @Test
    void ignoresGeneratedSources() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { DefaultLangChangeEvent } from '@ngx-translate/core';\nconst event: DefaultLangChangeEvent | undefined = undefined;\n",
                        source -> source.path("dist/generated.ts"))
        );
    }

    @Test
    void migratesGenshinCalcLanguageServiceAtPinnedCommit() {
        // Reduced from Kurarion/Genshin-Calc@c7dd4d850db8523e33302e98d71d9e180605bd4e.
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        """
                        import { Injectable } from '@angular/core';
                        import { TranslateService } from '@ngx-translate/core';
                        @Injectable({ providedIn: 'root' })
                        export class LanguageService {
                          constructor(private translateService: TranslateService) {}
                          getDefaultLang(): string | undefined {
                            return this.translateService.getDefaultLang();
                          }
                        }
                        """,
                        """
                        import { Injectable } from '@angular/core';
                        import { TranslateService } from '@ngx-translate/core';
                        @Injectable({ providedIn: 'root' })
                        export class LanguageService {
                          constructor(private translateService: TranslateService) {}
                          getDefaultLang(): string | undefined {
                            return this.translateService.getFallbackLang();
                          }
                        }
                        """,
                        source -> source.path("src/app/shared/service/language.service.ts"))
        );
    }

    @Test
    void migratesTuniSalesGatewaySetterAtPinnedCommit() {
        // Reduced from FeDi20-03/TuniSalesGateway@40e99bedf123169767fbcf7200f4e2e0a94eb402.
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(
                        "import { TranslateService } from '@ngx-translate/core';\nclass TranslationModule { constructor(private translateService: TranslateService) { this.translateService.setDefaultLang('fr'); } }\n",
                        "import { TranslateService } from '@ngx-translate/core';\nclass TranslationModule { constructor(private translateService: TranslateService) { this.translateService.setFallbackLang('fr'); } }\n",
                        source -> source.path("src/main/webapp/app/shared/language/translation.module.ts"))
        );
    }

    @ParameterizedTest(name = "preserves ambiguous source: {0}")
    @MethodSource("ambiguousSources")
    void preservesUnprovenPropertiesConfigurationsAndReceivers(String sourceCode) {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource()),
                typescript(sourceCode, source -> source.path("src/conservative.ts"))
        );
    }

    static Stream<String> ambiguousSources() {
        return Stream.of(
                "this.translate.setDefaultLang('en');\n",
                "import { TranslateService } from '@ngx-translate/core';\nother.setDefaultLang('en');\n",
                "import { TranslateService } from '@ngx-translate/core';\nconst translate: any = registry; translate.getDefaultLang();\n",
                "import { TranslateService } from '@other/i18n';\nclass X { constructor(private translate: TranslateService) {} run() { this.translate.setDefaultLang('en'); } }\n",
                "import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(private translate: TranslateService) {} run() { return this.translate.defaultLang; } }\n",
                "import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(private translate: TranslateService) {} run() { return this.translate.currentLang; } }\n",
                "import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(private translate: TranslateService) {} run() { return this.translate.langs; } }\n",
                "import { TranslateModule } from '@ngx-translate/core';\nTranslateModule.forRoot({ defaultLanguage: 'en' });\n",
                "import { provideTranslateService } from '@ngx-translate/core';\nprovideTranslateService({ defaultLanguage: 'en' });\n",
                "import { Component } from '@angular/core';\nimport { TranslateModule } from '@ngx-translate/core';\n@Component({ standalone: true, imports: [TranslateModule] }) class X {}\n",
                "import { FallbackLangChangeEvent } from '@ngx-translate/core';\nconst event: FallbackLangChangeEvent | undefined = undefined;\n",
                "import { TranslateService } from '@ngx-translate/core';\nclass X { constructor(private translate: TranslateService) {} run() { this.translate.setFallbackLang('en'); return this.translate.getFallbackLang(); } }\n",
                "import { DefaultLangChangeEvent } from '@other/i18n';\nconst event: DefaultLangChangeEvent | undefined = undefined;\n",
                "import { DefaultLangChangeEvent } from '@ngx-translate/core';\nconst registry = { DefaultLangChangeEvent };\n",
                "import * as translate from '@ngx-translate/core';\ntranslate.DefaultLangChangeEvent;\n",
                "const config = { defaultLanguage: 'en', useDefaultLang: false };\n",
                "class X { constructor(private translate: any) {} run() { return this.translate.onDefaultLangChange; } }\n",
                "import '@ngx-translate/core';\nservice.setDefaultLang('en');\n",
                "import { TranslateService } from '@ngx-translate/core';\nfunction run(translate: unknown) { translate.setDefaultLang('en'); }\n"
        );
    }

    @Test
    void deterministicMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicNgxTranslateSource())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import { DefaultLangChangeEvent, TranslateService } from '@ngx-translate/core';\nclass X { constructor(private t: TranslateService) {} run() { this.t.setDefaultLang('en'); } }\n",
                        "import { FallbackLangChangeEvent, TranslateService } from '@ngx-translate/core';\nclass X { constructor(private t: TranslateService) {} run() { this.t.setFallbackLang('en'); } }\n",
                        source -> source.path("src/idempotent.ts"))
        );
    }
}
