package com.huawei.clouds.openrewrite.mermaid;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;

class MermaidJavaScriptRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "await mermaid.parse(text);",
            "mermaid.parse(text, onError);",
            "await mermaid.render(id, text);",
            "mermaid.render(id, text, callback);",
            "mermaid.init(config, '.mermaid', done);",
            "mermaid.initThrowsErrors(config, nodes, done);",
            "mermaid.initialize({ startOnLoad: true });",
            "await mermaid.run({ querySelector: '.mermaid' });",
            "mermaid.setParseErrorHandler(onError);",
            "mermaid.registerExternalDiagrams(diagrams);",
            "mermaid.registerLayoutLoaders(loaders);",
            "mermaid.registerIconPacks(packs);"
    })
    void marksOwnedApiBoundaries(String statement) {
        assertMarked("import mermaid from 'mermaid';\n" + statement + "\n", "src/api.ts");
    }

    @Test
    void marksDeepImportsRequireAndDynamicImport() {
        rewriteRun(spec -> spec.recipe(new FindMermaid11JavaScriptRisks()),
                typescript("import mermaid from 'mermaid/dist/mermaid.esm.min.mjs';\n",
                        source -> source.path("src/deep.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("distribution/deep import")))),
                typescript("const mermaid = require('mermaid');\n",
                        source -> source.path("src/commonjs.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("ESM-only")))),
                typescript("const module = await import('mermaid');\n",
                        source -> source.path("src/lazy.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("bundler/SSR boundary")))));
    }

    @Test
    void marksGlobalMutationAndDeprecatedInternalApi() {
        rewriteRun(spec -> spec.recipe(new FindMermaid11JavaScriptRisks()),
                typescript(
                        """
                        import diagram from 'mermaid';
                        diagram.startOnLoad = false;
                        diagram.parseError = onError;
                        await diagram.mermaidAPI.parse(text);
                        """, source -> source.path("src/global.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Direct startOnLoad mutation"));
                                    assertTrue(printed.contains("Global parseError mutation"));
                                    assertTrue(printed.contains("deprecated/internal"));
                                })));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "securityLevel: 'loose'", "htmlLabels: true", "theme: 'forest'", "themeVariables: vars",
            "themeCSS: css", "curve: 'basis'", "hierarchicalNamespaces: false", "deterministicIds: true",
            "deterministicIDSeed: 'app'", "maxTextSize: 50000", "maxEdges: 1000",
            "suppressErrorRendering: true", "fontFamily: 'Inter'", "useMaxWidth: false"
    })
    void marksOwnedConfigurationBoundaries(String property) {
        assertMarked("import mermaid from 'mermaid';\nmermaid.initialize({ " + property + " });\n",
                "src/config.ts");
    }

    @Test
    void ignoresUnrelatedApisConfigAndExcludedSources() {
        rewriteRun(spec -> spec.recipe(new FindMermaid11JavaScriptRisks()),
                typescript(
                        """
                        import mermaid from 'not-mermaid';
                        mermaid.render(id, text, callback);
                        other.initialize({ securityLevel: 'loose', theme: 'dark' });
                        """, source -> source.path("src/unrelated.ts").afterRecipe(cu ->
                                assertFalse(cu.printAll().contains("~~")))),
                typescript("import mermaid from 'mermaid'; mermaid.render(id, text);",
                        source -> source.path("dist/bundle.ts")));
    }

    @Test
    void markerRecipeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindMermaid11JavaScriptRisks()).cycles(2),
                typescript("import mermaid from 'mermaid';\nawait mermaid.render(id, text);\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("Promise-based"));
                                    assertFalse(printed.contains("~~(~~("));
                                })));
    }

    private void assertMarked(String sourceCode, String path) {
        rewriteRun(spec -> spec.recipe(new FindMermaid11JavaScriptRisks()),
                typescript(sourceCode, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(cu -> assertTrue(cu.printAll().contains("~~")))));
    }
}
