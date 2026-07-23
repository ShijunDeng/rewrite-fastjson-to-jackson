package com.huawei.clouds.openrewrite.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class MermaidDependencyUpgradeTest implements RewriteTest {
    static final String STRICT = "com.huawei.clouds.openrewrite.mermaid.UpgradeMermaidTo11_15_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.1.1", "9.1.3", "9.1.6", "9.4.3"})
    void upgradesEveryExactWorkbookVersion(String version) {
        assertUpgrade(version, "11.15.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.1.1", "9.1.3", "9.1.6", "9.4.3"})
    void upgradesEveryCaretWorkbookVersion(String version) {
        assertUpgrade("^" + version, "^11.15.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.1.1", "9.1.3", "9.1.6", "9.4.3"})
    void upgradesEveryTildeWorkbookVersion(String version) {
        assertUpgrade("~" + version, "~11.15.0");
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {"dependencies":{"mermaid":"9.1.1"},"devDependencies":{"mermaid":"^9.1.3"},"peerDependencies":{"mermaid":"~9.1.6"},"optionalDependencies":{"mermaid":"9.4.3"}}
                """,
                """
                {"dependencies":{"mermaid":"11.15.0"},"devDependencies":{"mermaid":"^11.15.0"},"peerDependencies":{"mermaid":"~11.15.0"},"optionalDependencies":{"mermaid":"11.15.0"}}
                """, source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=9.1.1", ">=9.1.3 <10", "<=9.4.3", "9.1.1 || 9.4.3", "9.1.1 - 9.4.3",
            "9.x", "9.1.x", "*", ">9.1.6", "<11", "^9.1.1 || ^9.4.3", "~9.1.3 >=9.1.1"
    })
    void protectsComplexSemver(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "workspace:9.1.1", "workspace:^9.1.3", "npm:@company/mermaid@9.1.6",
            "github:mermaid-js/mermaid#v9.4.3", "git+https://github.com/mermaid-js/mermaid.git#v9.4.3",
            "file:../mermaid", "link:../mermaid", "https://example.test/mermaid-9.1.1.tgz"
    })
    void protectsProtocolsAndAliases(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "v9.1.1", "=9.1.3", " 9.1.6", "9.4.3 ", "9.1.1-beta.1", "9.4.3+company.2",
            "latest", "next", "", "$mermaidVersion"
    })
    void protectsDecoratedDynamicAndCentralValues(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8.14.0", "9.1.0", "9.1.2", "9.1.4", "9.1.5", "9.1.7", "9.4.2", "9.4.4",
            "10.0.0", "11.0.0", "11.15.0", "^11.15.0", "12.0.0"
    })
    void protectsUnlistedTargetAndNewerVersions(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void protectsLockfilesOrdinaryJsonCentralOwnersAndSimilarPackages() {
        rewriteRun(
                json("{\"packages\":{\"\":{\"dependencies\":{\"mermaid\":\"9.1.1\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"mermaid\":\"9.1.3\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                json("{\"overrides\":{\"mermaid\":\"9.1.6\"},\"resolutions\":{\"mermaid\":\"9.4.3\"},\"catalog\":{\"mermaid\":\"9.1.1\"}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"@bytemd/plugin-mermaid\":\"9.1.1\",\"Mermaid\":\"9.4.3\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void protectsVendorGeneratedInstallAndCacheParentsButNotLeafNamedInstallScript() {
        rewriteRun(
                json("{\"dependencies\":{\"mermaid\":\"9.1.1\"}}", source -> source.path("vendor/app/package.json")),
                json("{\"dependencies\":{\"mermaid\":\"9.1.3\"}}", source -> source.path("generated-client/package.json")),
                json("{\"dependencies\":{\"mermaid\":\"9.1.6\"}}", source -> source.path("install-cache/package.json")),
                json("{\"dependencies\":{\"mermaid\":\"9.4.3\"}}", source -> source.path(".cache/package.json")));
    }

    @Test
    void workspaceManifestsUpgradeIndependentlyAndRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"mermaid\":\"^9.1.1\"}}",
                        "{\"dependencies\":{\"mermaid\":\"^11.15.0\"}}",
                        source -> source.path("apps/docs/package.json")),
                json("{\"devDependencies\":{\"mermaid\":\"~9.4.3\"}}",
                        "{\"devDependencies\":{\"mermaid\":\"~11.15.0\"}}",
                        source -> source.path("packages/diagrams/package.json")));
    }

    @Test
    void strictRecipeIsDiscoverableAndValid() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(STRICT);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> STRICT.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private void assertUpgrade(String declaration, String expected) {
        rewriteRun(json("{\"dependencies\":{\"mermaid\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"mermaid\":\"" + expected + "\"}}",
                source -> source.path("package.json")));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"mermaid\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.mermaid")
                .scanYamlResources().build();
    }
}
