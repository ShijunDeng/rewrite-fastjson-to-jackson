package com.huawei.clouds.openrewrite.mermaid;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

/** Reduced fixtures from the immutable repository revisions documented in README.md. */
class MermaidRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.mermaid.MigrateMermaidTo11_15_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(MermaidDependencyUpgradeTest.environment().activateRecipes(RECOMMENDED));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesVueDevuiPinnedManifestFixture() {
        rewriteRun(json(
                """
                {"name":"@devui-design/devui-vue","dependencies":{"markdown-it-emoji":"^3.0.0","markdown-it-plantuml":"^1.4.1","mermaid":"9.1.1","mitt":"^3.0.0"}}
                """,
                """
                {"name":"@devui-design/devui-vue","dependencies":{"markdown-it-emoji":"^3.0.0","markdown-it-plantuml":"^1.4.1","mermaid":"11.15.0","mitt":"^3.0.0"}}
                """, source -> source.path("packages/devui-vue/package.json")));
    }

    @Test
    void migratesApacheDevlakeWebsitePinnedManifestFixture() {
        rewriteRun(json(
                """
                {"name":"website","dependencies":{"mdx-mermaid":"^1.3.2","mermaid":"^9.1.3","postcss":"^8.4.16"}}
                """,
                """
                {"name":"website","dependencies":{"mdx-mermaid":"^1.3.2","mermaid":"^11.15.0","postcss":"^8.4.16"}}
                """, source -> source.path("package.json")));
    }

    @Test
    void migratesCherryMarkdownPinnedManifestFixture() {
        rewriteRun(json(
                """
                {"name":"cherry-markdown","optionalDependencies":{"mermaid":"9.4.3"}}
                """,
                """
                {"name":"cherry-markdown","optionalDependencies":{"mermaid":"11.15.0"}}
                """, source -> source.path("packages/cherry-markdown/package.json")));
    }

    @Test
    void migratesOfficialV9AsyncAliasesThenMarksPromiseAndInternalApiReview() {
        rewriteRun(typescript(
                """
                import mermaid from 'mermaid';
                const valid = await mermaid.parseAsync(txt);
                const result = await mermaid.mermaidAPI.renderAsync(id, txt);
                """, source -> source.path("src/integration.ts").after(actual -> actual)
                        .afterRecipe(cu -> {
                            String printed = cu.printAll();
                            assertFalse(printed.contains("parseAsync"));
                            assertFalse(printed.contains("renderAsync"));
                            assertTrue(printed.contains("mermaid.parse(txt)"));
                            assertTrue(printed.contains("Promise-based"));
                            assertTrue(printed.contains("deprecated/internal"));
                        })));
    }

    @Test
    void preservesClassicCdnUntilSriCspAndEsmDecisionsAreMade() {
        rewriteRun(text(
                "<script src='https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/mermaid.min.js'></script>",
                source -> source.path("public/index.html").after(actual -> actual)
                        .afterRecipe(file -> {
                            String printed = file.printAll();
                            assertTrue(printed.contains("mermaid@9.4.3"));
                            assertTrue(printed.contains("ESM-only"));
                            assertTrue(printed.contains("SRI"));
                        })));
    }
}
