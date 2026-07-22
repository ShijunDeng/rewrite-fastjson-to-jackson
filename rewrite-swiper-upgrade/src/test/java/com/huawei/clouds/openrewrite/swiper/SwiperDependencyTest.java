package com.huawei.clouds.openrewrite.swiper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.openrewrite.json.Assertions.json;

class SwiperDependencyTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.swiper.UpgradeSwiperDependencyTo12_1_2";

    @ParameterizedTest(name = "upgrades strict spreadsheet declaration {0}")
    @MethodSource("selectedDeclarations")
    void upgradesOnlyExactCaretAndTildeSpreadsheetVersions(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"swiper\":\"" + declaration + "\"}}",
                        "{\"dependencies\":{\"swiper\":\"12.1.2\"}}",
                        source -> source.path("package.json")));
    }

    static Stream<String> selectedDeclarations() {
        return Stream.of("3.4.2", "6.8.1", "6.8.4", "7.2.0", "8.3.1", "8.4.7", "9.1.0", "9.2.0", "9.4.1")
                .flatMap(version -> Stream.of(version, "^" + version, "~" + version));
    }

    @ParameterizedTest(name = "upgrades direct section {0}")
    @ValueSource(strings = {"dependencies", "devDependencies", "peerDependencies", "optionalDependencies"})
    void upgradesEveryDirectDependencySection(String section) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"" + section + "\":{\"swiper\":\"^8.4.7\"}}",
                        "{\"" + section + "\":{\"swiper\":\"12.1.2\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "real repository manifest {0}")
    @MethodSource("realManifests")
    void upgradesFixedCommitRealRepositoryManifests(String label, String path, String before, String after) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json(before, after, source -> source.path(path)));
    }

    static Stream<Arguments> realManifests() {
        return Stream.of(
                // akveo/nebular@f761f852e6a46d163fe2c360a04df35c31b24246
                Arguments.of("nebular", "package.json",
                        "{\"name\":\"smag\",\"dependencies\":{\"angular2-useful-swiper\":\"4.0.7\",\"swiper\":\"^3.4.2\"},\"devDependencies\":{\"typescript\":\"2.3.4\"}}",
                        "{\"name\":\"smag\",\"dependencies\":{\"angular2-useful-swiper\":\"4.0.7\",\"swiper\":\"12.1.2\"},\"devDependencies\":{\"typescript\":\"2.3.4\"}}"),
                // jellyfin/jellyfin-web@a99ac7791a7f735b3041883aa7f8948af4f5543f
                Arguments.of("jellyfin", "package.json",
                        "{\"name\":\"jellyfin-web\",\"dependencies\":{\"swiper\":\"^6.8.1\"},\"devDependencies\":{\"typescript\":\"^4.3.5\",\"webpack\":\"^5.50.0\"}}",
                        "{\"name\":\"jellyfin-web\",\"dependencies\":{\"swiper\":\"12.1.2\"},\"devDependencies\":{\"typescript\":\"^4.3.5\",\"webpack\":\"^5.50.0\"}}"),
                // numbersprotocol/capture-cam@9e33602a
                Arguments.of("capture-cam", "package.json",
                        "{\"name\":\"capture-app\",\"dependencies\":{\"@ionic/angular\":\"^5.6.13\",\"swiper\":\"^6.8.4\"}}",
                        "{\"name\":\"capture-app\",\"dependencies\":{\"@ionic/angular\":\"^5.6.13\",\"swiper\":\"12.1.2\"}}"),
                // 6c65726f79/Transmissionic@54ca0a7c264d4534a5ed6c37db12d56ecf522002
                Arguments.of("transmissionic", "package.json",
                        "{\"name\":\"transmissionic\",\"dependencies\":{\"@ionic/vue\":\"^6.6.2\",\"swiper\":\"^8.4.7\",\"vue\":\"^3.2.47\"}}",
                        "{\"name\":\"transmissionic\",\"dependencies\":{\"@ionic/vue\":\"^6.6.2\",\"swiper\":\"12.1.2\",\"vue\":\"^3.2.47\"}}"),
                // Mapuppy09/tradetrust-website@143ed9b062be33cb0db58c45518aacfb2b568ddb
                Arguments.of("tradetrust", "package.json",
                        "{\"dependencies\":{\"next\":\"13.0.6\",\"react\":\"18.2.0\",\"swiper\":\"8.4.7\"}}",
                        "{\"dependencies\":{\"next\":\"13.0.6\",\"react\":\"18.2.0\",\"swiper\":\"12.1.2\"}}"),
                // Permify/permify@b17c461d/docs/documentation/package.json
                Arguments.of("permify", "docs/documentation/package.json",
                        "{\"name\":\"documentation\",\"dependencies\":{\"docusaurus\":\"^2.3.1\",\"swiper\":\"^9.1.0\"}}",
                        "{\"name\":\"documentation\",\"dependencies\":{\"docusaurus\":\"^2.3.1\",\"swiper\":\"12.1.2\"}}")
        );
    }

    @ParameterizedTest(name = "leaves unsupported npm declaration {0}")
    @ValueSource(strings = {
            "=3.4.2", "v3.4.2", "^v3.4.2", "=6.8.1", "v6.8.4", "^v7.2.0",
            ">=6.8.4 <10", "3.4.2 || 9.4.1", "6.8.1 - 9.4.1", "9.x", "8.*", "*",
            " 9.4.1", "9.4.1 ", "9", "9.4", "latest", "next", "beta", "",
            "3.4.2-beta.1", "6.8.4+company.2", "^9.4.1-beta.1",
            "workspace:^", "workspace:9.4.1", "npm:@company/swiper@9.4.1",
            "catalog:", "catalog:swiper", "${SWIPER_VERSION}", "{{swiperVersion}}",
            "file:../swiper", "link:../swiper", "portal:../swiper",
            "github:nolimits4web/swiper#v9.4.1", "git+https://github.com/nolimits4web/swiper.git#v9.4.1",
            "https://registry.example/swiper-9.4.1.tgz", "../vendor/swiper.tgz"
    })
    void leavesComplexProtocolAndNonWhitelistFormsUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"swiper\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves unlisted scalar {0}")
    @ValueSource(strings = {
            "3.4.1", "3.4.3", "4.5.1", "5.4.5", "6.8.0", "6.8.2", "6.8.3", "6.8.5",
            "7.1.0", "7.2.1", "7.4.1", "8.3.0", "8.3.2", "8.4.6", "8.4.8", "9.0.0",
            "9.0.5", "9.1.1", "9.2.1", "9.3.2", "9.4.0", "9.4.2", "10.0.0", "11.2.10",
            "12.0.3", "12.1.0", "12.1.1", "12.1.2", "^12.1.2", "12.1.3", "13.0.0", "99.0.0"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"swiper\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves non-direct section {0}")
    @ValueSource(strings = {"overrides", "resolutions", "pnpm", "custom", "scripts", "engines", "peerDependenciesMeta"})
    void leavesNonDirectTopLevelLocationsUntouched(String section) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"" + section + "\":{\"swiper\":\"9.4.1\"}}", source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves non-manifest {0}")
    @ValueSource(strings = {
            "package-lock.json", "npm-shrinkwrap.json", "pnpm-lock.json", "yarn.lock.json",
            "fixture.json", "config.json", "package.json.bak", "Package.json", "package-json"
    })
    void leavesLockfilesFixturesAndNearNamesUntouched(String path) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"swiper\":\"9.4.1\"}}", source -> source.path(path)));
    }

    @Test
    void leavesGeneratedManifestUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"swiper\":\"9.4.1\"}}",
                        source -> source.path("dist/package.json")));
    }

    @Test
    void leavesNestedDependencyLikeObjectUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"workspaceConfig\":{\"dependencies\":{\"swiper\":\"9.4.1\"}}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves similar package {0}")
    @ValueSource(strings = {
            "swiper-react", "vue-awesome-swiper", "react-id-swiper", "Swiper", "@types/swiper",
            "@company/swiper", "swiperjs", "swiper-extra", "swiper/"
    })
    void leavesSimilarPackageNamesUntouched(String name) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\"dependencies\":{\"" + name + "\":\"9.4.1\"}}", source -> source.path("package.json")));
    }

    @Test
    void upgradesWorkspaceChildWithoutChangingRootOrFormatting() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)),
                json("{\n  \"private\" : true,\n  \"workspaces\" : [\"apps/*\"]\n}\n", source -> source.path("package.json")),
                json("{\n    \"dependencies\" : {\n        \"swiper\" : \"~8.3.1\",\n        \"react\" : \"18.2.0\"\n    }\n}\n",
                        "{\n    \"dependencies\" : {\n        \"swiper\" : \"12.1.2\",\n        \"react\" : \"18.2.0\"\n    }\n}\n",
                        source -> source.path("apps/store/package.json")));
    }

    @Test
    void strictDependencyUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"peerDependencies\":{\"swiper\":\"^9.2.0\"}}",
                        "{\"peerDependencies\":{\"swiper\":\"12.1.2\"}}", source -> source.path("package.json")));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.swiper")
                .scanYamlResources().build();
    }
}
