package com.huawei.clouds.openrewrite.mermaid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class MermaidManifestRiskTest implements RewriteTest {
    @ParameterizedTest
    @ValueSource(strings = {
            ">=9.1.1", "9.1.1 || 9.4.3", "9.x", "workspace:^9.1.3", "npm:@company/mermaid@9.1.6",
            "github:mermaid-js/mermaid#v9.4.3", "file:../mermaid", "latest", "$mermaidVersion", "10.9.5"
    })
    void marksSkippedDirectDeclarations(String declaration) {
        rewriteRun(spec -> spec.recipe(new FindMermaid11ManifestRisks()),
                json("{\"dependencies\":{\"mermaid\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("Strict migration skipped"));
                                    assertTrue(document.printAll().contains(declaration));
                                })));
    }

    @Test
    void marksCommonJsAndCentralOwnersWhenManifestUsesMermaid() {
        rewriteRun(spec -> spec.recipe(new FindMermaid11ManifestRisks()),
                json(
                        """
                        {"type":"commonjs","dependencies":{"mermaid":"11.15.0"},"overrides":{"mermaid":"11.14.0"},"resolutions":{"mermaid":"11.13.0"},"browser":{"mermaid":false}}
                        """, source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("ESM-only"));
                                    assertTrue(printed.contains("central override"));
                                })));
    }

    @Test
    void targetDeclarationsAreNotMarkedAsSkipped() {
        rewriteRun(spec -> spec.recipe(new FindMermaid11ManifestRisks()),
                json("{\"dependencies\":{\"mermaid\":\"11.15.0\"},\"devDependencies\":{\"mermaid\":\"^11.15.0\"},\"optionalDependencies\":{\"mermaid\":\"~11.15.0\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("Strict migration skipped")))));
    }

    @Test
    void ordinaryManifestWithoutMermaidAndExcludedFilesAreIgnored() {
        rewriteRun(spec -> spec.recipe(new FindMermaid11ManifestRisks()),
                json("{\"type\":\"commonjs\",\"overrides\":{\"mermaid\":\"9.1.1\"}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"mermaid\":\"9.x\"}}",
                        source -> source.path("package-lock.json")),
                json("{\"type\":\"commonjs\",\"dependencies\":{\"mermaid\":\"9.x\"}}",
                        source -> source.path("generated/package.json")));
    }
}
