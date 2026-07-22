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

class UpgradeGridStackTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.gridstack.UpgradeGridStackTo12_3_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesWeTrackerAndLeavesLegacyImportsForManualMigration() {
        // Reduced from pgregory/wetracker at e461f104. Its source imports the v4 HTML5 D&D
        // adapter explicitly and retains jQuery for unrelated application code.
        // https://github.com/pgregory/wetracker/blob/e461f104a01895bd9380213f14aea77d104b3f08/package.json
        // https://github.com/pgregory/wetracker/blob/e461f104a01895bd9380213f14aea77d104b3f08/src/app.js
        rewriteRun(
                json(
                        """
                        {
                          "name": "wetracker",
                          "dependencies": {
                            "gridstack": "^4.2.6",
                            "jquery": "^3.6.0",
                            "jquery-ui": "^1.12.1"
                          },
                          "devDependencies": {"webpack": "^5.51.0"}
                        }
                        """,
                        """
                        {
                          "name": "wetracker",
                          "dependencies": {
                            "gridstack": "12.3.3",
                            "jquery": "^3.6.0",
                            "jquery-ui": "^1.12.1"
                          },
                          "devDependencies": {"webpack": "^5.51.0"}
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import $ from 'jquery';
                        import 'gridstack';
                        import 'gridstack/dist/gridstack.css';
                        import 'gridstack/dist/h5/gridstack-dd-native';
                        """,
                        spec -> spec.path("src/app.js")
                )
        );
    }

    @Test
    void upgradesHexaDataVueDashboardAndPreservesDeepImports() {
        // Reduced from Hexa-ai/hexa-data at 784f572b. The v5 source uses GridStackWidget,
        // disableOneColumnMode and old dist/h5 deep imports that require manual review.
        // https://github.com/Hexa-ai/hexa-data/blob/784f572b5775b4a3789bb86ca12eaafb374740cc/ui/package.json
        // https://github.com/Hexa-ai/hexa-data/blob/784f572b5775b4a3789bb86ca12eaafb374740cc/ui/src/components/DashboardGrid.vue
        rewriteRun(
                json(
                        """
                        {
                          "name": "hexa-data",
                          "dependencies": {"gridstack": "^5.1.1", "vue": "^3.2.31"},
                          "devDependencies": {"typescript": "^4.6.2", "vite": "^3.0.0"}
                        }
                        """,
                        """
                        {
                          "name": "hexa-data",
                          "dependencies": {"gridstack": "12.3.3", "vue": "^3.2.31"},
                          "devDependencies": {"typescript": "^4.6.2", "vite": "^3.0.0"}
                        }
                        """,
                        spec -> spec.path("ui/package.json")
                ),
                text(
                        """
                        import { GridStack, GridStackWidget, ColumnOptions } from "gridstack/dist/gridstack";
                        import "gridstack/dist/h5/gridstack-dd-native";
                        import "gridstack/dist/gridstack.min.css";
                        const grid = GridStack.init({ disableOneColumnMode: true });
                        const layout: GridStackWidget[] = grid.save() as GridStackWidget[];
                        """,
                        spec -> spec.path("ui/src/components/DashboardGrid.vue")
                )
        );
    }

    @Test
    void upgradesDevUiAngularDashboardAndPreservesEngineCustomization() {
        // Reduced from DevCloudFE/ng-devui at ef76c44e. It reaches DDGridStack, DDElement,
        // GridStackEngine and private fields, so a version-only recipe must not rewrite source.
        // https://github.com/DevCloudFE/ng-devui/blob/ef76c44e4a7489cfbc587056d947094d0a0c3d1e/package.json
        // https://github.com/DevCloudFE/ng-devui/blob/ef76c44e4a7489cfbc587056d947094d0a0c3d1e/devui/dashboard/grid-stack.service.ts
        rewriteRun(
                json(
                        """
                        {
                          "name": "ng-devui",
                          "engines": {"node": ">=18.19.1"},
                          "dependencies": {
                            "@angular/core": "~19.1.0",
                            "gridstack": "^6.0.0",
                            "tslib": "^2.0.0"
                          },
                          "devDependencies": {"typescript": "~5.7.0"}
                        }
                        """,
                        """
                        {
                          "name": "ng-devui",
                          "engines": {"node": ">=18.19.1"},
                          "dependencies": {
                            "@angular/core": "~19.1.0",
                            "gridstack": "12.3.3",
                            "tslib": "^2.0.0"
                          },
                          "devDependencies": {"typescript": "~5.7.0"}
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import { DDGridStack, GridItemHTMLElement, GridStack, GridStackNode, Utils } from 'gridstack';
                        import { DDElement } from 'gridstack/dist/dd-element';
                        const dd = DDGridStack.get() as DDGridStack;
                        const canFit = grid.engine.willItFit(node);
                        """,
                        spec -> spec.path("devui/dashboard/grid-stack.service.ts")
                )
        );
    }

    @Test
    void upgradesMdvTypeScriptApplicationAndPreservesEventHandlers() {
        // Reduced from Taylor-CCB-Group/MDV at bdec64eb. Its root manifest has GridStack 7.1.1
        // and source relies on gridstackNode, events, engine data and destroy(false).
        // https://github.com/Taylor-CCB-Group/MDV/blob/bdec64ebaa69cb04f4277e7cc7919b7406a5bac7/package.json
        // https://github.com/Taylor-CCB-Group/MDV/blob/bdec64ebaa69cb04f4277e7cc7919b7406a5bac7/src/charts/GridstackManager.ts
        rewriteRun(
                json(
                        """
                        {
                          "name": "mdv",
                          "type": "module",
                          "dependencies": {"gridstack": "^7.1.1", "react": "19.2.6"},
                          "devDependencies": {"typescript": "^6.0.3", "vite": "8.0.11"}
                        }
                        """,
                        """
                        {
                          "name": "mdv",
                          "type": "module",
                          "dependencies": {"gridstack": "12.3.3", "react": "19.2.6"},
                          "devDependencies": {"typescript": "^6.0.3", "vite": "8.0.11"}
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import "gridstack/dist/gridstack.min.css";
                        import { GridStack } from "gridstack";
                        const grid = GridStack.init({ oneColumnSize: 400 });
                        grid.on("resizestop", (_: Event, el: HTMLElement) => el.style.filter = "");
                        grid.destroy(false);
                        """,
                        spec -> spec.path("src/charts/GridstackManager.ts")
                )
        );
    }

    @Test
    void preservesKipAlreadyOnTarget() {
        // Reduced from mxtommy/Kip at 8b75977a; target is intentionally a no-op.
        // https://github.com/mxtommy/Kip/blob/8b75977accb67929aa6d2ba32056c2d807be9820/package.json
        rewriteRun(json(
                """
                {
                  "name": "@mxtommy/kip",
                  "devDependencies": {
                    "@angular/core": "22.0.5",
                    "gridstack": "^12.3.3",
                    "typescript": "^6.0.3"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "upgrades exact spreadsheet version {0}")
    @ValueSource(strings = {"4.2.6", "5.1.1", "6.0.0", "6.0.3", "7.1.1"})
    void upgradesEveryExactSpreadsheetVersion(String version) {
        rewriteRun(packageVersion("exact/" + version + "/package.json", version));
    }

    @ParameterizedTest(name = "upgrades safe selected declaration {0}")
    @ValueSource(strings = {
            "^4.2.6", "~4.2.6", "=4.2.6",
            "^5.1.1", "~5.1.1", "=5.1.1",
            "^6.0.0", "~6.0.0", "=6.0.0",
            "^6.0.3", "~6.0.3", "=6.0.3",
            "^7.1.1", "~7.1.1", "=7.1.1"
    })
    void upgradesSafeSelectedSemverForms(String declaration) {
        rewriteRun(packageVersion("semver/" + declaration.hashCode() + "/package.json", declaration));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"gridstack": "4.2.6"},
                  "devDependencies": {"gridstack": "^5.1.1"},
                  "peerDependencies": {"gridstack": "~6.0.3"},
                  "optionalDependencies": {"gridstack": "=7.1.1"}
                }
                """,
                """
                {
                  "dependencies": {"gridstack": "12.3.3"},
                  "devDependencies": {"gridstack": "12.3.3"},
                  "peerDependencies": {"gridstack": "12.3.3"},
                  "optionalDependencies": {"gridstack": "12.3.3"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesWorkspaceChildManifestAndPreservesRootWorkspaceConfig() {
        rewriteRun(
                json(
                        """
                        {"name":"dashboard-workspace","private":true,"workspaces":["apps/*","packages/*"]}
                        """,
                        spec -> spec.path("package.json")
                ),
                json(
                        """
                        {"name":"@example/dashboard","dependencies":{"gridstack":"6.0.3","vue":"^3.5.0"}}
                        """,
                        """
                        {"name":"@example/dashboard","dependencies":{"gridstack":"12.3.3","vue":"^3.5.0"}}
                        """,
                        spec -> spec.path("apps/dashboard/package.json")
                )
        );
    }

    @Test
    void preservesFormattingAndAdjacentDashboardPackages() {
        rewriteRun(json(
                """
                {
                    "dependencies" : {
                        "gridstack" : "6.0.0",
                        "gridster" : "^0.5.6",
                        "jquery" : "^3.7.1",
                        "react-grid-layout" : "^1.5.2"
                    }
                }
                """,
                """
                {
                    "dependencies" : {
                        "gridstack" : "12.3.3",
                        "gridster" : "^0.5.6",
                        "jquery" : "^3.7.1",
                        "react-grid-layout" : "^1.5.2"
                    }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotUseSiblingVersionAsGridstackPredicate() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@example/widget": "6.0.3",
                    "gridstack": "^11.5.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves unlisted version {0} untouched")
    @ValueSource(strings = {
            "4.2.5", "4.2.7", "5.0.0", "5.1.0", "5.1.2", "6.0.1", "6.0.2",
            "6.0.4", "7.0.0", "7.1.0", "7.1.2", "8.0.0", "9.0.0", "10.0.0", "11.0.0"
    })
    void leavesUnlistedVersionsUntouched(String version) {
        rewriteRun(noChangeVersion("unlisted/" + version + "/package.json", version));
    }

    @ParameterizedTest(name = "leaves target or newer declaration {0} untouched")
    @ValueSource(strings = {"12.3.3", "^12.3.3", "~12.3.3", "12.3.4", "12.4.0", "13.0.0"})
    void leavesTargetAndNewerVersionsUntouched(String version) {
        rewriteRun(noChangeVersion("newer/" + version + "/package.json", version));
    }

    @ParameterizedTest(name = "rejects ambiguous declaration {0}")
    @ValueSource(strings = {
            ">=4.2.6", ">6.0.0", "<=7.1.1", "<8", "4.2.6 || 7.1.1",
            "5.1.1 - 7.1.1", "6.x", "*", "latest", "next",
            "6.0.3-beta.1", "7.1.1+company.2", "${GRIDSTACK_VERSION}", "$gridstackVersion"
    })
    void leavesRangesTagsPrereleasesBuildsAndVariablesUntouched(String declaration) {
        rewriteRun(noChangeVersion("ambiguous/" + declaration.hashCode() + "/package.json", declaration));
    }

    @ParameterizedTest(name = "rejects protocol or alias {0}")
    @ValueSource(strings = {
            "workspace:6.0.3", "workspace:^7.1.1", "npm:@example/gridstack@6.0.0",
            "file:../gridstack", "link:../gridstack", "portal:../gridstack", "catalog:gridstack",
            "github:gridstack/gridstack.js#v7.1.1",
            "git+https://github.com/gridstack/gridstack.js.git#v6.0.3",
            "https://registry.example/gridstack-5.1.1.tgz"
    })
    void leavesProtocolsAliasesAndExternalReferencesUntouched(String declaration) {
        rewriteRun(noChangeVersion("references/" + declaration.hashCode() + "/package.json", declaration));
    }

    @Test
    void leavesOverridesResolutionsAndPackageManagerConfigurationUntouched() {
        rewriteRun(json(
                """
                {
                  "overrides": {"gridstack": "6.0.3"},
                  "resolutions": {"gridstack": "7.1.1"},
                  "pnpm": {"overrides": {"gridstack": "5.1.1"}},
                  "dependenciesMeta": {"gridstack": {"built": false}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNonStringVersionsUntouched() {
        rewriteRun(json(
                """
                {
                  "devDependencies": {"gridstack": false},
                  "optionalDependencies": {"gridstack": 6.0}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyPackageLock() {
        rewriteRun(json(
                """
                {
                  "packages": {
                    "": {"dependencies": {"gridstack": "6.0.3"}},
                    "node_modules/gridstack": {"version": "6.0.3"}
                  },
                  "dependencies": {"gridstack": {"version": "6.0.3"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyPnpmOrYarnLocks() {
        rewriteRun(
                text("gridstack@6.0.3:\n  resolution: {integrity: sha512-example}\n", spec -> spec.path("pnpm-lock.yaml")),
                text("gridstack@^7.1.1:\n  version \"7.1.1\"\n", spec -> spec.path("yarn.lock"))
        );
    }

    @Test
    void doesNotModifyOtherJsonOrBackupFiles() {
        rewriteRun(
                json("{\"dependencies\":{\"gridstack\":\"6.0.3\"}}", spec -> spec.path("fixtures/dependencies.json")),
                json("{\"dependencies\":{\"gridstack\":\"7.1.1\"}}", spec -> spec.path("package.json.backup"))
        );
    }

    @Test
    void doesNotModifySimilarPackageNames() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@types/gridstack": "6.0.3",
                    "@example/gridstack": "7.1.1",
                    "gridstack-angular": "5.1.1",
                    "gridstack-wrapper": "4.2.6",
                    "react-gridstack": "6.0.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesSourceCssAndBundlerConfigurationUntouched() {
        rewriteRun(
                text("import { GridStack, GridStackWidget } from 'gridstack';\n", spec -> spec.path("src/dashboard.ts")),
                text("@import 'gridstack/dist/gridstack.min.css';\n", spec -> spec.path("src/dashboard.css")),
                text("const dd = require('gridstack/dist/h5/gridstack-dd-native');\n", spec -> spec.path("webpack.config.js"))
        );
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static org.openrewrite.test.SourceSpecs packageVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"gridstack\":\"" + version + "\"}}",
                "{\"dependencies\":{\"gridstack\":\"12.3.3\"}}",
                spec -> spec.path(path)
        );
    }

    private static org.openrewrite.test.SourceSpecs noChangeVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"gridstack\":\"" + version + "\"}}",
                spec -> spec.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.gridstack")
                .scanYamlResources()
                .build();
    }
}
