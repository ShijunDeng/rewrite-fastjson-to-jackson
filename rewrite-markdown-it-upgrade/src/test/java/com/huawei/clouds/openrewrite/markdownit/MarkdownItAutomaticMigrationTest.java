package com.huawei.clouds.openrewrite.markdownit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class MarkdownItAutomaticMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void normalizesPublicIndexAndKnownDeepEsmImports() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                typescript(
                        """
                        import MarkdownIt from 'markdown-it/index.js';
                        import Token from 'markdown-it/lib/token';
                        import Renderer from "markdown-it/lib/renderer.js";
                        import { escapeHtml } from 'markdown-it/lib/common/utils';
                        import collapseText from 'markdown-it/lib/rules_inline/text_collapse.js';
                        """,
                        """
                        import MarkdownIt from 'markdown-it';
                        import Token from 'markdown-it/lib/token.mjs';
                        import Renderer from "markdown-it/lib/renderer.mjs";
                        import { escapeHtml } from 'markdown-it/lib/common/utils.mjs';
                        import collapseText from 'markdown-it/lib/rules_inline/fragments_join.mjs';
                        """,
                        source -> source.path("src/parser.ts")
                )
        );
    }

    @Test
    void normalizesSideEffectPublicIndexImport() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript("import 'markdown-it/index.js';\n", "import 'markdown-it';\n",
                        source -> source.path("src/setup.js"))
        );
    }

    @Test
    void migratesStaticReExportsAndDynamicEsmImportsAgainstPublishedFiles() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                typescript(
                        """
                        export { default as Token } from 'markdown-it/lib/token';
                        export * from "markdown-it/lib/rules_inline/text_collapse.mjs";
                        const parser = import('markdown-it/index');
                        const renderer = import("markdown-it/lib/renderer.js");
                        """,
                        """
                        export { default as Token } from 'markdown-it/lib/token.mjs';
                        export * from "markdown-it/lib/rules_inline/fragments_join.mjs";
                        const parser = import('markdown-it');
                        const renderer = import("markdown-it/lib/renderer.mjs");
                        """,
                        source -> source.path("src/loaders.ts")
                )
        );
    }

    @Test
    void normalizesDirectCommonJsPublicIndexRequireOnly() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript(
                        "const MarkdownIt = require('markdown-it/index.js');\nconst Other = require('markdown-it/index');\nconst md = new MarkdownIt();\n",
                        "const MarkdownIt = require('markdown-it');\nconst Other = require('markdown-it');\nconst md = new MarkdownIt();\n",
                        source -> source.path("src/cjs.js"))
        );
    }

    @Test
    void preservesBacktickDelimiterForStaticCommonJsSpecifier() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript("const MarkdownIt = require(`markdown-it/index.js`);\n",
                        "const MarkdownIt = require(`markdown-it`);\n",
                        source -> source.path("src/template-require.js"))
        );
    }

    @Test
    void renamesOwnedTextCollapseAnchorForImportedConstructor() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript(
                        """
                        import MarkdownIt from 'markdown-it';
                        const md = new MarkdownIt();
                        md.inline.ruler2.before('text_collapse', 'mentions', processMentions);
                        """,
                        """
                        import MarkdownIt from 'markdown-it';
                        const md = new MarkdownIt();
                        md.inline.ruler2.before('fragments_join', 'mentions', processMentions);
                        """,
                        source -> source.path("src/plugin.js"))
        );
    }

    @Test
    void renamesOwnedTextCollapseForFactoryCallAndDoubleQuotes() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                typescript(
                        """
                        import markdownit from "markdown-it";
                        const parser = markdownit();
                        parser.inline.ruler2.after("text_collapse", "audit", auditRule);
                        """,
                        """
                        import markdownit from "markdown-it";
                        const parser = markdownit();
                        parser.inline.ruler2.after("fragments_join", "audit", auditRule);
                        """,
                        source -> source.path("src/factory.ts"))
        );
    }

    @Test
    void renamesOwnedTextCollapseForCommonJsConstructor() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript(
                        """
                        const MarkdownIt = require('markdown-it');
                        const md = new MarkdownIt();
                        md.inline.ruler2.at('text_collapse', joinText);
                        """,
                        """
                        const MarkdownIt = require('markdown-it');
                        const md = new MarkdownIt();
                        md.inline.ruler2.at('fragments_join', joinText);
                        """,
                        source -> source.path("src/commonjs.js"))
        );
    }

    @Test
    void recognizesNamedDefaultImportBinding() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                typescript(
                        """
                        import { default as MarkdownIt } from 'markdown-it';
                        const md = new MarkdownIt();
                        md.inline.ruler2.disable(['text_collapse', 'balance_pairs']);
                        """,
                        """
                        import { default as MarkdownIt } from 'markdown-it';
                        const md = new MarkdownIt();
                        md.inline.ruler2.disable(['fragments_join', 'balance_pairs']);
                        """,
                        source -> source.path("src/named-default.ts")
                )
        );
    }

    @Test
    void typeOnlyImportDoesNotConferRuntimeOwnership() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                typescript(
                        """
                        import type MarkdownIt from 'markdown-it';
                        declare const md: MarkdownIt;
                        md.inline.ruler2.before('text_collapse', 'plugin', rule);
                        """,
                        source -> source.path("src/type-only.ts")
                )
        );
    }

    @Test
    void reassignedConstructorOrInstanceDoesNotConferOwnership() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript(
                        """
                        let MarkdownIt = require('markdown-it');
                        MarkdownIt = OtherParser;
                        const replacedConstructor = new MarkdownIt();
                        replacedConstructor.inline.ruler2.before('text_collapse', 'x', rule);

                        const StableMarkdownIt = require('markdown-it');
                        let reassigned = new StableMarkdownIt();
                        reassigned = otherParser;
                        reassigned.inline.ruler2.before('text_collapse', 'y', rule);
                        """,
                        source -> source.path("src/reassigned.js")
                )
        );
    }

    @Test
    void realFactlyScooterDeepImportFixtureGetsMjsExtension() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript(
                        """
                        import MarkdownIt from "markdown-it";
                        import Token from "markdown-it/lib/token";

                        const BREAK_REGEX = /(?:^|[^\\\\])\\\\n/;
                        export default function markdownTables(md) { return new Token('table', '', 0); }
                        """,
                        """
                        import MarkdownIt from "markdown-it";
                        import Token from "markdown-it/lib/token.mjs";

                        const BREAK_REGEX = /(?:^|[^\\\\])\\\\n/;
                        export default function markdownTables(md) { return new Token('table', '', 0); }
                        """,
                        source -> source.path("fixtures/factly/scooter/tableRules.js"))
        );
    }

    @Test
    void doesNotRewriteDeepCommonJsRequireBecauseTargetIsEsm() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript("const Token = require('markdown-it/lib/token');\n",
                        source -> source.path("src/legacy.cjs"))
        );
    }

    @Test
    void doesNotInventUnknownDeepFilesOrRewriteAlreadyTargetPaths() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript(
                        """
                        import custom from 'markdown-it/lib/company_extension';
                        import Token from 'markdown-it/lib/token.mjs';
                        import browser from 'markdown-it/dist/markdown-it.js';
                        """,
                        source -> source.path("src/paths.js"))
        );
    }

    @Test
    void doesNotRewriteUnownedOrLookalikeRuleCalls() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript(
                        """
                        export default function plugin(md) {
                          md.inline.ruler2.before('text_collapse', 'plugin', rule);
                        }
                        engine.inline.ruler2.before('text_collapse', 'x', rule);
                        md.block.ruler.before('text_collapse', 'x', rule);
                        audit('text_collapse');
                        """,
                        source -> source.path("src/unowned.js"))
        );
    }

    @Test
    void conservativelyLeavesShadowedInstanceNameEverywhereInFile() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                typescript(
                        """
                        import MarkdownIt from 'markdown-it';
                        const md = new MarkdownIt();
                        function configure(md: LocalParser) {
                          md.inline.ruler2.before('text_collapse', 'local', rule);
                        }
                        md.inline.ruler2.before('text_collapse', 'global', rule);
                        """,
                        source -> source.path("src/shadowed.ts"))
        );
    }

    @Test
    void doesNotRewriteStringsCommentsOrSimilarPackages() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript(
                        """
                        // import Token from 'markdown-it/lib/token';
                        const example = "import x from 'markdown-it/lib/renderer.js'";
                        import other from 'company-markdown-it/lib/token';
                        """,
                        source -> source.path("src/docs.js"))
        );
    }

    @Test
    void excludesGeneratedCaseVariantAndDependencyTrees() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript("import Token from 'markdown-it/lib/token';\n",
                        source -> source.path("Node_Modules/plugin/index.js")),
                javascript("import Token from 'markdown-it/lib/token';\n",
                        source -> source.path("generated-test-fixtures/index.js")),
                javascript("import Token from 'markdown-it/lib/token';\n",
                        source -> source.path("GeneratedClient/index.js"))
        );
    }

    @Test
    void acceptsInstallNamedLeafButExcludesInstallArtifactParents() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource()),
                javascript("import Token from 'markdown-it/lib/token';\n",
                        "import Token from 'markdown-it/lib/token.mjs';\n",
                        source -> source.path("src/install.js")),
                javascript("import Token from 'markdown-it/lib/token';\n",
                        source -> source.path("INSTALL-CACHE/plugin/index.js")),
                javascript("import Token from 'markdown-it/lib/token';\n",
                        source -> source.path("installation/plugin/index.js"))
        );
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new MigrateDeterministicMarkdownItSource())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript(
                        """
                        import MarkdownIt from 'markdown-it/index.js';
                        import Token from 'markdown-it/lib/token';
                        const md = new MarkdownIt();
                        md.inline.ruler2.before('text_collapse', 'x', rule);
                        """,
                        """
                        import MarkdownIt from 'markdown-it';
                        import Token from 'markdown-it/lib/token.mjs';
                        const md = new MarkdownIt();
                        md.inline.ruler2.before('fragments_join', 'x', rule);
                        """,
                        source -> source.path("src/idempotent.ts"))
        );
    }
}
