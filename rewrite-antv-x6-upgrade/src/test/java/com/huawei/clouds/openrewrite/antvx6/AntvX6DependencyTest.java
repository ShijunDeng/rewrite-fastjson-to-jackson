package com.huawei.clouds.openrewrite.antvx6;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class AntvX6DependencyTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.antvx6.UpgradeAntvX6To3_1_7";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.30.0", "1.31.0", "1.34.14", "1.34.5", "2.0.2", "2.11.1", "2.11.3"})
    void upgradesEveryXlsxExactSource(String version) {
        rewriteRun(json("{\"dependencies\":{\"@antv/x6\":\"" + version + "\"}}",
                "{\"dependencies\":{\"@antv/x6\":\"3.1.7\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.30.0", "1.31.0", "1.34.14", "1.34.5", "2.0.2", "2.11.1", "2.11.3"})
    void preservesCaretStyleForEveryXlsxSource(String version) {
        rewriteRun(json("{\"dependencies\":{\"@antv/x6\":\"^" + version + "\"}}",
                "{\"dependencies\":{\"@antv/x6\":\"^3.1.7\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.30.0", "1.31.0", "1.34.14", "1.34.5", "2.0.2", "2.11.1", "2.11.3"})
    void preservesTildeStyleForEveryXlsxSource(String version) {
        rewriteRun(json("{\"dependencies\":{\"@antv/x6\":\"~" + version + "\"}}",
                "{\"dependencies\":{\"@antv/x6\":\"~3.1.7\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void upgradesAllFourDirectSections() {
        rewriteRun(json(
                "{\"dependencies\":{\"@antv/x6\":\"1.30.0\"},\"devDependencies\":{\"@antv/x6\":\"^1.31.0\"},\"peerDependencies\":{\"@antv/x6\":\"~2.0.2\"},\"optionalDependencies\":{\"@antv/x6\":\"2.11.3\"}}",
                "{\"dependencies\":{\"@antv/x6\":\"3.1.7\"},\"devDependencies\":{\"@antv/x6\":\"^3.1.7\"},\"peerDependencies\":{\"@antv/x6\":\"~3.1.7\"},\"optionalDependencies\":{\"@antv/x6\":\"3.1.7\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "=1.30.0", "v1.31.0", "2.0.2-beta.1", "2.11.1+company.2", ">=1.30.0", ">2.0.2",
            "1.30.0 || 2.11.3", "1.31.0 - 2.11.1", "2.x", "*", "latest", "${X6_VERSION}",
            "workspace:^2.11.3", "npm:@example/x6@2.11.1", "file:../x6", "link:../x6",
            "github:antvis/X6#v2.11.3", "git+https://github.com/antvis/X6.git#v2.11.3",
            "https://example.test/x6.tgz", "1.29.9", "2.11.2", "3.1.7", "^3.1.7", "~3.1.7", "4.0.0"
    })
    void leavesComplexPrereleaseDynamicProtocolAndUnlistedDeclarations(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"@antv/x6\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void ignoresOverridesScriptsLocksOtherJsonAndSimilarPackages() {
        rewriteRun(
                json("{\"overrides\":{\"@antv/x6\":\"2.11.3\"},\"resolutions\":{\"@antv/x6\":\"2.0.2\"},\"scripts\":{\"x6\":\"echo 1.30.0\"},\"dependencies\":{\"@antv/x6-react-shape\":\"2.11.3\",\"@example/x6\":\"2.0.2\"}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"@antv/x6\":\"2.11.3\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"@antv/x6\":\"1.30.0\"}}",
                        source -> source.path("fixtures/dependencies.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"node_modules/demo/package.json", "dist/package.json", "build/app/package.json",
            "generated/package.json", "install/package.json", "target/package.json", ".yarn/cache/package.json",
            ".mvn/package.json", ".m2/package.json", "vendor/package.json"})
    void excludesGeneratedAndInstalledTrees(String path) {
        rewriteRun(json("{\"dependencies\":{\"@antv/x6\":\"2.11.3\"}}", source -> source.path(path)));
    }

    @Test
    void sourceWhitelistIsExactAndPackageVisible() {
        assertEquals(Set.of("1.30.0", "1.31.0", "1.34.14", "1.34.5", "2.0.2", "2.11.1", "2.11.3"),
                AntvX6Support.SOURCES);
    }

    @Test
    void strictRecipeIsIdempotentDiscoverableAndValid() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@antv/x6\":\"~2.11.1\"}}",
                        "{\"dependencies\":{\"@antv/x6\":\"~3.1.7\"}}",
                        source -> source.path("package.json")));
        Recipe recipe = environment().activateRecipes(UPGRADE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> UPGRADE.equals(candidate.getName())));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.antvx6")
                .scanYamlResources()
                .build();
    }
}
