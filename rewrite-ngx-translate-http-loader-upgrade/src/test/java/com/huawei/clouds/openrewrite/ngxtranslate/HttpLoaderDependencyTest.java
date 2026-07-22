package com.huawei.clouds.openrewrite.ngxtranslate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.json.Assertions.json;

class HttpLoaderDependencyTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateHttpLoaderTo17";

    @ParameterizedTest(name = "strict declaration {0}")
    @MethodSource("selected")
    void upgradesOnlyExactCaretAndTildeSpreadsheetVersions(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"" + declaration + "\"}}",
                        "{\"dependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\"}}",
                        source -> source.path("package.json")));
    }

    static Stream<String> selected() {
        return Stream.of("4.0.0", "6.0.0", "7.0.0", "8.0.0")
                .flatMap(version -> Stream.of(version, "^" + version, "~" + version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dependencies", "devDependencies", "peerDependencies", "optionalDependencies"})
    void upgradesEveryDirectDependencySection(String section) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"" + section + "\":{\"@ngx-translate/http-loader\":\"^8.0.0\"}}",
                        "{\"" + section + "\":{\"@ngx-translate/http-loader\":\"17.0.0\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "real manifest {0}")
    @MethodSource("realManifests")
    void upgradesFixedCommitRealRepositoryManifests(String label, String path, String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json(before, after, source -> source.path(path)));
    }

    static Stream<Arguments> realManifests() {
        return Stream.of(
                // ShahidBaig/eSyncMate_V2@8478a96267fb692985c70e32f5dde0544209d6a5
                Arguments.of("eSyncMate", "UI/package.json",
                        "{\"dependencies\":{\"@angular/core\":\"^16.2.4\",\"@ngx-translate/core\":\"^15.0.0\",\"@ngx-translate/http-loader\":\"^8.0.0\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"^16.2.4\",\"@ngx-translate/core\":\"^15.0.0\",\"@ngx-translate/http-loader\":\"17.0.0\"}}"),
                // Kurarion/Genshin-Calc@c7dd4d850db8523e33302e98d71d9e180605bd4e
                Arguments.of("Genshin-Calc", "package.json",
                        "{\"dependencies\":{\"@angular/core\":\"^14.2.12\",\"@ngx-translate/core\":\"^14.0.0\",\"@ngx-translate/http-loader\":\"^7.0.0\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"^14.2.12\",\"@ngx-translate/core\":\"^14.0.0\",\"@ngx-translate/http-loader\":\"17.0.0\"}}"),
                // FeDi20-03/TuniSalesGateway@40e99bedf123169767fbcf7200f4e2e0a94eb402
                Arguments.of("TuniSalesGateway", "package.json",
                        "{\"dependencies\":{\"@angular/core\":\"14.2.0\",\"@ngx-translate/core\":\"14.0.0\",\"@ngx-translate/http-loader\":\"7.0.0\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"14.2.0\",\"@ngx-translate/core\":\"14.0.0\",\"@ngx-translate/http-loader\":\"17.0.0\"}}")
        );
    }

    @ParameterizedTest(name = "no-op declaration {0}")
    @ValueSource(strings = {
            "=4.0.0", "v4.0.0", "^v4.0.0", "=6.0.0", "v7.0.0", "^v8.0.0",
            ">=4.0.0", ">=4.0.0 <17", "4.0.0 || 8.0.0", "4.0.0 - 8.0.0",
            "4.x", "8.*", "*", " 8.0.0", "8.0.0 ", "8", "8.0", "latest", "next", "",
            "4.0.0-beta.1", "8.0.0+vendor.1", "^8.0.0-rc.1",
            "workspace:^", "workspace:8.0.0", "npm:@vendor/http-loader@8.0.0", "catalog:",
            "catalog:frontend", "${httpLoaderVersion}", "{{httpLoaderVersion}}", "file:../http-loader",
            "link:../http-loader", "portal:../http-loader", "github:ngx-translate/core#v8.0.0",
            "git+https://github.com/ngx-translate/core.git#v8.0.0",
            "https://registry.example/http-loader-8.0.0.tgz", "../vendor/http-loader.tgz"
    })
    void leavesComplexProtocolAndUnsafePrefixFormsUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "unlisted {0}")
    @ValueSource(strings = {
            "3.0.0", "3.0.1", "4.0.1", "5.0.0", "5.1.0", "6.0.1", "6.1.0", "7.0.1",
            "7.1.0", "8.0.1", "9.0.0", "10.0.0", "16.0.0", "17.0.0", "^17.0.0",
            "17.0.1", "18.0.0", "99.0.0", "0.0.0"
    })
    void leavesUnlistedTargetAndNewerScalarsUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"overrides", "resolutions", "pnpm", "custom", "scripts", "peerDependenciesMeta"})
    void leavesNonDirectLocationsUntouched(String section) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"" + section + "\":{\"@ngx-translate/http-loader\":\"8.0.0\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"package-lock.json", "npm-shrinkwrap.json", "pnpm-lock.json", "fixture.json",
            "config.json", "package.json.bak", "Package.json", "package-json"})
    void leavesLockfilesFixturesAndNearNamesUntouched(String path) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"8.0.0\"}}",
                        source -> source.path(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"@ngx-translate/core", "@vendor/ngx-translate-http-loader", "ngx-translate-http-loader",
            "@ngx-translate/http-loader-extra", "@types/ngx-translate-http-loader", "@ngx-translate/http-loader/"})
    void leavesCompanionAndSimilarPackagesUntouched(String name) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"" + name + "\":\"8.0.0\"}}", source -> source.path("package.json")));
    }

    @Test
    void upgradesWorkspaceChildAndPreservesFormatting() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"private\":true,\"workspaces\":[\"apps/*\"]}", source -> source.path("package.json")),
                json("{\"config\":{\"dependencies\":{\"@ngx-translate/http-loader\":\"8.0.0\"}}}",
                        source -> source.path("nested/package.json")),
                json("{\"dependencies\":{\"@ngx-translate/http-loader\":\"8.0.0\"}}",
                        source -> source.path("node_modules/example/package.json")),
                json("{\n  \"dependencies\" : {\n    \"@ngx-translate/http-loader\" : \"~6.0.0\",\n    \"@ngx-translate/core\" : \"13.0.0\"\n  }\n}\n",
                        "{\n  \"dependencies\" : {\n    \"@ngx-translate/http-loader\" : \"17.0.0\",\n    \"@ngx-translate/core\" : \"13.0.0\"\n  }\n}\n",
                        source -> source.path("apps/admin/package.json")));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"optionalDependencies\":{\"@ngx-translate/http-loader\":\"^7.0.0\"}}",
                        "{\"optionalDependencies\":{\"@ngx-translate/http-loader\":\"17.0.0\"}}",
                        source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxtranslate")
                .scanYamlResources()
                .build();
    }
}
