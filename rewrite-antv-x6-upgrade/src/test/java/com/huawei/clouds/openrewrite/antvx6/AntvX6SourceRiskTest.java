package com.huawei.clouds.openrewrite.antvx6;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class AntvX6SourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksGraphWithoutExplicitPanningAndLeavesExplicitChoiceAlone() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JavaScriptRisks()),
                typescript("import { Graph } from '@antv/x6';\nconst graph = new Graph({ container });\n",
                        source -> source.path("src/graph.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("enables Graph panning by default")))),
                typescript("import { Graph } from '@antv/x6';\nconst graph = new Graph({ container, panning: false });\n",
                        source -> source.path("src/explicit.ts")));
    }

    @Test
    void marksRemovedTransitionOnlyOnTrackedX6Cell() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JavaScriptRisks()),
                typescript("import { Graph } from '@antv/x6';\nconst graph = new Graph({ panning: false });\nconst node = graph.addNode({});\nnode.transition('attrs/body/fill', '#fff');\nother.transition();\n",
                        source -> source.path("src/animation.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("removes the 2.x transition API"));
                                    assertTrue(printed.contains("other.transition()"));
                                })));
    }

    @Test
    void marksReactPortalProviderIncludingAlias() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JavaScriptRisks()),
                typescript("import { Portal as X6Portal } from '@antv/x6-react-shape';\nconst Provider = X6Portal.getProvider();\n",
                        source -> source.path("src/provider.tsx").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("named getProvider() export")))));
    }

    @Test
    void marksInternalAndUnsafeConsolidatedImports() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JavaScriptRisks()),
                typescript("import GraphImpl from '@antv/x6/lib/graph/graph';\nimport Selection from '@antv/x6-plugin-selection';\nimport * as Geometry from '@antv/x6-geometry';\nimport '@antv/x6-plugin-history';\n",
                        source -> source.path("src/unsafe.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("lib/es/src internals"));
                                    assertTrue(printed.contains("Only named imports"));
                                })));
    }

    @Test
    void marksDynamicRequireAndImportBoundaries() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JavaScriptRisks()),
                javascript("const x6 = require('@antv/x6');\nconst plugin = require('@antv/x6-plugin-minimap');\n",
                        source -> source.path("src/lazy.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("Dynamic X6 loading")))));
    }

    @Test
    void marksExecutableConfigAliasButNotPublicEntry() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JavaScriptRisks()),
                typescript("export default { resolve: { alias: { x6: '@antv/x6/es/index', publicX6: '@antv/x6' } } };\n",
                        source -> source.path("vite.config.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("executable-config alias")))));
    }

    @Test
    void doesNotConfuseLocalSameNamesWithX6Ownership() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JavaScriptRisks()),
                typescript("class Graph { constructor(_: any) {} }\nconst graph = new Graph({});\nconst node = local.addNode({});\nnode.transition();\nconst Portal = { getProvider() {} };\nPortal.getProvider();\n",
                        source -> source.path("src/local.ts")));
    }

    @Test
    void doesNotMarkPublicCssOrUnrelatedDeepImports() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JavaScriptRisks()),
                typescript("import '@antv/x6/dist/x6.css';\nimport value from '@example/x6/lib/graph';\n",
                        source -> source.path("src/assets.ts")));
    }

    @Test
    void excludesInstalledAndGeneratedSources() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JavaScriptRisks()),
                typescript("import { Graph } from '@antv/x6';\nnew Graph({});\n",
                        source -> source.path("node_modules/pkg/index.ts")),
                typescript("import Graph from '@antv/x6/lib/graph';\n",
                        source -> source.path("generated/client.ts")),
                typescript("export default { alias: '@antv/x6/es/index' };\n",
                        source -> source.path("dist/vite.config.ts")));
    }

    @Test
    void riskRecipeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JavaScriptRisks()).cycles(2),
                typescript("import { Graph } from '@antv/x6';\nnew Graph({});\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("enables Graph panning"));
                                    assertFalse(printed.contains("~~(~~("));
                                })));
    }
}
