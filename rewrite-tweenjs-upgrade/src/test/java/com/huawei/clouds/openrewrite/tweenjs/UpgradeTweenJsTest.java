package com.huawei.clouds.openrewrite.tweenjs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.json.Assertions.json;

class UpgradeTweenJsTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.tweenjs.UpgradeTweenJsTo23_1_1";

    @ParameterizedTest(name = "upgrades strict spreadsheet declaration {0}")
    @MethodSource("selectedDeclarations")
    void upgradesOnlyExactCaretAndTildeSpreadsheetVersions(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@tweenjs/tween.js\":\"" + declaration + "\"}}",
                        "{\"dependencies\":{\"@tweenjs/tween.js\":\"23.1.1\"}}",
                        source -> source.path("package.json")));
    }

    static Stream<String> selectedDeclarations() {
        return Stream.of("19.0.0", "^19.0.0", "~19.0.0", "20.0.3", "^20.0.3", "~20.0.3");
    }

    @ParameterizedTest(name = "upgrades direct dependency section {0}")
    @ValueSource(strings = {"dependencies", "devDependencies", "peerDependencies", "optionalDependencies"})
    void upgradesEveryDirectDependencySection(String section) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"" + section + "\":{\"@tweenjs/tween.js\":\"^20.0.3\"}}",
                        "{\"" + section + "\":{\"@tweenjs/tween.js\":\"23.1.1\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void preservesJson5ValueQuoteStyle() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{'dependencies':{'@tweenjs/tween.js':'19.0.0'}}",
                        "{'dependencies':{'@tweenjs/tween.js':'23.1.1'}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "real repository manifest {0}")
    @MethodSource("realRepositoryManifests")
    void upgradesReducedFixedCommitRealRepositoryManifests(String path, String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json(before, after, source -> source.path(path)));
    }

    static Stream<Arguments> realRepositoryManifests() {
        return Stream.of(
                // awslabs/iot-app-kit@f38251529912f65e4994b6a19fd035a29dd9d8c4
                Arguments.of("packages/scene-composer/package.json",
                        "{\"name\":\"@iot-app-kit/scene-composer\",\"type\":\"module\",\"dependencies\":{\"@tweenjs/tween.js\":\"^20.0.3\",\"three\":\"0.151.3\"},\"devDependencies\":{\"typescript\":\"^5.5.4\"}}",
                        "{\"name\":\"@iot-app-kit/scene-composer\",\"type\":\"module\",\"dependencies\":{\"@tweenjs/tween.js\":\"23.1.1\",\"three\":\"0.151.3\"},\"devDependencies\":{\"typescript\":\"^5.5.4\"}}"),
                // hululuuuuu/GlobeStream3D@ba75e68c1575673cbbb1d2edc89ff7c28586d4db
                Arguments.of("package.json",
                        "{\"name\":\"earth-flyline\",\"type\":\"module\",\"dependencies\":{\"@tweenjs/tween.js\":\"^20.0.3\",\"three\":\"^0.152.2\"},\"devDependencies\":{\"typescript\":\"^4.6.4\",\"vite\":\"^3.2.3\"}}",
                        "{\"name\":\"earth-flyline\",\"type\":\"module\",\"dependencies\":{\"@tweenjs/tween.js\":\"23.1.1\",\"three\":\"^0.152.2\"},\"devDependencies\":{\"typescript\":\"^4.6.4\",\"vite\":\"^3.2.3\"}}"),
                // mikemklee/three-viewcube@036db7b9e74c23fcb2dc6c7cb72d7220504ca558
                Arguments.of("package.json",
                        "{\"name\":\"three-viewcube\",\"peerDependencies\":{\"@tweenjs/tween.js\":\"^19.0.0\",\"three\":\"^0.174.0\"},\"devDependencies\":{\"typescript\":\"^5.0.3\"}}",
                        "{\"name\":\"three-viewcube\",\"peerDependencies\":{\"@tweenjs/tween.js\":\"23.1.1\",\"three\":\"^0.174.0\"},\"devDependencies\":{\"typescript\":\"^5.0.3\"}}"),
                // UBA-GCOEN/StichHub@1eb512f98f1f76cab581ceda39b9f89fbfb4547b
                Arguments.of("client/StichHub/package.json",
                        "{\"name\":\"stichhub\",\"type\":\"module\",\"dependencies\":{\"@react-three/fiber\":\"^8.12.0\",\"@tweenjs/tween.js\":\"^19.0.0\",\"react\":\"^18.2.0\",\"three\":\"^0.151.2\"}}",
                        "{\"name\":\"stichhub\",\"type\":\"module\",\"dependencies\":{\"@react-three/fiber\":\"^8.12.0\",\"@tweenjs/tween.js\":\"23.1.1\",\"react\":\"^18.2.0\",\"three\":\"^0.151.2\"}}")
        );
    }

    @ParameterizedTest(name = "leaves complex/protocol declaration {0}")
    @ValueSource(strings = {
            ">=19.0.0", ">=19.0.0 <21", "19.0.0 || 20.0.3", "19.0.0 - 20.0.3",
            "=19.0.0", "v19.0.0", "^v20.0.3", " 20.0.3", "20.0.3 ",
            "19", "19.0", "20.x", "*", "latest", "next", "beta", "",
            "19.0.0-beta.1", "20.0.3+company.5", "^20.0.3-beta.1",
            "workspace:^", "workspace:~20.0.3", "workspace:20.0.3",
            "npm:@company/tween@20.0.3", "catalog:", "catalog:tween", "${tweenVersion}",
            "file:../tween", "link:../tween", "portal:../tween",
            "github:tweenjs/tween.js#v20.0.3", "git+https://github.com/tweenjs/tween.js.git#v20.0.3",
            "https://registry.example/tween-20.0.3.tgz", "../vendor/tween.tgz"
    })
    void leavesEveryNonStrictNpmSpecUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@tweenjs/tween.js\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves unlisted scalar {0}")
    @ValueSource(strings = {
            "18.6.4", "^18.6.4", "~18.6.4", "19.0.1", "^19.1.0", "20.0.0", "20.0.2",
            "20.0.4", "21.0.0", "22.0.0", "23.0.0", "23.1.0", "23.1.1", "^23.1.1",
            "24.0.0", "^99.0.0", "0.0.0"
    })
    void leavesUnlistedTargetAndNewerScalarsUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@tweenjs/tween.js\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves non-direct location {0}")
    @ValueSource(strings = {"overrides", "resolutions", "pnpm", "custom", "scripts", "engines"})
    void leavesNonDirectTopLevelLocationsUntouched(String location) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"" + location + "\":{\"@tweenjs/tween.js\":\"19.0.0\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves non-manifest file {0}")
    @ValueSource(strings = {
            "package-lock.json", "npm-shrinkwrap.json", "pnpm-lock.json", "yarn.lock.json",
            "config.json", "fixture.json", "package.json.bak", "package-json", "Package.json"
    })
    void leavesLocksFixturesBackupsAndNearNamesUntouched(String path) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"@tweenjs/tween.js\":\"19.0.0\"}}",
                        source -> source.path(path)));
    }

    @ParameterizedTest(name = "leaves similar package {0}")
    @ValueSource(strings = {
            "tween.js", "@types/tween.js", "@tweenjs/tween", "@tweenjs/tween.js-extra",
            "@company/tween.js", "tweenjs", "@tweenjs/tween.js/"
    })
    void leavesSimilarPackageNamesUntouched(String name) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"" + name + "\":\"19.0.0\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void upgradesNestedWorkspaceManifestWithoutChangingRootOrFormatting() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\n  \"name\" : \"root\",\n  \"private\" : true,\n  \"workspaces\" : [\"packages/*\"]\n}\n",
                        source -> source.path("package.json")),
                json("{\n    \"dependencies\" : {\n        \"three\" : \"0.152.2\",\n        \"@tweenjs/tween.js\" : \"~19.0.0\"\n    }\n}\n",
                        "{\n    \"dependencies\" : {\n        \"three\" : \"0.152.2\",\n        \"@tweenjs/tween.js\" : \"23.1.1\"\n    }\n}\n",
                        source -> source.path("packages/animation/package.json")));
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"optionalDependencies\":{\"@tweenjs/tween.js\":\"^20.0.3\"}}",
                        "{\"optionalDependencies\":{\"@tweenjs/tween.js\":\"23.1.1\"}}",
                        source -> source.path("package.json")));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.tweenjs")
                .scanYamlResources().build();
    }
}
