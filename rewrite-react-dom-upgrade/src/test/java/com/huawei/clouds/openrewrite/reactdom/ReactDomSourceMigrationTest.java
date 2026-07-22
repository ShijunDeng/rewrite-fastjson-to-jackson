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

class ReactDomSourceMigrationTest implements RewriteTest {
    private static final String SOURCE = "com.huawei.clouds.openrewrite.reactdom.MigrateDeterministicReactDomSourceTo19";
    private static final String AUDIT = "com.huawei.clouds.openrewrite.reactdom.AuditReactDom19SourceCompatibility";
    private static final String MIGRATION = "com.huawei.clouds.openrewrite.reactdom.MigrateReactDomTo19_0_0";

    @Test
    void migratesOfficialReactCodemodRenderFixture() {
        // reactjs/react-codemod, fixed commit 5207d594fad6f8b39c51fd7edd2bcb51047dc872.
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "import ReactDom from \"react-dom\";\nimport Component from \"Component\";\n\n" +
                        "ReactDom.render(<Component />, document.getElementById(\"app\"));\n",
                        "import { createRoot } from \"react-dom/client\";\nimport ReactDom from \"react-dom\";\n" +
                        "import Component from \"Component\";\n\nconst root = createRoot(document.getElementById(\"app\"));\n" +
                        "root.render(<Component />);\n",
                        source -> source.path("transforms/__testfixtures__/replace-reactdom-render/default.input.js")
                )
        );
    }

    @Test
    void migratesRealDesignPatternsBootstrap() {
        // zoltantothcom/Design-Patterns-JavaScript, fixed commit 2c7ef902dbefb8a7a2ecea407ac7e8e6682f5b0a.
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "import ReactDOM from 'react-dom';\nReactDOM.render(<App />, document.getElementById('root'));\n",
                        "import { createRoot } from 'react-dom/client';\nimport ReactDOM from 'react-dom';\n" +
                        "const root = createRoot(document.getElementById('root'));\nroot.render(<App />);\n",
                        source -> source.path("index.js")
                )
        );
    }

    @Test
    void migratesNamespaceHydrateWithSimpleContainer() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "import * as DOM from 'react-dom';\nDOM.hydrate(<App />, container);\n",
                        "import { hydrateRoot } from 'react-dom/client';\nimport * as DOM from 'react-dom';\n" +
                        "hydrateRoot(container, <App />);\n",
                        source -> source.path("src/client.tsx")
                )
        );
    }

    @Test
    void migratesSoleNamedRenderImportAndCall() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "import { render } from 'react-dom';\nrender(<App />, node);\n",
                        "import { createRoot } from 'react-dom/client';\nconst root = createRoot(node);\nroot.render(<App />);\n",
                        source -> source.path("src/named.jsx")
                )
        );
    }

    @Test
    void migratesSoleNamedHydrateImportAndCall() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "import { hydrate } from \"react-dom\";\nhydrate(<App />, node);\n",
                        "import { hydrateRoot } from \"react-dom/client\";\nhydrateRoot(node, <App />);\n",
                        source -> source.path("src/named-hydrate.jsx")
                )
        );
    }

    @Test
    void migratesExactActImportAndImplicitRefAssignment() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "import { act } from 'react-dom/test-utils';\nconst view = <div ref={node => (ref.current = node)} />;\n",
                        "import { act } from 'react';\nconst view = <div ref={node => { ref.current = node; }} />;\n",
                        source -> source.path("src/App.test.tsx")
                )
        );
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text(
                        "import ReactDOM from 'react-dom';\nReactDOM.render(<App />, node);\n",
                        "import { createRoot } from 'react-dom/client';\nimport ReactDOM from 'react-dom';\n" +
                        "const root = createRoot(node);\nroot.render(<App />);\n",
                        source -> source.path("src/idempotent.jsx")
                )
        );
    }

    @Test
    void preservesCrLfLineEndings() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "import { render } from \"react-dom\";\r\nrender(<App />, node);\r\n",
                        "import { createRoot } from \"react-dom/client\";\r\nconst root = createRoot(node);\r\nroot.render(<App />);\r\n",
                        source -> source.path("src/windows.jsx")
                )
        );
    }

    @Test
    void leavesCallbacksMultipleRootsReturnValuesAndBindingConflictsUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        import ReactDOM from 'react-dom';
                        const root = registry.root;
                        const instance = ReactDOM.render(<App />, first, done);
                        ReactDOM.render(<Admin />, second);
                        """,
                        source -> source.path("src/unsafe.jsx")
                )
        );
    }

    @Test
    void leavesCommentsStringsTemplatesRegexAndLookalikesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        // ReactDOM.render(<App />, node)
                        /* ReactDOM.hydrate(<App />, node) */
                        const docs = "ReactDOM.render(<App />, node)";
                        const template = `ReactDOM.hydrate(<App />, node)`;
                        const matcher = /ReactDOM\\.render/;
                        renderer.render(<App />, node);
                        """,
                        source -> source.path("src/docs.tsx")
                )
        );
    }

    @Test
    void leavesSupportedCreatePortalAndModernViteRootUntouched() {
        // feedzai/brushable-histogram a46666e5 and vitejs/vite 055d2b86 fixed source shapes.
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text("import ReactDOM from 'react-dom';\nexport const Portal = p => ReactDOM.createPortal(p.children, p.element);\n",
                        source -> source.path("src/common/components/Portal.js")),
                text("import ReactDOM from 'react-dom/client';\nReactDOM.createRoot(node).render(<App />);\n",
                        source -> source.path("packages/create-vite/template-react/src/main.jsx"))
        );
    }

    @Test
    void sourceMigrationLeavesUnsupportedFilesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text("ReactDOM.render(<App />, node);\n", source -> source.path("docs/migration.md")),
                text("<script>ReactDOM.render(App, node)</script>\n", source -> source.path("public/index.html"))
        );
    }

    @Test
    void marksFaithlifeRealMultilineRender() {
        // Faithlife/styled-ui, fixed commit 4b771c63d1ed3381dfc40c29a87416402364d0bd.
        String message = "React DOM 19 removed render/hydrate; manually migrate callbacks, multiple roots, return values or complex containers to client roots";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "ReactDOM.render(\n  <Catalog pages={pages} />,\n  document.getElementById('catalog'),\n);\n",
                        mark(message, "ReactDOM.render") + "(\n  <Catalog pages={pages} />,\n  document.getElementById('catalog'),\n);\n",
                        source -> source.path("catalog/index.js")
                )
        );
    }

    @Test
    void marksTestingLibraryRealLegacyUnmount() {
        // testing-library/react-testing-library, fixed commit be9d81d91314c9f0bafaa363f70b409b4b31989c.
        String token = "ReactDOM.unmountComponentAtNode";
        String message = "React DOM 19 removed unmountComponentAtNode; retain the creating root and call root.unmount() in the correct host teardown";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text("return " + token + "(container)\n", "return " + mark(message, token) + "(container)\n",
                        source -> source.path("src/pure.js"))
        );
    }

    @Test
    void marksRealMultilineHydrationAndNodeStream() {
        // simpletut/Universal-React-Apollo-Registration 26f4864 and
        // chin2km/styled-components-react-streaming-bug b471ecd fixed commits.
        String hydrateMessage = "React DOM 19 removed render/hydrate; manually migrate callbacks, multiple roots, return values or complex containers to client roots";
        String streamMessage = "renderToNodeStream is obsolete; redesign Node streaming around renderToPipeableStream including shell, abort, errors and backpressure";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text("ReactDOM.hydrate(\n  <App />,\n  document.querySelector('#root')\n);\n",
                        mark(hydrateMessage, "ReactDOM.hydrate") + "(\n  <App />,\n  document.querySelector('#root')\n);\n",
                        source -> source.path("src/client.js")),
                text("const stream = ReactDOMServer.renderToNodeStream(jsx);\n",
                        "const stream = ReactDOMServer." + mark(streamMessage, "renderToNodeStream") + "(jsx);\n",
                        source -> source.path("src/ssr-render-methods/renderToNodeStream.js"))
        );
    }

    @ParameterizedTest(name = "marks React DOM source risk {0}")
    @MethodSource("sourceRiskCases")
    void marksEachContextDependentSourceRisk(String name, String path, String before, String token, String message) {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(before, before.replace(token, mark(message, token)), source -> source.path(path))
        );
    }

    @Test
    void auditLeavesCommentsLiteralsPortalsAndModernUnmountUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        """
                        // ReactDOM.render(<App />, node)
                        const docs = "findDOMNode(component), renderToNodeStream(view)";
                        const template = `ReactDOM.unmountComponentAtNode(node)`;
                        const matcher = /ReactDOM\\.hydrate/;
                        const portal = ReactDOM.createPortal(children, node);
                        root.unmount();
                        """,
                        source -> source.path("src/noop.tsx")
                )
        );
    }

    @Test
    void auditLeavesUnimportedPlainTypeScriptLookalikesUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        """
                        function findDOMNode(value: unknown) { return value; }
                        function renderToString(value: unknown) { return String(value); }
                        function createRoot(path: string) { return path; }
                        function flushSync(work: () => void) { work(); }
                        """,
                        source -> source.path("src/unrelated.ts")
                )
        );
    }

    @Test
    void recommendedRecipeUpgradesMigratesAndMarksRemainingWork() {
        String reactMessage = "Align React exactly with the React DOM 19.0.x line; mismatched renderers can cause invalid hooks and internal protocol failures";
        String rootMessage = "React DOM 19 changed root error reporting; verify Error Boundaries, monitoring and onUncaughtError/onCaughtError callbacks";
        String umdMessage = "React DOM 19 removed UMD builds; use an ESM CDN or bundler and align react/react-dom together";
        String url = "https://unpkg.com/react-dom@18.2.0/umd/react-dom.production.min.js";
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION)),
                json(
                        "{\"dependencies\":{\"react\":\"18.2.0\",\"react-dom\":\"^18.2.0\"}}",
                        "{\"dependencies\":{/*~~(" + reactMessage + ")~~>*/\"react\":\"18.2.0\",\"react-dom\":\"19.0.0\"}}",
                        source -> source.path("package.json")
                ),
                text(
                        "import ReactDOM from 'react-dom';\nReactDOM.render(<App />, node);\n",
                        "import { createRoot } from 'react-dom/client';\nimport ReactDOM from 'react-dom';\n" +
                        "const root = " + mark(rootMessage, "createRoot") + "(node);\nroot.render(<App />);\n",
                        source -> source.path("src/main.jsx")
                ),
                text("<script src=\"" + url + "\"></script>\n",
                        "<script src=\"" + mark(umdMessage, url) + "\"></script>\n",
                        source -> source.path("public/index.html"))
        );
    }

    @Test
    void discoversAndValidatesSourceAndRecommendedRecipes() {
        Environment environment = environment();
        for (String name : new String[]{SOURCE, AUDIT, MIGRATION}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
            assertEquals(name, recipe.getName());
        }
    }

    private static Stream<Arguments> sourceRiskCases() {
        return Stream.of(
                risk("render callback", "src/root.jsx", "ReactDOM.render(<App />, node, done);\n", "ReactDOM.render",
                        "React DOM 19 removed render/hydrate; manually migrate callbacks, multiple roots, return values or complex containers to client roots"),
                risk("named root import", "src/root.jsx", "import { render, findDOMNode } from 'react-dom';\n", "import { render, findDOMNode } from 'react-dom'",
                        "React DOM 19 removed this named legacy DOM API; migrate every binding with explicit root/ref ownership"),
                risk("unmount", "src/root.jsx", "ReactDOM.unmountComponentAtNode(node);\n", "ReactDOM.unmountComponentAtNode",
                        "React DOM 19 removed unmountComponentAtNode; retain the creating root and call root.unmount() in the correct host teardown"),
                risk("findDOMNode", "src/legacy.jsx", "ReactDOM.findDOMNode(component);\n", "ReactDOM.findDOMNode",
                        "React DOM 19 removed findDOMNode; use an owned/forwarded DOM ref and upgrade third-party transition or focus integrations"),
                risk("test utils", "src/test.tsx", "import { Simulate } from 'react-dom/test-utils';\n", "import { Simulate } from 'react-dom/test-utils'",
                        "React DOM 19 keeps only a deprecated act bridge in test-utils; import act from react and replace Simulate/renderIntoDocument helpers"),
                risk("dynamic test utils", "src/test.ts", "const utils = await import('react-dom/test-utils');\n", "import('react-dom/test-utils')",
                        "React DOM 19 keeps only a deprecated act bridge in test-utils; migrate this dynamic test integration and its fallback behavior"),
                risk("unstable API", "src/integration.js", "ReactDOM.unstable_flushControlled(update);\n", "ReactDOM.unstable_flushControlled",
                        "React DOM 19 removed this unstable API; choose a supported public root, event, scheduling or framework integration"),
                risk("internals", "src/integration.js", "ReactDOM.SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED;\n", "ReactDOM.SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED",
                        "React DOM 19 blocks secret internals; replace this coupling with a supported public API and upgrade the owning library"),
                risk("node stream", "server/render.tsx", "renderToNodeStream(<App />);\n", "renderToNodeStream",
                        "renderToNodeStream is obsolete; redesign Node streaming around renderToPipeableStream including shell, abort, errors and backpressure"),
                risk("string SSR", "server/render.tsx", "renderToString(<App />);\n", "renderToString",
                        "Legacy non-streaming SSR has Suspense/data limitations; verify that blocking markup remains intentional before React DOM 19"),
                risk("streaming SSR", "server/render.tsx", "renderToPipeableStream(<App />, options);\n", "renderToPipeableStream",
                        "Verify React DOM 19 streaming runtime, shell/error status, abort, bootstrap, nonce, backpressure and hydration behavior"),
                risk("static prerender", "server/static.tsx", "prerender(<App />, options);\n", "prerender",
                        "React DOM static prerender waits for data and is not streaming SSR; verify runtime, abort, cache and SSG semantics"),
                risk("runtime entry", "server/render.ts", "import { renderToReadableStream } from 'react-dom/server.browser';\n", "import { renderToReadableStream } from 'react-dom/server.browser'",
                        "Runtime-specific React DOM entry point found; verify bundler conditions and deploy runtime before changing server/static imports"),
                risk("deep import", "src/internal.js", "import secret from 'react-dom/cjs/react-dom.production.min.js';\n", "import secret from 'react-dom/cjs/react-dom.production.min.js'",
                        "Unsupported React DOM deep import bypasses package exports; replace it with a documented client/server/static entry point"),
                risk("hydration", "src/client.tsx", "hydrateRoot(node, <App />);\n", "hydrateRoot",
                        "Verify server/client markup, identifierPrefix, mismatch recovery and onRecoverableError under React DOM 19 hydration"),
                risk("root errors", "src/client.tsx", "const root = createRoot(node);\n", "createRoot",
                        "React DOM 19 changed root error reporting; verify Error Boundaries, monitoring and onUncaughtError/onCaughtError callbacks"),
                risk("error callback", "src/client.tsx", "onRecoverableError: report,\n", "onRecoverableError:",
                        "Root error callback semantics changed in React DOM 19; verify deduplication, reportError/console behavior and callback payloads"),
                risk("digest", "src/errors.ts", "import React from 'react';\nconst digest = errorInfo.digest;\n", "errorInfo.digest",
                        "React DOM 19 removed errorInfo.digest from onRecoverableError; read digest from the Error when the framework provides it"),
                risk("identifier prefix", "src/client.tsx", "identifierPrefix: 'shop-',\n", "identifierPrefix:",
                        "Keep identifierPrefix identical across server and client and unique across multiple hydrated roots"),
                risk("batching", "src/measure.tsx", "flushSync(() => setOpen(true));\n", "flushSync",
                        "Concurrent roots batch more updates; re-check render ordering and keep flushSync only for required synchronous DOM reads"),
                risk("act environment", "test/setup.ts", "import React from 'react';\nglobalThis.IS_REACT_ACT_ENVIRONMENT = true;\n", "IS_REACT_ACT_ENVIRONMENT",
                        "Verify the custom act environment flag with the upgraded test runner/library and async scheduling behavior"),
                risk("StrictMode", "src/main.tsx", "<React.StrictMode><App /></React.StrictMode>;\n", "<React.StrictMode",
                        "StrictMode replays effects and ref callbacks in development; verify idempotent setup and symmetric cleanup"),
                risk("Suspense", "src/App.tsx", "<Suspense fallback={<Wait />}><Page /></Suspense>;\n", "<Suspense",
                        "React 18/19 changed hydration and Suspense fallback/retry ordering; test boundary recovery, reveal order and effects"),
                risk("ref callback", "src/ref.tsx", "<div ref={node => cache(node)} />;\n", "ref={node =>",
                        "React 19 ref callbacks may return cleanup; make implicit returns explicit and verify attach/detach behavior"),
                risk("javascript URL", "src/Link.tsx", "<a href=\"javascript:run()\">Run</a>;\n", "href=\"javascript:",
                        "React DOM 19 rejects javascript: URLs and changed empty src/href handling; sanitize or omit this attribute explicitly"),
                risk("event pooling", "src/events.ts", "import React from 'react';\nevent.persist();\n", "event.persist",
                        "React 17 removed SyntheticEvent pooling; persist() is unnecessary and code retaining old pooling assumptions needs review"),
                risk("scroll bubbling", "src/List.tsx", "<div onScroll={handleScroll} />;\n", "onScroll=",
                        "React 17 stopped emulating scroll bubbling; verify the intended capture/target listener and nested scroll behavior")
        );
    }

    private static Arguments risk(String name, String path, String before, String token, String message) {
        return Arguments.of(name, path, before, token, message);
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
