package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class NgxTranslateProjectRiskTest implements RewriteTest {
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.ngxtranslate.AuditNgxTranslate17Project";

    @ParameterizedTest(name = "unresolved core declaration: {0}")
    @ValueSource(strings = {
            "10.0.0", "11.0.0", "11.0.2", "12.0.0", "13.0.1", "14.0.1", "15.0.1", "16.0.0",
            "^16.0.0", "~18.0.0", "18.0.0", "latest", "next", "catalog:frontend", "workspace:^",
            "npm:@vendor/core@15.0.0", "file:../core", "${ngxVersion}", ">=14 <18", "14.0.0 || 15.0.0"
    })
    void marksEveryUnresolvedDirectCoreDeclaration(String declaration) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("was not changed"), document::printAll)))
        );
    }

    @ParameterizedTest(name = "old Angular baseline: {0}")
    @ValueSource(strings = {
            "@angular/core", "@angular/common", "@angular/platform-browser", "@angular/platform-server",
            "@angular/cli", "@angular/compiler-cli", "@angular-devkit/build-angular"
    })
    void marksEveryRelevantAngularPackageBelowSixteen(String angularPackage) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\",\"" + angularPackage + "\":\"^15.2.10\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("declares Angular common/core >=16"), document::printAll)))
        );
    }

    @ParameterizedTest(name = "old TypeScript baseline: {0}")
    @ValueSource(strings = {"3.9.10", "^4.0.8", "~4.8.4", "4.8.99", "latest", "workspace:^"})
    void marksOldOrUnresolvedTypeScriptBaseline(String version) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\"},\"devDependencies\":{\"typescript\":\"" + version + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("old TypeScript version"), document::printAll)))
        );
    }

    @ParameterizedTest(name = "safe manifest matrix: {0}")
    @MethodSource("safeManifestCases")
    void leavesSupportedBaselinesAndUnrelatedManifestsUnmarked(String label, String manifest) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json(manifest, source -> source.path("package.json"))
        );
    }

    static Stream<Arguments> safeManifestCases() {
        return Stream.of(
                Arguments.of("target only", "{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\"}}"),
                Arguments.of("angular 16", "{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\",\"@angular/core\":\"16.0.0\"}}"),
                Arguments.of("angular 20", "{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\",\"@angular/common\":\"^20.1.0\"}}"),
                Arguments.of("typescript 4.9", "{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\"},\"devDependencies\":{\"typescript\":\"4.9.0\"}}"),
                Arguments.of("typescript 5", "{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\"},\"devDependencies\":{\"typescript\":\"~5.8.2\"}}"),
                Arguments.of("no core", "{\"dependencies\":{\"@angular/core\":\"15.2.0\",\"typescript\":\"4.8.0\"}}"),
                Arguments.of("core only override", "{\"overrides\":{\"@ngx-translate/core\":\"15.0.0\"},\"dependencies\":{\"@angular/core\":\"15.2.0\"}}")
        );
    }

    @Test
    void marksHttpLoaderAlignmentInFixedRealManifest() {
        // ShahidBaig/eSyncMate_V2@8478a96267fb692985c70e32f5dde0544209d6a5.
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@angular/core\":\"^16.2.0\",\"@ngx-translate/core\":\"17.0.0\",\"@ngx-translate/http-loader\":\"^8.0.0\"}}",
                        source -> source.path("UI/package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("Align @ngx-translate/http-loader"), document::printAll)))
        );
    }

    @ParameterizedTest(name = "internal manifest value: {0}")
    @ValueSource(strings = {
            "node_modules/@ngx-translate/core/lib/translate.service.d.ts",
            "@ngx-translate/core/lib/translate.service",
            "@ngx-translate/core/src/lib/translate.service"
    })
    void marksInternalPackagePathsInManifestValues(String path) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\"},\"config\":{\"entry\":\"" + path + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("references ngx-translate package internals"), document::printAll)))
        );
    }

    @ParameterizedTest(name = "tsconfig risk: {0}")
    @MethodSource("tsconfigRisks")
    void marksTypeScriptConfigurationRisks(String label, String config, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json(config, source -> source.path("tsconfig." + label + ".json").after(actual -> actual)
                        .afterRecipe(document -> assertTrue(document.printAll().contains(message), document::printAll)))
        );
    }

    static Stream<Arguments> tsconfigRisks() {
        return Stream.of(
                Arguments.of("deep-node-modules", "{\"compilerOptions\":{\"paths\":{\"i18n\":[\"node_modules/@ngx-translate/core/lib/index\"]}}}", "pins ngx-translate internals"),
                Arguments.of("deep-lib", "{\"compilerOptions\":{\"paths\":{\"i18n\":[\"@ngx-translate/core/lib/public-api\"]}}}", "pins ngx-translate internals"),
                Arguments.of("deep-src", "{\"compilerOptions\":{\"paths\":{\"i18n\":[\"@ngx-translate/core/src/index\"]}}}", "pins ngx-translate internals"),
                Arguments.of("strict", "{\"compilerOptions\":{\"strict\":false,\"types\":[\"@ngx-translate/core\"]}}", "run a strict typecheck"),
                Arguments.of("classic", "{\"compilerOptions\":{\"moduleResolution\":\"classic\",\"types\":[\"@ngx-translate/core\"]}}", "Classic module resolution")
        );
    }

    @Test
    void leavesJsonConfigWithoutCoreReferenceUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"compilerOptions\":{\"strict\":false,\"moduleResolution\":\"classic\"}}",
                        source -> source.path("tsconfig.json")),
                json("{\"compilerOptions\":{\"paths\":{\"i18n\":[\"@other/i18n/lib/index\"]}}}",
                        source -> source.path("jsconfig.json"))
        );
    }

    @Test
    void leavesNestedDependencyLikeObjectsUnmarked() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\"},\"custom\":{\"dependencies\":{\"@angular/core\":\"15.2.0\",\"typescript\":\"4.8.4\"}}}",
                        source -> source.path("package.json"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"dist/package.json", "generated/package.json", "node_modules/pkg/package.json"})
    void leavesGeneratedManifestRisksUnmarked(String path) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"16.0.0\",\"@angular/core\":\"15.2.0\"}}",
                        source -> source.path(path))
        );
    }

    @Test
    void leavesGeneratedTypeScriptConfigUnmarked() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json("{\"compilerOptions\":{\"strict\":false,\"types\":[\"@ngx-translate/core\"]}}",
                        source -> source.path("build/tsconfig.generated.json"))
        );
    }

    @Test
    void projectRiskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"16.0.0\",\"@angular/core\":\"15.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual))
        );
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxtranslate")
                .scanYamlResources()
                .build();
    }
}
