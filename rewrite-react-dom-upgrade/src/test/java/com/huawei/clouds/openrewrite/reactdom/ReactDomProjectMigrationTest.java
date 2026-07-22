package com.huawei.clouds.openrewrite.reactdom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class ReactDomProjectMigrationTest implements RewriteTest {
    private static final String PROJECT = "com.huawei.clouds.openrewrite.reactdom.AuditReactDom19ProjectCompatibility";
    private static final String RESOURCE = "com.huawei.clouds.openrewrite.reactdom.AuditReactDom19ResourceCompatibility";

    @ParameterizedTest(name = "marks unresolved react-dom declaration {0}")
    @MethodSource("unresolvedDeclarations")
    void marksComplexProtocolAndDynamicDeclarations(String declaration, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        "{\"dependencies\":{\"react-dom\":\"" + declaration + "\"}}",
                        "{\"dependencies\":{/*~~(" + message + ")~~>*/\"react-dom\":\"" + declaration + "\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void marksNonStringReactDomDeclarations() {
        String message = "Non-string react-dom declaration was not changed; resolve it to a package-manager scalar before applying the 19.0.0 target";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        "{\"dependencies\":{\"react-dom\":{\"version\":\"18.2.0\"}}}",
                        "{\"dependencies\":{/*~~(" + message + ")~~>*/\"react-dom\":{\"version\":\"18.2.0\"}}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void marksUnresolvedDeclarationInEveryDirectSection() {
        String message = "Complex react-dom range was not changed; replace it only after proving the intended supported React/renderer matrix";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        """
                        {
                          "dependencies": {"react-dom": "16.x"},
                          "devDependencies": {"react-dom": ">=17"},
                          "peerDependencies": {"react-dom": "^17 || ^18"},
                          "optionalDependencies": {"react-dom": "18.2.0 - 19.0.0"}
                        }
                        """,
                        """
                        {
                          "dependencies": {/*~~(%s)~~>*/"react-dom": "16.x"},
                          "devDependencies": {/*~~(%s)~~>*/"react-dom": ">=17"},
                          "peerDependencies": {/*~~(%s)~~>*/"react-dom": "^17 || ^18"},
                          "optionalDependencies": {/*~~(%s)~~>*/"react-dom": "18.2.0 - 19.0.0"}
                        }
                        """.formatted(message, message, message, message),
                        source -> source.path("package.json")
                )
        );
    }

    @ParameterizedTest(name = "marks companion dependency {0}")
    @MethodSource("companionPackages")
    void marksEachCompanionPackage(String packageName, String version, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        "{\"dependencies\":{\"" + packageName + "\":\"" + version + "\"}}",
                        "{\"dependencies\":{/*~~(" + message + ")~~>*/\"" + packageName + "\":\"" + version + "\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void leavesAlignedCompanionsAndTargetUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json(
                        """
                        {
                          "dependencies": {
                            "react": "19.0.0",
                            "react-dom": "19.0.0",
                            "@types/react": "^19.0.1",
                            "@types/react-dom": "19.0.2"
                          }
                        }
                        """,
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void marksUnlistedSimpleVersionsLeftUnchangedByTheUpgradeRecipe() {
        String message = "Unlisted react-dom scalar version was not changed; confirm it is in migration scope before selecting the 19.0.0 target";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"dependencies\":{\"react-dom\":\"18.3.1\"}}",
                        "{\"dependencies\":{/*~~(" + message + ")~~>*/\"react-dom\":\"18.3.1\"}}",
                        source -> source.path("legacy/package.json")),
                json("{\"dependencies\":{\"react-dom\":\"19.0.1\"}}",
                        "{\"dependencies\":{/*~~(" + message + ")~~>*/\"react-dom\":\"19.0.1\"}}",
                        source -> source.path("newer/package.json"))
        );
    }

    @Test
    void leavesOverridesMetadataLockfilesAndOrdinaryJsonUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"overrides\":{\"react-dom\":\"16.x\"},\"peerDependenciesMeta\":{\"react-dom\":{\"optional\":true}}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"react-dom\":\"16.x\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"react-dom\":\"16.x\"}}", source -> source.path("fixtures/data.json"))
        );
    }

    @Test
    void marksClassicAndPreservedTypeScriptJsxModes() {
        String message = "React 19 requires the modern JSX transform; select react-jsx/react-jsxdev unless the framework owns compilation";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"compilerOptions\":{\"jsx\":\"react\"}}",
                        "{\"compilerOptions\":{/*~~(" + message + ")~~>*/\"jsx\":\"react\"}}",
                        source -> source.path("tsconfig.json")),
                json("{\"compilerOptions\":{\"jsx\":\"preserve\"}}",
                        "{\"compilerOptions\":{/*~~(" + message + ")~~>*/\"jsx\":\"preserve\"}}",
                        source -> source.path("jsconfig.json"))
        );
    }

    @Test
    void marksBabelClassicRuntime() {
        String message = "React 19 requires the automatic JSX transform; verify Babel/framework ownership before changing this runtime";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"presets\":[[\"@babel/preset-react\",{\"runtime\":\"classic\"}]]}",
                        "{\"presets\":[[\"@babel/preset-react\",{/*~~(" + message + ")~~>*/\"runtime\":\"classic\"}]]}",
                        source -> source.path("babel.config.json"))
        );
    }

    @Test
    void leavesModernJsxModesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(PROJECT)),
                json("{\"compilerOptions\":{\"jsx\":\"react-jsx\"}}", source -> source.path("tsconfig.json")),
                json("{\"compilerOptions\":{\"jsx\":\"react-jsxdev\"}}", source -> source.path("jsconfig.json"))
        );
    }

    @Test
    void marksUnpkgAndJsdelivrReactDomUmdArtifacts() {
        String message = "React DOM 19 removed UMD builds; use an ESM CDN or bundler and align react/react-dom together";
        String unpkg = "https://unpkg.com/react-dom@16.14.0/umd/react-dom.production.min.js";
        String jsdelivr = "https://cdn.jsdelivr.net/npm/react-dom@18.2.0/umd/react-dom.development.js";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RESOURCE)),
                text("<script src=\"" + unpkg + "\"></script>\n",
                        "<script src=\"" + mark(message, unpkg) + "\"></script>\n",
                        source -> source.path("public/index.html")),
                text("<script src='" + jsdelivr + "'></script>\n",
                        "<script src='" + mark(message, jsdelivr) + "'></script>\n",
                        source -> source.path("public/legacy.htm"))
        );
    }

    @Test
    void marksInlineLegacyRootAndUnmount() {
        String message = "React DOM 19 removed this inline legacy root API; move bootstrap/teardown to a client module with explicit root ownership";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RESOURCE)),
                text("<script>ReactDOM.render(App, node); ReactDOM.unmountComponentAtNode(node);</script>\n",
                        "<script>" + mark(message, "ReactDOM.render") + "(App, node); " +
                        mark(message, "ReactDOM.unmountComponentAtNode") + "(node);</script>\n",
                        source -> source.path("public/index.html"))
        );
    }

    @Test
    void resourceAuditLeavesEsmCdnAndNonHtmlUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(RESOURCE)),
                text("<script type=\"module\" src=\"https://esm.sh/react-dom@19.0.0/client\"></script>\n",
                        source -> source.path("public/index.html")),
                text("https://unpkg.com/react-dom@18.2.0/umd/react-dom.production.min.js\n",
                        source -> source.path("docs/migration.md"))
        );
    }

    @Test
    void discoversAndValidatesProjectRecipes() {
        Environment environment = environment();
        for (String name : new String[]{PROJECT, RESOURCE}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
            assertEquals(name, recipe.getName());
        }
    }

    private static Stream<Arguments> unresolvedDeclarations() {
        String complex = "Complex react-dom range was not changed; replace it only after proving the intended supported React/renderer matrix";
        String dynamic = "Protocol, alias, tag or dynamic react-dom declaration was not changed; resolve its owner and compatible React DOM 19 target";
        return Stream.of(
                Arguments.of("16.x", complex),
                Arguments.of(">=16.14.0 <19", complex),
                Arguments.of("16.14.0 || ^18.2.0", complex),
                Arguments.of("16.6.1 - 18.2.0", complex),
                Arguments.of("v17.0.2", complex),
                Arguments.of("=18.2.0", complex),
                Arguments.of("workspace:^18.2.0", dynamic),
                Arguments.of("npm:@company/react-dom@18.2.0", dynamic),
                Arguments.of("file:../react-dom", dynamic),
                Arguments.of("github:facebook/react#v18.2.0", dynamic),
                Arguments.of("latest", dynamic),
                Arguments.of("${REACT_DOM_VERSION}", dynamic),
                Arguments.of("catalog:react-dom", dynamic)
        );
    }

    private static Stream<Arguments> companionPackages() {
        return Stream.of(
                companion("react", "18.2.0", "Align React exactly with the React DOM 19.0.x line; mismatched renderers can cause invalid hooks and internal protocol failures"),
                companion("@types/react", "18.3.20", "Align @types/react with React 19 and resolve ref, JSX, reducer and element-props type changes"),
                companion("@types/react-dom", "18.3.7", "Align @types/react-dom with React DOM 19 and resolve client/server/static entry-point types"),
                companion("react-test-renderer", "18.2.0", "react-test-renderer is deprecated and concurrent; align temporarily and migrate tests to a maintained renderer"),
                companion("@testing-library/react", "13.4.0", "Upgrade React Testing Library for React DOM 19 roots/act and re-run async and hydration tests"),
                companion("enzyme", "3.11.0", "Enzyme has no official React 18/19 adapter; replace it or explicitly own an unofficial adapter strategy"),
                companion("next", "12.1.0", "Use a Next.js release that declares React 19 support and verify RSC, SSR, hydration and bundler conditions"),
                companion("gatsby", "5.14.0", "Verify Gatsby React 19 support and test SSR, hydration, plugins and webpack aliases"),
                companion("react-router-dom", "5.3.4", "Verify this React Router DOM major supports React 19 roots, transitions, data routers and hydration"),
                companion("@vitejs/plugin-react", "4.0.4", "Verify the Vite React plugin and JSX transform support the selected React DOM 19 toolchain"),
                companion("react-scripts", "5.0.1", "Create React App is maintenance-only; verify its Babel, Jest, webpack and React 19 compatibility or migrate the build"),
                companion("scheduler", "0.23.0", "Do not pin React's internal scheduler independently unless the framework explicitly requires it; verify dependency deduplication")
        );
    }

    private static Arguments companion(String packageName, String version, String message) {
        return Arguments.of(packageName, version, message);
    }

    private static String mark(String message, String token) {
        return "~~(" + message + ")~~>" + token;
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.reactdom")
                .scanYamlResources()
                .build();
    }
}
