package com.huawei.clouds.openrewrite.reactdom;

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
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeReactDomTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.reactdom.UpgradeReactDomTo19_0_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesFeedzaiDevelopmentRendererButPreservesBroadPeerAndPortalSource() {
        // Reduced from feedzai/brushable-histogram at a46666e5. The source uses createPortal,
        // while React and the broad library peer range intentionally remain for manual alignment:
        // https://github.com/feedzai/brushable-histogram/blob/a46666e5de8cd24cfb490d14e26ea046b3945e60/package.json
        // https://github.com/feedzai/brushable-histogram/blob/a46666e5de8cd24cfb490d14e26ea046b3945e60/src/common/components/Portal.js
        rewriteRun(
                json(
                        """
                        {
                          "name": "@feedzai/brushable-histogram",
                          "main": "lib/index.js",
                          "devDependencies": {
                            "enzyme": "^3.11.0",
                            "react": "16.6.1",
                            "react-dom": "16.6.1"
                          },
                          "peerDependencies": {
                            "react": "16.x",
                            "react-dom": "16.x"
                          }
                        }
                        """,
                        """
                        {
                          "name": "@feedzai/brushable-histogram",
                          "main": "lib/index.js",
                          "devDependencies": {
                            "enzyme": "^3.11.0",
                            "react": "16.6.1",
                            "react-dom": "19.0.0"
                          },
                          "peerDependencies": {
                            "react": "16.x",
                            "react-dom": "16.x"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import React from "react";
                        import ReactDOM from "react-dom";
                        export const Portal = ({ children, element }) =>
                            ReactDOM.createPortal(children, element);
                        """,
                        spec -> spec.path("src/common/components/Portal.js")
                )
        );
    }

    @Test
    void upgradesFaithlifeDevelopmentRendererAndLeavesLegacyRenderForMigration() {
        // Reduced from Faithlife/styled-ui at 4b771c63. Its catalog still calls ReactDOM.render:
        // https://github.com/Faithlife/styled-ui/blob/4b771c63d1ed3381dfc40c29a87416402364d0bd/package.json
        // https://github.com/Faithlife/styled-ui/blob/4b771c63d1ed3381dfc40c29a87416402364d0bd/catalog/index.js
        rewriteRun(
                json(
                        """
                        {
                          "name": "@faithlife/styled-ui",
                          "main": "dist/main.js",
                          "devDependencies": {
                            "@testing-library/react": "^11.2.6",
                            "react": "^16.14.0",
                            "react-dom": "^16.14.0"
                          },
                          "peerDependencies": {
                            "react": "^16.8.0",
                            "react-dom": "^16.8.0",
                            "styled-components": "^4 || ^5"
                          }
                        }
                        """,
                        """
                        {
                          "name": "@faithlife/styled-ui",
                          "main": "dist/main.js",
                          "devDependencies": {
                            "@testing-library/react": "^11.2.6",
                            "react": "^16.14.0",
                            "react-dom": "19.0.0"
                          },
                          "peerDependencies": {
                            "react": "^16.8.0",
                            "react-dom": "^16.8.0",
                            "styled-components": "^4 || ^5"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                text(
                        """
                        import ReactDOM from 'react-dom';
                        ReactDOM.render(<Catalog />, document.getElementById('catalog'));
                        """,
                        spec -> spec.path("catalog/index.js")
                )
        );
    }

    @Test
    void upgradesGrafanaRuntimeRendererAndPreservesReactAndTypes() {
        // Reduced from grafana/grafana v8.5.0 commit 6134e3cf. The application pins the
        // React renderer pair and separate DefinitelyTyped declarations in its root manifest:
        // https://github.com/grafana/grafana/blob/6134e3cf35a99c7dd3041b7ececb47cb7619ba9a/package.json
        rewriteRun(json(
                """
                {
                  "name": "grafana",
                  "version": "8.5.0",
                  "private": true,
                  "dependencies": {
                    "react": "17.0.2",
                    "react-dom": "17.0.2"
                  },
                  "devDependencies": {
                    "@testing-library/react": "12.1.4",
                    "@types/react": "17.0.42",
                    "@types/react-dom": "17.0.14"
                  }
                }
                """,
                """
                {
                  "name": "grafana",
                  "version": "8.5.0",
                  "private": true,
                  "dependencies": {
                    "react": "17.0.2",
                    "react-dom": "19.0.0"
                  },
                  "devDependencies": {
                    "@testing-library/react": "12.1.4",
                    "@types/react": "17.0.42",
                    "@types/react-dom": "17.0.14"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesViteReactTemplateAndPreservesModernClientRootSource() {
        // Reduced from vitejs/vite v4.5.0 commit 055d2b86. The generated application
        // already uses the React 18 root API, but React and @types still need coordinated upgrades:
        // https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/package.json
        // https://github.com/vitejs/vite/blob/055d2b86b0543a7c1a2a4d5bc7298af62bc51fa7/packages/create-vite/template-react/src/main.jsx
        rewriteRun(
                json(
                        """
                        {
                          "name": "vite-react-starter",
                          "private": true,
                          "type": "module",
                          "dependencies": {
                            "react": "^18.2.0",
                            "react-dom": "^18.2.0"
                          },
                          "devDependencies": {
                            "@types/react": "^18.2.18",
                            "@types/react-dom": "^18.2.7",
                            "@vitejs/plugin-react": "^4.0.4",
                            "vite": "^4.4.8"
                          }
                        }
                        """,
                        """
                        {
                          "name": "vite-react-starter",
                          "private": true,
                          "type": "module",
                          "dependencies": {
                            "react": "^18.2.0",
                            "react-dom": "19.0.0"
                          },
                          "devDependencies": {
                            "@types/react": "^18.2.18",
                            "@types/react-dom": "^18.2.7",
                            "@vitejs/plugin-react": "^4.0.4",
                            "vite": "^4.4.8"
                          }
                        }
                        """,
                        spec -> spec.path("packages/create-vite/template-react/package.json")
                ),
                text(
                        """
                        import React from 'react'
                        import ReactDOM from 'react-dom/client'
                        ReactDOM.createRoot(document.getElementById('root')).render(
                          <React.StrictMode><App /></React.StrictMode>,
                        )
                        """,
                        spec -> spec.path("packages/create-vite/template-react/src/main.jsx")
                )
        );
    }

    @ParameterizedTest(name = "upgrades spreadsheet react-dom version {0}")
    @ValueSource(strings = {"16.6.1", "16.14.0", "17.0.2", "18.2.0"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(packageVersion("package.json", oldVersion));
    }

    @ParameterizedTest(name = "upgrades supported registry semver {0}")
    @ValueSource(strings = {
            "^16.6.1",
            "~16.14.0",
            "v17.0.2",
            "=18.2.0",
            "^v16.14.0"
    })
    void upgradesSupportedRegistrySemverForms(String oldVersion) {
        rewriteRun(packageVersion("package.json", oldVersion));
    }

    @Test
    void upgradesAllFourDirectDependencySections() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-dom": "16.6.1"},
                  "devDependencies": {"react-dom": "^16.14.0"},
                  "peerDependencies": {"react-dom": "=17.0.2"},
                  "optionalDependencies": {"react-dom": "~18.2.0"}
                }
                """,
                """
                {
                  "dependencies": {"react-dom": "19.0.0"},
                  "devDependencies": {"react-dom": "19.0.0"},
                  "peerDependencies": {"react-dom": "19.0.0"},
                  "optionalDependencies": {"react-dom": "19.0.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesWorkspaceChildrenButPreservesWorkspaceConfiguration() {
        rewriteRun(
                json(
                        """
                        {
                          "name": "react-monorepo",
                          "private": true,
                          "workspaces": ["apps/*", "packages/*"],
                          "packageManager": "pnpm@10.0.0"
                        }
                        """,
                        spec -> spec.path("package.json")
                ),
                packageVersion("apps/browser/package.json", "^17.0.2"),
                packageVersion("packages/widget/package.json", "18.2.0")
        );
    }

    @Test
    void preservesReactTypesRenderersAndTestingLibraries() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "react": "18.2.0",
                    "react-dom": "18.2.0",
                    "react-dom-factories": "^1.0.2",
                    "react-native": "0.76.0",
                    "react-test-renderer": "18.2.0"
                  },
                  "devDependencies": {
                    "@testing-library/react": "^14.0.0",
                    "@types/react": "18.2.0",
                    "@types/react-dom": "18.2.0"
                  }
                }
                """,
                """
                {
                  "dependencies": {
                    "react": "18.2.0",
                    "react-dom": "19.0.0",
                    "react-dom-factories": "^1.0.2",
                    "react-native": "0.76.0",
                    "react-test-renderer": "18.2.0"
                  },
                  "devDependencies": {
                    "@testing-library/react": "^14.0.0",
                    "@types/react": "18.2.0",
                    "@types/react-dom": "18.2.0"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesTargetAndTargetRangeUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-dom": "19.0.0"},
                  "devDependencies": {"react-dom": "^19.0.0"}
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
                  "dependencies": {"react-dom": "19.0.1"},
                  "devDependencies": {"react-dom": "^19.1.0"},
                  "peerDependencies": {"react-dom": ">=20.0.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesUnlistedOldVersionsUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-dom": "16.6.0"},
                  "devDependencies": {"react-dom": "16.14.1"},
                  "peerDependencies": {"react-dom": "17.0.1"},
                  "optionalDependencies": {"react-dom": "18.3.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesBroadAndUnboundedRangesUntouched() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-dom": ">=16"},
                  "devDependencies": {"react-dom": "17.x"},
                  "peerDependencies": {"react-dom": "*"},
                  "optionalDependencies": {"react-dom": ">=16.8 || ^17 || ^18"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves ambiguous selected-version declaration {0} untouched")
    @ValueSource(strings = {
            "18.2.0-rc.1",
            "17.0.2+vendor.4",
            ">=16.14.0 <18",
            "16.6.1 - 18.2.0",
            "16.14.0 || ^18.2.0"
    })
    void leavesPrereleaseBuildAndCompoundRangesUntouched(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"react-dom\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesNonStringDependencyValuesUntouchedWithoutMatcherFailure() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-dom": {"version": "18.2.0"}},
                  "devDependencies": {"react-dom": 18},
                  "peerDependencies": {"react-dom": true},
                  "optionalDependencies": {"react-dom": null}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void rejectsWorkspaceNpmAliasAndLocalProtocols() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-dom": "workspace:^18.2.0"},
                  "devDependencies": {"react-dom": "npm:@example/react-dom@17.0.2"},
                  "peerDependencies": {"react-dom": "link:../react-dom"},
                  "optionalDependencies": {"react-dom": "file:../react-dom-16.14.0"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void rejectsGitGithubAndUrlReferences() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-dom": "git+ssh://git@github.com/facebook/react.git#v16.6.1"},
                  "devDependencies": {"react-dom": "github:facebook/react#v17.0.2"},
                  "peerDependencies": {"react-dom": "https://registry.example/react-dom-18.2.0.tgz"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void rejectsTagsVariablesAndCatalogProtocol() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-dom": "latest"},
                  "devDependencies": {"react-dom": "next"},
                  "peerDependencies": {"react-dom": "${REACT_DOM_VERSION}"},
                  "optionalDependencies": {"react-dom": "catalog:react-dom"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void rejectsMalformedVersionLookalikes() {
        rewriteRun(json(
                """
                {
                  "dependencies": {"react-dom": "16.6.10"},
                  "devDependencies": {"react-dom": "16.14.00"},
                  "peerDependencies": {"react-dom": "17.0.20"},
                  "optionalDependencies": {"react-dom": "18.2.00"}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void doesNotModifyOverridesResolutionsOrPeerMetadata() {
        rewriteRun(json(
                """
                {
                  "overrides": {"react-dom": "16.14.0"},
                  "resolutions": {"react-dom": "17.0.2"},
                  "pnpm": {"overrides": {"react-dom": "18.2.0"}},
                  "peerDependenciesMeta": {"react-dom": {"optional": true}}
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
                    "": {"dependencies": {"react-dom": "18.2.0"}},
                    "node_modules/react-dom": {"version": "18.2.0"}
                  },
                  "dependencies": {"react-dom": {"version": "18.2.0"}}
                }
                """,
                spec -> spec.path("package-lock.json")
        ));
    }

    @Test
    void doesNotModifyOrdinaryJson() {
        rewriteRun(json(
                "{\"dependencies\":{\"react-dom\":\"17.0.2\"}}",
                spec -> spec.path("fixtures/dependencies.json")
        ));
    }

    @Test
    void doesNotModifySimilarNamesOrCaseVariants() {
        rewriteRun(json(
                """
                {
                  "dependencies": {
                    "@types/react-dom": "18.2.0",
                    "react-dom-factories": "16.14.0",
                    "react-dom-router": "17.0.2",
                    "react-dom17": "18.2.0",
                    "React-DOM": "16.6.1"
                  }
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesRemovedClientTestAndServerApisForSourceAwareMigration() {
        rewriteRun(
                text(
                        """
                        import ReactDOM, { findDOMNode, hydrate, render, unmountComponentAtNode } from 'react-dom';
                        render(<App />, container, onRendered);
                        hydrate(<App />, container);
                        findDOMNode(component);
                        unmountComponentAtNode(container);
                        ReactDOM.unstable_renderSubtreeIntoContainer(parent, <App />, container);
                        """,
                        spec -> spec.path("src/legacy-root.jsx")
                ),
                text(
                        """
                        import { act, Simulate } from 'react-dom/test-utils';
                        act(() => root.render(<App />));
                        Simulate.click(button);
                        """,
                        spec -> spec.path("src/App.test.tsx")
                ),
                text(
                        """
                        import { renderToNodeStream, renderToString } from 'react-dom/server';
                        import { prerender } from 'react-dom/static';
                        """,
                        spec -> spec.path("server/render.tsx")
                )
        );
    }

    @Test
    void discoversRecipeWithExpectedMetadataAndValidConfiguration() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertEquals("Upgrade React DOM to 19.0.0", recipe.getDisplayName());
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static org.openrewrite.test.SourceSpecs packageVersion(String path, String version) {
        return json(
                "{\"dependencies\":{\"react-dom\":\"" + version + "\"}}",
                "{\"dependencies\":{\"react-dom\":\"19.0.0\"}}",
                spec -> spec.path(path)
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.reactdom")
                .scanYamlResources()
                .build();
    }
}
