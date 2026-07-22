package com.huawei.clouds.openrewrite.i18next;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class I18nextManifestAndConfigRiskTest implements RewriteTest {
    @ParameterizedTest(name = "marks skipped i18next declaration {0}")
    @ValueSource(strings = {
            ">=21.6.14 <23", "21.10.0 || ^22.5.1", "workspace:^21.10.0", "v22.4.9",
            "22.4.10-rc.1", "latest", "21.9.2", "26.0.0"
    })
    void marksEverySkippedDeclarationClass(String declaration) {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextManifestRisks()),
                json("{\"dependencies\":{\"i18next\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll()
                                        .contains("Strict migration skipped"))))
        );
    }

    @Test
    void marksTypeScriptNodeAndIndependentlyVersionedCompanions() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextManifestRisks()),
                json(
                        """
                        {
                          "engines": {"node": ">=12"},
                          "dependencies": {
                            "i18next": "25.10.10",
                            "react-i18next": "11.x",
                            "next-i18next": "^12.0.0",
                            "i18next-browser-languagedetector": "^6.1.8",
                            "i18next-http-backend": "^1.4.5"
                          },
                          "devDependencies": {"typescript": "^4.9.5"}
                        }
                        """,
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("dropped Node versions below 14"));
                                    assertTrue(printed.contains("require TypeScript 5"));
                                    assertTrue(printed.contains("independent compatibility line"));
                                }))
        );
    }

    @Test
    void targetWithTypeScriptFiveNodeFourteenAndNoCompanionsIsNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextManifestRisks()),
                json("{\"engines\":{\"node\":\">=14\"},\"dependencies\":{\"i18next\":\"25.10.10\"},\"devDependencies\":{\"typescript\":\"^5.0.0\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void provenModernAlternativeRangesAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextManifestRisks()),
                json("{\"engines\":{\"node\":\">=14 <23 || >=24\"},\"dependencies\":{\"i18next\":\"25.10.10\"},\"devDependencies\":{\"typescript\":\"^5 || ^6\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void unrelatedManifestDoesNotReceiveTypeScriptOrNodeMarkers() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextManifestRisks()),
                json("{\"engines\":{\"node\":\"10\"},\"devDependencies\":{\"typescript\":\"4.9.5\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void marksOnlyExplicitFalseStrictOptionsInTsconfigCompilerOptions() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJsonConfigRisks()),
                json("{\"compilerOptions\":{\"strict\":false,\"strictNullChecks\":false,\"noImplicitAny\":false},\"strict\":false}",
                        source -> source.path("configs/tsconfig.app.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("i18next 23+ types require strict"));
                                    assertTrue(printed.contains("noImplicitAny\":false"));
                                    assertTrue(printed.endsWith(",\"strict\":false}"));
                                }))
        );
    }

    @Test
    void trueStrictAndNonTsconfigJsonAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJsonConfigRisks()),
                json("{\"compilerOptions\":{\"strict\":true,\"strictNullChecks\":true}}",
                        source -> source.path("tsconfig.json")),
                json("{\"compilerOptions\":{\"strict\":false}}",
                        source -> source.path("fixtures/compiler-options.json"))
        );
    }

    @Test
    void configurationMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindI18nextJsonConfigRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"compilerOptions\":{\"strictNullChecks\":false}}",
                        source -> source.path("tsconfig.test.json").after(actual -> actual))
        );
    }
}
