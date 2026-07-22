package com.huawei.clouds.openrewrite.ng2fileupload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class Ng2FileUploadManifestRiskTest implements RewriteTest {
    @ParameterizedTest(name = "unresolved owner {0}")
    @ValueSource(strings = {
            "2.0.0-3", "^3.0.0", "~4.0.0", ">=4", "4.x", "workspace:^4.0.0",
            "npm:@fork/ng2-file-upload@4.0.0", "github:valor-software/ng2-file-upload#v4.0.0",
            "latest", "next", "9.0.0"
    })
    void marksEveryNonTargetDirectOwner(String declaration) {
        assertManifestMarker("{\"dependencies\":{\"ng2-file-upload\":\"" + declaration + "\"}}",
                "outside the exact workbook AUTO target");
    }

    @ParameterizedTest(name = "accepted target owner {0}")
    @ValueSource(strings = {"10.0.0", "^10.0.0", "~10.0.0"})
    void acceptsExactCaretAndTildeTarget(String declaration) {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadManifestRisks()),
                json("{\"dependencies\":{\"ng2-file-upload\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").afterRecipe(after ->
                                assertFalse(after.printAll().contains("outside the exact workbook"), after.printAll()))));
    }

    @ParameterizedTest(name = "Angular companion {0}")
    @ValueSource(strings = {
            "@angular/animations", "@angular/cdk", "@angular/cli", "@angular/common", "@angular/compiler",
            "@angular/compiler-cli", "@angular/core", "@angular/forms", "@angular/platform-browser",
            "@angular/platform-browser-dynamic", "@angular/router", "@angular-devkit/build-angular"
    })
    void marksEveryNon21AngularCompanion(String dependency) {
        assertManifestMarker("{\"dependencies\":{\"ng2-file-upload\":\"10.0.0\",\"" + dependency + "\":\"^20.2.0\"}}",
                "align the complete Angular CLI/compiler/runtime/CDK set");
    }

    @ParameterizedTest(name = "accepted Angular 21 {0}")
    @ValueSource(strings = {"21.0.0", "^21.0.0", "~21.1.2", "21.x", "^21.x.x"})
    void acceptsSimpleAngular21Owners(String declaration) {
        assertNoMarker("{\"dependencies\":{\"ng2-file-upload\":\"10.0.0\",\"@angular/core\":\"" + declaration + "\"}}",
                "align the complete Angular");
    }

    @ParameterizedTest(name = "Node boundary {0}")
    @MethodSource("nodeBoundaries")
    void validatesAngular21NodeBaseline(String declaration, boolean marked) {
        String manifest = "{\"engines\":{\"node\":\"" + declaration +
                          "\"},\"dependencies\":{\"ng2-file-upload\":\"10.0.0\"}}";
        if (marked) assertManifestMarker(manifest, "Angular 21 requires Node");
        else assertNoMarker(manifest, "Angular 21 requires Node");
    }

    static Stream<Arguments> nodeBoundaries() {
        return Stream.of(
                Arguments.of("18.20.0", true), Arguments.of("^20.18.0", true),
                Arguments.of("20", true), Arguments.of("^22.11.0", true), Arguments.of("23.0.0", true),
                Arguments.of(">=20", true), Arguments.of("*", true),
                Arguments.of("^20.19.0", false), Arguments.of("20.19.1", false),
                Arguments.of("^22.12.0", false), Arguments.of("22.13.0", false),
                Arguments.of(">=24.0.0", false), Arguments.of("^24.0.0", false),
                Arguments.of("^20.19.0 || ^22.12.0 || >=24.0.0", false));
    }

    @ParameterizedTest(name = "RxJS boundary {0}")
    @MethodSource("rxjsBoundaries")
    void validatesAngular21RxjsPeer(String declaration, boolean marked) {
        String manifest = "{\"dependencies\":{\"ng2-file-upload\":\"10.0.0\",\"rxjs\":\"" +
                          declaration + "\"}}";
        if (marked) assertManifestMarker(manifest, "Angular 21 peers on RxJS");
        else assertNoMarker(manifest, "Angular 21 peers on RxJS");
    }

    static Stream<Arguments> rxjsBoundaries() {
        return Stream.of(
                Arguments.of("^5.5.0", true), Arguments.of("6.5.2", true),
                Arguments.of("^7.3.1", true), Arguments.of("8.0.0", true), Arguments.of("latest", true),
                Arguments.of("^6.5.3", false), Arguments.of("~6.6.7", false),
                Arguments.of("^7.4.0", false), Arguments.of("~7.8.2", false));
    }

    @ParameterizedTest(name = "TypeScript boundary {0}")
    @MethodSource("typescriptBoundaries")
    void validatesAngular21TypeScriptPeer(String declaration, boolean marked) {
        String manifest = "{\"dependencies\":{\"ng2-file-upload\":\"10.0.0\"},\"devDependencies\":{\"typescript\":\"" +
                          declaration + "\"}}";
        if (marked) assertManifestMarker(manifest, "requires TypeScript >=5.9 <6.0");
        else assertNoMarker(manifest, "requires TypeScript >=5.9 <6.0");
    }

    static Stream<Arguments> typescriptBoundaries() {
        return Stream.of(
                Arguments.of("4.9.5", true), Arguments.of("^5.8.2", true), Arguments.of("6.0.0", true),
                Arguments.of(">=5.9 <6", true), Arguments.of("next", true),
                Arguments.of("5.9.0", false), Arguments.of("^5.9.3", false), Arguments.of("~5.10.1", false));
    }

    @ParameterizedTest(name = "tslib boundary {0}")
    @MethodSource("tslibBoundaries")
    void validatesTslibRuntime(String declaration, boolean marked) {
        String manifest = "{\"dependencies\":{\"ng2-file-upload\":\"10.0.0\",\"tslib\":\"" +
                          declaration + "\"}}";
        if (marked) assertManifestMarker(manifest, "require tslib ^2.3.0");
        else assertNoMarker(manifest, "require tslib ^2.3.0");
    }

    static Stream<Arguments> tslibBoundaries() {
        return Stream.of(
                Arguments.of("1.14.1", true), Arguments.of("^2.2.0", true), Arguments.of("3.0.0", true),
                Arguments.of("workspace:^2.3.0", true), Arguments.of("2.3.0", false),
                Arguments.of("^2.4.0", false), Arguments.of("~2.8.1", false));
    }

    @Test
    void marksOverridesResolutionsAndExternalTypes() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadManifestRisks()),
                json("{\"dependencies\":{\"ng2-file-upload\":\"10.0.0\",\"@types/ng2-file-upload\":\"1.4.0\"},\"overrides\":{\"ng2-file-upload\":\"4.0.0\"},\"resolutions\":{\"ng2-file-upload\":\"3.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "override/resolution independently owns");
                            assertContains(printed, "publishes its own declarations");
                        })));
    }

    @Test
    void lookalikesOtherJsonAndExcludedParentsDoNotActivate() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadManifestRisks()),
                json("{\"engines\":{\"node\":\"18\"},\"dependencies\":{\"ng2-file-upload-extra\":\"4.0.0\",\"@angular/core\":\"15.0.0\"}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"ng2-file-upload\":\"4.0.0\"}}", source -> source.path("fixture.json")),
                json("{\"dependencies\":{\"ng2-file-upload\":\"4.0.0\"}}", source -> source.path("generated/package.json")));
    }

    @Test
    void markerIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadManifestRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"ng2-file-upload\":\">=4\"}}", source -> source.path("package.json")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            String message = "outside the exact workbook AUTO target";
                            assertTrue(printed.indexOf(message) == printed.lastIndexOf(message), printed);
                        })));
    }

    private void assertManifestMarker(String manifest, String message) {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadManifestRisks()),
                json(manifest, source -> source.path("package.json").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    private void assertNoMarker(String manifest, String message) {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadManifestRisks()),
                json(manifest, source -> source.path("package.json").afterRecipe(after ->
                        assertFalse(after.printAll().contains(message), after.printAll()))));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
