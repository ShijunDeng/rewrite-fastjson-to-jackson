package com.huawei.clouds.openrewrite.diagramjsminimap;

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

class UpgradeDiagramJsMinimapTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.diagramjsminimap.UpgradeDiagramJsMinimapTo5_2_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesSpreadsheetVersionWithOfficialUsageShape() {
        // The dependency is the spreadsheet input. The neighboring bpmn-js 9 line and source
        // integration shape are reduced from the official bpmn-io/bpmn-js-examples minimap sample
        // at 135f410e. Source and CSS remain manual migration concerns.
        // https://github.com/bpmn-io/bpmn-js-examples/blob/135f410e645cb85bf689a5e0e7b6c515812c73c9/minimap/package.json
        // https://github.com/bpmn-io/bpmn-js-examples/blob/135f410e645cb85bf689a5e0e7b6c515812c73c9/minimap/src/app.js
        rewriteRun(
                json(
                        """
                        {
                          "name": "bpmn-js-example-minimap",
                          "dependencies": {
                            "bpmn-js": "^9.0.0",
                            "diagram-js-minimap": "^2.1.0",
                            "jquery": "^3.6.0"
                          }
                        }
                        """,
                        """
                        {
                          "name": "bpmn-js-example-minimap",
                          "dependencies": {
                            "bpmn-js": "^9.0.0",
                            "diagram-js-minimap": "5.2.0",
                            "jquery": "^3.6.0"
                          }
                        }
                        """,
                        spec -> spec.path("minimap/package.json")
                ),
                text(
                        """
                        import BpmnModeler from 'bpmn-js/lib/Modeler';
                        import minimapModule from 'diagram-js-minimap';

                        const modeler = new BpmnModeler({ additionalModules: [ minimapModule ] });
                        """,
                        spec -> spec.path("minimap/src/app.js")
                ),
                text(
                        "@import 'diagram-js-minimap/assets/diagram-js-minimap.css';\n",
                        spec -> spec.path("minimap/src/app.css")
                )
        );
    }

    @Test
    void preservesOfficialExampleAlreadyOnTarget() {
        // Reduced from the official bpmn-io/bpmn-js-examples sample at c7baad91.
        // https://github.com/bpmn-io/bpmn-js-examples/blob/c7baad910b1185e8c6c58bb3676d7c9b0c36beac/minimap/package.json
        rewriteRun(json(
                """
                {
                  "name": "bpmn-js-example-minimap",
                  "devDependencies": {"webpack": "^5.104.1"},
                  "dependencies": {
                    "bpmn-js": "^18.20.0",
                    "diagram-js-minimap": "^5.2.0",
                    "jquery": "^4.0.0"
                  }
                }
                """,
                spec -> spec.path("minimap/package.json")
        ));
    }

    @Test
    void preservesRxDragDiagramJsNineCompatibleLine() {
        // Reduced from codebdy/rxdrag at 6759ce35. Version 3 is intentionally a no-op: the
        // recipe does not assume that every application may skip its own staged migration.
        // https://github.com/codebdy/rxdrag/blob/6759ce350edb5a822c88f7c2c73275b6662f4206/packages/bpmn-editor/package.json
        rewriteRun(json(
                """
                {
                  "name": "@rxdrag/bpmn-editor",
                  "dependencies": {
                    "@rxdrag/shared": "workspace:*",
                    "antd": "^5.10.0",
                    "bpmn-js": "^10.0.0",
                    "diagram-js-minimap": "^3.0.0",
                    "styled-components": "^5.3.9"
                  }
                }
                """,
                spec -> spec.path("packages/bpmn-editor/package.json")
        ));
    }

    @Test
    void preservesMoonStudioDiagramJsTwelveLine() {
        // Reduced from moon-studio/vite-vue-bpmn-process at db85ffcc.
        // https://github.com/moon-studio/vite-vue-bpmn-process/blob/db85ffccd714607ba966017a257d3699aec4d993/package.json
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "bpmn-js": "13.2.0",
                    "bpmn-js-properties-panel": "2.1.0",
                    "diagram-js": "12.2.0",
                    "diagram-js-grid-bg": "^1.0.1",
                    "diagram-js-minimap": "^4.1.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void preservesLegacyReactBpmnCompanionMatrix() {
        // Reduced from Link-Kou/React-bpmn at 72bb0ed5. The older 2.0.3 declaration is not
        // spreadsheet-selected and therefore must remain untouched.
        // https://github.com/Link-Kou/React-bpmn/blob/72bb0ed51053bd81ecc9a4f6c2d62a8a83f3f558/package.json
        rewriteRun(json(
                """
                {
                  "name": "my-app",
                  "dependencies": {
                    "bpmn-js": "^7.2.0",
                    "diagram-js": "^6.6.1",
                    "diagram-js-minimap": "^2.0.3",
                    "react": "^16.13.1"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void preservesWpsEgonTargetAndDiagramJsFifteenMatrix() {
        // Reduced from WPS/egon.io at 6b5dd602. This is a real target-compatible matrix.
        // https://github.com/WPS/egon.io/blob/6b5dd602b26ba9bb05cef8ce09f1d97180ac4b32/package.json
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@bpmn-io/align-to-origin": "0.7.0",
                    "diagram-js": "^15.4.0",
                    "diagram-js-direct-editing": "^3.2.0",
                    "diagram-js-minimap": "^5.2.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "upgrades safe 2.1.0 declaration {0}")
    @ValueSource(strings = {"2.1.0", "^2.1.0", "~2.1.0", "=2.1.0", "v2.1.0", "^v2.1.0"})
    void upgradesOnlySafeDeclarationsAnchoredExactlyOnSpreadsheetVersion(String declaration) {
        rewriteRun(packageVersion("fixtures/" + declaration.hashCode() + "/package.json", declaration));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"diagram-js-minimap": "2.1.0"},
                  "devDependencies": {"diagram-js-minimap": "^2.1.0"},
                  "peerDependencies": {"diagram-js-minimap": "~2.1.0"},
                  "optionalDependencies": {"diagram-js-minimap": "=2.1.0"}
                }
                """,
                """
                {
                  "dependencies": {"diagram-js-minimap": "5.2.0"},
                  "devDependencies": {"diagram-js-minimap": "5.2.0"},
                  "peerDependencies": {"diagram-js-minimap": "5.2.0"},
                  "optionalDependencies": {"diagram-js-minimap": "5.2.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesNestedWorkspaceManifestWithoutChangingWorkspaceConfiguration() {
        rewriteRun(
                json(
                        """
                        {
                          "name": "root",
                          "private": true,
                          "workspaces": ["packages/*", "apps/*"]
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                json(
                        """
                        {
                          "name": "@example/modeler",
                          "dependencies": {
                            "bpmn-js": "^9.0.3",
                            "diagram-js-minimap": "2.1.0"
                          }
                        }
                        """,
                        """
                        {
                          "name": "@example/modeler",
                          "dependencies": {
                            "bpmn-js": "^9.0.3",
                            "diagram-js-minimap": "5.2.0"
                          }
                        }
                        """,
                        spec -> spec.path("packages/modeler/package.json")
                )
        );
    }

    @Test
    void preservesFormattingAndAdjacentDiagramPackages() {
        rewriteRun(json(
                """
                {
                    "dependencies" : {
                        "bpmn-js" : "^9.0.3",
                        "diagram-js" : "^8.9.0",
                        "diagram-js-grid" : "^0.2.0",
                        "diagram-js-minimap" : "2.1.0"
                    }
                }
                """,
                """
                {
                    "dependencies" : {
                        "bpmn-js" : "^9.0.3",
                        "diagram-js" : "^8.9.0",
                        "diagram-js-grid" : "^0.2.0",
                        "diagram-js-minimap" : "5.2.0"
                    }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves other published version {0} untouched")
    @ValueSource(strings = {
            "1.3.0", "2.0.3", "2.0.4", "2.1.1", "2.2.0", "3.0.0", "4.0.0",
            "4.0.1", "4.1.0", "5.0.0", "5.1.0", "5.2.0", "5.3.0", "6.0.0"
    })
    void leavesEveryOtherVersionUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"diagram-js-minimap\":\"" + declaration + "\"}}",
                spec -> spec.path("versions/" + declaration + "/package.json")
        ));
    }

    @ParameterizedTest(name = "rejects ambiguous range {0}")
    @ValueSource(strings = {
            ">=2.1.0", ">2.1.0", "<=2.1.0", "<3.0.0", "2.1.0 || 3.0.0",
            "2.1.0 - 4.1.0", "2.x", "*", "latest", "next", "2.1.0-beta.1", "2.1.0+vendor.1"
    })
    void leavesRangesTagsPrereleasesAndBuildsUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"diagram-js-minimap\":\"" + declaration + "\"}}",
                spec -> spec.path("ranges/" + declaration.hashCode() + "/package.json")
        ));
    }

    @ParameterizedTest(name = "rejects non-registry reference {0}")
    @ValueSource(strings = {
            "workspace:2.1.0", "workspace:^2.1.0", "npm:@example/minimap@2.1.0",
            "file:../diagram-js-minimap", "link:../diagram-js-minimap",
            "github:bpmn-io/diagram-js-minimap#v2.1.0",
            "git+https://github.com/bpmn-io/diagram-js-minimap.git#v2.1.0",
            "https://registry.example/diagram-js-minimap-2.1.0.tgz"
    })
    void leavesProtocolsAliasesAndExternalReferencesUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"diagram-js-minimap\":\"" + declaration + "\"}}",
                spec -> spec.path("references/" + declaration.hashCode() + "/package.json")
        ));
    }

    @Test
    void leavesOverridesResolutionsAndPackageManagerOverridesUntouched() {
        rewriteRun(json(
                """
                {
                  "overrides": {"diagram-js-minimap": "2.1.0"},
                  "resolutions": {"diagram-js-minimap": "2.1.0"},
                  "pnpm": {"overrides": {"diagram-js-minimap": "2.1.0"}},
                  "dependenciesMeta": {"diagram-js-minimap": {"built": false}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNonStringDependencyValuesUntouched() {
        rewriteRun(json(
                """
                {
                  "devDependencies": {"diagram-js-minimap": false},
                  "optionalDependencies": {"diagram-js-minimap": 2.1}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyNpmPackageLock() {
        rewriteRun(json(
                """
                {
                  "packages": {
                    "": {"dependencies": {"diagram-js-minimap": "2.1.0"}},
                    "node_modules/diagram-js-minimap": {"version": "2.1.0"}
                  },
                  "dependencies": {"diagram-js-minimap": {"version": "2.1.0"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyPnpmOrYarnLocks() {
        rewriteRun(
                text(
                        "diagram-js-minimap@2.1.0:\n  resolution: {integrity: sha512-example}\n",
                        spec -> spec.path("pnpm-lock.yaml")
                ),
                text(
                        "diagram-js-minimap@^2.1.0:\n  version \"2.1.0\"\n",
                        spec -> spec.path("yarn.lock")
                )
        );
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                "{\"dependencies\":{\"diagram-js-minimap\":\"2.1.0\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNames() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@example/diagram-js-minimap": "2.1.0",
                    "diagram-js": "2.1.0",
                    "diagram-js-minimap-plugin": "2.1.0",
                    "diagram-js-minimap-css": "2.1.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesSourceImportsDeepImportsAndCssAssetsUntouched() {
        rewriteRun(
                text(
                        """
                        import minimapModule from 'diagram-js-minimap';
                        import Minimap from 'diagram-js-minimap/lib/Minimap';
                        """,
                        spec -> spec.path("src/modeler.js")
                ),
                text(
                        "@import 'diagram-js-minimap/assets/diagram-js-minimap.css';\n",
                        spec -> spec.path("src/modeler.css")
                ),
                text(
                        "const css = require.resolve('diagram-js-minimap/assets/diagram-js-minimap.css');\n",
                        spec -> spec.path("webpack.config.js")
                )
        );
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static org.openrewrite.test.SourceSpecs packageVersion(String path, String declaration) {
        return json(
                "{\"dependencies\":{\"diagram-js-minimap\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"diagram-js-minimap\":\"5.2.0\"}}",
                spec -> spec.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.diagramjsminimap")
                .scanYamlResources()
                .build();
    }
}
