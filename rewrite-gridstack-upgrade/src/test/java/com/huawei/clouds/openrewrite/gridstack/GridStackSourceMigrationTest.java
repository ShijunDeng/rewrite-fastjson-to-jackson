package com.huawei.clouds.openrewrite.gridstack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class GridStackSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void normalizesPublicImportAndRemovesObsoleteDndAndExtraCssImports() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackSource()),
                typescript(
                        """
                        import { GridStack, GridStackWidget } from 'gridstack/dist/gridstack';
                        import 'gridstack/dist/h5/gridstack-dd-native';
                        import 'gridstack/dist/gridstack-extra.min.css';
                        import 'gridstack/dist/gridstack.min.css';
                        const grid = GridStack.init();
                        """,
                        source -> source.path("src/dashboard.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("from 'gridstack'"));
                                    assertFalse(printed.contains("gridstack-dd-native"));
                                    assertFalse(printed.contains("gridstack-extra"));
                                    assertTrue(printed.contains("gridstack.min.css"));
                                })));
    }

    @Test
    void migratesOwnedInitOptions() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackSource()),
                typescript(
                        "import { GridStack } from 'gridstack';\nconst grid = GridStack.init({ subGrid: { column: 'auto' }, disableOneColumnMode: true, animate: true });\n",
                        "import { GridStack } from 'gridstack';\nconst grid = GridStack.init({ subGridOpts: { column: 'auto' }, animate: true });\n",
                        source -> source.path("src/init.ts")));
    }

    @Test
    void migratesQuotedTypedAliasedOptions() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackSource()),
                typescript(
                        "import { GridStackOptions as Options } from 'gridstack';\nconst options: Options = { \"subGrid\": {}, \"disableOneColumnMode\": true, float: false };\n",
                        "import { GridStackOptions as Options } from 'gridstack';\nconst options: Options = { \"subGridOpts\": {}, float: false };\n",
                        source -> source.path("src/options.ts")));
    }

    @Test
    void renamesOnParentResizeOnlyOnTrackedGrid() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackSource()),
                javascript(
                        "import { GridStack as GS } from 'gridstack';\nconst grid = GS.init();\ngrid.onParentResize();\nother.onParentResize();\n",
                        "import { GridStack as GS } from 'gridstack';\nconst grid = GS.init();\ngrid.onResize();\nother.onParentResize();\n",
                        source -> source.path("src/resize.js")));
    }

    @Test
    void migratesSubGridOptionPassedToOwnedAddWidget() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackSource()),
                typescript(
                        "import { GridStack } from 'gridstack';\nconst grid = GridStack.init();\ngrid.addWidget({ id: 'nested', subGrid: { children: [] } });\n",
                        "import { GridStack } from 'gridstack';\nconst grid = GridStack.init();\ngrid.addWidget({ id: 'nested', subGridOpts: { children: [] } });\n",
                        source -> source.path("src/nested.ts")));
    }

    @Test
    void leavesDetachedUntypedAndUnrelatedSameNames() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackSource()),
                typescript(
                        """
                        import { GridStack } from 'gridstack';
                        const detached = { subGrid: {}, disableOneColumnMode: true };
                        unrelated.init({ subGrid: {}, disableOneColumnMode: true });
                        GridStack.init(detached);
                        """,
                        source -> source.path("src/conservative.ts")));
    }

    @Test
    void leavesSpreadConflictingDuplicateAndShadowedOptionsForReview() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackSource()),
                typescript(
                        "import { GridStack } from 'gridstack';\nGridStack.init({ ...legacy, subGrid: {}, disableOneColumnMode: true });\n",
                        source -> source.path("src/spread.ts")),
                typescript(
                        "import { GridStack } from 'gridstack';\nGridStack.init({ subGrid: {}, subGridOpts: {}, disableOneColumnMode: true, disableOneColumnMode: false });\n",
                        source -> source.path("src/conflict.ts")),
                typescript(
                        "import { GridStack } from 'gridstack';\nfunction configure(GridStack: any) { GridStack.init({ subGrid: {}, disableOneColumnMode: true }); }\n",
                        source -> source.path("src/shadowed.ts"))
        );
    }

    @Test
    void leavesModernSourceAndOtherDeepPackageAlone() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackSource()),
                typescript(
                        "import { GridStack } from 'gridstack';\nGridStack.init({ columnOpts: { breakpoints: [{ w: 768, c: 1 }] }, subGridOpts: {} });\n",
                        source -> source.path("src/modern.ts")),
                typescript("import value from 'other/dist/gridstack';\n", source -> source.path("src/other.ts")),
                typescript("import { customAdapter } from 'gridstack/dist/h5/gridstack-dd-native';\ncustomAdapter();\n",
                        source -> source.path("src/named-adapter.ts")));
    }

    @Test
    void recognizesTypedClassFieldAsGridInstance() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackSource()),
                typescript(
                        "import { GridStack } from 'gridstack';\nclass Dashboard { private grid: GridStack; resize() { this.grid.onParentResize(); } }\n",
                        "import { GridStack } from 'gridstack';\nclass Dashboard { private grid: GridStack; resize() { this.grid.onResize(); } }\n",
                        source -> source.path("src/Dashboard.ts")));
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackSource())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        "import { GridStack } from 'gridstack/dist/gridstack';\nGridStack.init({ subGrid: {} });\n",
                        "import { GridStack } from 'gridstack';\nGridStack.init({ subGridOpts: {} });\n",
                        source -> source.path("src/idempotent.ts")));
    }
}
