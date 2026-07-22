package com.huawei.clouds.openrewrite.ng2fileupload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class Ng2FileUploadDependencyTest implements RewriteTest {
    static final String UPGRADE =
            "com.huawei.clouds.openrewrite.ng2fileupload.UpgradeNg2FileUploadTo10_0_0";
    static final String MIGRATE =
            "com.huawei.clouds.openrewrite.ng2fileupload.MigrateNg2FileUploadTo10_0_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedNg2FileUploadDependency());
    }

    @ParameterizedTest(name = "exact source {0}")
    @ValueSource(strings = {"2.0.0-3", "3.0.0", "4.0.0"})
    void upgradesExactWorkbookSources(String version) {
        assertUpgrade(version, "10.0.0");
    }

    @ParameterizedTest(name = "caret source {0}")
    @ValueSource(strings = {"2.0.0-3", "3.0.0", "4.0.0"})
    void upgradesCaretWorkbookSources(String version) {
        assertUpgrade("^" + version, "^10.0.0");
    }

    @ParameterizedTest(name = "tilde source {0}")
    @ValueSource(strings = {"2.0.0-3", "3.0.0", "4.0.0"})
    void upgradesTildeWorkbookSources(String version) {
        assertUpgrade("~" + version, "~10.0.0");
    }

    @ParameterizedTest(name = "complex range NOOP {0}")
    @ValueSource(strings = {
            ">=2.0.0-3", ">=3.0.0 <10", "<=4.0.0", "2.0.0-3 || 4.0.0", "3.0.0 - 4.0.0",
            "2.x", "3.x", "4.0.x", "*", ">4.0.0", "<10.0.0", "^3.0.0 || ^4.0.0",
            "~2.0.0-3 || ~4.0.0", "3", "4"
    })
    void leavesComplexRangesUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "protocol/fork NOOP {0}")
    @ValueSource(strings = {
            "workspace:2.0.0-3", "workspace:^3.0.0", "workspace:*",
            "npm:@company/ng2-file-upload@4.0.0", "github:valor-software/ng2-file-upload#v4.0.0",
            "git+https://github.com/valor-software/ng2-file-upload.git#v3.0.0",
            "git://github.com/valor-software/ng2-file-upload.git#v2.0.0-3",
            "file:../ng2-file-upload", "link:../ng2-file-upload",
            "https://example.test/ng2-file-upload-4.0.0.tgz", "catalog:", "catalog:angular"
    })
    void leavesProtocolsAliasesForksAndCatalogsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest(name = "unlisted/decorated NOOP {0}")
    @ValueSource(strings = {
            "2.0.0-2", "2.0.0", "2.0.0-4", "3.0.1", "4.0.1", "5.0.0", "6.0.0", "7.0.1",
            "8.0.0", "9.0.0", "10.0.0", "^10.0.0", "~10.0.0", "11.0.0",
            "v3.0.0", "=4.0.0", "4.0.0-beta.1", "4.0.0+build.1", "latest", "next", "dev", "",
            " 3.0.0", "4.0.0 "
    })
    void leavesUnlistedTargetAndDecoratedVersionsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void upgradesAllDirectSectionsAndPreservesOperators() {
        rewriteRun(json(
                "{\"dependencies\":{\"ng2-file-upload\":\"2.0.0-3\"},\"devDependencies\":{\"ng2-file-upload\":\"^3.0.0\"},\"peerDependencies\":{\"ng2-file-upload\":\"~4.0.0\"},\"optionalDependencies\":{\"ng2-file-upload\":\"3.0.0\"}}",
                "{\"dependencies\":{\"ng2-file-upload\":\"10.0.0\"},\"devDependencies\":{\"ng2-file-upload\":\"^10.0.0\"},\"peerDependencies\":{\"ng2-file-upload\":\"~10.0.0\"},\"optionalDependencies\":{\"ng2-file-upload\":\"10.0.0\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void ignoresOverridesResolutionsCatalogNestedAndLockfiles() {
        rewriteRun(
                json("{\"overrides\":{\"ng2-file-upload\":\"2.0.0-3\"},\"resolutions\":{\"ng2-file-upload\":\"3.0.0\"},\"pnpm\":{\"overrides\":{\"ng2-file-upload\":\"4.0.0\"}},\"catalog\":{\"ng2-file-upload\":\"3.0.0\"}}", source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"ng2-file-upload\":\"4.0.0\"}},\"node_modules/ng2-file-upload\":{\"version\":\"4.0.0\"}}}", source -> source.path("package-lock.json")));
    }

    @Test
    void ignoresSimilarPackagesNonStringsAndOtherJson() {
        rewriteRun(
                json("{\"dependencies\":{\"ng2-file-upload-extra\":\"4.0.0\",\"@scope/ng2-file-upload\":\"3.0.0\",\"Ng2-File-Upload\":\"2.0.0-3\",\"ng2-file-upload\":false}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"ng2-file-upload\":\"4.0.0\"}}", source -> source.path("fixture.json")));
    }

    @Test
    void workspacePackageUpgradesButExcludedParentsDoNot() {
        rewriteRun(
                json("{\"dependencies\":{\"ng2-file-upload\":\"^4.0.0\"}}", "{\"dependencies\":{\"ng2-file-upload\":\"^10.0.0\"}}", source -> source.path("apps/web/package.json")),
                json("{\"dependencies\":{\"ng2-file-upload\":\"4.0.0\"}}", source -> source.path("generated-fixtures/package.json")),
                json("{\"dependencies\":{\"ng2-file-upload\":\"4.0.0\"}}", source -> source.path("GeneratedClient/package.json")),
                json("{\"dependencies\":{\"ng2-file-upload\":\"4.0.0\"}}", source -> source.path("installation/package.json")),
                json("{\"dependencies\":{\"ng2-file-upload\":\"4.0.0\"}}", source -> source.path("node_modules/pkg/package.json")));
    }

    @Test
    void realCourseManifestShapeMigrates() {
        // HelloImKevo/UdemyDatingApp documented exact 2.0.0-3 installation for its Angular client.
        rewriteRun(json("{\"dependencies\":{\"@angular/core\":\"~12.2.0\",\"ng2-file-upload\":\"2.0.0-3\",\"rxjs\":\"~6.6.0\"},\"devDependencies\":{\"typescript\":\"~4.3.5\"}}",
                "{\"dependencies\":{\"@angular/core\":\"~12.2.0\",\"ng2-file-upload\":\"10.0.0\",\"rxjs\":\"~6.6.0\"},\"devDependencies\":{\"typescript\":\"~4.3.5\"}}",
                source -> source.path("fixtures/udemy/client/package.json")));
    }

    @Test
    void dependencyUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"ng2-file-upload\":\"~3.0.0\"}}",
                        "{\"dependencies\":{\"ng2-file-upload\":\"~10.0.0\"}}", source -> source.path("package.json")));
    }

    @Test
    void recipesAreDiscoverableOrderedAndValid() {
        Environment environment = environment();
        Recipe upgrade = environment.activateRecipes(UPGRADE);
        Recipe migrate = environment.activateRecipes(MIGRATE);
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> UPGRADE.equals(recipe.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(recipe -> MIGRATE.equals(recipe.getName())));
        assertTrue(upgrade.validate().isValid(), () -> upgrade.validate().failures().toString());
        assertTrue(migrate.validate().isValid(), () -> migrate.validate().failures().toString());
        assertEquals(1, upgrade.getRecipeList().size());
        assertTrue(upgrade.getRecipeList().get(0) instanceof UpgradeSelectedNg2FileUploadDependency);
        assertEquals(UPGRADE, migrate.getRecipeList().get(0).getName());
        assertTrue(migrate.getRecipeList().get(1) instanceof NormalizeNg2FileUploadPublicImports);
    }

    private void assertUpgrade(String before, String after) {
        rewriteRun(json("{\"dependencies\":{\"ng2-file-upload\":\"" + before + "\"}}",
                "{\"dependencies\":{\"ng2-file-upload\":\"" + after + "\"}}", source -> source.path("package.json")));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"ng2-file-upload\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.ng2fileupload")
                .scanYamlResources().build();
    }
}
