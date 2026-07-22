package com.huawei.clouds.openrewrite.losslessjson;

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

class UpgradeLosslessJsonTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.losslessjson.UpgradeLosslessJsonTo4_0_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesProvectusKafkaUiAndLeavesNamedParseStringifyImportsForReview() {
        // Reduced from provectus/kafka-ui at 83b5a60c:
        // https://github.com/provectus/kafka-ui/blob/83b5a60cc08501b570a0c4d0b4cdfceb1b88d6b7/kafka-ui-react-app/package.json
        // https://github.com/provectus/kafka-ui/blob/83b5a60cc08501b570a0c4d0b4cdfceb1b88d6b7/kafka-ui-react-app/src/components/common/EditorViewer/EditorViewer.tsx
        rewriteRun(
                json(
                        """
                        {
                          "name": "kafka-ui-react-app",
                          "dependencies": {
                            "lodash": "^4.17.21",
                            "lossless-json": "^2.0.8",
                            "pretty-ms": "7.0.1"
                          },
                          "devDependencies": {
                            "@types/lossless-json": "^1.0.1",
                            "@types/node": "^16.4.13"
                          }
                        }
                        """,
                        """
                        {
                          "name": "kafka-ui-react-app",
                          "dependencies": {
                            "lodash": "^4.17.21",
                            "lossless-json": "4.0.1",
                            "pretty-ms": "7.0.1"
                          },
                          "devDependencies": {
                            "@types/lossless-json": "^1.0.1",
                            "@types/node": "^16.4.13"
                          }
                        }
                        """,
                        spec -> spec.path("kafka-ui-react-app/package.json")
                ),
                text(
                        "import { parse, stringify } from 'lossless-json';\n",
                        spec -> spec.path("kafka-ui-react-app/src/components/common/EditorViewer/EditorViewer.tsx")
                )
        );
    }

    @Test
    void upgradesPowerSyncJsonbigWorkspaceAndLeavesTypedNumberParserForReview() {
        // Reduced from powersync-ja/powersync-service at 1c44c31b:
        // https://github.com/powersync-ja/powersync-service/blob/1c44c31b1ceef675f3bffaef2d5bd26c7ccf69b6/packages/jsonbig/package.json
        // https://github.com/powersync-ja/powersync-service/blob/1c44c31b1ceef675f3bffaef2d5bd26c7ccf69b6/packages/jsonbig/src/json.ts
        rewriteRun(
                json(
                        """
                        {
                          "name": "@powersync/jsonbig",
                          "scripts": {"build": "tsc -b"},
                          "dependencies": {"lossless-json": "^2.0.8"},
                          "devDependencies": {}
                        }
                        """,
                        """
                        {
                          "name": "@powersync/jsonbig",
                          "scripts": {"build": "tsc -b"},
                          "dependencies": {"lossless-json": "4.0.1"},
                          "devDependencies": {}
                        }
                        """,
                        spec -> spec.path("packages/jsonbig/package.json")
                ),
                text(
                        """
                        import * as json from 'lossless-json';
                        import { isInteger, JavaScriptValue, NumberParser, Replacer, Reviver } from 'lossless-json';

                        const numberParser: NumberParser = (value) =>
                          isInteger(value) ? BigInt(value) : parseFloat(value);
                        """,
                        spec -> spec.path("packages/jsonbig/src/json.ts")
                )
        );
    }

    @Test
    void upgradesEthstakerVueFrontendAndLeavesBigIntegerParserForReview() {
        // Reduced from serenita-org/ethstaker.tax at 1baf7fbf:
        // https://github.com/serenita-org/ethstaker.tax/blob/1baf7fbfe024576770fe56b13fe25b37b68e08b5/src/frontend_vue/package.json
        // https://github.com/serenita-org/ethstaker.tax/blob/1baf7fbfe024576770fe56b13fe25b37b68e08b5/src/frontend_vue/src/views/TheMainView.vue
        rewriteRun(
                json(
                        """
                        {
                          "dependencies": {
                            "date-fns": "^2.30.0",
                            "lossless-json": "^2.0.11",
                            "vue": "^3.3.4"
                          }
                        }
                        """,
                        """
                        {
                          "dependencies": {
                            "date-fns": "^2.30.0",
                            "lossless-json": "4.0.1",
                            "vue": "^3.3.4"
                          }
                        }
                        """,
                        spec -> spec.path("src/frontend_vue/package.json")
                ),
                text(
                        "import { parse, isInteger } from 'lossless-json'\n",
                        spec -> spec.path("src/frontend_vue/src/views/TheMainView.vue")
                )
        );
    }

    @Test
    void upgradesLidoHardhatProjectAndLeavesNamespaceImportForReview() {
        // Reduced from color-typea/lido-trustless-tvl-oracle-solution at 89bdedcb:
        // https://github.com/color-typea/lido-trustless-tvl-oracle-solution/blob/89bdedcbb5f1e2bcb66e7f4d2aea58ff4b666fb2/package.json
        // https://github.com/color-typea/lido-trustless-tvl-oracle-solution/blob/89bdedcbb5f1e2bcb66e7f4d2aea58ff4b666fb2/deploy/00-gates.ts
        rewriteRun(
                json(
                        """
                        {
                          "dependencies": {
                            "ethers": "^6.7.1",
                            "hardhat": "^2.17.2",
                            "hardhat-deploy": "^0.11.37",
                            "lossless-json": "^2.0.11"
                          }
                        }
                        """,
                        """
                        {
                          "dependencies": {
                            "ethers": "^6.7.1",
                            "hardhat": "^2.17.2",
                            "hardhat-deploy": "^0.11.37",
                            "lossless-json": "4.0.1"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        "import * as losslessJSON from 'lossless-json';\n",
                        spec -> spec.path("deploy/00-gates.ts")
                )
        );
    }

    @Test
    void upgradesKafbatKafkaUiPinnedDependencyAndPreservesBundlerContext() {
        // Reduced from kafbat/kafka-ui at bc2d7cad:
        // https://github.com/kafbat/kafka-ui/blob/bc2d7cad4678d5ebb6fd06af49068e34c9cc8b59/frontend/package.json
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "jsonpath-plus": "10.4.0",
                    "lossless-json": "2.0.11",
                    "pretty-ms": "7.0.1"
                  },
                  "devDependencies": {
                    "@types/lossless-json": "1.0.4",
                    "@types/node": "20.11.17"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "jsonpath-plus": "10.4.0",
                    "lossless-json": "4.0.1",
                    "pretty-ms": "7.0.1"
                  },
                  "devDependencies": {
                    "@types/lossless-json": "1.0.4",
                    "@types/node": "20.11.17"
                  }
                }
                """,
                spec -> spec.path("frontend/package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0.8", "2.0.11"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"" + oldVersion + "\"}}",
                "{\"dependencies\":{\"lossless-json\":\"4.0.1\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"lossless-json": "2.0.8"},
                  "devDependencies": {"lossless-json": "~2.0.11"},
                  "peerDependencies": {"lossless-json": ">=2.0.8 <3"},
                  "optionalDependencies": {"lossless-json": "^2.0.11"}
                }
                """,
                """
                {
                  "dependencies": {"lossless-json": "4.0.1"},
                  "devDependencies": {"lossless-json": "4.0.1"},
                  "peerDependencies": {"lossless-json": "4.0.1"},
                  "optionalDependencies": {"lossless-json": "4.0.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "^2.0.8", "~2.0.11", "v2.0.8", "=v2.0.11",
            ">= 2.0.8 < 3", "<=2.0.11", "2.0.8 || ^2.0.11",
            "2.0.8 - 2.0.11", "2.0.11-beta.1", "2.0.8+build.7"
    })
    void upgradesSemverFormsAnchoredOnSpreadsheetVersions(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"" + version + "\"}}",
                "{\"dependencies\":{\"lossless-json\":\"4.0.1\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesMultipleWorkspaceManifestsInOneRun() {
        rewriteRun(
                json(
                        "{\"name\":\"@example/parser\",\"dependencies\":{\"lossless-json\":\"^2.0.8\"}}",
                        "{\"name\":\"@example/parser\",\"dependencies\":{\"lossless-json\":\"4.0.1\"}}",
                        spec -> spec.path("packages/parser/package.json")
                ),
                json(
                        "{\"name\":\"@example/web\",\"devDependencies\":{\"lossless-json\":\"2.0.11\"}}",
                        "{\"name\":\"@example/web\",\"devDependencies\":{\"lossless-json\":\"4.0.1\"}}",
                        spec -> spec.path("apps/web/package.json")
                )
        );
    }

    @Test
    void preservesAdjacentTypesAndBigNumberLibraries() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "decimal.js": "10.4.3",
                    "lossless-json": "2.0.11",
                    "json-bigint": "1.0.0"
                  },
                  "devDependencies": {"@types/lossless-json": "1.0.4"}
                }
                """,
                """
                {
                  "dependencies": {
                    "decimal.js": "10.4.3",
                    "lossless-json": "4.0.1",
                    "json-bigint": "1.0.0"
                  },
                  "devDependencies": {"@types/lossless-json": "1.0.4"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOfficialTargetManifestShapeUntouched() {
        // Reduced from the official v4.0.1 package manifest:
        // https://github.com/josdejong/lossless-json/blob/ff25d5086295d0b21decd6bd4b67d9a5c2be9143/package.json
        rewriteRun(json(
                """
                {
                  "name": "lossless-json",
                  "version": "4.0.1",
                  "main": "lib/umd/lossless-json.js",
                  "module": "lib/esm/index.js",
                  "types": "lib/types/index.d.ts",
                  "sideEffects": false,
                  "exports": {
                    ".": {
                      "import": "./lib/esm/index.js",
                      "require": "./lib/umd/lossless-json.js",
                      "types": "./lib/types/index.d.ts"
                    }
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4.0.1", "^4.0.1", "4.0.2", "^4.1.0", "4.3.0", "5.0.0-beta.1"})
    void doesNotChangeTargetOrNewerVersions(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"" + version + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0.7", "2.0.9", "2.0.10", "3.0.0", "3.0.2"})
    void leavesUnlistedVersionsUntouched(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"" + version + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {">=2.0.0", "2.x", "*", "latest", "next"})
    void leavesBroadOrTaggedRangesUntouched(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"" + version + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "workspace:^2.0.8",
            "npm:@example/lossless-json@2.0.11",
            "github:josdejong/lossless-json#v2.0.11",
            "git+https://github.com/josdejong/lossless-json.git#v2.0.8",
            "file:../lossless-json",
            "https://registry.example/lossless-json-2.0.11.tgz"
    })
    void leavesExternalReferencesUntouched(String version) {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"" + version + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOverridesResolutionsAndPnpmOverridesUntouched() {
        rewriteRun(json(
                """
                {
                  "overrides": {"lossless-json": "2.0.11"},
                  "resolutions": {"lossless-json": "2.0.8"},
                  "pnpm": {"overrides": {"lossless-json": "2.0.11"}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNestedOverrideInsideDirectDependencyUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"example": "1.0.0"},
                  "overrides": {"example": {"lossless-json": "2.0.8"}}
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
                  "dependencies": {"lossless-json": false},
                  "devDependencies": {"lossless-json": 2.011}
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
                    "": {"dependencies": {"lossless-json": "^2.0.11"}},
                    "node_modules/lossless-json": {"version": "2.0.11"}
                  },
                  "dependencies": {"lossless-json": {"version": "2.0.11"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                "{\"dependencies\":{\"lossless-json\":\"2.0.11\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNames() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@types/lossless-json": "2.0.11",
                    "@example/lossless-json": "2.0.11",
                    "lossless-json-parser": "2.0.11",
                    "lossless-json2": "2.0.8"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.losslessjson")
                .scanYamlResources()
                .build();
    }
}
