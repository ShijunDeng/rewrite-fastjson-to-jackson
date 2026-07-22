package com.huawei.clouds.openrewrite.reactrouterdom;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeReactRouterDomTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.reactrouterdom.UpgradeReactRouterDomTo6_30_4";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesExactDependencyFromClassToFunctionHooks() {
        // Adapted from s-yadav/class-to-function-with-react-hooks at f8853e0c:
        // https://github.com/s-yadav/class-to-function-with-react-hooks/blob/f8853e0cf38a919c7688da73576f1c995ebf7f25/package.json
        rewriteRun(json(
                """
                {
                  "name": "new",
                  "dependencies": {
                    "create-effect": "0.3.1",
                    "react": "16.8.0",
                    "react-dom": "16.8.0",
                    "react-router-dom": "4.3.1",
                    "react-scripts": "2.0.3"
                  }
                }
                """,
                """
                {
                  "name": "new",
                  "dependencies": {
                    "create-effect": "0.3.1",
                    "react": "16.8.0",
                    "react-dom": "16.8.0",
                    "react-router-dom": "6.30.4",
                    "react-scripts": "2.0.3"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesCaretDependencyFromNavStateReactRouter() {
        // Adapted from ohansemmanuel/nav-state-react-router at e38f9f03:
        // https://github.com/ohansemmanuel/nav-state-react-router/blob/e38f9f039d1119c3a2f8a82ddecc16c23883bac0/package.json
        rewriteRun(json(
                """
                {
                  "name": "nav-state-react-router",
                  "dependencies": {
                    "lodash": "^4.17.10",
                    "react": "^16.4.1",
                    "react-dom": "^16.4.1",
                    "react-redux": "^5.0.7",
                    "react-router": "^4.3.1",
                    "react-router-dom": "^4.3.1",
                    "react-scripts": "1.1.4",
                    "redux": "^4.0.0"
                  }
                }
                """,
                """
                {
                  "name": "nav-state-react-router",
                  "dependencies": {
                    "lodash": "^4.17.10",
                    "react": "^16.4.1",
                    "react-dom": "^16.4.1",
                    "react-redux": "^5.0.7",
                    "react-router": "^4.3.1",
                    "react-router-dom": "6.30.4",
                    "react-scripts": "1.1.4",
                    "redux": "^4.0.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesConnectedReactRouterExampleWithoutChangingCompanions() {
        // Adapted from supasate/connected-react-router at d822fb9a:
        // https://github.com/supasate/connected-react-router/blob/d822fb9afd12e9d32e8353a04c1c6b4b5ba95f72/examples/basic/package.json
        rewriteRun(json(
                """
                {
                  "name": "connected-react-router-example-basic",
                  "dependencies": {
                    "connected-react-router": "^6.0.0",
                    "history": "^4.7.2",
                    "react": "^16.6.0",
                    "react-dom": "^16.6.0",
                    "react-redux": "^6.0.0",
                    "react-router": "^4.3.1",
                    "react-router-dom": "^4.3.1",
                    "redux": "^4.0.1"
                  }
                }
                """,
                """
                {
                  "name": "connected-react-router-example-basic",
                  "dependencies": {
                    "connected-react-router": "^6.0.0",
                    "history": "^4.7.2",
                    "react": "^16.6.0",
                    "react-dom": "^16.6.0",
                    "react-redux": "^6.0.0",
                    "react-router": "^4.3.1",
                    "react-router-dom": "6.30.4",
                    "redux": "^4.0.1"
                  }
                }
                """,
                spec -> spec.path("examples/basic/package.json")
        ));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-router-dom": "4.3.1"},
                  "devDependencies": {"react-router-dom": "^4.3.1"},
                  "peerDependencies": {"react-router-dom": "~4.3.1"},
                  "optionalDependencies": {"react-router-dom": ">=4.3.1 <5"}
                }
                """,
                """
                {
                  "dependencies": {"react-router-dom": "6.30.4"},
                  "devDependencies": {"react-router-dom": "6.30.4"},
                  "peerDependencies": {"react-router-dom": "6.30.4"},
                  "optionalDependencies": {"react-router-dom": "6.30.4"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesPrefixesAndCommonRanges() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-router-dom": "v4.3.1"},
                  "devDependencies": {"react-router-dom": " = v4.3.1"},
                  "peerDependencies": {"react-router-dom": "4.3.1 - 5.3.4"},
                  "optionalDependencies": {"react-router-dom": "4.3.1 || ^5.3.4"}
                }
                """,
                """
                {
                  "dependencies": {"react-router-dom": "6.30.4"},
                  "devDependencies": {"react-router-dom": "6.30.4"},
                  "peerDependencies": {"react-router-dom": "6.30.4"},
                  "optionalDependencies": {"react-router-dom": "6.30.4"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesBuildMetadataAndPrereleaseQualifiers() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-router-dom": "4.3.1+company.7"},
                  "devDependencies": {"react-router-dom": "4.3.1-rc.1"}
                }
                """,
                """
                {
                  "dependencies": {"react-router-dom": "6.30.4"},
                  "devDependencies": {"react-router-dom": "6.30.4"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesRootAndWorkspacePackageJsonFiles() {
        rewriteRun(
                json(
                        "{\"private\": true, \"dependencies\": {\"react-router-dom\": \"4.3.1\"}}",
                        "{\"private\": true, \"dependencies\": {\"react-router-dom\": \"6.30.4\"}}",
                        spec -> spec.path("package.json")
                ),
                json(
                        "{\"name\": \"@example/web\", \"dependencies\": {\"react-router-dom\": \"^4.3.1\"}}",
                        "{\"name\": \"@example/web\", \"dependencies\": {\"react-router-dom\": \"6.30.4\"}}",
                        spec -> spec.path("packages/web/package.json")
                )
        );
    }

    @Test
    void upgradesDeeplyNestedWorkspacePackageJson() {
        rewriteRun(json(
                """
                {
                  "name": "@example/router-adapter",
                  "peerDependencies": {"react": ">=16.8", "react-router-dom": "^4.3.1"}
                }
                """,
                """
                {
                  "name": "@example/router-adapter",
                  "peerDependencies": {"react": ">=16.8", "react-router-dom": "6.30.4"}
                }
                """,
                spec -> spec.path("apps/admin/packages/router-adapter/package.json")
        ));
    }

    @Test
    void preservesReactAndRouterCompanionDependencies() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@types/react-router-dom": "^4.3.1",
                    "connected-react-router": "^6.9.3",
                    "history": "^4.10.1",
                    "react": "^16.8.0",
                    "react-dom": "^16.8.0",
                    "react-router": "^4.3.1",
                    "react-router-config": "^1.0.0-beta.4",
                    "react-router-dom": "^4.3.1"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "@types/react-router-dom": "^4.3.1",
                    "connected-react-router": "^6.9.3",
                    "history": "^4.10.1",
                    "react": "^16.8.0",
                    "react-dom": "^16.8.0",
                    "react-router": "^4.3.1",
                    "react-router-config": "^1.0.0-beta.4",
                    "react-router-dom": "6.30.4"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesExactTargetUntouched() {
        rewriteRun(json(
                "{\"dependencies\": {\"react-router-dom\": \"6.30.4\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTargetRangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-router-dom": "^6.30.4"},
                  "devDependencies": {"react-router-dom": ">=6.30.4 <7"}
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
                  "dependencies": {"react-router-dom": "6.30.5"},
                  "devDependencies": {"react-router-dom": "^7.0.0"},
                  "peerDependencies": {"react-router-dom": "8.0.0-beta.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOtherV4PatchesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-router-dom": "4.3.0"},
                  "devDependencies": {"react-router-dom": "4.3.2"},
                  "peerDependencies": {"react-router-dom": "4.4.0"},
                  "optionalDependencies": {"react-router-dom": "4.3.10"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesV5DeclarationsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-router-dom": "5.1.2"},
                  "devDependencies": {"react-router-dom": "^5.3.4"},
                  "peerDependencies": {"react-router-dom": ">=5 <6"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNonRegistryReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-router-dom": "workspace:*"},
                  "devDependencies": {"react-router-dom": "npm:@example/router-dom@4.3.1"},
                  "peerDependencies": {"react-router-dom": "github:remix-run/react-router#v4.3.1"},
                  "optionalDependencies": {"react-router-dom": "file:../react-router-dom"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesLinkTarballAndUrlReferencesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-router-dom": "link:../router"},
                  "devDependencies": {"react-router-dom": "https://registry.example/react-router-dom-4.3.1.tgz"},
                  "optionalDependencies": {"react-router-dom": "../artifacts/react-router-dom-4.3.1.tgz"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesWildcardsTagsAndVariablesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-router-dom": "*"},
                  "devDependencies": {"react-router-dom": "latest"},
                  "peerDependencies": {"react-router-dom": "$routerVersion"},
                  "optionalDependencies": {"react-router-dom": "next"}
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
                    "": {"dependencies": {"react-router-dom": "4.3.1"}},
                    "node_modules/react-router-dom": {"version": "4.3.1"}
                  },
                  "dependencies": {"react-router-dom": {"version": "4.3.1"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyNpmShrinkwrap() {
        rewriteRun(json(
                """
                {
                  "name": "shrinkwrapped-app",
                  "dependencies": {"react-router-dom": {"version": "4.3.1"}}
                }
                """,
                spec -> spec.path("npm-shrinkwrap.json")
        ));
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(json(
                "{\"dependencies\": {\"react-router-dom\": \"4.3.1\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarPackageNames() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@example/react-router-dom": "4.3.1",
                    "react-router-dom-v5-compat": "4.3.1",
                    "react-router-dom-webpack": "4.3.1",
                    "react-router-native": "4.3.1"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyOverridesOrResolutions() {
        rewriteRun(json(
                """
                {
                  "overrides": {"react-router-dom": "4.3.1"},
                  "resolutions": {"react-router-dom": "4.3.1"},
                  "pnpm": {"overrides": {"react-router-dom": "4.3.1"}}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyNestedToolConfiguration() {
        rewriteRun(json(
                """
                {
                  "tooling": {
                    "dependencies": {"react-router-dom": "4.3.1"}
                  },
                  "metadata": {"react-router-dom": "4.3.1"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNonStringDeclarationsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-router-dom": {"version": "4.3.1"}},
                  "devDependencies": {"react-router-dom": 431},
                  "optionalDependencies": {"react-router-dom": false}
                }
                """,
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
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.reactrouterdom")
                .scanYamlResources()
                .build();
    }
}
