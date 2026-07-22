package com.huawei.clouds.openrewrite.markdownit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class MarkdownItRiskAndValidationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksComplexOrUnlistedDirectDeclarationAtValueNode() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItManifestRisks()),
                json("{\"dependencies\":{\"markdown-it\":\">=12.2.0 <15\"}}",
                        source -> {
                            source.path("package.json");
                            contains(source, "not the exact workbook target");
                        })
        );
    }

    @Test
    void targetDeclarationsAreClean() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItManifestRisks()),
                json("{\"dependencies\":{\"markdown-it\":\"14.3.0\"},\"devDependencies\":{\"markdown-it\":\"^14.3.0\"},\"peerDependencies\":{\"markdown-it\":\"~14.3.0\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void marksOverrideAndResolutionOwnershipButNotArbitraryNestedObjects() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItManifestRisks()),
                json(
                        """
                        {"overrides":{"markdown-it":"13.0.2"},"resolutions":{"markdown-it":"14.1.0"},"tool":{"markdown-it":"11.0.0"}}
                        """,
                        source -> source.path("package.json")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertEquals(2, occurrences(printed, "override/resolution"), printed);
                                }))
        );
    }

    @Test
    void marksOnlyPackageManagerOwnedOverridePaths() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItManifestRisks()),
                json(
                        """
                        {"pnpm":{"overrides":{"markdown-it":"13.0.2","docs>markdown-it":"14.3.0"}},"overrides":{"markdown-it@^13":"14.3.0","@company/markdown-it":"1.0.0"},"resolutions":{"**/markdown-it":"14.3.0"},"tool":{"overrides":{"markdown-it":"11.0.0"}},"nested":{"resolutions":{"markdown-it":"12.2.0"}}}
                        """,
                        source -> source.path("package.json")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertEquals(4, occurrences(printed, "override/resolution"), printed);
                                }))
        );
    }

    @Test
    void directProtocolAndAliasDeclarationsAreMarkedAtTheirValue() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItManifestRisks()),
                json(
                        """
                        {"dependencies":{"markdown-it":"workspace:^13.0.2"},"devDependencies":{"markdown-it":"npm:@company/markdown-it@12.2.0"}}
                        """,
                        source -> source.path("package.json")
                                .after(actual -> actual)
                                .afterRecipe(after ->
                                        assertEquals(2, occurrences(after.printAll(), "not the exact workbook target"),
                                                after.printAll())))
        );
    }

    @Test
    void marksDirectTypesAndPluginCompanionsPrecisely() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItManifestRisks()),
                json(
                        """
                        {"dependencies":{"markdown-it":"14.3.0","markdown-it-emoji":"^2.0.0","markdown-it-anchor":"^8.6.7","@types/markdown-it":"^13.0.7","markdown-itself":"1.0.0"},"config":{"markdown-it-plugin":"x"}}
                        """,
                        source -> source.path("package.json")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertEquals(3, occurrences(printed, "type/plugin package"), printed);
                                    assertFalse(printed.contains("~~>markdown-itself"), printed);
                                }))
        );
    }

    @Test
    void marksStaticDeepImportAtModuleLiteral() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                typescript("import Token from 'markdown-it/lib/token.mjs';\n",
                        source -> contains(source, "internal lib module"))
        );
    }

    @Test
    void marksStaticReExportAndDynamicImportModuleLiterals() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                typescript(
                        """
                        export { default as Token } from 'markdown-it/lib/token.mjs';
                        const lazy = import('markdown-it/lib/renderer.mjs');
                        """,
                        source -> source.path("src/module-references.ts")
                                .after(actual -> actual)
                                .afterRecipe(after ->
                                        assertEquals(2, occurrences(after.printAll(), "internal lib module"),
                                                after.printAll())))
        );
    }

    @Test
    void marksCommonJsDeepRequireWithEsmSpecificGuidance() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript("const Token = require('markdown-it/lib/token');\n",
                        source -> contains(source, "CommonJS require cannot safely load"))
        );
    }

    @Test
    void marksOldPublicIndexWhenRiskRecipeRunsAlone() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript("import MarkdownIt from 'markdown-it/index.js';\n",
                        source -> contains(source, "no longer resolves this legacy index subpath"))
        );
    }

    @Test
    void publicRootImportsAndRequiresAreClean() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript("import MarkdownIt from 'markdown-it';\nconst Cjs = require('markdown-it');\n",
                        source -> source.path("src/public.js"))
        );
    }

    @Test
    void marksRealDiscourseUnownedTextCollapseAnchorPrecisely() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript(
                        """
                        export default function setup(md, ruler) {
                          md.inline.ruler.push("bbcode-inline", (state, silent) => ruler(state, silent));
                          md.inline.ruler2.before("text_collapse", "bbcode-inline", processBBCode);
                        }
                        """,
                        source -> source.path("fixtures/discourse/bbcode-inline.js")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertEquals(1, occurrences(printed, "renamed inline rule text_collapse"), printed);
                                }))
        );
    }

    @Test
    void marksTextCollapseInsideBulkRuler2RuleLists() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript(
                        "md.inline.ruler2.enableOnly(['emphasis', 'text_collapse']);\n",
                        source -> source.path("src/unowned-bulk-rules.js")
                                .after(actual -> actual)
                                .afterRecipe(after ->
                                        assertEquals(1, occurrences(after.printAll(), "renamed inline rule text_collapse"),
                                                after.printAll())))
        );
    }

    @Test
    void marksCustomRulerRegistrationWhenMarkdownItOwnershipExists() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript(
                        """
                        import MarkdownIt from 'markdown-it';
                        const md = new MarkdownIt();
                        md.core.ruler.after('inline', 'audit', auditPlugin);
                        """,
                        source -> contains(source, "Custom markdown-it ruler integration"))
        );
    }

    @Test
    void doesNotMarkLookalikeRulerWithoutMarkdownItOwnership() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript("engine.core.ruler.after('inline', 'audit', auditPlugin);\n",
                        source -> source.path("src/unrelated.js"))
        );
    }

    @Test
    void marksRendererRuleOverrideWhenMarkdownItIsReferenced() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript(
                        """
                        const MarkdownIt = require('markdown-it');
                        const md = new MarkdownIt();
                        md.renderer.rules.image = function (tokens, idx, options, env, self) { return self.renderToken(tokens, idx, options); };
                        """,
                        source -> contains(source, "image-alt HTML/hardbreak rendering"))
        );
    }

    @Test
    void doesNotMarkUnrelatedRendererRuleAssignment() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript("engine.renderer.rules.image = renderImage;\n",
                        source -> source.path("src/unrelated-renderer.js"))
        );
    }

    @Test
    void doesNotMarkUnrelatedRendererOrRulerMerelyBecauseSameFileUsesMarkdownIt() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript(
                        """
                        import MarkdownIt from 'markdown-it';
                        const md = new MarkdownIt();
                        engine.renderer.rules.image = renderImage;
                        engine.core.ruler.after('inline', 'audit', auditPlugin);
                        """,
                        source -> source.path("src/mixed-engines.js"))
        );
    }

    @Test
    void marksRemovedDelimiterJumpAccess() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript(
                        """
                        import StateInline from 'markdown-it/lib/rules_inline/state_inline.mjs';
                        export function inspect(state, i) { return state.delimiters[i].jump; }
                        """,
                        source -> source.path("src/delimiters.js")
                                .after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("delimiters[].jump was removed"), printed);
                                    assertTrue(printed.contains("internal lib module"), printed);
                                }))
        );
    }

    @Test
    void doesNotMarkSimilarJumpWithoutPackageOwnership() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript("export const jump = state.delimiters[i].jump;\n",
                        source -> source.path("src/other-parser.js"))
        );
    }

    @Test
    void exactDeepModuleConfigStringIsMarkedButProseAndCommentsAreNot() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript(
                        """
                        // markdown-it/lib/token is mentioned only in a comment.
                        const prose = 'see markdown-it/lib/token before editing';
                        const aliasTarget = 'markdown-it/lib/token.mjs';
                        """,
                        source -> source.path("vite.config.js")
                                .after(actual -> actual)
                                .afterRecipe(after ->
                                        assertEquals(1, occurrences(after.printAll(), "internal lib module"), after.printAll())))
        );
    }

    @Test
    void exactModuleExampleStringInOrdinarySourceIsNotMarked() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript("const example = 'markdown-it/lib/token.mjs';\n",
                        source -> source.path("src/documentation-example.js"))
        );
    }

    @Test
    void textCollapseMarkerRequiresTheInlineRuler2Chain() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript(
                        """
                        engine.inline.ruler.before('text_collapse', 'x', rule);
                        engine.block.ruler.before('text_collapse', 'x', rule);
                        """,
                        source -> source.path("src/lookalike-rulers.js"))
        );
    }

    @Test
    void typeOnlyOrReassignedBindingsDoNotMarkLookalikeOwnedIntegration() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                typescript(
                        """
                        import type MarkdownIt from 'markdown-it';
                        declare const typed: MarkdownIt;
                        typed.core.ruler.after('inline', 'audit', rule);
                        """,
                        source -> source.path("src/type-only-owner.ts")),
                javascript(
                        """
                        const MarkdownIt = require('markdown-it');
                        let md = new MarkdownIt();
                        md = otherParser;
                        md.renderer.rules.image = renderImage;
                        """,
                        source -> source.path("src/reassigned-owner.js"))
        );
    }

    @Test
    void excludesGeneratedAndDependencySourceRisks() {
        rewriteRun(
                spec -> spec.recipe(new FindMarkdownItSourceRisks()),
                javascript("import Token from 'markdown-it/lib/token';\n",
                        source -> source.path("NODE_MODULES/markdown-it-plugin/index.js")),
                javascript("import Token from 'markdown-it/lib/token';\n",
                        source -> source.path("generated-docs/index.js"))
        );
    }

    @Test
    void recommendedRecipeCombinesStrictAutoAndPreciseMarks() {
        rewriteRun(
                spec -> spec.recipe(MarkdownItDependencyTest.environment()
                        .activateRecipes(MarkdownItDependencyTest.MIGRATE)),
                json("{\"dependencies\":{\"markdown-it\":\"^12.3.2\",\"markdown-it-emoji\":\"^2.0.0\"}}",
                        "{\"dependencies\":{\"markdown-it\":\"^14.3.0\",\"markdown-it-emoji\":/*~~(Validate this markdown-it type/plugin package against 14.3.0: v14 rewrote internals and bundled plugins to ESM, and markdown-it-emoji changed its signature)~~>*/\"^2.0.0\"}}",
                        source -> source.path("package.json").afterRecipe(after ->
                                assertTrue(after.printAll().contains("type/plugin package"), after.printAll()))),
                typescript(
                        """
                        import MarkdownIt from 'markdown-it/index.js';
                        import Token from 'markdown-it/lib/token';
                        const md = new MarkdownIt();
                        md.inline.ruler2.before('text_collapse', 'x', rule);
                        """,
                        """
                        import MarkdownIt from 'markdown-it';
                        import Token from /*~~(This code imports a markdown-it internal lib module; v14 renamed internals to .mjs and does not promise their API, so verify the target file/export and plugin behavior)~~>*/'markdown-it/lib/token.mjs';
                        const md = new MarkdownIt();
                        /*~~(Custom markdown-it ruler integration crosses parser changes: delimiter jump was removed, text_special/text_join were added, and invalid plugin progress now throws; test rule order, token positions, escaping, and pathological input)~~>*/md.inline.ruler2.before('fragments_join', 'x', rule);
                        """,
                        source -> source.path("src/migrate.ts").afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("internal lib module"), printed);
                            assertTrue(printed.contains("Custom markdown-it ruler integration"), printed);
                        }))
        );
    }

    @Test
    void manifestAndSourceMarkersAreIdempotentAcrossTwoCycles() {
        rewriteRun(
                spec -> spec.recipe(MarkdownItDependencyTest.environment()
                                .activateRecipes(MarkdownItDependencyTest.MIGRATE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"markdown-it\":\">=12 <15\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(), "not the exact workbook target"), after.printAll()))),
                javascript("const Token = require('markdown-it/lib/token');\n",
                        source -> source.path("src/legacy.js").after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(), "CommonJS require cannot safely load"), after.printAll())))
        );
    }

    @Test
    void recommendedRecipeDiscoveryAndValidationRemainStable() {
        Environment environment = MarkdownItDependencyTest.environment();
        Recipe recipe = environment.activateRecipes(MarkdownItDependencyTest.MIGRATE);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static <T extends org.openrewrite.SourceFile> void contains(
            org.openrewrite.test.SourceSpec<T> source, String text) {
        source.after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains(text), after.printAll()));
    }

    private static int occurrences(String text, String fragment) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(fragment, index)) >= 0; index += fragment.length()) count++;
        return count;
    }
}
