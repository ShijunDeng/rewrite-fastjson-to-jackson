package com.huawei.clouds.openrewrite.gridstack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class GridStackManifestRiskTest implements RewriteTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "=4.2.6", ">=5.1.1 <8", "6.0.3 || 7.1.1", "workspace:^7.1.1",
            "github:gridstack/gridstack.js#v7.1.1", "6.0.3-beta.1", "11.0.0", "13.0.0"
    })
    void marksSkippedDeclarationClasses(String declaration) {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackManifestRisks()),
                json("{\"dependencies\":{\"gridstack\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll()
                                        .contains("Strict migration skipped")))));
    }

    @Test
    void marksFrameworkAndThirdPartyWrapperIntegration() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackManifestRisks()),
                json("{\"dependencies\":{\"gridstack\":\"12.3.3\",\"react\":\"19.1.0\",\"react-dom\":\"19.1.0\",\"next\":\"15.0.0\",\"vue\":\"3.5.0\",\"@angular/core\":\"19.0.0\",\"react-gridstack\":\"1.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll()
                                        .contains("rendering callbacks, mount/unmount ownership")))));
    }

    @Test
    void targetWithoutFrameworkAndUnrelatedManifestAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackManifestRisks()),
                json("{\"dependencies\":{\"gridstack\":\"^12.3.3\",\"lit\":\"3.0.0\"}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"react\":\"19.1.0\"}}", source -> source.path("apps/other/package.json")));
    }

    @Test
    void manifestMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackManifestRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"gridstack\":\">=6\"}}",
                        source -> source.path("package.json").after(actual -> actual)));
    }
}
