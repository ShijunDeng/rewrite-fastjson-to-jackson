package com.huawei.clouds.openrewrite.vuei18n;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class VueI18nRecommendedRecipeTest implements RewriteTest {
    private static final String MIGRATION =
            "com.huawei.clouds.openrewrite.vuei18n.MigrateVueI18nTo11_3_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(MIGRATION));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=8.11.2 <9", "8.11.2 || 8.24.4", "workspace:^8.27.1", "npm:vue-i18n@8.24.4",
            "github:intlify/vue-i18n#v8.27.1", "file:../vue-i18n", "latest", "v8.27.1",
            "8.27.1-beta.1", "9.14.5", "10.0.8", "11.2.8"
    })
    void unsafeOrUnlistedDeclarationsStayAndReceiveSelectionMarker(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"vue-i18n\":\"" + declaration + "\"}}",
                source -> source.path("package.json").after(actual -> actual)
                        .afterRecipe(document -> assertTrue(document.printAll().contains("Strict migration skipped")))));
    }

    @Test
    void upgradesAndMarksPixelfedAtPinnedCommit() {
        // pixelfed/pixelfed c8bed78bee3d796c5efb57393dafafbba3706f38
        rewriteRun(
                json(
                        """
                        {"name":"pixelfed","dependencies":{"bootstrap-vue":"^2.22.0","vue-i18n":"^8.27.1"},"devDependencies":{"vue":"^2.6.14","vue-router":"^3.5.4","vuex":"^3.6.2"}}
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"vue-i18n\":\"11.3.0\""));
                                    assertTrue(printed.contains("requires Vue ^3.0.0"));
                                })),
                javascript(
                        """
                        import VueI18n from 'vue-i18n';
                        window.App.boot = function() {
                            Vue.use(VueI18n);
                            let i18nMessages = {
                                en: require('./i18n/en.json'),
                                pt: require('./i18n/pt.json'),
                                ja: require('./i18n/ja.json'),
                            };
                            let locale = document.querySelector('html').getAttribute('lang');
                            const i18n = new VueI18n({
                              locale: locale,
                              fallbackLocale: 'en',
                              messages: i18nMessages
                            });
                            new Vue({ el: '#content', i18n });
                        }
                        """,
                        source -> source.path("resources/assets/js/app.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("default class import"));
                                    assertTrue(printed.contains("Vue.use(VueI18n)"));
                                    assertTrue(printed.contains("new VueI18n(options)"));
                                }))
        );
    }

    @Test
    void upgradesAndMarksXbootAtPinnedCommit() {
        // Exrick/xboot-front fb933de4c5927792b71da31479f5f0693aeb71c6
        rewriteRun(
                json("{\"dependencies\":{\"vue\":\"^2.6.14\",\"vue-i18n\":\"^8.24.4\",\"vue-router\":\"^3.5.1\",\"vuex\":\"^3.6.2\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("\"vue-i18n\":\"11.3.0\""));
                                    assertTrue(document.printAll().contains("requires Vue ^3.0.0"));
                                })),
                javascript(
                        """
                        import Vue from 'vue';
                        import VueI18n from 'vue-i18n';
                        Vue.use(VueI18n);
                        const messages = {
                          'zh-CN': Object.assign(zhCnLocale, zhLocale),
                          'en-US': Object.assign(enUsLocale, enLocale)
                        };
                        const i18n = new VueI18n({ locale: lang, messages });
                        export default i18n;
                        """,
                        source -> source.path("src/locale/index.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("new VueI18n(options)"))))
        );
    }

    @Test
    void migratesAndMarksMqttxAtPinnedCommit() {
        // emqx/MQTTX a8a9087fd6a9b434300bf4882c7978c9196ac674
        rewriteRun(
                json("{\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"vue\":\"^2.6.12\",\"vue-i18n\":\"^8.11.2\"},\"devDependencies\":{\"vue-template-compiler\":\"^2.6.12\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("\"vue-i18n\":\"11.3.0\""));
                                    assertTrue(document.printAll().contains("Vue 2-era"));
                                })),
                text(
                        """
                        <i18n path="common.emqx" tag="p">
                          <template #emqx><a :href="emqxWebsite">EMQX</a></template>
                        </i18n>
                        <i18n path="common.cloud" tag="p">
                          <template #cloud><a :href="emqxCloudWebsite">EMQX Cloud</a></template>
                        </i18n>
                        """,
                        """
                        <i18n-t keypath="common.emqx" tag="p">
                          <template #emqx><a :href="emqxWebsite">EMQX</a></template>
                        </i18n-t>
                        <i18n-t keypath="common.cloud" tag="p">
                          <template #cloud><a :href="emqxCloudWebsite">EMQX Cloud</a></template>
                        </i18n-t>
                        """,
                        source -> source.path("src/components/EmptyPage.vue")),
                text("<script>const item = { title: this.$tc('help.learn') }</script>\n",
                        source -> source.path("src/views/help/index.vue").after(actual -> actual)
                                .afterRecipe(file -> assertTrue(file.printAll().contains("$tc was removed"))))
        );
    }

    @Test
    void keepsUnlistedVuesticVersionAndModernSourceAtPinnedCommit() {
        // epicmaxco/vuestic-admin 9c5b44f3674d4c3e7ad01cc043d5331cee953c49
        rewriteRun(
                json("{\"dependencies\":{\"pinia\":\"^2.1.7\",\"vue\":\"3.5.8\",\"vue-i18n\":\"^9.6.2\",\"vue-router\":\"^4.2.5\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"vue-i18n\":/*~~(Strict migration skipped"));
                                    assertTrue(printed.contains("\"^9.6.2\""));
                                })),
                typescript(
                        """
                        import { createI18n } from 'vue-i18n'
                        export default createI18n({
                          legacy: false,
                          locale: 'gb',
                          fallbackLocale: 'gb',
                          messages,
                        })
                        """,
                        source -> source.path("src/i18n/index.ts").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~>"))))
        );
    }

    @Test
    void officialTargetManifestAndCompositionSourceAreNoOp() {
        rewriteRun(
                json("{\"name\":\"vue-i18n\",\"version\":\"11.3.0\",\"main\":\"index.js\",\"module\":\"dist/vue-i18n.mjs\",\"peerDependencies\":{\"vue\":\"^3.0.0\"},\"engines\":{\"node\":\">= 16\"}}",
                        source -> source.path("packages/vue-i18n/package.json")),
                typescript("import { createI18n } from 'vue-i18n';\nexport const i18n = createI18n({ legacy: false, locale: 'en', messages });\n",
                        source -> source.path("src/i18n.ts"))
        );
    }

    @Test
    void recommendedRecipeIsIdempotentAcrossManifestSourceTemplateAndLocale() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"vue-i18n\":\"~8.25.0\",\"vue\":\"^3.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript("import { createI18n } from 'vue-i18n';\nconst i18n = createI18n({ legacy: false, dateTimeFormats: formats });\n",
                        source -> source.path("src/i18n.ts").after(actual -> actual)),
                text("<i18n path=\"hello\" />\n<p v-t=\"'old'\">old</p>\n",
                        source -> source.path("src/App.vue").after(actual -> actual)),
                json("{\"email\":\"support@example.com\"}",
                        source -> source.path("src/locales/en.json").after(actual -> actual))
        );
    }

    @Test
    void recommendedRecipeIsDiscoverableAndValid() {
        Recipe recipe = environment().activateRecipes(MIGRATION);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> MIGRATION.equals(candidate.getName())));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.vuei18n")
                .scanYamlResources()
                .build();
    }
}
