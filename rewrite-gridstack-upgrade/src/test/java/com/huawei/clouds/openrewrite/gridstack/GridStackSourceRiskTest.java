package com.huawei.clouds.openrewrite.gridstack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class GridStackSourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksDeepImportsAndEngineDragDropExports() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackJavaScriptRisks()),
                typescript(
                        """
                        import { GridStack, DDGridStack, GridStackEngine, Utils } from 'gridstack';
                        import { DDElement } from 'gridstack/dist/dd-element';
                        import 'gridstack/dist/gridstack.min.css';
                        """,
                        source -> source.path("src/internal.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("engine or drag/drop internals"));
                                    assertTrue(printed.contains("Deep GridStack distribution"));
                                    assertFalse(printed.contains("~~(Deep GridStack") && printed.contains("gridstack.min.css/*~~"));
                                })));
    }

    @Test
    void marksInitializationAndBrowserGlobalsForSsrReview() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackJavaScriptRisks()),
                typescript(
                        "import { GridStack } from 'gridstack';\nconst grid = GridStack.init({}, document.querySelector('.grid-stack'));\nwindow.addEventListener('resize', () => grid.onResize());\n",
                        source -> source.path("src/browser.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    assertTrue(cu.printAll().contains("initialization crosses ES2020"));
                                    assertTrue(cu.printAll().contains("Browser global access"));
                                })));
    }

    @Test
    void marksResponsiveDragNestedAndRenderingOptions() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackJavaScriptRisks()),
                typescript(
                        """
                        import { GridStack } from 'gridstack';
                        GridStack.init({
                          oneColumnSize: 768,
                          oneColumnModeDomSort: true,
                          addRemoveCB: factory,
                          dragInOptions: { helper: 'clone' },
                          subGridOpts: { children: [] },
                          acceptWidgets: true
                        });
                        """,
                        source -> source.path("src/options.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Legacy one-column options"));
                                    assertTrue(printed.contains("moved to a global callback"));
                                    assertTrue(printed.contains("Nested-grid configuration"));
                                    assertTrue(printed.contains("rewrote drag/drop"));
                                })));
    }

    @Test
    void marksWidgetContentNumericIdMutationAndLifecycle() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackJavaScriptRisks()),
                typescript(
                        """
                        import { GridStack } from 'gridstack';
                        const grid = GridStack.init();
                        const el = grid.addWidget({ id: 42, content: '<b>unsafe</b>' });
                        grid.update(el, { w: 3, content: '<i>updated</i>' });
                        grid.removeWidget(el, false);
                        grid.destroy(false);
                        """,
                        source -> source.path("src/widgets.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("accepts only a GridStackWidget"));
                                    assertTrue(printed.contains("no longer writes widget content"));
                                    assertTrue(printed.contains("id is string"));
                                    assertTrue(printed.contains("Widget mutation affects DOM"));
                                    assertTrue(printed.contains("destroy(removeDOM)"));
                                })));
    }

    @Test
    void marksEventsSerializationNestedGridAndSidePanel() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackJavaScriptRisks()),
                javascript(
                        """
                        import { GridStack } from 'gridstack';
                        const grid = GridStack.init();
                        grid.on('change added removed dropped', handler);
                        const saved = grid.save(true, true);
                        grid.load(saved);
                        grid.makeSubGrid(item);
                        GridStack.setupDragIn('.sidebar-item', { helper: 'clone' }, widgets);
                        """,
                        source -> source.path("src/runtime.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("event payloads and propagation"));
                                    assertTrue(printed.contains("serialization changed"));
                                    assertTrue(printed.contains("Nested-grid ownership"));
                                    assertTrue(printed.contains("rewrote side-panel drag-in"));
                                })));
    }

    @Test
    void marksGlobalCallbacksEngineRuntimeNodeAndRemovedMethods() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackJavaScriptRisks()),
                typescript(
                        """
                        import { GridStack } from 'gridstack';
                        GridStack.renderCB = (el, widget) => { el.textContent = widget.content || ''; };
                        GridStack.addRemoveCB = factory;
                        const grid = GridStack.init();
                        grid.engine.willItFit(node);
                        const node = item.gridstackNode;
                        node.subGrid.destroy(false);
                        grid.move(item, 1, 2);
                        """,
                        source -> source.path("src/contracts.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("global process state"));
                                    assertTrue(printed.contains("Direct grid.engine access"));
                                    assertTrue(printed.contains("live runtime state"));
                                    assertTrue(printed.contains("Runtime GridStackNode.subGrid"));
                                    assertTrue(printed.contains("removed or internal GridStack method"));
                                })));
    }

    @Test
    void importOnlyAndUnownedSameNamedCallsAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackJavaScriptRisks()),
                typescript(
                        """
                        import type { GridStackWidget } from 'gridstack';
                        router.on('change', handler);
                        store.save();
                        store.load();
                        widget.destroy();
                        function configure(GridStack: any) {
                          GridStack.init({ subGrid: {}, disableOneColumnMode: true });
                        }
                        """,
                        source -> source.path("src/unrelated.ts")));
    }

    @Test
    void marksLegacySubGridWhenAutomaticRenameIsBlockedByConflict() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackJavaScriptRisks()),
                typescript(
                        "import { GridStack } from 'gridstack';\nGridStack.init({ subGrid: {}, subGridOpts: {} });\n",
                        source -> source.path("src/conflict.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Nested-grid configuration"));
                                    assertTrue(printed.contains("subGrid"));
                                }))
        );
    }

    @Test
    void recognizesGridStoredOnClassFieldAndMarksRenderCallbackInnerHtml() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackJavaScriptRisks()),
                typescript(
                        """
                        import { GridStack } from 'gridstack';
                        GridStack.renderCB = (el, widget) => { el.innerHTML = widget.content || ''; };
                        class Dashboard {
                          private grid: GridStack;
                          mount() { this.grid = GridStack.init(); this.grid.on('change', handler); }
                        }
                        """,
                        source -> source.path("src/Dashboard.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    assertTrue(cu.printAll().contains("event payloads and propagation"));
                                    assertTrue(cu.printAll().contains("XSS and ownership boundary"));
                                })));
    }

    @Test
    void sourceMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackJavaScriptRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { GridStack } from 'gridstack';\nGridStack.init();\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual)));
    }
}
