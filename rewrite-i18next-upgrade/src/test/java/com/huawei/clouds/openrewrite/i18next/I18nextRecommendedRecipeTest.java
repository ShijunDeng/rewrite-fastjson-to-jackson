package com.huawei.clouds.openrewrite.i18next;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class I18nextRecommendedRecipeTest implements RewriteTest {
    private static final String MIGRATION =
            "com.huawei.clouds.openrewrite.i18next.MigrateI18nextTo25_10_10";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(I18nextDependencyTest.environment().activateRecipes(MIGRATION));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesAndMarksModuleFederationAtPinnedCommit() {
        // module-federation/module-federation-examples @ 9c4e554af5b5a7d4d2b1dce9adf263fd1a46d6b5
        rewriteRun(
                json(
                        """
                        {
                          "name":"i18next-shared-lib",
                          "devDependencies":{"typescript":"5.5.3","i18next":"21.10.0","i18next-browser-languagedetector":"6.1.8","react-i18next":"11.18.6"},
                          "peerDependencies":{"i18next":"^21.10.0","i18next-browser-languagedetector":"^6.1.8","react-i18next":"^11.18.6"}
                        }
                        """,
                        source -> source.path("i18next-nextjs-react/i18next-shared-lib/package.json")
                                .after(actual -> actual).afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"i18next\":\"25.10.10\""));
                                    assertTrue(printed.contains("independent compatibility line"));
                                })),
                typescript(
                        """
                        import i18next, { i18n, i18n as I18n, InitOptions as I18nInitOptions } from 'i18next';
                        const initOptions: I18nInitOptions = { ...options, initImmediate: false };
                        const newInstance = i18next.createInstance().use(Backend).use(LanguageDetector);
                        newInstance.init(initOptions);
                        export type Available = Omit<I18nInitOptions, 'initImmediate' | 'resources'>;
                        """,
                        source -> source.path("i18next-nextjs-react/i18next-shared-lib/src/i18nService.ts")
                                .after(actual -> actual).afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("initAsync: false"));
                                    assertTrue(printed.contains("property/type key remains"));
                                }))
        );
    }

    @Test
    void upgradesNtfyAndMarksItsIndependentPluginsAtPinnedCommit() {
        // binwiederhier/ntfy @ 7680cb490687e5e80b9d3ce501bb538db8ee1776
        rewriteRun(
                json("{\"dependencies\":{\"i18next\":\"^21.6.14\",\"i18next-browser-languagedetector\":\"^8.2.1\",\"i18next-http-backend\":\"^4.0.0\",\"react-i18next\":\"^11.16.2\"}}",
                        source -> source.path("web/package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("\"i18next\":\"25.10.10\""));
                                    assertTrue(document.printAll().contains("independent compatibility line"));
                                })),
                javascript(
                        """
                        import i18next from "i18next";
                        import Backend from "i18next-http-backend";
                        import LanguageDetector from "i18next-browser-languagedetector";
                        i18next.use(Backend).use(LanguageDetector).init({ fallbackLng: "en", interpolation: { escapeValue: false } });
                        """,
                        source -> source.path("web/src/app/i18n.js")),
                json("{\"publish_dialog_progress_uploading\":\"Uploading…\",\"publish_dialog_progress_uploading_detail\":\"Uploading file…\"}",
                        source -> source.path("web/public/static/langs/en.json"))
        );
    }

    @Test
    void upgradesOpenMrsAndMarksOnlyReactBridgeAtPinnedCommit() {
        // openmrs/openmrs-esm-fast-data-entry-app @ e7b81a0fb60ccb028eb7dd74a5af30e79e75f593
        rewriteRun(
                json("{\"dependencies\":{\"i18next\":\"^21.10.0\"},\"devDependencies\":{\"typescript\":\"^5.0.0\"},\"peerDependencies\":{\"react-i18next\":\"11.x\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("\"i18next\":\"25.10.10\""));
                                    assertTrue(document.printAll().contains("react-i18next has an independent"));
                                })),
                typescript(
                        """
                        import { useTranslation } from 'react-i18next';
                        export const CancelModal = () => {
                          const { t } = useTranslation();
                          return t('areYouSure', 'Are you sure?');
                        };
                        """,
                        source -> source.path("src/CancelModal.tsx"))
        );
    }

    @Test
    void upgradesSleepingOwlWithoutInventingRuntimeChangesAtPinnedCommit() {
        // LaravelRUS/SleepingOwlAdmin @ 2ef22d8e656aa159a9e21f77ef603dbd259e431a
        rewriteRun(
                json("{\"dependencies\":{\"i18next\":\"^21.10.0\",\"vue\":\"^2.7.16\"}}",
                        "{\"dependencies\":{\"i18next\":\"25.10.10\",\"vue\":\"^2.7.16\"}}",
                        source -> source.path("package.json")),
                javascript("import i18next from 'i18next';\ni18next.init({ lng: Admin.locale, resources: {} });\nwindow.trans = key => i18next.t(key);\n",
                        source -> source.path("resources/assets/js_owl/libs/i18next.js"))
        );
    }

    @Test
    void appliesOfficialConverterEnglishFixture() {
        // i18next/i18next-v4-format-converter @ f18d4f5424994c5f34ab535f7ba82ca617e02616
        rewriteRun(json("{\"myKey\":\"item\",\"myKey_plural\":\"items\"}",
                "{\"myKey_one\":\"item\",\"myKey_other\":\"items\"}",
                source -> source.path("locales/en/translation.json")));
    }

    @Test
    void officialTargetManifestAndModernSourceAreNoOp() {
        // i18next/i18next v25.10.10 peeled commit e0fa8382de3b64100a594a2c27124ea9fa48814b
        rewriteRun(
                json("{\"name\":\"i18next\",\"version\":\"25.10.10\",\"main\":\"./dist/cjs/i18next.js\",\"module\":\"./dist/esm/i18next.js\",\"peerDependencies\":{\"typescript\":\"^5 || ^6\"}}",
                        source -> source.path("vendor/i18next/package.json")),
                typescript("import i18next from 'i18next';\ni18next.init({ initAsync: true, compatibilityJSON: 'v4', returnObjects: false });\n",
                        source -> source.path("src/i18n.ts"))
        );
    }

    @Test
    void recommendedRecipeIsIdempotentDiscoverableAndValid() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"i18next\":\"~22.5.1\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript("import i18next from 'i18next';\ni18next.init({ initImmediate: false });\n",
                        source -> source.path("src/i18n.ts").after(actual -> actual)),
                json("{\"book\":\"book\",\"book_plural\":\"books\"}",
                        source -> source.path("locales/en.json").after(actual -> actual))
        );
        Recipe recipe = I18nextDependencyTest.environment().activateRecipes(MIGRATION);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(I18nextDependencyTest.environment().listRecipes().stream()
                .anyMatch(candidate -> MIGRATION.equals(candidate.getName())));
    }
}
