package com.huawei.clouds.openrewrite.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class MermaidMarkupRiskTest implements RewriteTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "<script src='https://cdn.jsdelivr.net/npm/mermaid@9.4.3/dist/mermaid.min.js'></script>",
            "https://unpkg.com/mermaid@9.1.1/dist/mermaid.esm.min.mjs",
            "%%{init: {'theme': 'forest'}}%%\ngraph TD; A-->B",
            "click A callback \"tooltip\"",
            "click A href \"https://example.test\"",
            "namespace Company.Project",
            "#graph-arrowhead { fill: red; }",
            "[id$='arrowhead'] { stroke: blue; }",
            "securityLevel: 'loose'",
            "htmlLabels: true",
            "themeVariables: { primaryColor: '#fff' }",
            "vendor/mermaid/dist/mermaid.min.js"
    })
    void marksMarkupDiagramSecurityAndStyleBoundaries(String content) {
        rewriteRun(spec -> spec.recipe(new FindMermaid11MarkupRisks()),
                text(content, source -> source.path(pathFor(content)).after(actual -> actual)
                        .afterRecipe(file -> assertTrue(file.printAll().contains("~~")))));
    }

    @Test
    void ignoresModernOrdinaryAndCommentedMarkup() {
        rewriteRun(spec -> spec.recipe(new FindMermaid11MarkupRisks()),
                text("<script type='module'>import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11.15.0/dist/mermaid.esm.min.mjs';</script><pre class='mermaid'>flowchart TD; A-->B</pre>",
                        source -> source.path("src/index.html")),
                text("<!-- <script src='mermaid@9.4.3/dist/mermaid.min.js'></script> -->",
                        source -> source.path("src/commented.html")),
                text("graph TD\nA --> B", source -> source.path("src/simple.mmd")));
    }

    @Test
    void excludesGeneratedVendorDistInstallAndCacheParents() {
        rewriteRun(spec -> spec.recipe(new FindMermaid11MarkupRisks()),
                text("%%{init: {'theme':'dark'}}%%", source -> source.path("generated/docs.mmd")),
                text("click A callback", source -> source.path("vendor/docs.mmd")),
                text("#arrowhead {fill:red}", source -> source.path("dist/theme.css")),
                text("securityLevel: loose", source -> source.path("install-cache/config.md")),
                text("namespace Nested", source -> source.path(".cache/diagram.mmd")));
    }

    @Test
    void markerRecipeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindMermaid11MarkupRisks()).cycles(2),
                text("%%{init: {'securityLevel':'loose'}}%%\ngraph TD; A-->B",
                        source -> source.path("src/idempotent.mmd").after(actual -> actual)
                                .afterRecipe(file -> {
                                    assertTrue(file.printAll().contains("deprecated in favor"));
                                    assertFalse(file.printAll().contains("~~(~~("));
                                })));
    }

    private static String pathFor(String content) {
        if (content.contains("#graph") || content.contains("[id")) return "src/theme.css";
        if (content.startsWith("<script") || content.startsWith("https://")) return "src/index.html";
        return "src/diagram.mmd";
    }
}
