package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class AngularCompilerTemplateRiskTest implements RewriteTest {
    @Test
    void marksForHeaderIncludingTrackAtExactSnippet() {
        assertTemplate("@for (item of items; track item.id) { <p>{{item.name}}</p> }", "src/list.html",
                "stable unique track expression");
    }

    @Test
    void marksIfSwitchAndDeferBlocks() {
        assertTemplate("@if (ready) { A } @switch (state) { @default { B } } @defer (on viewport) { C }",
                "src/blocks.html", "Block control flow changes", "trigger/prefetch/loading/error");
    }

    @Test
    void marksLegacyStructuralDirectivesForOfficialMigration() {
        assertTemplate("<li *ngFor=\"let item of items; trackBy: trackById\" *ngIf=\"ready\">{{item}}</li>",
                "src/legacy.html", "official parser-aware migration");
    }

    @Test
    void marksAngular20ParenthesizedOptionalChainBreakingCase() {
        assertTemplate("<p>{{ (foo?.bar).baz }}</p>", "src/optional.html",
                "always respects parentheses");
    }

    @Test
    void leavesSafeOptionalChainAndOrdinaryParenthesesAlone() {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerTemplateRisks()),
                text("<p>{{foo?.bar?.baz}}</p><p>{{(price + tax) * count}}</p>", s -> s.path("src/safe.html")),
                text("<!-- @for (x of xs; track x) --><script>const t = '@defer';</script><style>/* i18n */</style>",
                        s -> s.path("src/raw.html")));
    }

    @Test
    void marksI18nSecurityAndIframeAtExactSnippets() {
        assertTemplate("<p i18n=\"welcome|Greeting@@welcome\">Hello</p><div [innerHTML]=\"html\"></div><iframe sandbox [src]=\"url\"></iframe>",
                "src/security.html", "controlled catalog diff", "security schema/sanitization", "Security-sensitive iframe bindings");
    }

    @Test
    void marksNgNonBindableAndNgTemplateScopes() {
        assertTemplate("<code ngNonBindable>{{literal}}</code><ng-template #row let-item>{{item}}</ng-template>",
                "src/template-scope.html", "Literal Angular syntax/template reference scope");
    }

    @Test
    void ignoresNonHtmlAndKeepsMarkersNonOverlappingAcrossCycles() {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerTemplateRisks()),
                text("const sample = '@for (x of xs; track x)';", s -> s.path("src/sample.ts")));
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerTemplateRisks()).cycles(2),
                text("<iframe i18n [src]=\"url\"></iframe>", s -> s.path("src/frame.html")
                        .after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertTrue(out.contains("Security-sensitive iframe bindings"), out);
                            assertFalse(out.contains("controlled catalog diff"), out);
                        })));
    }

    private void assertTemplate(String before, String path, String... messages) {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerTemplateRisks()),
                text(before, s -> s.path(path).after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    for (String message : messages) assertTrue(out.contains(message), out);
                })));
    }
}
