package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class HttpLoaderProjectRiskTest implements RewriteTest {
    private static final String AUDIT =
            "com.huawei.clouds.openrewrite.ngxtranslate.AuditNgxTranslateHttpLoader17Project";

    @ParameterizedTest(name = "unresolved loader declaration {0}")
    @ValueSource(strings = {
            "3.0.0", "4.0.1", "5.0.0", "6.0.1", "7.0.1", "8.0.1", "9.0.0", "16.0.0",
            "^16.0.0", "~18.0.0", "18.0.0", "latest", "next", "catalog:frontend", "workspace:^",
            "npm:@vendor/http-loader@8.0.0", "file:../http-loader", "${loaderVersion}", ">=7 <18",
            "7.0.0 || 8.0.0"
    })
    void marksEveryUnresolvedDirectLoaderDeclaration(String declaration) {
        rewriteRun(
                spec -> spec.recipe(HttpLoaderDependencyTest.environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("declaration was not migrated"), document::printAll)))
        );
    }

    @ParameterizedTest(name = "misaligned core {0}")
    @ValueSource(strings = {
            "11.0.1", "^13.0.0", "~14.0.0", "15.0.0", "16.0.0", "17.0.1", "18.0.0",
            "latest", "workspace:^", "${coreVersion}"
    })
    void marksCoreLinesNotExplicitlyOnTargetSeventeen(String version) {
        rewriteRun(
                spec -> spec.recipe(HttpLoaderDependencyTest.environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\",\"@ngx-translate/core\":\"" + version + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("Align @ngx-translate/core"), document::printAll)))
        );
    }

    @ParameterizedTest(name = "old Angular package {0}")
    @ValueSource(strings = {
            "@angular/core", "@angular/common", "@angular/cli", "@angular/compiler-cli",
            "@angular-devkit/build-angular"
    })
    void marksEveryRelevantAngularPackageBelowSixteen(String angularPackage) {
        rewriteRun(
                spec -> spec.recipe(HttpLoaderDependencyTest.environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\",\"" + angularPackage + "\":\"^15.2.10\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("requires Angular common/core >=16"), document::printAll)))
        );
    }

    @ParameterizedTest(name = "old TypeScript {0}")
    @ValueSource(strings = {"3.9.10", "^4.0.8", "~4.8.4", "4.8.99", "latest", "workspace:^"})
    void marksOldOrUnresolvedTypeScriptBaseline(String version) {
        rewriteRun(
                spec -> spec.recipe(HttpLoaderDependencyTest.environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\"},\"devDependencies\":{\"typescript\":\"" + version + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("exact TypeScript range"), document::printAll)))
        );
    }

    @ParameterizedTest(name = "supported manifest {0}")
    @MethodSource("safeManifests")
    void leavesSupportedAndUnrelatedManifestsUnmarked(String label, String manifest) {
        rewriteRun(
                spec -> spec.recipe(HttpLoaderDependencyTest.environment().activateRecipes(AUDIT)),
                json(manifest, source -> source.path("package.json"))
        );
    }

    static Stream<Arguments> safeManifests() {
        return Stream.of(
                Arguments.of("loader only", "{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\"}}"),
                Arguments.of("exact core", "{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\",\"@ngx-translate/core\":\"17.0.0\"}}"),
                Arguments.of("caret core", "{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\",\"@ngx-translate/core\":\"^17.0.0\"}}"),
                Arguments.of("tilde core", "{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\",\"@ngx-translate/core\":\"~17.0.0\"}}"),
                Arguments.of("angular 16", "{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\",\"@angular/core\":\"16.0.0\",\"@angular/common\":\"^16.2.0\"}}"),
                Arguments.of("typescript 4.9", "{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\"},\"devDependencies\":{\"typescript\":\"4.9.0\"}}"),
                Arguments.of("typescript 5", "{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\"},\"devDependencies\":{\"typescript\":\"~5.8.2\"}}"),
                Arguments.of("no loader", "{\"dependencies\":{\"@ngx-translate/core\":\"15.0.0\",\"@angular/core\":\"15.2.0\"}}"),
                Arguments.of("override only", "{\"overrides\":{\"@ngx-translate/http-loader\":\"8.0.0\"},\"dependencies\":{\"@angular/core\":\"15.2.0\"}}")
        );
    }

    @ParameterizedTest(name = "internal manifest path {0}")
    @ValueSource(strings = {
            "node_modules/@ngx-translate/http-loader/lib/http-loader.d.ts",
            "@ngx-translate/http-loader/lib/http-loader",
            "@ngx-translate/http-loader/src/lib/http-loader"
    })
    void marksInternalPackagePathsInManifestValues(String path) {
        rewriteRun(
                spec -> spec.recipe(HttpLoaderDependencyTest.environment().activateRecipes(AUDIT)),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\"},\"config\":{\"entry\":\"" + path + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("references ngx-translate HTTP loader internals"), document::printAll)))
        );
    }

    @ParameterizedTest(name = "tsconfig risk {0}")
    @MethodSource("tsconfigRisks")
    void marksTypeScriptConfigurationRisks(String label, String config, String message) {
        rewriteRun(
                spec -> spec.recipe(HttpLoaderDependencyTest.environment().activateRecipes(AUDIT)),
                json(config, source -> source.path("tsconfig." + label + ".json").after(actual -> actual)
                        .afterRecipe(document -> assertTrue(document.printAll().contains(message), document::printAll)))
        );
    }

    static Stream<Arguments> tsconfigRisks() {
        return Stream.of(
                Arguments.of("node-modules", "{\"compilerOptions\":{\"paths\":{\"loader\":[\"node_modules/@ngx-translate/http-loader/lib/index\"]}}}", "pins HTTP loader internals"),
                Arguments.of("lib", "{\"compilerOptions\":{\"paths\":{\"loader\":[\"@ngx-translate/http-loader/lib/public-api\"]}}}", "pins HTTP loader internals"),
                Arguments.of("src", "{\"compilerOptions\":{\"paths\":{\"loader\":[\"@ngx-translate/http-loader/src/index\"]}}}", "pins HTTP loader internals"),
                Arguments.of("strict", "{\"compilerOptions\":{\"strict\":false,\"types\":[\"@ngx-translate/http-loader\"]}}", "Run strict typecheck"),
                Arguments.of("classic", "{\"compilerOptions\":{\"moduleResolution\":\"classic\",\"types\":[\"@ngx-translate/http-loader\"]}}", "Classic module resolution")
        );
    }

    @Test
    void leavesJsonConfigWithoutLoaderReferenceUntouched() {
        rewriteRun(
                spec -> spec.recipe(HttpLoaderDependencyTest.environment().activateRecipes(AUDIT)),
                json("{\"compilerOptions\":{\"strict\":false,\"moduleResolution\":\"classic\"}}",
                        source -> source.path("tsconfig.json")),
                json("{\"compilerOptions\":{\"paths\":{\"loader\":[\"@other/http-loader/lib/index\"]}}}",
                        source -> source.path("jsconfig.json"))
        );
    }

    @Test
    void projectRiskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(HttpLoaderDependencyTest.environment().activateRecipes(AUDIT))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"16.0.0\",\"@angular/core\":\"15.2.0\"}}",
                        source -> source.path("package.json").after(actual -> actual))
        );
    }
}
