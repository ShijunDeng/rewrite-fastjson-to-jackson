package com.huawei.clouds.openrewrite.diagramjsminimap;

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
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeDiagramJsMinimapTest implements RewriteTest {
    private static final String RECIPE =
            "com.huawei.clouds.openrewrite.diagramjsminimap.UpgradeDiagramJsMinimapTo5_2_0";

    @Override
    public void defaults(RecipeSpec spec) { spec.recipe(environment().activateRecipes(RECIPE)); }

    @ParameterizedTest(name = "upgrades XLSX declaration {0}")
    @ValueSource(strings = {"2.1.0", "^2.1.0", "~2.1.0"})
    void upgradesOnlyExactCaretAndTildeSpreadsheetVersion(String declaration) {
        rewriteRun(packageVersion("package.json", declaration));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"diagram-js-minimap": "2.1.0"},
                  "devDependencies": {"diagram-js-minimap": "^2.1.0"},
                  "peerDependencies": {"diagram-js-minimap": "~2.1.0"},
                  "optionalDependencies": {"diagram-js-minimap": "2.1.0"}
                }
                """,
                """
                {
                  "dependencies": {"diagram-js-minimap": "5.2.0"},
                  "devDependencies": {"diagram-js-minimap": "5.2.0"},
                  "peerDependencies": {"diagram-js-minimap": "5.2.0"},
                  "optionalDependencies": {"diagram-js-minimap": "5.2.0"}
                }
                """, source -> source.path("package.json")));
    }

    @Test
    void upgradesSpreadsheetInputBesideOfficialExampleMatrix() {
        // Dependency input is from XLSX; surrounding bpmn-js/jQuery values are reduced from
        // bpmn-io/bpmn-js-examples commit 135f410e645cb85bf689a5e0e7b6c515812c73c9.
        rewriteRun(json(
                """
                {"dependencies":{"bpmn-js":"^9.0.0","diagram-js-minimap":"^2.1.0","jquery":"^3.3.1"}}
                """,
                """
                {"dependencies":{"bpmn-js":"^9.0.0","diagram-js-minimap":"5.2.0","jquery":"^3.3.1"}}
                """, source -> source.path("minimap/package.json")));
    }

    @Test
    void upgradesNestedWorkspaceManifestOnly() {
        rewriteRun(
                json("{\"private\":true,\"workspaces\":[\"packages/*\"]}", source -> source.path("package.json")),
                packageVersion("packages/modeler/package.json", "~2.1.0")
        );
    }

    @Test
    void preservesOfficialOldExampleUnlistedDependency() {
        // bpmn-io/bpmn-js-examples fixed commit 135f410e645cb85bf689a5e0e7b6c515812c73c9.
        rewriteRun(json("{\"dependencies\":{\"bpmn-js\":\"^9.0.0\",\"diagram-js-minimap\":\"^1.2.1\"}}",
                source -> source.path("minimap/package.json")));
    }

    @Test
    void preservesOfficialExampleAlreadyOnTarget() {
        // bpmn-io/bpmn-js-examples fixed commit c7baad910b1185e8c6c58bb3676d7c9b0c36beac.
        rewriteRun(json("{\"dependencies\":{\"bpmn-js\":\"^18.20.0\",\"diagram-js-minimap\":\"^5.2.0\"}}",
                source -> source.path("minimap/package.json")));
    }

    @Test
    void preservesRealRxDragVersionThreeMatrix() {
        // codebdy/rxdrag fixed commit 6759ce350edb5a822c88f7c2c73275b6662f4206.
        rewriteRun(json("{\"dependencies\":{\"@rxdrag/shared\":\"workspace:*\",\"bpmn-js\":\"^10.0.0\",\"diagram-js-minimap\":\"^3.0.0\"}}",
                source -> source.path("packages/bpmn-editor/package.json")));
    }

    @Test
    void preservesRealMoonStudioVersionFourMatrix() {
        // moon-studio/vite-vue-bpmn-process fixed commit db85ffccd714607ba966017a257d3699aec4d993.
        rewriteRun(json("{\"dependencies\":{\"bpmn-js\":\"13.2.0\",\"diagram-js\":\"12.2.0\",\"diagram-js-minimap\":\"^4.1.0\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void preservesRealReactBpmnLegacyMatrix() {
        // Link-Kou/React-bpmn fixed commit 72bb0ed51053bd81ecc9a4f6c2d62a8a83f3f558.
        rewriteRun(json("{\"dependencies\":{\"bpmn-js\":\"^7.2.0\",\"diagram-js\":\"^6.6.1\",\"diagram-js-minimap\":\"^2.0.3\"}}",
                source -> source.path("package.json")));
    }

    @Test
    void preservesRealWpsTargetMatrix() {
        // WPS/egon.io fixed commit 6b5dd602b26ba9bb05cef8ce09f1d97180ac4b32.
        rewriteRun(json("{\"dependencies\":{\"diagram-js\":\"^15.4.0\",\"diagram-js-minimap\":\"^5.2.0\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest(name = "leaves unlisted version {0}")
    @ValueSource(strings = {
            "1.2.1", "1.3.0", "2.0.3", "2.0.4", "2.1.1", "2.2.0", "3.0.0", "4.0.0",
            "4.0.1", "4.1.0", "5.0.0", "5.1.0", "5.2.0", "5.2.1", "5.3.0", "6.0.0"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"diagram-js-minimap\":\"" + declaration + "\"}}",
                source -> source.path("versions/" + declaration + "/package.json")));
    }

    @ParameterizedTest(name = "leaves unsafe npm declaration {0}")
    @ValueSource(strings = {
            "=2.1.0", "v2.1.0", "^v2.1.0", ">=2.1.0", ">2.1.0", "<=2.1.0", "<3.0.0",
            "2.1.0 || 3.0.0", "2.1.0 - 4.1.0", "2.x", "*", "latest", "next",
            "2.1.0-beta.1", "2.1.0+vendor.1", "workspace:2.1.0", "workspace:^2.1.0",
            "npm:@example/minimap@2.1.0", "file:../diagram-js-minimap", "link:../diagram-js-minimap",
            "github:bpmn-io/diagram-js-minimap#v2.1.0",
            "git+https://github.com/bpmn-io/diagram-js-minimap.git#v2.1.0",
            "https://registry.example/diagram-js-minimap-2.1.0.tgz", "${minimapVersion}", "catalog:"
    })
    void leavesComplexRangesPrefixesProtocolsTagsAndVariablesUntouched(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"diagram-js-minimap\":\"" + declaration + "\"}}",
                source -> source.path("unsafe/" + declaration.hashCode() + "/package.json")));
    }

    @Test
    void leavesOverridesMetadataAndNonStringValuesUntouched() {
        rewriteRun(json(
                """
                {
                  "overrides":{"diagram-js-minimap":"2.1.0"},
                  "resolutions":{"diagram-js-minimap":"2.1.0"},
                  "pnpm":{"overrides":{"diagram-js-minimap":"2.1.0"}},
                  "peerDependenciesMeta":{"diagram-js-minimap":{"optional":true}},
                  "metadata":{"dependencies":{"diagram-js-minimap":"2.1.0"}},
                  "dependenciesMeta":{"diagram-js-minimap":{"built":false}},
                  "devDependencies":{"diagram-js-minimap":false},
                  "optionalDependencies":{"diagram-js-minimap":2.1}
                }
                """, source -> source.path("package.json")));
    }

    @Test
    void leavesNpmPnpmAndYarnLocksUntouched() {
        rewriteRun(
                json("{\"packages\":{\"\":{\"dependencies\":{\"diagram-js-minimap\":\"2.1.0\"}}},\"dependencies\":{\"diagram-js-minimap\":{\"version\":\"2.1.0\"}}}",
                        source -> source.path("package-lock.json")),
                text("diagram-js-minimap@2.1.0:\n  resolution: {integrity: sha512-example}\n",
                        source -> source.path("pnpm-lock.yaml")),
                text("diagram-js-minimap@^2.1.0:\n  version \"2.1.0\"\n",
                        source -> source.path("yarn.lock"))
        );
    }

    @Test
    void leavesOtherJsonSimilarPackagesAndSourceUntouched() {
        rewriteRun(
                json("{\"dependencies\":{\"diagram-js-minimap\":\"2.1.0\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                json("{\"dependencies\":{\"@example/diagram-js-minimap\":\"2.1.0\",\"diagram-js-minimap-plugin\":\"2.1.0\",\"diagram-js\":\"2.1.0\"}}",
                        source -> source.path("package.json")),
                text("import minimapModule from 'diagram-js-minimap';\n",
                        source -> source.path("src/app.js")),
                text("@import 'diagram-js-minimap/assets/diagram-js-minimap.css';\n",
                        source -> source.path("src/app.css"))
        );
    }

    @Test
    void strictUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                packageVersion("package.json", "^2.1.0"));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE.equals(candidate.getName())));
        assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()));
    }

    private static SourceSpecs packageVersion(String path, String declaration) {
        return json("{\"dependencies\":{\"diagram-js-minimap\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"diagram-js-minimap\":\"5.2.0\"}}", source -> source.path(path));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.diagramjsminimap")
                .scanYamlResources().build();
    }
}
