package com.huawei.clouds.openrewrite.markdownit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class MarkdownItDependencyTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.markdownit.UpgradeMarkdownItTo14_3_0";
    static final String MIGRATE = "com.huawei.clouds.openrewrite.markdownit.MigrateMarkdownItTo14_3_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "exact workbook source {0}")
    @ValueSource(strings = {"11.0.0", "12.2.0", "12.3.2", "13.0.1", "13.0.2", "14.0.0", "14.1.0"})
    void upgradesEveryExactWorkbookSource(String version) {
        assertUpgrade(version, "14.3.0");
    }

    @ParameterizedTest(name = "caret workbook source {0}")
    @ValueSource(strings = {"11.0.0", "12.2.0", "12.3.2", "13.0.1", "13.0.2", "14.0.0", "14.1.0"})
    void upgradesEveryCaretWorkbookSource(String version) {
        assertUpgrade("^" + version, "^14.3.0");
    }

    @ParameterizedTest(name = "tilde workbook source {0}")
    @ValueSource(strings = {"11.0.0", "12.2.0", "12.3.2", "13.0.1", "13.0.2", "14.0.0", "14.1.0"})
    void upgradesEveryTildeWorkbookSource(String version) {
        assertUpgrade("~" + version, "~14.3.0");
    }

    @Test
    void upgradesAllDirectDependencySectionsAndPreservesOperatorIntent() {
        rewriteRun(json(
                """
                {"dependencies":{"markdown-it":"11.0.0"},"devDependencies":{"markdown-it":"^12.2.0"},"peerDependencies":{"markdown-it":"~13.0.1"},"optionalDependencies":{"markdown-it":"14.1.0"}}
                """,
                """
                {"dependencies":{"markdown-it":"14.3.0"},"devDependencies":{"markdown-it":"^14.3.0"},"peerDependencies":{"markdown-it":"~14.3.0"},"optionalDependencies":{"markdown-it":"14.3.0"}}
                """,
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest(name = "complex range NOOP {0}")
    @ValueSource(strings = {
            ">=11.0.0", ">=12.2.0 <14", "<=13.0.2", "11.0.0 || 14.1.0", "12.2.0 - 13.0.2",
            "11.x", "12.3.x", "*", ">13.0.1", "<14.1.0", "^12.2.0 || ^13.0.2"
    })
    void leavesComplexRangesUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "non-registry declaration NOOP {0}")
    @ValueSource(strings = {
            "workspace:12.3.2", "workspace:^13.0.2", "npm:@company/markdown-it@12.2.0",
            "github:markdown-it/markdown-it#13.0.1", "git+https://github.com/markdown-it/markdown-it.git#14.1.0",
            "file:../markdown-it", "link:../markdown-it", "https://example.test/markdown-it-14.0.0.tgz"
    })
    void leavesProtocolsAliasesAndForksUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "unlisted/decorated declaration NOOP {0}")
    @ValueSource(strings = {
            "10.0.0", "11.0.1", "12.2.1", "12.3.1", "13.0.0", "13.0.3", "14.0.1", "14.1.1",
            "14.2.0", "14.3.0", "^14.3.0", "~14.3.0", "15.0.0", "v12.3.2", "=13.0.1",
            "13.0.1-beta.1", "latest", "next", ""
    })
    void leavesUnlistedTargetAndDecoratedDeclarationsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void upgradesNestedWorkspaceManifestsIndependently() {
        rewriteRun(
                json("{\"dependencies\":{\"markdown-it\":\"^11.0.0\"}}",
                        "{\"dependencies\":{\"markdown-it\":\"^14.3.0\"}}",
                        source -> source.path("apps/docs/package.json")),
                json("{\"devDependencies\":{\"markdown-it\":\"~14.1.0\"}}",
                        "{\"devDependencies\":{\"markdown-it\":\"~14.3.0\"}}",
                        source -> source.path("packages/parser/package.json"))
        );
    }

    @Test
    void doesNotUpgradeOverridesResolutionsCatalogsOrNestedLookalikes() {
        rewriteRun(json(
                """
                {"overrides":{"markdown-it":"11.0.0","tool":{"dependencies":{"markdown-it":"12.2.0"}}},"resolutions":{"markdown-it":"12.3.2"},"pnpm":{"overrides":{"markdown-it":"13.0.1"}},"catalog":{"markdown-it":"13.0.2"}}
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void doesNotTouchLockfilesOrdinaryJsonSimilarPackagesOrNonStringValues() {
        rewriteRun(
                json("{\"packages\":{\"\":{\"dependencies\":{\"markdown-it\":\"11.0.0\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"markdown-it\":\"12.2.0\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                json("{\"dependencies\":{\"markdown-it-anchor\":\"11.0.0\",\"@types/markdown-it\":\"12.2.0\",\"Markdown-It\":\"13.0.1\",\"markdown-it\":false}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void excludesGeneratedAndCaseVariantDependencyTrees() {
        rewriteRun(
                json("{\"dependencies\":{\"markdown-it\":\"11.0.0\"}}",
                        source -> source.path("Node_Modules/tool/package.json")),
                json("{\"dependencies\":{\"markdown-it\":\"12.2.0\"}}",
                        source -> source.path("generated-test-fixtures/package.json")),
                json("{\"dependencies\":{\"markdown-it\":\"12.3.2\"}}",
                        source -> source.path("GeneratedClient/package.json")),
                json("{\"dependencies\":{\"markdown-it\":\"13.0.1\"}}",
                        source -> source.path("installation/package.json")),
                json("{\"dependencies\":{\"markdown-it\":\"13.0.2\"}}",
                        source -> source.path("INSTALL-CACHE/tool/package.json"))
        );
    }

    @Test
    void migratesPublicRepositoryManifestShapesPinnedByCommit() {
        rewriteRun(
                json("{\"devDependencies\":{\"markdown-it\":\"^11.0.0\",\"markdown-it-bracketed-spans\":\"^1.0.1\"}}",
                        "{\"devDependencies\":{\"markdown-it\":\"^14.3.0\",\"markdown-it-bracketed-spans\":\"^1.0.1\"}}",
                        source -> source.path("fixtures/igembitsgoa/package.json")),
                json("{\"dependencies\":{\"markdown-it\":\"^12.2.0\",\"nanoid\":\"^4.0.2\"}}",
                        "{\"dependencies\":{\"markdown-it\":\"^14.3.0\",\"nanoid\":\"^4.0.2\"}}",
                        source -> source.path("fixtures/rubick/feature/package.json")),
                json("{\"devDependencies\":{\"lint-staged\":\"^11.2.6\",\"markdown-it\":\"^12.3.2\"}}",
                        "{\"devDependencies\":{\"lint-staged\":\"^11.2.6\",\"markdown-it\":\"^14.3.0\"}}",
                        source -> source.path("fixtures/animate-css/package.json")),
                json("{\"dependencies\":{\"markdown-it\":\"13.0.1\",\"markdown-it-anchor\":\"8.6.4\"}}",
                        "{\"dependencies\":{\"markdown-it\":\"14.3.0\",\"markdown-it-anchor\":\"8.6.4\"}}",
                        source -> source.path("fixtures/coauthor/package.json")),
                json("{\"devDependencies\":{\"markdown-it\":\"^13.0.2\",\"markdown-it-anchor\":\"^8.6.7\"}}",
                        "{\"devDependencies\":{\"markdown-it\":\"^14.3.0\",\"markdown-it-anchor\":\"^8.6.7\"}}",
                        source -> source.path("fixtures/zui/package.json")),
                json("{\"devDependencies\":{\"markdown-it\":\"^14.0.0\",\"markdown-it-anchor\":\"^8.6.7\"}}",
                        "{\"devDependencies\":{\"markdown-it\":\"^14.3.0\",\"markdown-it-anchor\":\"^8.6.7\"}}",
                        source -> source.path("fixtures/observable/package.json")),
                json("{\"dependencies\":{\"highlight.js\":\"^11.7.0\",\"markdown-it\":\"^14.1.0\"}}",
                        "{\"dependencies\":{\"highlight.js\":\"^11.7.0\",\"markdown-it\":\"^14.3.0\"}}",
                        source -> source.path("fixtures/coroot/front/package.json"))
        );
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"markdown-it\":\"^12.3.2\"}}",
                        "{\"dependencies\":{\"markdown-it\":\"^14.3.0\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void bothRecipesAreDiscoverableAndValid() {
        Environment environment = environment();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> UPGRADE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATE.equals(recipe.getName())));
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
    }

    private void assertUpgrade(String before, String after) {
        rewriteRun(json("{\"dependencies\":{\"markdown-it\":\"" + before + "\"}}",
                "{\"dependencies\":{\"markdown-it\":\"" + after + "\"}}",
                source -> source.path("package.json")));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"markdown-it\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.markdownit")
                .scanYamlResources()
                .build();
    }
}
