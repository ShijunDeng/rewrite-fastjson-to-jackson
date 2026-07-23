package com.huawei.clouds.openrewrite.angularelements;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class AngularElementsManifestRiskTest implements RewriteTest {
    @ParameterizedTest
    @ValueSource(strings = {">=12.2.13", "12.x", "workspace:^12.2.13", "latest", "20.3.24", "20.3.26"})
    void marksSkippedOwnedDeclarations(String declaration) {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20ManifestRisks()),
                json("{\"dependencies\":{\"@angular/elements\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(document ->
                                assertTrue(document.printAll().contains("actual owner deliberately")))));
    }

    @Test
    void marksFrameworkLockstepAndNestedCentralOwner() {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20ManifestRisks()),
                json("{\"dependencies\":{\"@angular/elements\":\"20.3.25\",\"@angular/core\":\"20.3.24\",\"@angular/common\":\"^19.2.0\"},\"pnpm\":{\"overrides\":{\"tool\":{\"@angular/elements\":\"12.2.13\"}}}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("same 20.3.25 patch"));
                            assertTrue(printed.contains("centrally owned"));
                        })));
    }

    @Test
    void marksToolchainRuntimePolyfillNodeAndNgccNodes() {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20ManifestRisks()),
                json("{\"engines\":{\"node\":\">=16\"},\"scripts\":{\"postinstall\":\"ngcc\"},\"dependencies\":{\"@angular/elements\":\"20.3.25\",\"rxjs\":\"~6.6.0\",\"zone.js\":\"~0.11.4\",\"@webcomponents/custom-elements\":\"^1.5.0\"},\"devDependencies\":{\"@angular/cli\":\"12.2.18\",\"typescript\":\"4.3.5\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("Angular 20 toolchain baseline"));
                            assertTrue(printed.contains("zoneless scheduling"));
                            assertTrue(printed.contains("native custom elements"));
                            assertTrue(printed.contains("ngcc/View Engine"));
                        })));
    }

    @Test
    void ignoresAlignedFrameworkAndTargetWithoutCompanions() {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20ManifestRisks()),
                json("{\"dependencies\":{\"@angular/elements\":\"^20.3.25\",\"@angular/core\":\"20.3.25\",\"@angular/common\":\"20.3.25\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~")))));
    }

    @Test
    void centralOnlySiblingManifestLockfileAndExcludedTreesAreNoop() {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20ManifestRisks()),
                json("{\"engines\":{\"node\":\">=16\"},\"dependencies\":{\"@angular/core\":\"12.2.0\"},\"overrides\":{\"@angular/elements\":\"12.2.13\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~")))),
                json("{\"dependencies\":{\"@angular/elements\":\"12.x\"}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"@angular/elements\":\"12.x\"}}",
                        source -> source.path("dist/package.json")));
    }

    @Test
    void markersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindAngularElements20ManifestRisks()).cycles(2),
                json("{\"engines\":{\"node\":\">=16\"},\"dependencies\":{\"@angular/elements\":\"20.3.25\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("Angular 20 toolchain baseline"));
                            assertFalse(printed.contains("~~(~~("));
                        })));
    }
}
