package com.huawei.clouds.openrewrite.vuei18n;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class VueI18nManifestRiskTest implements RewriteTest {
    @Test
    void marksSkippedDeclarationVue2ToolchainBridgeAndNodePrecisely() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nManifestRisks()),
                json(
                        """
                        {
                          "engines": {"node": ">=14"},
                          "dependencies": {
                            "vue-i18n": ">=8.11.2 <9",
                            "vue": "^2.6.14",
                            "vue-i18n-bridge": "9.13.0",
                            "@vue/composition-api": "1.7.2"
                          },
                          "devDependencies": {
                            "vue-template-compiler": "^2.6.14",
                            "@vue/test-utils": "1.3.6",
                            "@intlify/vue-i18n-loader": "4.2.0"
                          }
                        }
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("Strict migration skipped"));
                                    assertTrue(printed.contains("requires Vue ^3.0.0"));
                                    assertTrue(printed.contains("bridge or loader boundary"));
                                    assertTrue(printed.contains("Vue 2-era"));
                                    assertTrue(printed.contains("declares Node >=16"));
                                })
                )
        );
    }

    @Test
    void alignedOfficialTargetRuntimeIsNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nManifestRisks()),
                json("{\"engines\":{\"node\":\">= 16\"},\"dependencies\":{\"vue-i18n\":\"11.3.0\",\"vue\":\"^3.0.0\"},\"devDependencies\":{\"@vue/test-utils\":\"^2.4.6\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~>"))))
        );
    }

    @Test
    void unrelatedManifestDoesNotReceivePeerMarkers() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nManifestRisks()),
                json("{\"engines\":{\"node\":\">=12\"},\"dependencies\":{\"vue\":\"^2.7.16\",\"vue-template-compiler\":\"2.7.16\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void markerRecipeIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindVueI18nManifestRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"vue-i18n\":\"workspace:*\",\"vue\":\"2.6.14\"}}",
                        source -> source.path("package.json").after(actual -> actual))
        );
    }
}
