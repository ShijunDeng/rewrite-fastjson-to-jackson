package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeAngularCdkTest implements RewriteTest {
    private static final String RECIPE = "com.huawei.clouds.openrewrite.angular.UpgradeAngularCdkTo20_2_14";

    @Override
    public void defaults(RecipeSpec spec) { spec.recipe(environment().activateRecipes(RECIPE)); }

    @ParameterizedTest(name = "upgrades spreadsheet version {0}")
    @ValueSource(strings = {
            "10.2.6", "10.2.7", "11.2.13", "12.2.10", "12.2.13", "13.1.3",
            "13.3.1", "13.3.9", "14.0.6", "14.2.0"
    })
    void upgradesEveryVisibleSpreadsheetVersion(String version) {
        rewriteRun(packageVersion("package.json", version));
    }

    @ParameterizedTest(name = "upgrades exact anchored declaration {0}")
    @ValueSource(strings = {
            "^10.2.6", "~10.2.6", "^10.2.7", "~10.2.7", "^11.2.13", "~11.2.13",
            "^12.2.10", "~12.2.10", "^12.2.13", "~12.2.13", "^13.1.3", "~13.1.3",
            "^13.3.1", "~13.3.1", "^13.3.9", "~13.3.9", "^14.0.6", "~14.0.6",
            "^14.2.0", "~14.2.0"
    })
    void upgradesEveryCaretAndTildeForm(String version) {
        rewriteRun(packageVersion("package.json", version));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"@angular/cdk": "10.2.6"},
                  "devDependencies": {"@angular/cdk": "^11.2.13"},
                  "peerDependencies": {"@angular/cdk": "~12.2.13"},
                  "optionalDependencies": {"@angular/cdk": "13.3.9"}
                }
                """,
                """
                {
                  "dependencies": {"@angular/cdk": "20.2.14"},
                  "devDependencies": {"@angular/cdk": "20.2.14"},
                  "peerDependencies": {"@angular/cdk": "20.2.14"},
                  "optionalDependencies": {"@angular/cdk": "20.2.14"}
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesRealSliitFossManifest() {
        // sliit-foss/sliitfoss, fixed commit 3d06f8e2b94a9fc6ef0d77b3676bf490b4e9cd6c.
        rewriteRun(realManifest("10.2.6", "10.2.6", "package.json"));
    }

    @Test
    void upgradesRealTibcoTcstkManifest() {
        // TIBCOSoftware/TCSTK-Angular, fixed commit da04a8ac3553d0cf29eaf2361b4cf59cf4fec1d0.
        rewriteRun(realManifest("^12.2.13", "^12.2.13", "package.json"));
    }

    @Test
    void upgradesRealAetherRocGuiManifest() {
        // onosproject/aether-roc-gui, fixed commit d344536843f81ff673e999f210cc7af0a2ca632a.
        rewriteRun(realManifest("~12.2.13", "~12.2.13", "package.json"));
    }

    @Test
    void leavesRealDspaceTargetManifestUntouched() {
        // DSpace/dspace-angular, fixed commit 8410074a9e74654a260d000a89b6f6fe1fd54167.
        rewriteRun(json(
                """
                {"dependencies":{"@angular/cdk":"^20.2.14","@angular/common":"^20.3.25","@angular/core":"^20.3.25"}}
                """, source -> source.path("package.json")));
    }

    @Test
    void leavesRealTaskBoardUnlistedVersionUntouched() {
        // kiswa/TaskBoard, fixed commit 857583e4bb508c7b449a8e45bb0747d22d88abdb.
        rewriteRun(json("{\"dependencies\":{\"@angular/cdk\":\"^10.1.3\",\"@angular/core\":\"^10.0.11\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void upgradesRootAndNestedWorkspaceManifests() {
        rewriteRun(
                packageVersion("package.json", "10.2.7"),
                packageVersion("apps/browser/package.json", "^13.1.3"),
                packageVersion("libs/ui/package.json", "~14.2.0")
        );
    }

    @ParameterizedTest(name = "leaves unlisted scalar {0}")
    @ValueSource(strings = {
            "9.2.4", "10.1.3", "10.2.5", "10.2.8", "11.2.14", "12.2.11", "12.2.14",
            "13.0.0", "13.3.8", "14.0.5", "14.2.1", "15.0.0", "19.2.0", "20.2.13",
            "20.2.14", "20.2.15", "21.0.0"
    })
    void leavesUnlistedTargetAndNewerScalarVersionsUntouched(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"@angular/cdk\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves complex or unsafe declaration {0}")
    @ValueSource(strings = {
            ">=10.2.6 <20", "10.2.6 || ^12.2.13", "10.2.6 - 14.2.0", "12.x", "*",
            "v11.2.13", "=12.2.13", "^v13.1.3", "13.3.9-next.1", "14.2.0+vendor.2",
            "workspace:^", "npm:@company/cdk@12.2.13", "file:../cdk", "github:angular/components",
            "latest", "next", "${angularCdkVersion}", "catalog:"
    })
    void leavesComplexProtocolsTagsAndVariablesUntouched(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"@angular/cdk\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void leavesOverridesMetadataLockfilesAndOtherJsonUntouched() {
        rewriteRun(
                json("{\"overrides\":{\"@angular/cdk\":\"10.2.6\"},\"metadata\":{\"dependencies\":{\"@angular/cdk\":\"12.2.13\"}}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"@angular/cdk\":\"10.2.7\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"@angular/cdk\":\"10.2.7\"}}",
                        source -> source.path("fixtures/dependencies.json"))
        );
    }

    @Test
    void leavesSimilarPackagesAndAngularPeersUntouched() {
        rewriteRun(json(
                """
                {"dependencies":{"@angular/cdk-experimental":"10.2.7","angular-cdk":"10.2.7","@angular/core":"10.2.7"}}
                """, source -> source.path("package.json")));
    }

    @Test
    void dependencyUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                packageVersion("package.json", "12.2.13"));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE.equals(candidate.getName())));
        assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()));
    }

    private SourceSpecs packageVersion(String path, String declaration) {
        return json("{\"dependencies\":{\"@angular/cdk\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"@angular/cdk\":\"20.2.14\"}}", source -> source.path(path));
    }

    private SourceSpecs realManifest(String cdk, String material, String path) {
        return json("{\"dependencies\":{\"@angular/cdk\":\"" + cdk + "\",\"@angular/material\":\"" + material + "\"}}",
                "{\"dependencies\":{\"@angular/cdk\":\"20.2.14\",\"@angular/material\":\"" + material + "\"}}",
                source -> source.path(path));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources().build();
    }
}
