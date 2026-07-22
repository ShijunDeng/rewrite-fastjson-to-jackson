package com.huawei.clouds.openrewrite.diagramjsminimap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class DiagramJsMinimapProjectMigrationTest implements RewriteTest {
    private static final String PROJECT =
            "com.huawei.clouds.openrewrite.diagramjsminimap.AuditDiagramJsMinimap5Project";
    private static final String TEMPLATE =
            "com.huawei.clouds.openrewrite.diagramjsminimap.AuditDiagramJsMinimap5TemplatesAndStyles";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.diagramjsminimap.MigrateDeterministicDiagramJsMinimapTo5";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.diagramjsminimap.MigrateDiagramJsMinimapTo5_2_0";

    @AfterAll
    static void stopRpc() { JavaScriptRewriteRpc.shutdownCurrent(); }

    @ParameterizedTest(name = "marks unresolved declaration {0}")
    @MethodSource("unresolvedDeclarations")
    void marksEveryUnresolvedDependencyDeclaration(String declaration, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"diagram-js-minimap\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> unresolvedDeclarations() {
        return Stream.of(
                Arguments.of(">=2 <6", "Complex diagram-js-minimap range"),
                Arguments.of("2.1.0 || 4.1.0", "Complex diagram-js-minimap range"),
                Arguments.of("2.1.0 - 5.2.0", "Complex diagram-js-minimap range"),
                Arguments.of("=2.1.0", "Complex diagram-js-minimap range"),
                Arguments.of("v2.1.0", "Complex diagram-js-minimap range"),
                Arguments.of("workspace:^", "Protocol, alias, tag or dynamic"),
                Arguments.of("npm:@example/minimap@2.1.0", "Protocol, alias, tag or dynamic"),
                Arguments.of("file:../minimap", "Protocol, alias, tag or dynamic"),
                Arguments.of("github:bpmn-io/diagram-js-minimap", "Protocol, alias, tag or dynamic"),
                Arguments.of("latest", "Protocol, alias, tag or dynamic"),
                Arguments.of("next", "Protocol, alias, tag or dynamic"),
                Arguments.of("${minimapVersion}", "Protocol, alias, tag or dynamic"),
                Arguments.of("catalog:", "Protocol, alias, tag or dynamic"),
                Arguments.of("2.1.1", "Unlisted or non-target"),
                Arguments.of("^5.2.0", "Unlisted or non-target")
        );
    }

    @Test
    void marksNonStringDeclaration() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"diagram-js-minimap\":true}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "Non-string diagram-js-minimap"))));
    }

    @ParameterizedTest(name = "marks adjacent package constraint {0}")
    @MethodSource("packageConstraints")
    void marksDiagramRuntimeTouchAndBuildConstraints(String dependency, String version, String message) {
        assertPackageMarker(dependency, version, message);
    }

    static Stream<Arguments> packageConstraints() {
        return Stream.of(
                Arguments.of("diagram-js", "8.1.1", "targets diagram-js 15.1 facilities"),
                Arguments.of("diagram-js", "9.0.0", "targets diagram-js 15.1 facilities"),
                Arguments.of("diagram-js", "12.2.0", "targets diagram-js 15.1 facilities"),
                Arguments.of("diagram-js", "14.0.0", "targets diagram-js 15.1 facilities"),
                Arguments.of("diagram-js", "15.0.0", "targets diagram-js 15.1 facilities"),
                Arguments.of("bpmn-js", "9.0.0", "owns the transitive diagram-js runtime"),
                Arguments.of("bpmn-js", "18.20.0", "owns the transitive diagram-js runtime"),
                Arguments.of("hammerjs", "2.0.8", "no longer needs HammerJS"),
                Arguments.of("webpack", "4.41.0", "Legacy bundler/test parsing"),
                Arguments.of("webpack-cli", "3.3.1", "Legacy bundler/test parsing"),
                Arguments.of("rollup", "1.22.0", "Legacy bundler/test parsing"),
                Arguments.of("vite", "4.5.0", "Legacy bundler/test parsing"),
                Arguments.of("parcel", "1.12.5", "Legacy bundler/test parsing"),
                Arguments.of("@angular-devkit/build-angular", "16.2.0", "Legacy bundler/test parsing")
        );
    }

    @Test
    void marksInternetExplorerBrowserTarget() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"browserslist\":[\"last 2 versions\",\"ie 11\"],\"dependencies\":{\"diagram-js-minimap\":\"5.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "ships ES2018"))));
    }

    @Test
    void marksSideEffectsFalseCssTreeShaking() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"sideEffects\":false,\"dependencies\":{\"diagram-js-minimap\":\"5.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "can remove the required minimap CSS"))));
    }

    @ParameterizedTest(name = "leaves supported adjacent package {0}")
    @MethodSource("supportedPackages")
    void leavesSupportedTargetAndBuildVersionsWithoutCompatibilityMarker(String dependency, String version) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"diagram-js-minimap\":\"5.2.0\",\"" + dependency + "\":\"" + version + "\"}}",
                        source -> source.path("package.json")));
    }

    static Stream<Arguments> supportedPackages() {
        return Stream.of(
                Arguments.of("diagram-js", "15.1.0"),
                Arguments.of("diagram-js", "^15.4.0"),
                Arguments.of("webpack", "5.91.0"),
                Arguments.of("rollup", "4.9.4"),
                Arguments.of("vite", "5.4.0"),
                Arguments.of("parcel", "2.12.0"),
                Arguments.of("@angular-devkit/build-angular", "17.3.0")
        );
    }

    @Test
    void leavesManifestWithoutDirectMinimapUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"sideEffects\":false,\"browserslist\":[\"ie 11\"],\"dependencies\":{\"diagram-js\":\"8.1.1\",\"hammerjs\":\"2.0.8\",\"webpack\":\"4.0.0\"}}",
                        source -> source.path("tools/package.json")));
    }

    @Test
    void marksAngularStylesAssetConfiguration() {
        assertJsonMarker("{\"projects\":{\"app\":{\"architect\":{\"build\":{\"options\":{\"styles\":[\"node_modules/diagram-js-minimap/assets/diagram-js-minimap.css\"]}}}}}}",
                "angular.json", "public minimap CSS asset remains ordered");
    }

    @Test
    void marksAngularCopiedAssetConfiguration() {
        assertJsonMarker("{\"projects\":{\"app\":{\"architect\":{\"build\":{\"options\":{\"assets\":[{\"glob\":\"**/*\",\"input\":\"node_modules/diagram-js-minimap/assets\"}]}}}}}}",
                "workspace.json", "Copied minimap assets rely on node_modules layout");
    }

    @Test
    void marksJestNodeModulesTransformExclusion() {
        assertJsonMarker("{\"transformIgnorePatterns\":[\"/node_modules/(?!diagram-js|bpmn-js)/\"]}", "jest.config.json",
                "may fail to parse diagram-js ES2018");
    }

    @Test
    void leavesGenericJestNodeModulesExclusionUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"transformIgnorePatterns\":[\"/node_modules/\"]}",
                        source -> source.path("jest.config.json")));
    }

    @ParameterizedTest(name = "normalizes exact Sass asset URL {0}")
    @MethodSource("sassMigrations")
    void removesLegacyTildeFromExactPublicSassAsset(String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                text(before, after, source -> source.path("src/styles.scss")));
    }

    @Test
    void normalizesOnlyExecutableSassAssetStatements() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                text("// @import '~diagram-js-minimap/assets/diagram-js-minimap.css';\n" +
                             "/* @use \"~diagram-js-minimap/assets/diagram-js-minimap.css\"; */\n" +
                             "@import '~diagram-js-minimap/assets/diagram-js-minimap.css';",
                        "// @import '~diagram-js-minimap/assets/diagram-js-minimap.css';\n" +
                             "/* @use \"~diagram-js-minimap/assets/diagram-js-minimap.css\"; */\n" +
                             "@import 'diagram-js-minimap/assets/diagram-js-minimap.css';",
                        source -> source.path("src/styles.scss")));
    }

    static Stream<Arguments> sassMigrations() {
        return Stream.of(
                Arguments.of("@import '~diagram-js-minimap/assets/diagram-js-minimap.css';",
                        "@import 'diagram-js-minimap/assets/diagram-js-minimap.css';"),
                Arguments.of("@use \"~diagram-js-minimap/assets/diagram-js-minimap.css\";",
                        "@use \"diagram-js-minimap/assets/diagram-js-minimap.css\";"),
                Arguments.of("@forward '~diagram-js-minimap/assets/diagram-js-minimap.css';",
                        "@forward 'diagram-js-minimap/assets/diagram-js-minimap.css';")
        );
    }

    @ParameterizedTest(name = "leaves unsafe style occurrence {0}")
    @ValueSource(strings = {
            "@import '~diagram-js-minimap/assets/custom.css';",
            "@import '~@company/diagram-js-minimap/assets/diagram-js-minimap.css';",
            "$asset: '~diagram-js-minimap/assets/diagram-js-minimap.css';",
            ".x { background: url('~diagram-js-minimap/assets/diagram-js-minimap.css'); }"
    })
    void leavesUnrelatedAndNonStatementStyleOccurrencesUntouched(String input) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                text(input, source -> source.path("src/styles.scss")));
    }

    @Test
    void deterministicStyleMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("@import '~diagram-js-minimap/assets/diagram-js-minimap.css';",
                        "@import 'diagram-js-minimap/assets/diagram-js-minimap.css';",
                        source -> source.path("src/styles.scss")));
    }

    @ParameterizedTest(name = "marks template risk {1}")
    @MethodSource("templateRisks")
    void marksHtmlAssetAndInternalMarkupBoundaries(String input, String label, String message) {
        assertTextMarker(input, "src/index.html", message);
    }

    static Stream<Arguments> templateRisks() {
        return Stream.of(
                Arguments.of("<link rel=\"stylesheet\" href=\"diagram-js-minimap/assets/diagram-js-minimap.css\">", "public link", "public minimap stylesheet link"),
                Arguments.of("<link rel=\"stylesheet\" href=\"vendor/diagram-js-minimap/assets/diagram-js-minimap.css\">", "vendor link", "public minimap stylesheet link"),
                Arguments.of("<div class=\"djs-minimap open\"></div>", "internal class", "internal classes/IDs"),
                Arguments.of("<svg id=\"djs-minimap-task-1\"></svg>", "fixed id", "internal classes/IDs")
        );
    }

    @ParameterizedTest(name = "marks style risk {1}")
    @MethodSource("styleRisks")
    void marksCssAssetInternalSelectorAndSvgIdBoundaries(String input, String path, String message) {
        assertTextMarker(input, path, message);
    }

    static Stream<Arguments> styleRisks() {
        return Stream.of(
                Arguments.of("@import 'diagram-js-minimap/assets/diagram-js-minimap.css';", "src/app.scss", "public minimap CSS asset"),
                Arguments.of(".djs-minimap { z-index: 100; }", "src/app.css", "Custom .djs-minimap overrides"),
                Arguments.of(".djs-minimap .viewport-dom { border-color: red; }", "src/app.less", "child selector couples"),
                Arguments.of("#djs-minimap-task-1 { fill: red; }", "src/svg.css", "prefixes SVG graphic IDs"),
                Arguments.of(".marker { fill: url(#djs-minimap-task-1); }", "src/svg.scss", "prefixes SVG graphic IDs"),
                Arguments.of(".x { src: url('../node_modules/diagram-js-minimap/assets/x'); }", "src/vendor.sass", "Direct vendor/node_modules asset URLs")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!-- <link href=\"vendor/diagram-js-minimap/assets/diagram-js-minimap.css\"><div class=\"djs-minimap\"></div> -->",
            "<p>diagram-js-minimap/assets/diagram-js-minimap.css is documentation text.</p>"
    })
    void leavesHtmlCommentsAndOrdinaryTextUnchanged(String input) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(input, source -> source.path("src/safe.html")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/* .djs-minimap .viewport-dom { color: red; } */",
            "// @import 'diagram-js-minimap/assets/diagram-js-minimap.css';\n.safe { color: red; }"
    })
    void leavesStyleCommentsUnchanged(String input) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(input, source -> source.path("src/safe.scss")));
    }

    @Test
    void leavesMinimapTextInMarkdownAndJsonFixtureUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(".djs-minimap @import diagram-js-minimap/assets/diagram-js-minimap.css", source -> source.path("README.md")),
                text("{\"selector\":\".djs-minimap\"}", source -> source.path("fixtures/style.json")));
    }

    @Test
    void recommendedRecipeCombinesUpgradeAutomaticChangesAndMarkers() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"diagram-js-minimap\":\"^2.1.0\",\"diagram-js\":\"12.2.0\",\"bpmn-js\":\"13.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "\"diagram-js-minimap\":\"5.2.0\"");
                            assertContains(printed, "targets diagram-js 15.1 facilities");
                            assertContains(printed, "owns the transitive diagram-js runtime");
                        })),
                javascript("import minimapModule from 'diagram-js-minimap/dist/index.esm.js';\n" +
                                "new BpmnModeler({ additionalModules: [minimapModule, minimapModule] });\n",
                        source -> source.path("src/app.js").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "from 'diagram-js-minimap'");
                            assertContains(printed, "[minimapModule]");
                            assertContains(printed, "one registration per modeler");
                        })),
                text("@import '~diagram-js-minimap/assets/diagram-js-minimap.css';",
                        source -> source.path("src/styles.scss").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "@import 'diagram-js-minimap/assets");
                            assertContains(after.printAll(), "public minimap CSS asset");
                        })));
    }

    @Test
    void exposesAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{PROJECT, TEMPLATE, MIGRATE, RECOMMENDED,
                "com.huawei.clouds.openrewrite.diagramjsminimap.UpgradeDiagramJsMinimapTo5_2_0",
                "com.huawei.clouds.openrewrite.diagramjsminimap.AuditDiagramJsMinimap5Source"}) {
            Recipe recipe = environment.activateRecipes(name);
            assertNotNull(recipe);
            assertTrue(recipe.getRecipeList().size() > 0, name);
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()), name);
        }
    }

    private void assertPackageMarker(String dependency, String version, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"diagram-js-minimap\":\"5.2.0\",\"" + dependency + "\":\"" + version + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    private void assertJsonMarker(String before, String path, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(before, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    private void assertTextMarker(String before, String path, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(before, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.diagramjsminimap")
                .scanYamlResources().build();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
