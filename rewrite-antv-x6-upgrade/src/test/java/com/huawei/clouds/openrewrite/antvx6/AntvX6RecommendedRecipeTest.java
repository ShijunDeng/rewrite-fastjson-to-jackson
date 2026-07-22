package com.huawei.clouds.openrewrite.antvx6;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class AntvX6RecommendedRecipeTest implements RewriteTest {
    private static final String MIGRATION = "com.huawei.clouds.openrewrite.antvx6.MigrateAntvX6To3_1_7";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(AntvX6DependencyTest.environment().activateRecipes(MIGRATION));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesOfficialUpgradeGuideAtPinnedX6Tag() {
        // antvis/X6 v3.1.7 @ 2c46438298e3aeb54549b8ddda25b934f9da7131
        // site/docs/tutorial/update.en.md: the documented 2.x -> 3.x import examples.
        rewriteRun(
                json("{\"dependencies\":{\"@antv/x6\":\"^2.11.3\",\"@antv/x6-plugin-scroller\":\"2.1.6\",\"@antv/x6-plugin-selection\":\"2.2.2\",\"@antv/x6-common\":\"2.1.0\",\"@antv/x6-geometry\":\"2.1.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("\"@antv/x6\":\"^3.1.7\""));
                                    assertTrue(printed.contains("merged into @antv/x6"));
                                })),
                typescript(
                        "import { Scroller } from '@antv/x6-plugin-scroller';\nimport { Selection } from '@antv/x6-plugin-selection';\nimport { Dom, FunctionExt } from '@antv/x6-common';\nimport { Point, Rectangle } from '@antv/x6-geometry';\ngraph.use(new Scroller());\ngraph.use(new Selection());\n",
                        source -> source.path("src/official-guide.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertFalse(printed.contains("from '@antv/x6-plugin"));
                                    assertFalse(printed.contains("from '@antv/x6-common'"));
                                    assertFalse(printed.contains("from '@antv/x6-geometry'"));
                                    assertTrue(printed.contains("from '@antv/x6'"));
                                })));
    }

    @Test
    void migratesXFlowTypesAtPinnedCommit() {
        // antvis/XFlow @ faa44bf048c92ac14108f10c0a9a3bffb5e4cd2f
        // packages/core/src/types/index.ts
        rewriteRun(
                json("{\"dependencies\":{\"@antv/x6\":\"2.11.1\",\"@antv/x6-plugin-scroller\":\"2.1.6\",\"@antv/x6-plugin-selection\":\"2.2.2\"}}",
                        source -> source.path("packages/core/package.json").after(actual -> actual)),
                typescript(
                        "import type { Node, Edge, Cell, Graph, Options, Rectangle, CellView, Markup } from '@antv/x6';\nimport type { Scroller } from '@antv/x6-plugin-scroller';\nimport type { Selection } from '@antv/x6-plugin-selection';\nexport type GraphModel = { nodes: Node.Metadata[]; edges: Edge.Metadata[] };\nexport type SelectOptions = Omit<Selection.Options, 'enabled'>;\n",
                        source -> source.path("packages/core/src/types/index.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("import type { Scroller } from '@antv/x6'"));
                                    assertTrue(printed.contains("import type { Selection } from '@antv/x6'"));
                                })));
    }

    @Test
    void migratesAndMarksRxdragGraphAtPinnedCommit() {
        // codebdy/rxdrag @ 6759ce350edb5a822c88f7c2c73275b6662f4206
        // packages/minions/editor/src/hooks/useCreateGraph.ts
        rewriteRun(
                json("{\"dependencies\":{\"@antv/x6\":\"~2.11.3\",\"@antv/x6-plugin-selection\":\"2.2.2\",\"@antv/x6-plugin-minimap\":\"2.0.7\",\"react\":\"18.3.1\"}}",
                        source -> source.path("packages/minions/editor/package.json").after(actual -> actual)),
                typescript(
                        "import { Graph } from \"@antv/x6\";\nimport { Selection } from '@antv/x6-plugin-selection';\nimport { MiniMap } from \"@antv/x6-plugin-minimap\";\nconst graph = new Graph({ container: document.getElementById('canvas')! });\ngraph.use(new Selection());\ngraph.use(new MiniMap());\n",
                        source -> source.path("packages/minions/editor/src/hooks/useCreateGraph.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("from '@antv/x6'"));
                                    assertTrue(printed.contains("from \"@antv/x6\""));
                                    assertTrue(printed.contains("enables Graph panning by default"));
                                })));
    }

    @Test
    void officialTargetManifestAndPublicTypesAreNoOp() {
        // antvis/X6 v3.1.7 package.json @ 2c46438298e3aeb54549b8ddda25b934f9da7131
        rewriteRun(
                json("{\"name\":\"@antv/x6\",\"version\":\"3.1.7\",\"engines\":{\"node\":\">=20.0.0\"},\"dependencies\":{\"@antv/x6\":\"3.1.7\"}}",
                        source -> source.path("package.json")),
                typescript("import type { Graph, Node, Edge, Point, Rectangle } from '@antv/x6';\nexport type Diagram = { graph: Graph; cells: Array<Node | Edge>; origin: Point; bounds: Rectangle };\n",
                        source -> source.path("src/model.ts")));
    }

    @Test
    void followsPinnedOpenRewriteImportTestShapesAndIsIdempotent() {
        // openrewrite/rewrite-javascript @ 9e3b820e6a44808b095bb7e3aab670fd67de99a5
        // rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@antv/x6\":\"1.34.14\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript("import { Point as X6Point , Rectangle , Line } from '@antv/x6-geometry';\nexport type Geometry = [X6Point, Rectangle, Line];\n",
                        source -> source.path("src/geometry.ts").after(actual -> actual)));
        Recipe recipe = AntvX6DependencyTest.environment().activateRecipes(MIGRATION);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(AntvX6DependencyTest.environment().listRecipes().stream()
                .anyMatch(candidate -> MIGRATION.equals(candidate.getName())));
    }
}
