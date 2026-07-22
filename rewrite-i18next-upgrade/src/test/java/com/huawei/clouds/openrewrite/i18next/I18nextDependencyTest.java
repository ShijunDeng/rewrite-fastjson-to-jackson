package com.huawei.clouds.openrewrite.i18next;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class I18nextDependencyTest implements RewriteTest {
    private static final String STRICT =
            "com.huawei.clouds.openrewrite.i18next.UpgradeI18nextTo25_10_10";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(STRICT));
    }

    @ParameterizedTest(name = "upgrades exact XLSX source {0}")
    @ValueSource(strings = {"21.10.0", "21.6.14", "22.4.10", "22.4.9", "22.5.1"})
    void upgradesEveryExactSpreadsheetSource(String source) {
        rewriteRun(packageVersion(source));
    }

    @ParameterizedTest(name = "upgrades caret XLSX source {0}")
    @ValueSource(strings = {"21.10.0", "21.6.14", "22.4.10", "22.4.9", "22.5.1"})
    void upgradesEveryCaretSpreadsheetSource(String source) {
        rewriteRun(packageVersion("^" + source));
    }

    @ParameterizedTest(name = "upgrades tilde XLSX source {0}")
    @ValueSource(strings = {"21.10.0", "21.6.14", "22.4.10", "22.4.9", "22.5.1"})
    void upgradesEveryTildeSpreadsheetSource(String source) {
        rewriteRun(packageVersion("~" + source));
    }

    @Test
    void upgradesOnlyFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"i18next": "21.10.0"},
                  "devDependencies": {"i18next": "^21.6.14"},
                  "peerDependencies": {"i18next": "~22.4.9"},
                  "optionalDependencies": {"i18next": "22.5.1"}
                }
                """,
                """
                {
                  "dependencies": {"i18next": "25.10.10"},
                  "devDependencies": {"i18next": "25.10.10"},
                  "peerDependencies": {"i18next": "25.10.10"},
                  "optionalDependencies": {"i18next": "25.10.10"}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesWorkspaceChildManifestWithoutChangingWorkspaceMetadata() {
        rewriteRun(
                json("{\"private\":true,\"workspaces\":[\"packages/*\"]}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"i18next\":\"22.4.10\"}}",
                        "{\"dependencies\":{\"i18next\":\"25.10.10\"}}",
                        source -> source.path("packages/i18n/package.json"))
        );
    }

    @ParameterizedTest(name = "leaves unsupported declaration {0}")
    @ValueSource(strings = {
            "v21.10.0", "=21.6.14", "22.4.10-rc.1", "22.4.9+vendor.1",
            ">=21.6.14 <23", "21.6.14 - 22.5.1", "21.10.0 || ^22.5.1",
            "workspace:^21.10.0", "npm:@vendor/i18next@22.5.1", "file:../i18next",
            "github:i18next/i18next#v22.4.9", "https://example.test/i18next.tgz",
            "latest", "22.x", "*", "21.9.2", "22.5.0", "25.10.10", "26.0.0"
    })
    void leavesComplexDecoratedProtocolAndUnlistedDeclarations(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"i18next\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void leavesNestedOverridesLocksOrdinaryJsonAndSimilarNames() {
        rewriteRun(
                json("{\"overrides\":{\"i18next\":\"21.10.0\"},\"resolutions\":{\"i18next\":\"22.5.1\"},\"dependencies\":{\"i18next-http-backend\":\"21.10.0\",\"I18next\":\"22.4.9\"}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"i18next\":\"21.10.0\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"i18next\":\"22.5.1\"}}",
                        source -> source.path("fixtures/dependencies.json"))
        );
    }

    @Test
    void strictRecipeIsIdempotentDiscoverableAndValid() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"i18next\":\"~22.5.1\"}}",
                        "{\"dependencies\":{\"i18next\":\"25.10.10\"}}",
                        source -> source.path("package.json"))
        );
        Recipe recipe = environment().activateRecipes(STRICT);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> STRICT.equals(candidate.getName())));
        assertEquals("Upgrade selected i18next dependencies to 25.10.10", recipe.getDisplayName());
    }

    private static org.openrewrite.test.SourceSpecs packageVersion(String declaration) {
        return json("{\"dependencies\":{\"i18next\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"i18next\":\"25.10.10\"}}",
                source -> source.path("package.json"));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.i18next")
                .scanYamlResources()
                .build();
    }
}
