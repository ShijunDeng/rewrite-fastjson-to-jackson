package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class AngularCdkProjectMigrationTest implements RewriteTest {
    private static final String PROJECT = "com.huawei.clouds.openrewrite.angular.AuditAngularCdk20Project";
    private static final String TEMPLATE = "com.huawei.clouds.openrewrite.angular.AuditAngularCdk20TemplatesAndStyles";
    private static final String SOURCE = "com.huawei.clouds.openrewrite.angular.MigrateDeterministicAngularCdkTo20";
    private static final String RECOMMENDED = "com.huawei.clouds.openrewrite.angular.MigrateAngularCdkTo20_2_14";

    @ParameterizedTest(name = "marks unresolved declaration {0}")
    @MethodSource("unresolvedDeclarations")
    void marksEveryUnresolvedCdkDeclaration(String declaration, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/cdk\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> unresolvedDeclarations() {
        return Stream.of(
                Arguments.of(">=10 <20", "Complex @angular/cdk range"),
                Arguments.of("10.2.6 || 12.2.13", "Complex @angular/cdk range"),
                Arguments.of("10.2.6 - 14.2.0", "Complex @angular/cdk range"),
                Arguments.of("v12.2.13", "Complex @angular/cdk range"),
                Arguments.of("=13.3.9", "Complex @angular/cdk range"),
                Arguments.of("workspace:^", "Protocol, alias, tag or dynamic"),
                Arguments.of("npm:@angular/cdk@12.2.13", "Protocol, alias, tag or dynamic"),
                Arguments.of("file:../cdk", "Protocol, alias, tag or dynamic"),
                Arguments.of("github:angular/components", "Protocol, alias, tag or dynamic"),
                Arguments.of("latest", "Protocol, alias, tag or dynamic"),
                Arguments.of("next", "Protocol, alias, tag or dynamic"),
                Arguments.of("${cdkVersion}", "Protocol, alias, tag or dynamic"),
                Arguments.of("catalog:", "Protocol, alias, tag or dynamic"),
                Arguments.of("10.1.3", "Unlisted or non-target"),
                Arguments.of("20.2.13", "Unlisted or non-target")
        );
    }

    @Test
    void marksNonStringCdkDeclaration() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/cdk\":true}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "Non-string @angular/cdk"))));
    }

    @ParameterizedTest(name = "marks package constraint {0}")
    @MethodSource("packageConstraints")
    void marksPackageCompatibilityConstraints(String dependency, String version, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"@angular/cdk\":\"20.2.14\",\"" + dependency + "\":\"" + version + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> packageConstraints() {
        return Stream.of(
                Arguments.of("@angular/material", "19.2.0", "Material should align with CDK 20.2.14"),
                Arguments.of("@angular/core", "14.2.0", "compatible Angular core/common peers"),
                Arguments.of("@angular/common", "14.2.0", "compatible Angular core/common peers"),
                Arguments.of("@angular/cli", "14.2.0", "Align Angular CLI/build/schematics"),
                Arguments.of("@angular-devkit/build-angular", "14.2.0", "Align Angular CLI/build/schematics"),
                Arguments.of("@angular/build", "19.2.0", "Align Angular CLI/build/schematics"),
                Arguments.of("@angular-devkit/core", "14.2.0", "Align Angular CLI/build/schematics"),
                Arguments.of("@angular-devkit/schematics", "14.2.0", "Align Angular CLI/build/schematics"),
                Arguments.of("@schematics/angular", "14.2.0", "Align Angular CLI/build/schematics"),
                Arguments.of("typescript", "5.7.3", "requires TypeScript >=5.8 and <6.0"),
                Arguments.of("rxjs", "6.4.0", "supports RxJS ^6.5.3 or ^7.4.0")
        );
    }

    @ParameterizedTest(name = "marks unsupported Node engine {0}")
    @ValueSource(strings = {"16.20.2", "18.20.5", "20.18.3", "22.11.0", "^18.0.0"})
    void marksUnsupportedNodeRuntime(String node) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"" + node + "\"},\"dependencies\":{\"@angular/cdk\":\"20.2.14\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "requires supported Node"))));
    }

    @Test
    void leavesSupportedTargetToolchainWithoutNoise() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"20.19.1\"},\"dependencies\":{\"@angular/cdk\":\"20.2.14\",\"@angular/material\":\"20.2.14\",\"@angular/core\":\"20.3.1\",\"@angular/common\":\"20.3.1\",\"rxjs\":\"7.8.2\"},\"devDependencies\":{\"typescript\":\"5.9.2\",\"@angular/cli\":\"20.3.1\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void marksCustomWorkspaceBuilder() {
        assertJsonMarker("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@nrwl/angular:webpack-browser\"}}}}}",
                "workspace.json", "Custom builder must preserve");
    }

    @Test
    void marksCdkGlobalStyleEntry() {
        assertJsonMarker("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"options\":{\"styles\":[\"node_modules/@angular/cdk/overlay-prebuilt.css\"]}}}}}}",
                "angular.json", "Global CDK/prebuilt stylesheet order");
    }

    @ParameterizedTest(name = "marks SSR workspace key {0}")
    @ValueSource(strings = {"server", "ssr", "prerender"})
    void marksSsrAndPrerenderTargets(String key) {
        assertJsonMarker("{\"projects\":{\"web\":{\"architect\":{\"" + key + "\":{}}}}}",
                "angular.json", "hydration-safe initial markup");
    }

    @Test
    void marksProtractorBuilder() {
        assertJsonMarker("{\"projects\":{\"web\":{\"architect\":{\"e2e\":{\"builder\":\"@angular-devkit/build-angular:protractor\"}}}}}",
                "angular.json", "Protractor harness support was removed");
    }

    @Test
    void marksDisabledStrictTemplates() {
        assertJsonMarker("{\"angularCompilerOptions\":{\"strictTemplates\":false}}", "tsconfig.app.json",
                "Enable strictTemplates");
    }

    @Test
    void marksCdkTypeScriptPathMappings() {
        assertJsonMarker("{\"compilerOptions\":{\"paths\":{\"@angular/cdk/*\":[\"vendor/cdk/*\"]}}}", "tsconfig.json",
                "path mapping can bypass");
    }

    @Test
    void leavesUnrelatedManifestWorkspaceAndTsconfigUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"engines\":{\"node\":\"18.0.0\"},\"dependencies\":{\"typescript\":\"4.9.5\"}}",
                        source -> source.path("services/api/package.json")),
                json("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@angular/build:application\"}}}}}",
                        source -> source.path("angular.json")),
                json("{\"angularCompilerOptions\":{\"strictTemplates\":true}}",
                        source -> source.path("tsconfig.app.json")));
    }

    @ParameterizedTest(name = "marks template risk {1}")
    @MethodSource("templateRisks")
    void marksTemplateCompatibilityBoundaries(String input, String token, String message) {
        assertTextMarker(input, "src/app.component.html", message);
    }

    static Stream<Arguments> templateRisks() {
        return Stream.of(
                Arguments.of("<ng-template cdkConnectedOverlay></ng-template>", "overlay", "Connected overlay placement"),
                Arguments.of("<div cdkDrag><span cdkDragHandle></span></div>", "drag", "Drag/drop pointer"),
                Arguments.of("<div cdkDropList></div>", "drop", "Drag/drop pointer"),
                Arguments.of("<cdk-virtual-scroll-viewport><div *cdkVirtualFor=\"let x of xs\"></div></cdk-virtual-scroll-viewport>", "virtual", "Virtual scroll has stricter"),
                Arguments.of("<ng-template cdkPortal></ng-template><ng-template [cdkPortalOutlet]=\"portal\"></ng-template>", "portal", "Portal attachment/detachment"),
                Arguments.of("<cdk-table [dataSource]=\"data\"><ng-container cdkColumnDef=\"name\"></ng-container></cdk-table>", "table", "CDK table sticky/rendering"),
                Arguments.of("<div cdkTrapFocus><button cdkFocusInitial>OK</button></div>", "focus", "Focus trapping/monitoring"),
                Arguments.of("<main cdkScrollable></main>", "scroll", "Scrollable registration")
        );
    }

    @ParameterizedTest(name = "marks style risk {1}")
    @MethodSource("styleRisks")
    void marksStyleCompatibilityBoundaries(String input, String path, String message) {
        assertTextMarker(input, path, message);
    }

    static Stream<Arguments> styleRisks() {
        return Stream.of(
                Arguments.of("@import '~@angular/cdk/overlay-prebuilt.css';", "src/styles.scss", "Legacy Sass @import"),
                Arguments.of(".x { background: url('~@angular/cdk/a.png'); }", "src/styles.scss", "Webpack tilde resolution"),
                Arguments.of("@include cdk.high-contrast(active, off);", "src/a11y.scss", "high-contrast mixin"),
                Arguments.of(".cdk-overlay-container { z-index: 1000; }", "src/overlay.css", "load order and specificity"),
                Arguments.of(".cdk-overlay-pane { max-width: 90vw; }", "src/overlay.less", "load order and specificity"),
                Arguments.of(".cdk-drag-preview { box-shadow: none; }", "src/drag.scss", "Custom drag/drop CSS"),
                Arguments.of(".cdk-drop-list-dragging { cursor: grabbing; }", "src/drop.sass", "Custom drag/drop CSS")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!-- <div cdkDrag cdkScrollable></div> -->",
            "<p>cdkConnectedOverlay is documentation text.</p>"
    })
    void leavesHtmlCommentsAndOrdinaryTextUnchanged(String input) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(input, source -> source.path("src/safe.html")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/* .cdk-overlay-pane { color: red; } */",
            "// @import '~@angular/cdk/overlay';\n.safe { color: red; }"
    })
    void leavesStyleCommentsUnchanged(String input) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(TEMPLATE)),
                text(input, source -> source.path("src/safe.scss")));
    }

    @ParameterizedTest(name = "normalizes Sass module URL {0}")
    @ValueSource(strings = {"@use '~@angular/cdk' as cdk;", "@forward \"~@angular/cdk/a11y\";"})
    void removesTildeFromSassUseAndForward(String before) {
        String after = before.replace("~@angular", "@angular");
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(before, after, source -> source.path("src/styles.scss")));
    }

    @Test
    void normalizesOnlyExecutableSassModuleDirectives() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "// @use '~@angular/cdk' as commented;\n" +
                        "/* @forward \"~@angular/cdk/a11y\"; */\n" +
                        "@use '~@angular/cdk' as cdk;\n",
                        "// @use '~@angular/cdk' as commented;\n" +
                        "/* @forward \"~@angular/cdk/a11y\"; */\n" +
                        "@use '@angular/cdk' as cdk;\n",
                        source -> source.path("src/styles.scss")));
    }

    @ParameterizedTest(name = "leaves unsafe Sass form {0}")
    @ValueSource(strings = {
            "@import '~@angular/cdk/overlay';", "@use '~@angular/material' as mat;",
            ".x { background: url('~@angular/cdk/a.png'); }", "$module: '~@angular/cdk';"
    })
    void leavesLegacyAndUnrelatedSassOccurrencesUntouched(String input) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(input, source -> source.path("src/styles.scss")));
    }

    @Test
    void recommendedRecipeCombinesUpgradeAutomaticChangesAndMarkers() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"@angular/cdk\":\"12.2.13\",\"@angular/material\":\"12.2.13\",\"@angular/core\":\"12.2.13\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "\"@angular/cdk\":\"20.2.14\"");
                            assertContains(printed, "Material should align");
                            assertContains(printed, "compatible Angular core/common peers");
                        })),
                text("@use '~@angular/cdk' as cdk;\n.cdk-overlay-pane { width: 20rem; }",
                        source -> source.path("src/styles.scss").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "@use '@angular/cdk'");
                            assertContains(after.printAll(), "load order and specificity");
                        })));
    }

    @Test
    void exposesAndValidatesEveryPublicRecipe() {
        Environment environment = environment();
        for (String name : new String[]{PROJECT, TEMPLATE, SOURCE, RECOMMENDED,
                "com.huawei.clouds.openrewrite.angular.UpgradeAngularCdkTo20_2_14",
                "com.huawei.clouds.openrewrite.angular.AuditAngularCdk20Source"}) {
            Recipe recipe = environment.activateRecipes(name);
            assertNotNull(recipe);
            assertTrue(recipe.getRecipeList().size() > 0, name);
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()), name);
        }
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
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources().build();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
