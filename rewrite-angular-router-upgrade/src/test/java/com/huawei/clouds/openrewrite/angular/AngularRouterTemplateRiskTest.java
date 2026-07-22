package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class AngularRouterTemplateRiskTest implements RewriteTest {
    @Test
    void marksPinnedAngularGridsterRouterLinksActiveStateAndOutlet() {
        assertTemplateMarkers(
                "<a routerLink=\"/api\" routerLinkActive=\"active\">API</a>\n<router-outlet />\n",
                "tiberiuzuld/angular-gridster2/src/app/app.html",
                "Router link resolution needs",
                "RouterLinkActive matching needs",
                "RouterOutlet activation timing"
        );
    }

    @Test
    void marksBoundRouterLinkAtTheCompleteAttributeName() {
        assertTemplateMarkers(
                "<a [routerLink]=\"['team', team.id]\">Team</a>",
                "src/team-link.html",
                "nested/empty paths"
        );
    }

    @Test
    void marksQueryParamsHandlingAndFragmentBindings() {
        assertTemplateMarkers(
                "<a [routerLink]=\"['.']\" [queryParams]=\"filters\" queryParamsHandling=\"merge\" [fragment]=\"section\">View</a>",
                "src/filter-link.html",
                "Query/fragment merging and preservation"
        );
    }

    @Test
    void marksNamedRouterOutlet() {
        assertTemplateMarkers(
                "<router-outlet name=\"side\"></router-outlet>",
                "src/shell.html",
                "named outlets"
        );
    }

    @Test
    void ignoresSimilarTextOutsideHtmlFiles() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularRouterTemplateRisks()),
                text("const sample = '<router-outlet [routerLink]>';\n",
                        source -> source.path("src/example.ts"))
        );
    }

    @Test
    void leavesTemplateWithoutRouterConstructsAlone() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularRouterTemplateRisks()),
                text("<main><a href=\"/docs\">Docs</a><section>{{title}}</section></main>",
                        source -> source.path("src/static.html"))
        );
    }

    @Test
    void templateMarkersRemainNonOverlappingAcrossCycles() {
        rewriteRun(
                spec -> spec.recipe(new FindAngularRouterTemplateRisks()).cycles(2),
                text(
                        "<a [routerLink]=\"['/']\" routerLinkActive=\"active\">Home</a><router-outlet />",
                        source -> source.path("src/app.html").after(actual -> actual)
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertTrue(printed.contains("Router link resolution needs"));
                                    assertTrue(printed.contains("RouterLinkActive matching needs"));
                                    assertTrue(printed.contains("RouterOutlet activation timing"));
                                })
                )
        );
    }

    private void assertTemplateMarkers(String before, String path, String... messages) {
        rewriteRun(
                spec -> spec.recipe(new FindAngularRouterTemplateRisks()),
                text(before, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> {
                            String printed = after.printAll();
                            for (String message : messages) {
                                assertTrue(printed.contains(message), printed);
                            }
                        }))
        );
    }
}
