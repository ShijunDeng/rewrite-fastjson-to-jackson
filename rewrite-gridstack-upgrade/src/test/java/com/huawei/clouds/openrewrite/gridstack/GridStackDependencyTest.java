package com.huawei.clouds.openrewrite.gridstack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class GridStackDependencyTest implements RewriteTest {
    static final String STRICT = "com.huawei.clouds.openrewrite.gridstack.UpgradeGridStackTo12_3_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4.2.6", "5.1.1", "6.0.0", "6.0.3", "7.1.1"})
    void upgradesEveryExactSpreadsheetSource(String version) {
        rewriteRun(packageVersion(version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4.2.6", "5.1.1", "6.0.0", "6.0.3", "7.1.1"})
    void upgradesEveryCaretSpreadsheetSource(String version) {
        rewriteRun(packageVersion("^" + version));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4.2.6", "5.1.1", "6.0.0", "6.0.3", "7.1.1"})
    void upgradesEveryTildeSpreadsheetSource(String version) {
        rewriteRun(packageVersion("~" + version));
    }

    @Test
    void upgradesAllFourDirectSections() {
        rewriteRun(json(
                "{\"dependencies\":{\"gridstack\":\"4.2.6\"},\"devDependencies\":{\"gridstack\":\"^5.1.1\"},\"peerDependencies\":{\"gridstack\":\"~6.0.3\"},\"optionalDependencies\":{\"gridstack\":\"7.1.1\"}}",
                "{\"dependencies\":{\"gridstack\":\"12.3.3\"},\"devDependencies\":{\"gridstack\":\"12.3.3\"},\"peerDependencies\":{\"gridstack\":\"12.3.3\"},\"optionalDependencies\":{\"gridstack\":\"12.3.3\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void upgradesWorkspaceChildAndPreservesRoot() {
        rewriteRun(
                json("{\"private\":true,\"workspaces\":[\"apps/*\"]}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"gridstack\":\"6.0.3\"}}",
                        "{\"dependencies\":{\"gridstack\":\"12.3.3\"}}",
                        source -> source.path("apps/dashboard/package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "=4.2.6", "v5.1.1", "6.0.3-beta.1", "7.1.1+company.2",
            ">=4.2.6", ">6.0.0", "4.2.6 || 7.1.1", "5.1.1 - 7.1.1",
            "6.x", "*", "latest", "${GRIDSTACK_VERSION}", "workspace:^7.1.1",
            "npm:@example/gridstack@6.0.0", "file:../gridstack", "link:../gridstack",
            "github:gridstack/gridstack.js#v7.1.1", "git+https://github.com/gridstack/gridstack.js.git#v6.0.3",
            "https://example.test/gridstack.tgz", "4.2.5", "7.1.2", "11.0.0", "12.3.3", "13.0.0"
    })
    void leavesComplexDecoratedProtocolAndUnlistedDeclarations(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"gridstack\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void leavesOverridesLocksOtherJsonAndSimilarPackages() {
        rewriteRun(
                json("{\"overrides\":{\"gridstack\":\"6.0.3\"},\"resolutions\":{\"gridstack\":\"7.1.1\"},\"dependencies\":{\"gridstack-angular\":\"5.1.1\",\"react-gridstack\":\"6.0.0\",\"@types/gridstack\":\"4.2.6\"}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"gridstack\":\"6.0.3\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"gridstack\":\"7.1.1\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                text("gridstack@^7.1.1:\n  version 7.1.1\n", source -> source.path("yarn.lock")));
    }

    @Test
    void strictRecipeIsIdempotentDiscoverableAndValid() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"gridstack\":\"~6.0.0\"}}",
                        "{\"dependencies\":{\"gridstack\":\"12.3.3\"}}",
                        source -> source.path("package.json")));
        Recipe recipe = environment().activateRecipes(STRICT);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> STRICT.equals(candidate.getName())));
    }

    private static org.openrewrite.test.SourceSpecs packageVersion(String declaration) {
        return json("{\"dependencies\":{\"gridstack\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"gridstack\":\"12.3.3\"}}",
                source -> source.path("package.json"));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.gridstack")
                .scanYamlResources()
                .build();
    }
}
