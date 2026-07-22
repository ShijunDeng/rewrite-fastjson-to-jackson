package com.huawei.clouds.openrewrite.vuei18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeVueI18nTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.vuei18n.UpgradeVueI18nTo11_3_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesPixelfedVue2Dependency() {
        // Reduced from pixelfed/pixelfed at c8bed78b:
        // https://github.com/pixelfed/pixelfed/blob/c8bed78bee3d796c5efb57393dafafbba3706f38/package.json
        rewriteRun(json(
                """
                {
                  "name": "pixelfed",
                  "dependencies": {
                    "bootstrap-vue": "^2.22.0",
                    "vue-i18n": "^8.27.1"
                  },
                  "devDependencies": {
                    "vue": "^2.6.14",
                    "vue-router": "^3.5.4",
                    "vuex": "^3.6.2"
                  }
                }
                """,
                """
                {
                  "name": "pixelfed",
                  "dependencies": {
                    "bootstrap-vue": "^2.22.0",
                    "vue-i18n": "11.3.0"
                  },
                  "devDependencies": {
                    "vue": "^2.6.14",
                    "vue-router": "^3.5.4",
                    "vuex": "^3.6.2"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesXbootFrontAndPreservesVue2Stack() {
        // Reduced from Exrick/xboot-front at fb933de4:
        // https://github.com/Exrick/xboot-front/blob/fb933de4c5927792b71da31479f5f0693aeb71c6/package.json
        rewriteRun(json(
                """
                {
                  "name": "xboot-front",
                  "dependencies": {
                    "vue": "^2.6.14",
                    "vue-i18n": "^8.24.4",
                    "vue-router": "^3.5.1",
                    "vuex": "^3.6.2"
                  }
                }
                """,
                """
                {
                  "name": "xboot-front",
                  "dependencies": {
                    "vue": "^2.6.14",
                    "vue-i18n": "11.3.0",
                    "vue-router": "^3.5.1",
                    "vuex": "^3.6.2"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesVuesticAdminVue3Dependency() {
        // Reduced from epicmaxco/vuestic-admin at 9c5b44f3:
        // https://github.com/epicmaxco/vuestic-admin/blob/9c5b44f3674d4c3e7ad01cc043d5331cee953c49/package.json
        rewriteRun(json(
                """
                {
                  "name": "vuestic-admin",
                  "dependencies": {
                    "pinia": "^2.1.7",
                    "vue": "3.5.8",
                    "vue-i18n": "^9.6.2",
                    "vue-router": "^4.2.5"
                  }
                }
                """,
                """
                {
                  "name": "vuestic-admin",
                  "dependencies": {
                    "pinia": "^2.1.7",
                    "vue": "3.5.8",
                    "vue-i18n": "11.3.0",
                    "vue-router": "^4.2.5"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "upgrades spreadsheet version {0}")
    @ValueSource(strings = {
            "7.3.2", "8.11.2", "8.20.0", "8.22.1", "8.22.4",
            "8.24.3", "8.24.4", "8.25.0", "8.26.7", "8.27.1"
    })
    void upgradesEveryExplicitSpreadsheetVersion(String oldVersion) {
        rewriteRun(json(
                "{\"dependencies\":{\"vue-i18n\":\"" + oldVersion + "\"}}",
                "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"vue-i18n": "^7.3.2"},
                  "devDependencies": {"vue-i18n": "~8.27.1"},
                  "peerDependencies": {"vue-i18n": ">=9.0.0 <10"},
                  "optionalDependencies": {"vue-i18n": "10.0.8"}
                }
                """,
                """
                {
                  "dependencies": {"vue-i18n": "11.3.0"},
                  "devDependencies": {"vue-i18n": "11.3.0"},
                  "peerDependencies": {"vue-i18n": "11.3.0"},
                  "optionalDependencies": {"vue-i18n": "11.3.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesComparatorRangesAndVPrefix() {
        rewriteRun(
                json(
                        "{\"dependencies\":{\"vue-i18n\":\">=8.27.1 <11\"}}",
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                        spec -> spec.path("apps/range/package.json")
                ),
                json(
                        "{\"dependencies\":{\"vue-i18n\":\"v10.0.5\"}}",
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                        spec -> spec.path("apps/v-prefix/package.json")
                ),
                json(
                        "{\"dependencies\":{\"vue-i18n\":\"~ 9.14.5\"}}",
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                        spec -> spec.path("apps/spaced-range/package.json")
                )
        );
    }

    @Test
    void upgradesBareAndWildcardOldMajors() {
        rewriteRun(
                json(
                        "{\"dependencies\":{\"vue-i18n\":\"8\"}}",
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                        spec -> spec.path("packages/bare/package.json")
                ),
                json(
                        "{\"dependencies\":{\"vue-i18n\":\"10.x\"}}",
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                        spec -> spec.path("packages/wildcard/package.json")
                )
        );
    }

    @Test
    void upgradesLastStableLineAndTargetPrerelease() {
        rewriteRun(
                json(
                        "{\"dependencies\":{\"vue-i18n\":\"11.2.8\"}}",
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                        spec -> spec.path("apps/previous/package.json")
                ),
                json(
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0-rc.1\"}}",
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                        spec -> spec.path("apps/prerelease/package.json")
                )
        );
    }

    @Test
    void upgradesNestedWorkspaceManifest() {
        rewriteRun(json(
                """
                {
                  "name": "@example/localized-ui",
                  "peerDependencies": {
                    "vue": "^3.2.0",
                    "vue-i18n": "^9.2.2"
                  }
                }
                """,
                """
                {
                  "name": "@example/localized-ui",
                  "peerDependencies": {
                    "vue": "^3.2.0",
                    "vue-i18n": "11.3.0"
                  }
                }
                """,
                spec -> spec.path("packages/localized-ui/package.json")
        ));
    }

    @Test
    void leavesOfficialTargetManifestShapeUntouched() {
        // Peer and engine values come from the official v11.3.0 package manifest:
        // https://github.com/intlify/vue-i18n/blob/v11.3.0/packages/vue-i18n/package.json
        rewriteRun(json(
                """
                {
                  "name": "vue-i18n-consumer",
                  "dependencies": {"vue-i18n": "^11.3.0"},
                  "peerDependencies": {"vue": "^3.0.0"},
                  "engines": {"node": ">= 16"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotDowngradeNewerVersions() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"vue-i18n": "11.3.1"},
                  "devDependencies": {"vue-i18n": "^11.4.0"},
                  "peerDependencies": {"vue-i18n": ">=12.0.0"},
                  "optionalDependencies": {"vue-i18n": "20.0.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesWorkspaceAndAliasProtocolsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"vue-i18n": "workspace:^8.27.1"},
                  "devDependencies": {"vue-i18n": "npm:vue-i18n@8.27.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesGitFileAndUrlReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"vue-i18n": "file:../vue-i18n"},
                  "devDependencies": {"vue-i18n": "git+ssh://git@github.com/intlify/vue-i18n.git#v8.27.1"},
                  "peerDependencies": {"vue-i18n": "github:intlify/vue-i18n#v8.27.1"},
                  "optionalDependencies": {"vue-i18n": "https://example.test/vue-i18n-8.27.1.tgz"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTagsAndUnboundedRangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"vue-i18n": "latest"},
                  "devDependencies": {"vue-i18n": "next"},
                  "peerDependencies": {"vue-i18n": "*"},
                  "optionalDependencies": {"vue-i18n": ""}
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
                  "name": "locked-app",
                  "packages": {
                    "": {"dependencies": {"vue-i18n": "^8.27.1"}},
                    "node_modules/vue-i18n": {"version": "8.27.1"}
                  },
                  "dependencies": {
                    "vue-i18n": {"version": "8.27.1"}
                  }
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                "{\"dependencies\":{\"vue-i18n\":\"8.27.1\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNames() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@intlify/vue-i18n-bridge": "8.27.1",
                    "petite-vue-i18n": "8.27.1",
                    "vue-i18n-composable": "8.27.1",
                    "vue-i18n-extract": "8.27.1"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOutOfScopeLegacyMajorUntouched() {
        rewriteRun(json(
                "{\"dependencies\":{\"vue-i18n\":\"6.1.1\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.vuei18n")
                .scanYamlResources()
                .build();
    }
}
