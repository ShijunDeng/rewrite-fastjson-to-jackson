package com.huawei.clouds.openrewrite.vuei18n;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class VueI18nSourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksVue2ImportInstallConstructionAndStaticApis() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJavaScriptRisks()),
                javascript(
                        """
                        import Vue from 'vue';
                        import VueI18n from 'vue-i18n';
                        Vue.use(VueI18n);
                        console.log(VueI18n.version, VueI18n.availability);
                        const i18n = new VueI18n({ locale: 'en' });
                        """,
                        source -> source.path("src/i18n.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("default class import"));
                                    assertTrue(printed.contains("Vue.use(VueI18n)"));
                                    assertTrue(printed.contains("new VueI18n(options)"));
                                    assertTrue(printed.contains("VERSION export"));
                                }))
        );
    }

    @Test
    void marksLegacyDefaultAndRemovedOrModeSensitiveOptions() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJavaScriptRisks()),
                typescript(
                        """
                        import { createI18n } from 'vue-i18n';
                        const i18n = createI18n({
                          formatter,
                          preserveDirectiveContent: true,
                          allowComposition: true,
                          silentTranslationWarn: true,
                          silentFallbackWarn: /fallback/,
                          formatFallbackMessages: true,
                          warnHtmlInMessage: 'off',
                          pluralizationRules: rules
                        });
                        """,
                        source -> source.path("src/options.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("defaults to legacy:true"));
                                    assertTrue(printed.contains("was removed before Vue I18n 11"));
                                    assertTrue(printed.contains("Legacy-versus-Composition"));
                                }))
        );
    }

    @Test
    void marksConflictingLegacyAndCurrentDateTimeFormatKeys() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJavaScriptRisks()),
                typescript(
                        "import { createI18n } from 'vue-i18n';\n" +
                        "const i18n = createI18n({ dateTimeFormats: legacy, datetimeFormats: current });\n",
                        source -> source.path("src/conflicting-formats.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("conflicting datetimeFormats key"));
                                    assertTrue(printed.contains("dateTimeFormats"));
                                }))
        );
    }

    @Test
    void marksAmbiguousTcAndTranslationOverloadAndStructuredResult() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJavaScriptRisks()),
                typescript(
                        """
                        import { createI18n } from 'vue-i18n';
                        const i18n = createI18n({ legacy: true });
                        i18n.global.tc('items', locale);
                        class View {
                          label() { return this.$tc('items'); }
                          japanese() { return this.$t('hello', 'ja'); }
                          error(code: number) { return this.$t('errors')[code]; }
                          positional(name: string) { return this.$t('hello', {'0': name}); }
                        }
                        """,
                        source -> source.path("src/view.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("tc/$tc was removed"));
                                    assertTrue(printed.contains("string second argument is ambiguous"));
                                    assertTrue(printed.contains("returns strings only"));
                                    assertTrue(printed.contains("array-like objects"));
                                }))
        );
    }

    @Test
    void marksGetChoiceIndexAndBridgeImports() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJavaScriptRisks()),
                typescript(
                        "import { createI18n, castToVueI18n } from 'vue-i18n-bridge';\nlegacy.getChoiceIndex(choice, choices);\n",
                        source -> source.path("src/bridge.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    assertTrue(cu.printAll().contains("ended at v9"));
                                    assertTrue(cu.printAll().contains("getChoiceIndex was removed"));
                                }))
        );
    }

    @Test
    void marksJitBuildIntegrationBoundary() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJavaScriptRisks()),
                typescript(
                        "import VueI18nPlugin from '@intlify/unplugin-vue-i18n/vite';\nconst define = { __INTLIFY_JIT_COMPILATION__: false };\n",
                        source -> source.path("vite.config.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    assertTrue(cu.printAll().contains("enabled JIT compilation by default"));
                                    assertTrue(cu.printAll().contains("compatibility flag is obsolete"));
                                }))
        );
    }

    @Test
    void modernCompositionSourceAndUnrelatedTranslationNamesAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJavaScriptRisks()),
                typescript(
                        """
                        import { createI18n, useI18n } from 'vue-i18n';
                        export const i18n = createI18n({ legacy: false, locale: 'en', messages });
                        function metrics() { const t = (key: string) => key; return t('latency'); }
                        """,
                        source -> source.path("src/modern.ts").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~>"))))
        );
    }

    @Test
    void sourceMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nJavaScriptRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import VueI18n from 'vue-i18n';\nconst i18n = new VueI18n({});\n",
                        source -> source.path("src/legacy.ts").after(actual -> actual))
        );
    }
}
