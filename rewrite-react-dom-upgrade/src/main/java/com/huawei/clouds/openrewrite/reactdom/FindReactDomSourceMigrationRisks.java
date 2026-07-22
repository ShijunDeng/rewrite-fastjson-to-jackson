package com.huawei.clouds.openrewrite.reactdom;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Mark React DOM 17/18/19 source changes that need binding, root, runtime or framework context. */
public final class FindReactDomSourceMigrationRisks extends Recipe {
    private static final String IDENTIFIER = "[A-Za-z_$][\\w$]*";
    private static final Pattern DEFAULT_IMPORT = Pattern.compile(
            "\\bimport\\s+(?<alias>" + IDENTIFIER + ")\\s+from\\s*([\"'])react-dom\\2");
    private static final Pattern NAMESPACE_IMPORT = Pattern.compile(
            "\\bimport\\s*\\*\\s*as\\s*(?<alias>" + IDENTIFIER + ")\\s*from\\s*([\"'])react-dom\\2");
    private static final Pattern REQUIRE = Pattern.compile(
            "\\b(?:const|let|var)\\s+(?<alias>" + IDENTIFIER + ")\\s*=\\s*require\\(\\s*([\"'])react-dom\\2\\s*\\)");
    private static final Pattern REACT_EVIDENCE = Pattern.compile(
            "(?m)\\bimport\\b[^;\\r\\n]*?(?:from\\s*)?([\"'])react(?:-dom|-test-renderer)?(?:/[^\"']+)?\\1|" +
            "\\brequire\\s*\\(\\s*([\"'])react(?:-dom|-test-renderer)?(?:/[^\"']+)?\\2\\s*\\)|" +
            "\\bReact(?:DOM(?:Server)?)?\\s*[.]");

    @Override
    public String getDisplayName() {
        return "Find React DOM 19 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Precisely mark removed roots, DOM lookup, unmount, test and unstable APIs plus hydration, " +
               "server rendering, root errors, batching, StrictMode, Suspense, DOM and type-sensitive changes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                if (!ReactDomSourceText.isSupported(visited)) {
                    return visited;
                }
                String source = visited.getText();
                Set<String> aliases = new LinkedHashSet<>();
                aliases.add("ReactDOM");
                ReactDomSourceText.collectCodeGroup(source, DEFAULT_IMPORT, "alias", aliases);
                ReactDomSourceText.collectCodeGroup(source, NAMESPACE_IMPORT, "alias", aliases);
                ReactDomSourceText.collectCodeGroup(source, REQUIRE, "alias", aliases);
                String path = visited.getSourcePath().toString().toLowerCase(Locale.ROOT);
                boolean reactEvidence = path.endsWith(".jsx") || path.endsWith(".tsx") ||
                                        ReactDomSourceText.hasCodeMatch(source, REACT_EVIDENCE);
                return ReactDomSourceText.markMatches(visited, risks(aliases, reactEvidence));
            }
        };
    }

    private static List<ReactDomSourceText.RiskPattern> risks(Set<String> domAliases, boolean reactEvidence) {
        List<ReactDomSourceText.RiskPattern> risks = new ArrayList<>();
        String aliases = domAliases.stream().map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right).orElse("ReactDOM");
        risks.add(risk(Pattern.compile("\\b(?:" + aliases + ")\\s*[.]\\s*(?:render|hydrate)(?=\\s*\\()"),
                "React DOM 19 removed render/hydrate; manually migrate callbacks, multiple roots, return values or complex containers to client roots"));
        risks.add(risk(Pattern.compile("\\bimport\\s*\\{[^}]*\\b(?:render|hydrate|unmountComponentAtNode|findDOMNode)\\b[^}]*}\\s*from\\s*([\"'])react-dom\\1"),
                "React DOM 19 removed this named legacy DOM API; migrate every binding with explicit root/ref ownership"));
        risks.add(risk(Pattern.compile("\\b(?:" + aliases + ")\\s*[.]\\s*unmountComponentAtNode(?=\\s*\\()"),
                "React DOM 19 removed unmountComponentAtNode; retain the creating root and call root.unmount() in the correct host teardown"));
        risks.add(risk(Pattern.compile("\\b(?:" + aliases + ")\\s*[.]\\s*findDOMNode(?=\\s*\\()"),
                "React DOM 19 removed findDOMNode; use an owned/forwarded DOM ref and upgrade third-party transition or focus integrations"));
        risks.add(risk(Pattern.compile("\\b(?:import|export)\\s+.*?from\\s*([\"'])react-dom/test-utils\\1"),
                "React DOM 19 keeps only a deprecated act bridge in test-utils; import act from react and replace Simulate/renderIntoDocument helpers"));
        risks.add(risk(Pattern.compile("\\b(?:import|require)\\s*\\(\\s*([\"'])react-dom/test-utils\\1\\s*\\)"),
                "React DOM 19 keeps only a deprecated act bridge in test-utils; migrate this dynamic test integration and its fallback behavior"));
        risks.add(risk(Pattern.compile("\\b(?:" + aliases + ")\\s*[.]\\s*unstable_(?:flushControlled|createEventHandle|renderSubtreeIntoContainer|runWithPriority)(?=\\s*\\()"),
                "React DOM 19 removed this unstable API; choose a supported public root, event, scheduling or framework integration"));
        risks.add(risk(Pattern.compile("\\b(?:" + aliases + ")\\s*[.]\\s*SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED\\b"),
                "React DOM 19 blocks secret internals; replace this coupling with a supported public API and upgrade the owning library"));
        if (!reactEvidence) {
            return risks;
        }
        risks.add(risk(Pattern.compile("\\bunmountComponentAtNode(?=\\s*\\()"),
                "React DOM 19 removed unmountComponentAtNode; retain the creating root and call root.unmount() in the correct host teardown"));
        risks.add(risk(Pattern.compile("\\bfindDOMNode(?=\\s*\\()"),
                "React DOM 19 removed findDOMNode; use an owned/forwarded DOM ref and upgrade third-party transition or focus integrations"));
        risks.add(risk(Pattern.compile("\\bunstable_(?:flushControlled|createEventHandle|renderSubtreeIntoContainer|runWithPriority)(?=\\s*\\()"),
                "React DOM 19 removed this unstable API; choose a supported public root, event, scheduling or framework integration"));
        risks.add(risk(Pattern.compile("\\bSECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED\\b"),
                "React DOM 19 blocks secret internals; replace this coupling with a supported public API and upgrade the owning library"));
        risks.add(risk(Pattern.compile("\\brenderToNodeStream(?=\\s*\\()"),
                "renderToNodeStream is obsolete; redesign Node streaming around renderToPipeableStream including shell, abort, errors and backpressure"));
        risks.add(risk(Pattern.compile("\\b(?:renderToString|renderToStaticMarkup)(?=\\s*\\()"),
                "Legacy non-streaming SSR has Suspense/data limitations; verify that blocking markup remains intentional before React DOM 19"));
        risks.add(risk(Pattern.compile("\\b(?:renderToPipeableStream|renderToReadableStream)(?=\\s*\\()"),
                "Verify React DOM 19 streaming runtime, shell/error status, abort, bootstrap, nonce, backpressure and hydration behavior"));
        risks.add(risk(Pattern.compile("\\b(?:prerender|prerenderToNodeStream)(?=\\s*\\()"),
                "React DOM static prerender waits for data and is not streaming SSR; verify runtime, abort, cache and SSG semantics"));
        risks.add(risk(Pattern.compile("\\bimport\\s+.*?from\\s*([\"'])react-dom/(?:server|static)[.](?:browser|node|edge|bun)\\1"),
                "Runtime-specific React DOM entry point found; verify bundler conditions and deploy runtime before changing server/static imports"));
        risks.add(risk(Pattern.compile("\\b(?:import|require)\\s*(?:\\(|).*?([\"'])react-dom/(?:cjs|src)/[^\"']+\\1"),
                "Unsupported React DOM deep import bypasses package exports; replace it with a documented client/server/static entry point"));
        risks.add(risk(Pattern.compile("\\bhydrateRoot(?=\\s*\\()"),
                "Verify server/client markup, identifierPrefix, mismatch recovery and onRecoverableError under React DOM 19 hydration"));
        risks.add(risk(Pattern.compile("\\bcreateRoot(?=\\s*\\()"),
                "React DOM 19 changed root error reporting; verify Error Boundaries, monitoring and onUncaughtError/onCaughtError callbacks"));
        risks.add(risk(Pattern.compile("\\b(?:onRecoverableError|onUncaughtError|onCaughtError)\\s*[:=]"),
                "Root error callback semantics changed in React DOM 19; verify deduplication, reportError/console behavior and callback payloads"));
        risks.add(risk(Pattern.compile("\\berrorInfo\\s*[.]\\s*digest\\b"),
                "React DOM 19 removed errorInfo.digest from onRecoverableError; read digest from the Error when the framework provides it"));
        risks.add(risk(Pattern.compile("\\bidentifierPrefix\\s*[:=]"),
                "Keep identifierPrefix identical across server and client and unique across multiple hydrated roots"));
        risks.add(risk(Pattern.compile("\\b(?:unstable_batchedUpdates|flushSync)(?=\\s*\\()"),
                "Concurrent roots batch more updates; re-check render ordering and keep flushSync only for required synchronous DOM reads"));
        risks.add(risk(Pattern.compile("\\bIS_REACT_ACT_ENVIRONMENT\\b"),
                "Verify the custom act environment flag with the upgraded test runner/library and async scheduling behavior"));
        risks.add(risk(Pattern.compile("<(?:React\\s*[.]\\s*)?StrictMode\\b"),
                "StrictMode replays effects and ref callbacks in development; verify idempotent setup and symmetric cleanup"));
        risks.add(risk(Pattern.compile("<(?:React\\s*[.]\\s*)?Suspense\\b"),
                "React 18/19 changed hydration and Suspense fallback/retry ordering; test boundary recovery, reveal order and effects"));
        risks.add(risk(Pattern.compile("\\bref\\s*=\\s*\\{\\s*" + IDENTIFIER + "\\s*=>\\s*(?!\\{)"),
                "React 19 ref callbacks may return cleanup; make implicit returns explicit and verify attach/detach behavior"));
        risks.add(risk(Pattern.compile("\\b(?:src|href)\\s*=\\s*[\"'](?:javascript:|[\"'])"),
                "React DOM 19 rejects javascript: URLs and changed empty src/href handling; sanitize or omit this attribute explicitly"));
        risks.add(risk(Pattern.compile("\\b(?:event|e)\\s*[.]\\s*persist(?=\\s*\\()"),
                "React 17 removed SyntheticEvent pooling; persist() is unnecessary and code retaining old pooling assumptions needs review"));
        risks.add(risk(Pattern.compile("\\bonScroll\\s*="),
                "React 17 stopped emulating scroll bubbling; verify the intended capture/target listener and nested scroll behavior"));
        return risks;
    }

    private static ReactDomSourceText.RiskPattern risk(Pattern pattern, String message) {
        return new ReactDomSourceText.RiskPattern(pattern, message);
    }
}
