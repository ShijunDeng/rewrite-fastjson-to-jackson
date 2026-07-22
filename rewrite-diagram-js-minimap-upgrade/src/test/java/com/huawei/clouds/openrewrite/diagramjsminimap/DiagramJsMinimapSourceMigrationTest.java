package com.huawei.clouds.openrewrite.diagramjsminimap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class DiagramJsMinimapSourceMigrationTest implements RewriteTest {
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.diagramjsminimap.MigrateDeterministicDiagramJsMinimapTo5";
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.diagramjsminimap.AuditDiagramJsMinimap5Source";

    @AfterAll
    static void stopRpc() { JavaScriptRewriteRpc.shutdownCurrent(); }

    @ParameterizedTest(name = "normalizes distribution import {0}")
    @MethodSource("distributionImports")
    void normalizesStableDistributionImportsToPublicRoot(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript(before, after, source -> source.path("src/modeler.js")));
    }

    static Stream<Arguments> distributionImports() {
        return Stream.of(
                Arguments.of("import minimap from 'diagram-js-minimap/dist/index.esm.js';\n",
                        "import minimap from 'diagram-js-minimap';\n"),
                Arguments.of("import minimap from \"diagram-js-minimap/dist/index.esm.js\";\n",
                        "import minimap from \"diagram-js-minimap\";\n"),
                Arguments.of("import minimap from 'diagram-js-minimap/dist/index.js';\n",
                        "import minimap from 'diagram-js-minimap';\n")
        );
    }

    @Test
    void removesDuplicateProvenAdditionalModuleRegistration() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript(
                        "import minimapModule from 'diagram-js-minimap';\n" +
                        "const modeler = new BpmnModeler({ additionalModules: [ minimapModule, custom, minimapModule ] });\n",
                        "import minimapModule from 'diagram-js-minimap';\n" +
                        "const modeler = new BpmnModeler({ additionalModules: [ minimapModule, custom ] });\n",
                        source -> source.path("src/duplicate.js")));
    }

    @Test
    void deduplicatesAliasedDistributionBindingAfterRootNormalization() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                typescript(
                        "import map from 'diagram-js-minimap/dist/index.esm.js';\n" +
                        "const config = { additionalModules: [map, map] };\n",
                        "import map from 'diagram-js-minimap';\n" +
                        "const config = { additionalModules: [map] };\n",
                        source -> source.path("src/config.ts")));
    }

    @Test
    void leavesSingleOfficialRegistrationUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript("import minimapModule from 'diagram-js-minimap';\n" +
                        "new BpmnModeler({ additionalModules: [ minimapModule ] });\n",
                        source -> source.path("src/app.js")));
    }

    @Test
    void leavesSameNamedLocalArrayAndUnprovenExpressionsUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript("const minimapModule = localModule;\n" +
                        "const config = { additionalModules: [ minimapModule, minimapModule ] };\n",
                        source -> source.path("src/local.js")),
                javascript("import minimapModule from 'diagram-js-minimap';\n" +
                        "const config = { additionalModules: [ make(minimapModule), make(minimapModule) ] };\n",
                        source -> source.path("src/expressions.js")));
    }

    @Test
    void leavesUmdPrivateAssetAndUnrelatedDistributionImportsUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                javascript("import minimap from 'diagram-js-minimap/dist/diagram-minimap.umd.js';\n",
                        source -> source.path("src/umd.js")),
                javascript("import Minimap from 'diagram-js-minimap/lib/Minimap';\n",
                        source -> source.path("src/private.js")),
                javascript("import minimap from '@company/diagram-js-minimap/dist/index.esm.js';\n",
                        source -> source.path("src/company.js")));
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                javascript("import minimap from 'diagram-js-minimap/dist/index.esm.js';\n",
                        "import minimap from 'diagram-js-minimap';\n",
                        source -> source.path("src/idempotent.js")));
    }

    @Test
    void marksOfficialOldExampleRegistrationAndServiceLifecycle() {
        // bpmn-io/bpmn-js-examples fixed commit 135f410e645cb85bf689a5e0e7b6c515812c73c9.
        assertMarkers(
                "import minimapModule from 'diagram-js-minimap';\n" +
                "const bpmnModeler = new BpmnModeler({ additionalModules: [ minimapModule ] });\n" +
                "bpmnModeler.get('minimap').open();\n",
                "minimap/app/app.js", "one registration per modeler", "service open/close/toggle");
    }

    @Test
    void marksOfficialTargetExampleCssAndRegistrationBoundaries() {
        // bpmn-io/bpmn-js-examples fixed commit c7baad910b1185e8c6c58bb3676d7c9b0c36beac.
        assertMarkers(
                "import 'diagram-js-minimap/assets/diagram-js-minimap.css';\n" +
                "import minimapModule from 'diagram-js-minimap';\n" +
                "new BpmnModeler({ additionalModules: [ minimapModule ] });\n",
                "minimap/src/app.js", "must survive production tree-shaking", "one registration per modeler");
    }

    @ParameterizedTest(name = "marks entry-point risk {0}")
    @MethodSource("entryPointRisks")
    void marksDeepAndInvalidEntryPointForms(String source, String message) {
        assertMarkers(source, "src/entry.js", message);
    }

    static Stream<Arguments> entryPointRisks() {
        return Stream.of(
                Arguments.of("import Minimap from 'diagram-js-minimap/lib/Minimap';\n", "does not publish lib"),
                Arguments.of("import minimap from 'diagram-js-minimap/dist/diagram-minimap.umd.js';\n", "Direct dist/UMD entry points"),
                Arguments.of("import * as minimap from 'diagram-js-minimap';\n", "exports the minimap module as default"),
                Arguments.of("import { Minimap } from 'diagram-js-minimap';\n", "exports the minimap module as default"),
                Arguments.of("import 'diagram-js-minimap';\n", "exports the minimap module as default")
        );
    }

    @Test
    void marksHammerJsWhenMinimapIsRegistered() {
        assertMarkers("import minimapModule from 'diagram-js-minimap';\nimport Hammer from 'hammerjs';\n",
                "src/touch.js", "removed broken HammerJS touch support");
    }

    @ParameterizedTest(name = "marks removed touch assumption {0}")
    @MethodSource("touchEvents")
    void marksTouchEventHooksInMinimapIntegration(String event) {
        assertMarkers("import minimapModule from 'diagram-js-minimap';\n" +
                        "canvas.addEventListener('" + event + "', onTouch);\n",
                "src/touch.js", "application-owned touch/pinch/pan");
    }

    static Stream<String> touchEvents() {
        return Stream.of("touchstart", "touchmove", "touchend", "touchcancel", "gesturestart", "gesturechange", "gestureend");
    }

    @Test
    void marksLegacyKeyboardAndFixedDomQueries() {
        assertMarkers("import minimapModule from 'diagram-js-minimap';\n" +
                        "if (event.keyCode === 27) close();\n" +
                        "document.querySelector('#djs-minimap-task-1');\n",
                "src/dom.js", "moved from deprecated keyCode", "prefixes copied graphic IDs");
    }

    @Test
    void marksRealOfficialWebpackVendorCopyLiteral() {
        // bpmn-io/bpmn-js-examples fixed commit 135f410e645cb85bf689a5e0e7b6c515812c73c9.
        assertMarkers("const pattern = { from: 'assets/**', to: 'vendor/diagram-js-minimap', context: 'node_modules/diagram-js-minimap/' };\n",
                "minimap/webpack.config.js", "depend on node_modules layout");
    }

    @Test
    void leavesUnrelatedTouchKeyboardAndDomCodeUnmarked() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                javascript("canvas.addEventListener('touchstart', handler);\nif (event.keyCode === 13) submit();\n",
                        source -> source.path("src/unrelated.js")));
    }

    @Test
    void leavesApplicationOwnedIdLookupUnmarkedInsideMinimapIntegration() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                javascript("import minimapModule from 'diagram-js-minimap';\n" +
                                "document.querySelector('#app');\n",
                        source -> source.path("src/app.js").after(actual -> actual).afterRecipe(after ->
                                org.junit.jupiter.api.Assertions.assertFalse(
                                        after.printAll().contains("prefixes copied graphic IDs")))));
    }

    private void assertMarkers(String before, String path, String... messages) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                javascript(before, source -> source.path(path).after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    for (String message : messages) assertTrue(printed.contains(message), printed);
                })));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.diagramjsminimap")
                .scanYamlResources().build();
    }
}
