package com.huawei.clouds.openrewrite.mermaid;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;

class MermaidSourceMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesSignatureEquivalentTopLevelAsyncAliases() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicMermaidApis()),
                typescript(
                        """
                        import mermaid from 'mermaid';
                        const valid = await mermaid.parseAsync(source);
                        const output = await mermaid.renderAsync(id, source);
                        """,
                        """
                        import mermaid from 'mermaid';
                        const valid = await mermaid.parse(source);
                        const output = await mermaid.render(id, source);
                        """, source -> source.path("src/diagram.ts")));
    }

    @Test
    void migratesSignatureEquivalentMermaidApiAliases() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicMermaidApis()),
                typescript(
                        """
                        import diagramEngine from "mermaid";
                        await diagramEngine.mermaidAPI.parseAsync(text);
                        await diagramEngine.mermaidAPI.renderAsync('graph', text);
                        """,
                        """
                        import diagramEngine from "mermaid";
                        await diagramEngine.mermaidAPI.parse(text);
                        await diagramEngine.mermaidAPI.render('graph', text);
                        """, source -> source.path("src/api.ts")));
    }

    @Test
    void leavesCallbackAndContainerOverloadsForReview() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicMermaidApis()),
                typescript(
                        """
                        import mermaid from 'mermaid';
                        mermaid.parseAsync(source, onError);
                        mermaid.renderAsync(id, source, callback);
                        mermaid.renderAsync(id, source, callback, container);
                        """, source -> source.path("src/callbacks.ts")));
    }

    @Test
    void leavesDeepRequireDynamicNamespaceAndUnrelatedCalls() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicMermaidApis()),
                typescript(
                        """
                        import deep from 'mermaid/dist/mermaid.esm.min.mjs';
                        import * as MermaidModule from 'mermaid';
                        deep.parseAsync(text);
                        MermaidModule.default.renderAsync(id, text);
                        const required = require('mermaid');
                        required.parseAsync(text);
                        other.renderAsync(id, text);
                        """, source -> source.path("src/unsafe.ts")));
    }

    @Test
    void excludesGeneratedVendorBuildInstallAndCacheSources() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicMermaidApis()),
                typescript("import mermaid from 'mermaid'; mermaid.parseAsync(text);",
                        source -> source.path("generated/sdk.ts")),
                typescript("import mermaid from 'mermaid'; mermaid.renderAsync(id, text);",
                        source -> source.path("vendor/diagram.ts")),
                typescript("import mermaid from 'mermaid'; mermaid.parseAsync(text);",
                        source -> source.path("dist/index.ts")),
                typescript("import mermaid from 'mermaid'; mermaid.parseAsync(text);",
                        source -> source.path("install-cache/index.ts")));
    }

    @Test
    void deterministicMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicMermaidApis())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import mermaid from 'mermaid';\nawait mermaid.parseAsync(text);\n",
                        "import mermaid from 'mermaid';\nawait mermaid.parse(text);\n",
                        source -> source.path("src/idempotent.ts")));
    }
}
