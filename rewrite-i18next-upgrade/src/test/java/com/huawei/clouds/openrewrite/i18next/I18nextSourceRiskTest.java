package com.huawei.clouds.openrewrite.i18next;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class I18nextSourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksRemovedAndInternalizedI18nextTypes() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJavaScriptRisks()),
                typescript(
                        """
                        import i18next, {
                          StringMap,
                          TFuncKey as Key,
                          DefaultTFuncReturn,
                          ParseKeys
                        } from 'i18next';
                        """,
                        source -> source.path("src/types.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("type redesign"));
                                    assertTrue(printed.contains("StringMap"));
                                    assertTrue(printed.contains("TFuncKey"));
                                    assertTrue(printed.contains("DefaultTFuncReturn"));
                                    assertFalse(printed.contains("~~(ParseKeys"));
                                }))
        );
    }

    @Test
    void marksDeepImportAndRemovedLoggerMethods() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJavaScriptRisks()),
                typescript(
                        """
                        import i18next from 'i18next/dist/cjs/i18next.js';
                        i18next.services.logger.setDebug(true);
                        i18next.services.languageUtils.isWhitelisted('en');
                        """,
                        source -> source.path("src/internal.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Deep i18next implementation imports"));
                                    assertTrue(printed.contains("setDebug function was removed"));
                                    assertTrue(printed.contains("isWhitelisted was removed"));
                                }))
        );
    }

    @Test
    void marksOwnedRuntimeBehaviorBoundaries() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJavaScriptRisks()),
                javascript(
                        """
                        import i18next from 'i18next';
                        i18next.changeLanguage(nextLanguage);
                        if (i18next.exists('navigation')) render();
                        const local = i18next.createInstance().use(plugin);
                        local.changeLanguage('de');
                        local.exists('nested.object');
                        """,
                        source -> source.path("src/runtime.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("concurrent changeLanguage completion"));
                                    assertTrue(printed.contains("exists() return false for object keys"));
                                }))
        );
    }

    @Test
    void marksOldConfigAndReturnSelectorDecisions() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJavaScriptRisks()),
                typescript(
                        """
                        import i18next from 'i18next';
                        i18next.init({
                          jsonFormat: 'v3',
                          compatibilityJSON: 'v3',
                          returnNull: true,
                          returnObjects: true,
                          enableSelector: true
                        });
                        """,
                        source -> source.path("src/config.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("jsonFormat was removed"));
                                    assertTrue(printed.contains("Only compatibilityJSON:'v4' remains"));
                                    assertTrue(printed.contains("returnNull now defaults false"));
                                    assertTrue(printed.contains("Global returnObjects:true"));
                                    assertTrue(printed.contains("Selector API is opt-in"));
                                }))
        );
    }

    @Test
    void marksDetachedInitImmediateTypeKeyButNotUnrelatedIdentifier() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJavaScriptRisks()),
                typescript(
                        """
                        import i18next, { InitOptions } from 'i18next';
                        type Available = Omit<InitOptions, 'initImmediate'>;
                        const detached = { initImmediate: false };
                        const ordinary = initImmediate;
                        """,
                        source -> source.path("src/options.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll()
                                        .contains("property/type key remains"))))
        );
    }

    @Test
    void marksConflictingOwnedInitKeysAtTheLegacyProperty() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJavaScriptRisks()),
                typescript(
                        "import i18next from 'i18next';\ni18next.init({ initImmediate: false, initAsync: true });\n",
                        source -> source.path("src/conflict.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("deterministic rename was unsafe"));
                                    assertTrue(printed.contains("initImmediate"));
                                }))
        );
    }

    @Test
    void modernConfigurationAndUnownedSameNamedMethodsAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJavaScriptRisks()),
                typescript(
                        """
                        import i18next from 'i18next';
                        i18next.init({ initAsync: true, compatibilityJSON: 'v4', returnObjects: false });
                        router.changeLanguage('en');
                        registry.exists('key');
                        logger.setDebug(true);
                        languageUtils.isWhitelisted('en');
                        function configure(i18next: any) {
                          i18next.init({ initImmediate: false });
                        }
                        """,
                        source -> source.path("src/modern.ts"))
        );
    }

    @Test
    void sourceRiskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJavaScriptRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import i18next from 'i18next';\ni18next.changeLanguage('en');\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual))
        );
    }
}
