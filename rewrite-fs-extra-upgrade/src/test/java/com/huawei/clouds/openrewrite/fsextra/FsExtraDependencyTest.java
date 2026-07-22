package com.huawei.clouds.openrewrite.fsextra;

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

class FsExtraDependencyTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.fsextra.UpgradeFsExtraTo11_3_4";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.1.0", "9.1.0", "10.0.0", "10.0.1", "10.1.0", "11.1.1"})
    void upgradesEveryXlsxExactSource(String version) {
        rewriteRun(json("{\"dependencies\":{\"fs-extra\":\"" + version + "\"}}",
                "{\"dependencies\":{\"fs-extra\":\"11.3.4\"}}", source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.1.0", "9.1.0", "10.0.0", "10.0.1", "10.1.0", "11.1.1"})
    void preservesCaretForEveryXlsxSource(String version) {
        rewriteRun(json("{\"dependencies\":{\"fs-extra\":\"^" + version + "\"}}",
                "{\"dependencies\":{\"fs-extra\":\"^11.3.4\"}}", source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.1.0", "9.1.0", "10.0.0", "10.0.1", "10.1.0", "11.1.1"})
    void preservesTildeForEveryXlsxSource(String version) {
        rewriteRun(json("{\"dependencies\":{\"fs-extra\":\"~" + version + "\"}}",
                "{\"dependencies\":{\"fs-extra\":\"~11.3.4\"}}", source -> source.path("package.json")));
    }

    @Test
    void upgradesAllFourDirectSections() {
        rewriteRun(json(
                "{\"dependencies\":{\"fs-extra\":\"8.1.0\"},\"devDependencies\":{\"fs-extra\":\"^9.1.0\"},\"peerDependencies\":{\"fs-extra\":\"~10.1.0\"},\"optionalDependencies\":{\"fs-extra\":\"11.1.1\"}}",
                "{\"dependencies\":{\"fs-extra\":\"11.3.4\"},\"devDependencies\":{\"fs-extra\":\"^11.3.4\"},\"peerDependencies\":{\"fs-extra\":\"~11.3.4\"},\"optionalDependencies\":{\"fs-extra\":\"11.3.4\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "=8.1.0", "v9.1.0", "10.0.0-beta.1", "10.1.0+company.1", ">=8.1.0", ">10.0.0",
            "8.1.0 || 11.1.1", "9.1.0 - 11.1.1", "10.x", "*", "latest", "${FS_EXTRA_VERSION}",
            "workspace:^10.1.0", "npm:@example/fs-extra@10.1.0", "file:../fs-extra", "link:../fs-extra",
            "github:jprichardson/node-fs-extra#10.1.0", "git+https://github.com/jprichardson/node-fs-extra.git#10.1.0",
            "https://example.test/fs-extra.tgz", "8.0.1", "9.0.1", "10.0.2", "11.0.0", "11.3.4",
            "^11.3.4", "12.0.0"
    })
    void leavesComplexPrereleaseDynamicProtocolAndUnlistedDeclarations(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"fs-extra\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void ignoresOverridesResolutionsScriptsLocksOtherJsonAndSimilarPackages() {
        rewriteRun(
                json("{\"overrides\":{\"fs-extra\":\"10.1.0\"},\"resolutions\":{\"fs-extra\":\"11.1.1\"},\"scripts\":{\"copy\":\"fs-extra 10.1.0\"},\"dependencies\":{\"@types/fs-extra\":\"10.1.0\",\"fs-extra-promise\":\"8.1.0\"}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"fs-extra\":\"10.1.0\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"fs-extra\":\"9.1.0\"}}",
                        source -> source.path("fixtures/dependencies.json")));
    }

    @Test
    void ignoresNestedObjectsNamedLikeDirectDependencySections() {
        rewriteRun(json("{\"tool\":{\"dependencies\":{\"fs-extra\":\"10.1.0\"}}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"node_modules/pkg/package.json", "vendor/fs-extra/package.json", "dist/package.json",
            "build/package.json", "generated/package.json", "install/package.json", "target/package.json",
            ".mvn/package.json", ".m2/cache/package.json", ".yarn/cache/package.json"})
    void excludesInstalledGeneratedAndBuiltTrees(String path) {
        rewriteRun(json("{\"dependencies\":{\"fs-extra\":\"10.1.0\"}}", source -> source.path(path)));
    }

    @Test
    void exactWhitelistIsPackageVisible() {
        assertEquals(Set.of("8.1.0", "9.1.0", "10.0.0", "10.0.1", "10.1.0", "11.1.1"),
                FsExtraSupport.SOURCES);
    }

    @Test
    void strictRecipeIsDiscoverableValidAndIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"fs-extra\":\"~10.0.1\"}}",
                        "{\"dependencies\":{\"fs-extra\":\"~11.3.4\"}}",
                        source -> source.path("package.json")));
        Recipe recipe = environment().activateRecipes(UPGRADE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> UPGRADE.equals(candidate.getName())));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.fsextra")
                .scanYamlResources()
                .build();
    }
}
