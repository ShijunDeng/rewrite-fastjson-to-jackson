package com.huawei.clouds.openrewrite.antvx6;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class AntvX6ImportMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "@antv/x6-plugin-selection", "@antv/x6-plugin-transform", "@antv/x6-plugin-scroller",
            "@antv/x6-plugin-keyboard", "@antv/x6-plugin-history", "@antv/x6-plugin-clipboard",
            "@antv/x6-plugin-snapline", "@antv/x6-plugin-dnd", "@antv/x6-plugin-minimap",
            "@antv/x6-plugin-stencil", "@antv/x6-plugin-export", "@antv/x6-common", "@antv/x6-geometry"
    })
    void migratesEveryOfficialConsolidatedNamedImport(String module) {
        rewriteRun(spec -> spec.recipe(new MigrateConsolidatedAntvX6Imports()),
                typescript("import { Feature } from '" + module + "';\nFeature;\n",
                        "import { Feature } from '@antv/x6';\nFeature;\n",
                        source -> source.path("src/feature.ts")));
    }

    @Test
    void preservesAliasesTypeOnlyAndDoubleQuotes() {
        rewriteRun(spec -> spec.recipe(new MigrateConsolidatedAntvX6Imports()),
                typescript("import type { Point as X6Point, Rectangle } from \"@antv/x6-geometry\";\nexport type Box = [X6Point, Rectangle];\n",
                        "import type { Point as X6Point, Rectangle } from \"@antv/x6\";\nexport type Box = [X6Point, Rectangle];\n",
                        source -> source.path("src/types.ts")));
    }

    @Test
    void migratesMultipleRealPluginImportsWithoutChangingUsage() {
        rewriteRun(spec -> spec.recipe(new MigrateConsolidatedAntvX6Imports()),
                typescript(
                        "import { Selection } from '@antv/x6-plugin-selection';\nimport { MiniMap } from '@antv/x6-plugin-minimap';\ngraph.use(new Selection());\ngraph.use(new MiniMap());\n",
                        "import { Selection } from '@antv/x6';\nimport { MiniMap } from '@antv/x6';\ngraph.use(new Selection());\ngraph.use(new MiniMap());\n",
                        source -> source.path("src/plugins.ts")));
    }

    @Test
    void leavesDefaultNamespaceSideEffectDynamicRequireAndUnrelatedImports() {
        rewriteRun(spec -> spec.recipe(new MigrateConsolidatedAntvX6Imports()),
                typescript("import Selection from '@antv/x6-plugin-selection';\nimport * as Geometry from '@antv/x6-geometry';\nimport '@antv/x6-plugin-history';\nconst plugin = require('@antv/x6-plugin-minimap');\nimport { Graph } from '@antv/x6';\n",
                        source -> source.path("src/conservative.ts")),
                javascript("import { Selection } from '@example/x6-plugin-selection';\n",
                        source -> source.path("src/unrelated.js")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"node_modules/a.ts", "dist/a.ts", "build/a.ts", "generated/a.ts", "install/a.ts"})
    void excludesInstalledAndGeneratedSource(String path) {
        rewriteRun(spec -> spec.recipe(new MigrateConsolidatedAntvX6Imports()),
                typescript("import { Selection } from '@antv/x6-plugin-selection';\n", source -> source.path(path)));
    }

    @Test
    void migrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateConsolidatedAntvX6Imports())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { Dom } from '@antv/x6-common';\n",
                        "import { Dom } from '@antv/x6';\n",
                        source -> source.path("src/idempotent.ts")));
    }
}
