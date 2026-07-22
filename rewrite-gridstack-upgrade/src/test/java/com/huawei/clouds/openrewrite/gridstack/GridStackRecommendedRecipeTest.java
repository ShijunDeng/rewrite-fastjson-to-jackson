package com.huawei.clouds.openrewrite.gridstack;

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

class GridStackRecommendedRecipeTest implements RewriteTest {
    private static final String MIGRATION =
            "com.huawei.clouds.openrewrite.gridstack.MigrateGridStackTo12_3_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(GridStackDependencyTest.environment().activateRecipes(MIGRATION));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesWeTrackerAtPinnedCommit() {
        // pgregory/wetracker @ e461f104a01895bd9380213f14aea77d104b3f08
        rewriteRun(
                json("{\"dependencies\":{\"gridstack\":\"^4.2.6\",\"jquery\":\"^3.6.0\",\"jquery-ui\":\"^1.12.1\"}}",
                        "{\"dependencies\":{\"gridstack\":\"12.3.3\",\"jquery\":\"^3.6.0\",\"jquery-ui\":\"^1.12.1\"}}",
                        source -> source.path("package.json")),
                javascript(
                        """
                        import $ from 'jquery';
                        import 'gridstack';
                        import 'gridstack/dist/gridstack.css';
                        import 'gridstack/dist/h5/gridstack-dd-native';
                        """,
                        source -> source.path("src/app.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    assertFalse(cu.printAll().contains("gridstack-dd-native"));
                                    assertTrue(cu.printAll().contains("gridstack.css"));
                                })));
    }

    @Test
    void migratesAndMarksHexaDataAtPinnedCommit() {
        // Hexa-ai/hexa-data @ 784f572b5775b4a3789bb86ca12eaafb374740cc
        rewriteRun(
                json("{\"dependencies\":{\"gridstack\":\"^5.1.1\",\"vue\":\"^3.2.31\"},\"devDependencies\":{\"typescript\":\"^4.6.2\",\"vite\":\"^3.0.0\"}}",
                        source -> source.path("ui/package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("\"gridstack\":\"12.3.3\""));
                                    assertTrue(document.printAll().contains("vue integration must be verified"));
                                })),
                typescript(
                        """
                        import { GridStack, GridStackWidget, ColumnOptions } from 'gridstack/dist/gridstack';
                        import 'gridstack/dist/h5/gridstack-dd-native';
                        import 'gridstack/dist/gridstack.min.css';
                        const grid = GridStack.init({ disableOneColumnMode: true });
                        const layout: GridStackWidget[] = grid.save() as GridStackWidget[];
                        """,
                        source -> source.path("ui/src/components/DashboardGrid.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("from 'gridstack'"));
                                    assertFalse(printed.contains("disableOneColumnMode"));
                                    assertTrue(printed.contains("initialization crosses ES2020"));
                                    assertTrue(printed.contains("serialization changed"));
                                })));
    }

    @Test
    void marksDevUiInternalContractsAtPinnedCommit() {
        // DevCloudFE/ng-devui @ ef76c44e4a7489cfbc587056d947094d0a0c3d1e
        rewriteRun(
                json("{\"dependencies\":{\"@angular/core\":\"~19.1.0\",\"gridstack\":\"^6.0.0\"},\"devDependencies\":{\"typescript\":\"~5.7.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("\"gridstack\":\"12.3.3\""));
                                    assertTrue(document.printAll().contains("@angular/core integration"));
                                })),
                typescript(
                        """
                        import { DDGridStack, GridItemHTMLElement, GridStack, GridStackNode, Utils } from 'gridstack';
                        import { DDElement } from 'gridstack/dist/dd-element';
                        const dd = DDGridStack.get() as DDGridStack;
                        """,
                        source -> source.path("devui/dashboard/grid-stack.service.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    assertTrue(cu.printAll().contains("engine or drag/drop internals"));
                                    assertTrue(cu.printAll().contains("Deep GridStack distribution"));
                                })));
    }

    @Test
    void migratesAndMarksMdvAtPinnedCommit() {
        // Taylor-CCB-Group/MDV @ bdec64ebaa69cb04f4277e7cc7919b7406a5bac7
        rewriteRun(
                json("{\"dependencies\":{\"gridstack\":\"^7.1.1\",\"react\":\"19.2.6\"},\"devDependencies\":{\"vite\":\"8.0.11\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("\"gridstack\":\"12.3.3\""));
                                    assertTrue(document.printAll().contains("react integration"));
                                })),
                typescript(
                        """
                        import 'gridstack/dist/gridstack.min.css';
                        import { GridStack } from 'gridstack';
                        const grid = GridStack.init({ oneColumnSize: 400 });
                        grid.on('resizestop', (_: Event, el: HTMLElement) => el.style.filter = '');
                        grid.destroy(false);
                        """,
                        source -> source.path("src/charts/GridstackManager.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Legacy one-column options"));
                                    assertTrue(printed.contains("event payloads and propagation"));
                                    assertTrue(printed.contains("destroy(removeDOM)"));
                                })));
    }

    @Test
    void officialTargetManifestAndImportOnlySourceAreNoOp() {
        // gridstack/gridstack.js v12.3.3 @ cb3af1cc9bdb0a98375fe6df7118eb5ea2f7dbdb
        rewriteRun(
                json("{\"name\":\"gridstack\",\"version\":\"12.3.3\",\"main\":\"./dist/gridstack.js\",\"types\":\"./dist/gridstack.d.ts\"}",
                        source -> source.path("vendor/gridstack/package.json")),
                typescript("import type { GridStackWidget, GridStackOptions } from 'gridstack';\nexport const layout: GridStackWidget[] = [];\n",
                        source -> source.path("src/layout.ts")));
    }

    @Test
    void recommendedRecipeIsIdempotentDiscoverableAndValid() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"gridstack\":\"~6.0.3\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript("import { GridStack } from 'gridstack/dist/gridstack';\nGridStack.init({ subGrid: {} });\n",
                        source -> source.path("src/grid.ts").after(actual -> actual)));
        Recipe recipe = GridStackDependencyTest.environment().activateRecipes(MIGRATION);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(GridStackDependencyTest.environment().listRecipes().stream()
                .anyMatch(candidate -> MIGRATION.equals(candidate.getName())));
    }
}
