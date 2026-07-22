package com.huawei.clouds.openrewrite.tweenjs;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
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

class TweenJsProjectMigrationTest implements RewriteTest {
    private static final String PROJECT =
            "com.huawei.clouds.openrewrite.tweenjs.AuditTweenJs23Project";
    private static final String TEMPLATE =
            "com.huawei.clouds.openrewrite.tweenjs.AuditTweenJs23TemplatesAndConfig";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.tweenjs.MigrateTweenJsTo23_1_1";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "marks unresolved declaration {0}")
    @MethodSource("unresolvedDeclarations")
    void marksEveryUnresolvedDependencyDeclaration(String declaration, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@tweenjs/tween.js\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> unresolvedDeclarations() {
        return Stream.of(
                Arguments.of(">=19.0.0 <21", "Complex @tweenjs/tween.js range"),
                Arguments.of("19.0.0 || 20.0.3", "Complex @tweenjs/tween.js range"),
                Arguments.of("19.0.0 - 23.1.1", "Complex @tweenjs/tween.js range"),
                Arguments.of("=20.0.3", "Complex @tweenjs/tween.js range"),
                Arguments.of("v19.0.0", "Complex @tweenjs/tween.js range"),
                Arguments.of("19.0.0-beta.1", "Complex @tweenjs/tween.js range"),
                Arguments.of("20.0.3+build.1", "Complex @tweenjs/tween.js range"),
                Arguments.of("workspace:^", "Protocol, alias, tag or dynamic"),
                Arguments.of("workspace:20.0.3", "Protocol, alias, tag or dynamic"),
                Arguments.of("npm:@company/tween@20.0.3", "Protocol, alias, tag or dynamic"),
                Arguments.of("file:../tween", "Protocol, alias, tag or dynamic"),
                Arguments.of("link:../tween", "Protocol, alias, tag or dynamic"),
                Arguments.of("github:tweenjs/tween.js", "Protocol, alias, tag or dynamic"),
                Arguments.of("https://example.test/tween.tgz", "Protocol, alias, tag or dynamic"),
                Arguments.of("latest", "Protocol, alias, tag or dynamic"),
                Arguments.of("next", "Protocol, alias, tag or dynamic"),
                Arguments.of("catalog:", "Protocol, alias, tag or dynamic"),
                Arguments.of("${tweenVersion}", "Protocol, alias, tag or dynamic"),
                Arguments.of("18.6.4", "Unlisted or non-target"),
                Arguments.of("19.0.1", "Unlisted or non-target"),
                Arguments.of("^21.0.0", "Unlisted or non-target"),
                Arguments.of("~23.1.1", "Unlisted or non-target"),
                Arguments.of("24.0.0", "Unlisted or non-target")
        );
    }

    @Test
    void marksNonStringDeclaration() {
        assertPackageMarker("{\"dependencies\":{\"@tweenjs/tween.js\":true}}",
                "Non-string @tweenjs/tween.js declaration");
    }

    @ParameterizedTest(name = "marks legacy toolchain {0}@{1}")
    @MethodSource("legacyToolchains")
    void marksOldModuleAndTestToolchains(String tool, String version) {
        assertPackageMarker("{\"dependencies\":{\"@tweenjs/tween.js\":\"23.1.1\"},\"devDependencies\":{\"" +
                        tool + "\":\"" + version + "\"}}",
                "older module toolchain");
    }

    static Stream<Arguments> legacyToolchains() {
        return Stream.of(
                Arguments.of("typescript", "4.6.4"),
                Arguments.of("webpack", "4.47.0"),
                Arguments.of("webpack-cli", "4.10.0"),
                Arguments.of("webpack-dev-server", "4.15.0"),
                Arguments.of("rollup", "2.79.1"),
                Arguments.of("vite", "2.9.18"),
                Arguments.of("parcel", "1.12.5"),
                Arguments.of("jest", "28.1.3"),
                Arguments.of("ts-jest", "28.0.8"),
                Arguments.of("babel-jest", "28.1.3")
        );
    }

    @ParameterizedTest(name = "leaves supported toolchain {0}@{1}")
    @MethodSource("supportedToolchains")
    void leavesSupportedToolchainScalarsUnmarked(String tool, String version) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@tweenjs/tween.js\":\"23.1.1\"},\"devDependencies\":{\"" +
                                tool + "\":\"" + version + "\"}}",
                        source -> source.path("package.json")));
    }

    static Stream<Arguments> supportedToolchains() {
        return Stream.of(
                Arguments.of("typescript", "4.7.4"),
                Arguments.of("typescript", "^5.5.4"),
                Arguments.of("webpack", "5.91.0"),
                Arguments.of("rollup", "3.29.5"),
                Arguments.of("vite", "3.2.10"),
                Arguments.of("parcel", "2.12.0"),
                Arguments.of("jest", "29.7.0"),
                Arguments.of("ts-jest", "29.2.5"),
                Arguments.of("babel-jest", "29.7.0")
        );
    }

    @ParameterizedTest(name = "marks adjacent ownership {0}")
    @MethodSource("legacyOwnership")
    void marksLegacyPackageAndTypesOwnership(String dependency, String version, String message) {
        assertPackageMarker("{\"dependencies\":{\"@tweenjs/tween.js\":\"23.1.1\",\"" + dependency +
                "\":\"" + version + "\"}}", message);
    }

    static Stream<Arguments> legacyOwnership() {
        return Stream.of(
                Arguments.of("tween.js", "16.6.0", "separate legacy package"),
                Arguments.of("@types/tween.js", "16.3.4", "publishes its own dist/tween.d.ts")
        );
    }

    @ParameterizedTest(name = "marks physical package config {0}")
    @MethodSource("physicalManifestValues")
    void marksPhysicalDistributionPathsInManifestConfiguration(String label, String member) {
        assertPackageMarker("{\"dependencies\":{\"@tweenjs/tween.js\":\"23.1.1\"}," + member + "}",
                "names a Tween.js physical dist entry");
    }

    static Stream<Arguments> physicalManifestValues() {
        return Stream.of(
                Arguments.of("copy-script", "\"scripts\":{\"copy:tween\":\"cp node_modules/@tweenjs/tween.js/dist/tween.cjs public/vendor/\"}"),
                Arguments.of("browser", "\"browser\":{\"@tweenjs/tween.js\":\"@tweenjs/tween.js/dist/tween.esm.js\"}"),
                Arguments.of("jest-map", "\"jest\":{\"moduleNameMapper\":{\"^@tweenjs/tween.js$\":\"@tweenjs/tween.js/dist/tween.cjs.js\"}}"),
                Arguments.of("unpkg", "\"unpkg\":\"vendor/@tweenjs/tween.js/dist/tween.cjs\""),
                Arguments.of("exports", "\"exports\":{\"./tween\":\"./node_modules/@tweenjs/tween.js/dist/tween.esm.js\"}")
        );
    }

    @Test
    void marksTweenAwareTypeScriptConfigAndLegacyResolution() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"compilerOptions\":{\"moduleResolution\":\"node\",\"paths\":{\"@tweenjs/tween.js\":[\"./node_modules/@tweenjs/tween.js/dist/tween.esm.js\"]}}}",
                        source -> source.path("tsconfig.json").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "pins a Tween.js physical dist path");
                            assertContains(after.printAll(), "uses legacy module resolution");
                        })));
    }

    @ParameterizedTest(name = "marks Tween-aware JSON config {0}")
    @MethodSource("jsonConfigs")
    void marksPhysicalPathsInJsonToolConfiguration(String path, String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(source, input -> input.path(path).after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "pins a Tween.js physical dist path"))));
    }

    static Stream<Arguments> jsonConfigs() {
        return Stream.of(
                Arguments.of("jest.config.json", "{\"moduleNameMapper\":{\"^@tweenjs/tween.js$\":\"@tweenjs/tween.js/dist/tween.cjs\"}}"),
                Arguments.of("webpack.config.json", "{\"alias\":{\"@tweenjs/tween.js\":\"node_modules/@tweenjs/tween.js/dist/tween.esm.js\"}}"),
                Arguments.of("vite.config.json", "{\"resolve\":{\"alias\":{\"@tweenjs/tween.js\":\"@tweenjs/tween.js/dist/tween.esm.js\"}}}"),
                Arguments.of("rollup.config.json", "{\"input\":\"vendor/@tweenjs/tween.js/dist/tween.esm.js\"}"),
                Arguments.of("jsconfig.app.json", "{\"compilerOptions\":{\"paths\":{\"@tweenjs/tween.js\":[\"@tweenjs/tween.js/dist/tween.esm.js\"]}}}")
        );
    }

    @Test
    void leavesManifestWithoutDirectTweenDependencyUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"tween.js\":\"16.6.0\"},\"devDependencies\":{\"webpack\":\"4.47.0\",\"@types/tween.js\":\"16.3.4\"},\"scripts\":{\"copy\":\"cp node_modules/@tweenjs/tween.js/dist/tween.cjs public/\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void leavesGenericTypeScriptConfigWithoutTweenReferenceUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"compilerOptions\":{\"moduleResolution\":\"node\",\"paths\":{\"@app/*\":[\"src/*\"]}}}",
                        source -> source.path("tsconfig.json")));
    }

    @ParameterizedTest(name = "marks template/deploy risk {0}")
    @MethodSource("templateRisks")
    void marksPhysicalCdnVendorAndPackagePaths(String label, String path, String source, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(source, input -> input.path(path).after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> templateRisks() {
        return Stream.of(
                Arguments.of("jsdelivr", "public/index.html", "<script src=\"https://cdn.jsdelivr.net/npm/@tweenjs/tween.js@20.0.3/dist/tween.umd.js\"></script>", "CDN URL pins"),
                Arguments.of("unpkg", "public/demo.html", "<script src='https://unpkg.com/@tweenjs/tween.js@19.0.0/dist/tween.umd.js'></script>", "CDN URL pins"),
                Arguments.of("vendor", "deploy/assets.yml", "tween: vendor/@tweenjs/tween.js/dist/tween.cjs", "copied Tween.js asset"),
                Arguments.of("node-modules", "build/copy.yaml", "from: node_modules/@tweenjs/tween.js/dist/tween.esm.js\nto: public/vendor/tween.js\n", "copied Tween.js asset"),
                Arguments.of("package-path", "config/modules.conf", "external=@tweenjs/tween.js/dist/tween.cjs.js", "exports do not expose physical dist"),
                Arguments.of("shell", "scripts/copy.sh", "cp node_modules/@tweenjs/tween.js/dist/tween.cjs public/vendor/tween.cjs\n", "copied Tween.js asset")
        );
    }

    @ParameterizedTest(name = "leaves template/config no-op {0}")
    @MethodSource("safeTemplateSources")
    void leavesCommentsDocumentationAndUnrelatedFilesUntouched(String path, String source) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(source, input -> input.path(path)));
    }

    static Stream<Arguments> safeTemplateSources() {
        return Stream.of(
                Arguments.of("public/index.html", "<!-- <script src=\"https://cdn.example/@tweenjs/tween.js@20.0.3/dist/tween.umd.js\"></script> -->"),
                Arguments.of("deploy/assets.yml", "# from: node_modules/@tweenjs/tween.js/dist/tween.cjs\nfrom: app.js\n"),
                Arguments.of("README.md", "Use @tweenjs/tween.js/dist/tween.cjs only when reading old release notes."),
                Arguments.of("notes.txt", "node_modules/@tweenjs/tween.js/dist/tween.cjs"),
                Arguments.of("src/style.css", ".logo::after { content: '@tweenjs/tween.js/dist/tween.cjs'; }"),
                Arguments.of("public/index.html", "<script type=\"module\">import { Tween } from '@tweenjs/tween.js';</script>"),
                Arguments.of("deploy/assets.yml", "from: node_modules/@company/tween.js/dist/tween.cjs\n")
        );
    }

    @Test
    void recommendedRecipeCombinesAutomaticChangesAndPreciseMarkers() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"@tweenjs/tween.js\":\"^20.0.3\"},\"devDependencies\":{\"typescript\":\"4.6.4\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "\"@tweenjs/tween.js\":\"23.1.1\"");
                            assertContains(after.printAll(), "older module toolchain");
                        })),
                javascript("import TWEEN from '@tweenjs/tween.js/dist/tween.esm.js';\n" +
                                "const tween = new TWEEN.Tween(state, TWEEN);\n" +
                                "tween.to(target, 300);\n" +
                                "tween.repeat(2);\n" +
                                "tween.onComplete(done);\n",
                        source -> source.path("src/app.js").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "from '@tweenjs/tween.js'");
                            assertContains(after.printAll(), "new TWEEN.Tween(state)");
                            assertContains(after.printAll(), "snapshot targets by default");
                            assertContains(after.printAll(), "skipped repeat");
                        })),
                text("<script src=\"https://cdn.example/@tweenjs/tween.js@20.0.3/dist/tween.umd.js\"></script>",
                        source -> source.path("public/index.html").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "CDN URL pins"))));
    }

    @Test
    void exposesAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{
                "com.huawei.clouds.openrewrite.tweenjs.UpgradeTweenJsTo23_1_1",
                "com.huawei.clouds.openrewrite.tweenjs.MigrateDeterministicTweenJsTo23",
                "com.huawei.clouds.openrewrite.tweenjs.AuditTweenJs23Source",
                PROJECT, TEMPLATE, RECOMMENDED
        }) {
            Recipe recipe = environment.activateRecipes(name);
            assertNotNull(recipe, name);
            assertTrue(recipe.getRecipeList().size() > 0, name);
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()), name);
        }
    }

    private void assertPackageMarker(String before, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(before, source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), message))));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.tweenjs")
                .scanYamlResources().build();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
