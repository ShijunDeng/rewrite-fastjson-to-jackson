package com.huawei.clouds.openrewrite.gridstack;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class GridStackTemplateStyleTest implements RewriteTest {
    @Test
    void migratesAllLegacyTemplateAttributeFamilies() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackTemplates()),
                text(
                        """
                        <div class="grid-stack-item" data-gs-x="1" data-gs-y="2" data-gs-width="3" data-gs-height="4"
                             data-gs-min-width="2" data-gs-max-width="8" data-gs-min-height="1" data-gs-max-height="9"
                             data-gs-auto-position data-gs-locked data-gs-no-resize data-gs-no-move data-gs-id="a"></div>
                        """,
                        """
                        <div class="grid-stack-item" gs-x="1" gs-y="2" gs-w="3" gs-h="4"
                             gs-min-w="2" gs-max-w="8" gs-min-h="1" gs-max-h="9"
                             gs-auto-position gs-locked gs-no-resize gs-no-move gs-id="a"></div>
                        """,
                        source -> source.path("src/dashboard.html")));
    }

    @Test
    void removesExactTargetAbsentExtraCssIncludes() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackTemplates()),
                text("@import 'gridstack/dist/gridstack-extra.min.css';\n@import 'gridstack/dist/gridstack.min.css';\n",
                        "@import 'gridstack/dist/gridstack.min.css';\n",
                        source -> source.path("src/dashboard.scss")),
                text("<link rel=\"stylesheet\" href=\"/assets/gridstack-extra.min.css\">\n<div class=\"grid-stack\"></div>\n",
                        "<div class=\"grid-stack\"></div>\n",
                        source -> source.path("public/dashboard.html")));
    }

    @Test
    void templateMigrationIgnoresCommentsAndVueScriptStyleBlocks() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackTemplates()),
                text(
                        """
                        <!-- <div data-gs-width="2"></div> -->
                        <script>const old = 'data-gs-width';</script>
                        <style>.example::after { content: 'data-gs-width'; }</style>
                        <template><div gs-w="2"></div></template>
                        """,
                        source -> source.path("src/Dashboard.vue")),
                text("/* @import 'gridstack/dist/gridstack-extra.css'; */\n", source -> source.path("src/comment.scss")));
    }

    @Test
    void marksFrameworkRenderingNestedAndOldColumnTemplateBoundaries() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackTemplateStyleRisks()),
                text("<gridstack><gridstack-item v-html=\"widget.content\" class=\"nested-grid grid-stack-6\"></gridstack-item></gridstack>\n",
                        source -> source.path("src/Dashboard.vue").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("framework component/directive"));
                                    assertTrue(printed.contains("stopped injecting HTML"));
                                    assertTrue(printed.contains("Nested-grid template"));
                                    assertTrue(printed.contains("generated column class"));
                                })));
    }

    @Test
    void marksCustomGeometryAndContentStyles() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackTemplateStyleRisks()),
                text(".grid-stack-20 > .grid-stack-item[gs-x='1'] { left: 5%; }\n.grid-stack-item-content { overflow: auto; }\n",
                        source -> source.path("src/dashboard.scss").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("column classes are obsolete"));
                                    assertTrue(printed.contains("attribute-based GridStack geometry"));
                                    assertTrue(printed.contains("Custom widget-content CSS"));
                                })));
    }

    @Test
    void ordinaryGridMarkupAndBaseCssImportAreNoOp() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackTemplateStyleRisks()),
                text("<div class=\"grid-stack\"><div class=\"grid-stack-item\" gs-w=\"2\"></div></div>\n",
                        source -> source.path("src/dashboard.html")),
                text("@import 'gridstack/dist/gridstack.min.css';\n.grid-stack { min-height: 10rem; }\n",
                        source -> source.path("src/dashboard.scss")));
    }

    @Test
    void commentsAreNotMarked() {
        rewriteRun(
                spec -> spec.recipe(new FindGridStackTemplateStyleRisks()),
                text("<!-- <gridstack-item v-html=\"x\"></gridstack-item> -->\n", source -> source.path("src/comment.html")),
                text("/* .grid-stack-20 > .grid-stack-item-content {} */\n", source -> source.path("src/comment.css")));
    }

    @Test
    void templateMigrationAndMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicGridStackTemplates())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("<div data-gs-width=\"2\"></div>\n", "<div gs-w=\"2\"></div>\n",
                        source -> source.path("src/idempotent.html")));
        rewriteRun(
                spec -> spec.recipe(new FindGridStackTemplateStyleRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text(".grid-stack-20 {}\n", source -> source.path("src/idempotent.css").after(actual -> actual)));
    }
}
