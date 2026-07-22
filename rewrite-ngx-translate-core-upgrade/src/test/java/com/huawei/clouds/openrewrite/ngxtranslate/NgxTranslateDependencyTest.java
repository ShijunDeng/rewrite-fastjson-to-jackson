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

class NgxTranslateDependencyTest implements RewriteTest {
    private static final String UPGRADE = "com.huawei.clouds.openrewrite.ngxtranslate.UpgradeNgxTranslateCoreTo17";

    @ParameterizedTest(name = "upgrades strict declaration {0}")
    @MethodSource("selected")
    void upgradesOnlyExactCaretAndTildeSpreadsheetVersions(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"" + declaration + "\"}}",
                        "{\"dependencies\":{\"@ngx-translate/core\":\"17.0.0\"}}",
                        source -> source.path("package.json")));
    }

    static Stream<String> selected() {
        return Stream.of("11.0.1", "13.0.0", "14.0.0", "15.0.0")
                .flatMap(version -> Stream.of(version, "^" + version, "~" + version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dependencies", "devDependencies", "peerDependencies", "optionalDependencies"})
    void upgradesEveryDirectDependencySection(String section) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"" + section + "\":{\"@ngx-translate/core\":\"^15.0.0\"}}",
                        "{\"" + section + "\":{\"@ngx-translate/core\":\"17.0.0\"}}",
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
                        "{\"dependencies\":{\"@angular/core\":\"^16.2.0\",\"@ngx-translate/core\":\"^15.0.0\",\"@ngx-translate/http-loader\":\"^8.0.0\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"^16.2.0\",\"@ngx-translate/core\":\"17.0.0\",\"@ngx-translate/http-loader\":\"^8.0.0\"}}"),
                // Kurarion/Genshin-Calc@c7dd4d850db8523e33302e98d71d9e180605bd4e
                Arguments.of("Genshin-Calc", "package.json",
                        "{\"dependencies\":{\"@angular/core\":\"^15.2.0\",\"@ngx-translate/core\":\"^14.0.0\",\"@ngx-translate/http-loader\":\"^7.0.0\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"^15.2.0\",\"@ngx-translate/core\":\"17.0.0\",\"@ngx-translate/http-loader\":\"^7.0.0\"}}"),
                // FeDi20-03/TuniSalesGateway@40e99bedf123169767fbcf7200f4e2e0a94eb402
                Arguments.of("TuniSalesGateway", "package.json",
                        "{\"dependencies\":{\"@angular/common\":\"15.2.10\",\"@ngx-translate/core\":\"14.0.0\"}}",
                        "{\"dependencies\":{\"@angular/common\":\"15.2.10\",\"@ngx-translate/core\":\"17.0.0\"}}")
        );
    }

    @ParameterizedTest(name = "no-op declaration {0}")
    @ValueSource(strings = {
            "=11.0.1", "v11.0.1", "^v11.0.1", "=13.0.0", "v14.0.0", "^v15.0.0",
            ">=11.0.1", ">=11.0.1 <17", "11.0.1 || 15.0.0", "11.0.1 - 15.0.0",
            "11.x", "15.*", "*", " 15.0.0", "15.0.0 ", "15", "15.0", "latest", "next", "",
            "11.0.1-beta.1", "15.0.0+vendor.1", "^15.0.0-rc.1",
            "workspace:^", "workspace:15.0.0", "npm:@vendor/core@15.0.0", "catalog:", "catalog:frontend",
            "${ngxTranslateVersion}", "{{ngxTranslateVersion}}", "file:../core", "link:../core", "portal:../core",
            "github:ngx-translate/core#v15.0.0", "git+https://github.com/ngx-translate/core.git#v15.0.0",
            "https://registry.example/core-15.0.0.tgz", "../vendor/core.tgz"
    })
    void leavesComplexProtocolAndNonWhitelistFormsUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "unlisted {0}")
    @ValueSource(strings = {
            "10.0.0", "11.0.0", "11.0.2", "12.0.0", "13.0.1", "13.1.0", "14.0.1",
            "14.1.0", "15.0.1", "16.0.0", "16.0.1", "17.0.0", "^17.0.0", "17.0.1",
            "18.0.0", "19.0.0", "99.0.0", "0.0.0"
    })
    void leavesUnlistedTargetAndNewerScalarsUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"overrides", "resolutions", "pnpm", "custom", "scripts", "peerDependenciesMeta"})
    void leavesNonDirectLocationsUntouched(String section) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"" + section + "\":{\"@ngx-translate/core\":\"15.0.0\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void leavesNestedDependencyLikeObjectsUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"custom\":{\"dependencies\":{\"@ngx-translate/core\":\"15.0.0\"}}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dist/package.json", "build/package.json", "generated/package.json",
            "node_modules/example/package.json", ".angular/cache/package.json"})
    void leavesGeneratedAndInstalledManifestsUntouched(String path) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"15.0.0\"}}",
                        source -> source.path(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"package-lock.json", "npm-shrinkwrap.json", "pnpm-lock.json", "fixture.json",
            "config.json", "package.json.bak", "Package.json", "package-json"})
    void leavesLockfileFixtureAndNearNameUntouched(String path) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@ngx-translate/core\":\"15.0.0\"}}", source -> source.path(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"@ngx-translate/http-loader", "@vendor/ngx-translate-core", "ngx-translate",
            "@ngx-translate/core-extra", "@types/ngx-translate", "@ngx-translate/core/"})
    void leavesCompanionAndSimilarPackagesUntouched(String name) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"" + name + "\":\"15.0.0\"}}", source -> source.path("package.json")));
    }

    @Test
    void upgradesWorkspaceChildAndPreservesFormatting() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"private\":true,\"workspaces\":[\"apps/*\"]}", source -> source.path("package.json")),
                json("{\n  \"dependencies\" : {\n    \"@ngx-translate/core\" : \"~13.0.0\",\n    \"@ngx-translate/http-loader\" : \"7.0.0\"\n  }\n}\n",
                        "{\n  \"dependencies\" : {\n    \"@ngx-translate/core\" : \"17.0.0\",\n    \"@ngx-translate/http-loader\" : \"7.0.0\"\n  }\n}\n",
                        source -> source.path("apps/admin/package.json")));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"optionalDependencies\":{\"@ngx-translate/core\":\"^14.0.0\"}}",
                        "{\"optionalDependencies\":{\"@ngx-translate/core\":\"17.0.0\"}}",
                        source -> source.path("package.json")));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxtranslate")
                .scanYamlResources().build();
    }
}
