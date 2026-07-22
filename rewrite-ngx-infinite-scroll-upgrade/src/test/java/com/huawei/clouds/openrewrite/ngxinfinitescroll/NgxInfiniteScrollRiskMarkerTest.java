package com.huawei.clouds.openrewrite.ngxinfinitescroll;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class NgxInfiniteScrollRiskMarkerTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksDeprecatedAliasDeepEntryStandaloneDeclarationAndRuntimePropertyExactly() {
        rewriteRun(spec -> spec.recipe(new FindNgxInfiniteScrollTypeScriptRisks()), typescript(
                """
                import { InfiniteScrollModule as ScrollModule, InfiniteScrollDirective as ScrollDirective } from 'ngx-infinite-scroll';
                import { helper } from 'ngx-infinite-scroll/lib/services/scroll-register';
                @NgModule({ declarations: [ScrollDirective], imports: [ScrollModule] })
                export class LegacyModule {
                  update(directive: ScrollDirective) { directive.infiniteScrollThrottle = 0; }
                }
                """,
                source -> source.path("src/legacy.module.ts").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "InfiniteScrollModule is deprecated");
                    assertContains(out, "~~>*/ScrollModule");
                    assertContains(out, "Private ngx-infinite-scroll deep import");
                    assertContains(out, "~~>*/'ngx-infinite-scroll/lib/services/scroll-register'");
                    assertContains(out, "InfiniteScrollDirective is standalone");
                    assertContains(out, "~~>*/ScrollDirective]");
                    assertContains(out, "~~>*/infiniteScrollThrottle");
                })
        ));
    }

    @Test
    void marksInlineTemplateLiteralWithoutChangingItsContent() {
        rewriteRun(spec -> spec.recipe(new FindNgxInfiniteScrollTypeScriptRisks()), typescript(
                """
                import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                @Component({
                  imports: [InfiniteScrollDirective],
                  template: '<div infiniteScroll [scrollWindow]="false" (scrolled)="next()"></div>'
                })
                export class FeedComponent {}
                """,
                source -> source.path("src/feed.component.ts").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "Inline ngx-infinite-scroll template detected");
                    assertContains(out, "~~>*/'<div infiniteScroll [scrollWindow]=\"false\" (scrolled)=\"next()\"></div>'");
                })
        ));
    }

    @Test
    void sameNamedForeignAndOrdinarySourceRemainUnmarked() {
        rewriteRun(spec -> spec.recipe(new FindNgxInfiniteScrollTypeScriptRisks()),
                typescript("""
                           import { InfiniteScrollModule } from '@company/scroll';
                           @NgModule({ declarations: [InfiniteScrollDirective], imports: [InfiniteScrollModule] }) class ForeignModule {}
                           const config = { infiniteScrollThrottle: 50 };
                           """, source -> source.path("src/foreign.ts").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("""
                           class Pager { scrolled = false; scrollWindow = true; }
                           """, source -> source.path("src/ordinary.ts").afterRecipe(after -> assertNoMarker(after.printAll())))
        );
    }

    @Test
    void importedDirectiveDoesNotMakeUnrelatedRuntimePropertiesRisks() {
        rewriteRun(spec -> spec.recipe(new FindNgxInfiniteScrollTypeScriptRisks()), typescript("""
                import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
                const carousel = { horizontal: true, alwaysCallback: false };
                const pager = { scrollWindow: true, scrolled: false };
                pager.scrollWindow = false;
                """, source -> source.path("src/unrelated-properties.ts")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @ParameterizedTest(name = "marks package risk {0}")
    @MethodSource("jsonRisks")
    void marksPackageCompatibilityValueAtExactLiteral(String name, String declaration, String message) {
        rewriteRun(spec -> spec.recipe(new FindNgxInfiniteScrollJsonRisks()), json(
                "{\"dependencies\":{\"ngx-infinite-scroll\":\"17.0.1\",\"" + name + "\":\"" + declaration + "\"}}",
                source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, message);
                    assertContains(out, "~~>*/\"" + declaration + "\"");
                })
        ));
    }

    static Stream<Arguments> jsonRisks() {
        return Stream.of(
                Arguments.of("@angular/core", "~14.2.0", "aligned to Angular 17"),
                Arguments.of("@angular/common", "^16.2.12", "aligned to Angular 17"),
                Arguments.of("@angular/cli", "13.3.0", "aligned to Angular 17"),
                Arguments.of("typescript", "~4.8.4", "requires TypeScript >=5.2"),
                Arguments.of("typescript", "^5.5.0", "requires TypeScript >=5.2"),
                Arguments.of("rxjs", "~6.4.0", "supports RxJS ^6.5.3"),
                Arguments.of("tslib", "^2.2.0", "depends on tslib ^2.3.0")
        );
    }

    @Test
    void marksEngineCentralOwnerAndUnselectedDeclaration() {
        rewriteRun(spec -> spec.recipe(new FindNgxInfiniteScrollJsonRisks()), json(
                """
                {
                  "engines":{"node":"16.20.0"},
                  "dependencies":{"ngx-infinite-scroll":">=14 <18"},
                  "pnpm":{"overrides":{"ngx-infinite-scroll":"16.0.0"}}
                }
                """,
                source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "choose the 17.0.1 constraint explicitly");
                    assertContains(out, "Angular 17 supports Node");
                    assertContains(out, "Central package-manager ownership detected");
                })
        ));
    }

    @Test
    void compatibleTargetManifestAndUnrelatedManifestRemainUnmarked() {
        rewriteRun(spec -> spec.recipe(new FindNgxInfiniteScrollJsonRisks()),
                json("""
                     {"engines":{"node":"^18.13.0 || ^20.9.0"},"dependencies":{"ngx-infinite-scroll":"17.0.1","@angular/core":"^17.3.0","@angular/common":"^17.3.0","rxjs":"^7.8.0","tslib":"^2.6.0"},"devDependencies":{"typescript":"~5.4.5"}}
                     """, source -> source.path("package.json").afterRecipe(after -> assertNoMarker(after.printAll()))),
                json("""
                     {"engines":{"node":"12.0.0"},"dependencies":{"@angular/core":"9.1.0","rxjs":"5.5.0"}}
                     """, source -> source.path("services/api/package.json").afterRecipe(after -> assertNoMarker(after.printAll())))
        );
    }

    @ParameterizedTest(name = "marks exact template attribute {2}")
    @MethodSource("templateRisks")
    void marksTemplateAttributeAtExactSnippet(String input, String markedSnippet, String message) {
        rewriteRun(spec -> spec.recipe(new FindNgxInfiniteScrollTemplateRisks()), text(
                input,
                source -> source.path("src/feed.component.html").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, message);
                    assertExactAttributeMarked(out, markedSnippet);
                })
        ));
    }

    static Stream<Arguments> templateRisks() {
        return Stream.of(
                Arguments.of("<div infiniteScroll></div>", "infiniteScroll", "standalone InfiniteScrollDirective"),
                Arguments.of("<div [scrollWindow]=\"false\"></div>", "[scrollWindow]=\"false\"", "window/element/query-root selection"),
                Arguments.of("<div [infiniteScrollContainer]=\"'.feed'\"></div>", "[infiniteScrollContainer]=\"'.feed'\"", "selector uniqueness"),
                Arguments.of("<div [infiniteScrollThrottle]=\"1500\"></div>", "[infiniteScrollThrottle]=\"1500\"", "leading/trailing event counts"),
                Arguments.of("<div (scrolled)=\"next($event)\"></div>", "(scrolled)=\"next($event)\"", "pagination across"),
                Arguments.of("<div on-scrolledUp=\"previous($event)\"></div>", "on-scrolledUp=\"previous($event)\"", "IInfiniteScrollEvent typing")
        );
    }

    @Test
    void marksEverySensitiveAttributeIndependentlyInRealRepositoryTemplates() {
        // MaherNajar/shop@4b199ca08ae3795ef311936ec2c5bc0f6a13b255 product-list.component.html.
        // punkrocker178/angular4reddit@f20f806e0710deffd5109ae612b3ab9ac6532520 listings.html.
        // da7a90-backup/cro-website@4076096174c4398061d8cbbd520020c4f5ec158e friend-chat.component.html.
        rewriteRun(spec -> spec.recipe(new FindNgxInfiniteScrollTemplateRisks()), text(
                """
                <div infiniteScroll
                     [infiniteScrollDistance]="2"
                     [infiniteScrollUpDistance]="1.5"
                     [infiniteScrollThrottle]="1500"
                     [scrollWindow]="false"
                     (scrolled)="onScrollDown()"
                     (scrolledUp)="onScrollUp()"></div>
                """,
                source -> source.path("real/friend-chat.component.html").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(markerCount(out) == 7, () -> "Expected seven independently marked attributes:\n" + out);
                    for (String snippet : new String[]{"infiniteScroll", "[infiniteScrollDistance]", "[infiniteScrollUpDistance]",
                            "[infiniteScrollThrottle]", "[scrollWindow]", "(scrolled)", "(scrolledUp)"}) {
                        assertExactAttributeMarked(out, snippet);
                    }
                })
        ));
    }

    @Test
    void ignoresCommentsTextAndSimilarAttributes() {
        rewriteRun(spec -> spec.recipe(new FindNgxInfiniteScrollTemplateRisks()), text(
                """
                <!-- <div infiniteScroll [scrollWindow]="false"></div> -->
                <p>infiniteScroll and scrolled are documented here.</p>
                <company-feed [infiniteScrollStrategy]="strategy" (scrolledLater)="later()"></company-feed>
                """,
                source -> source.path("src/safe.html").afterRecipe(after -> assertNoMarker(after.printAll()))),
                text("<div infiniteScroll [scrollWindow]=\"false\"></div>",
                        source -> source.path("node_modules/example/template.html")
                                .afterRecipe(after -> assertNoMarker(after.printAll())))
        );
    }

    @Test
    void recommendedRecipeUpgradesMigratesAndMarksFixedRepositoryShape() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxinfinitescroll")
                .scanYamlResources().build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.ngxinfinitescroll.MigrateNgxInfiniteScrollTo17_0_1")),
                json("{\"dependencies\":{\"@angular/core\":\"^16.2.12\",\"ngx-infinite-scroll\":\"^16.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertContains(out, "\"ngx-infinite-scroll\":\"17.0.1\"");
                            assertContains(out, "aligned to Angular 17");
                        })),
                typescript("""
                           import {InfiniteScrollModule} from 'ngx-infinite-scroll';
                           @NgModule({imports:[InfiniteScrollModule],exports:[InfiniteScrollModule]}) export class SharedModule{}
                           """, source -> source.path("src/app/shared/shared.module.ts").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "InfiniteScrollDirective");
                    assertFalse(out.contains("InfiniteScrollModule"), out);
                })),
                text("<div infiniteScroll [scrollWindow]=\"false\" (scrolled)=\"next()\"></div>",
                        source -> source.path("src/app/feed.html").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertExactAttributeMarked(out, "infiniteScroll");
                            assertExactAttributeMarked(out, "[scrollWindow]=\"false\"");
                            assertExactAttributeMarked(out, "(scrolled)=\"next()\"");
                        }))
        );
    }

    private static int markerCount(String source) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf("~~>", index)) >= 0) {
            count++;
            index += 3;
        }
        return count;
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertExactAttributeMarked(String actual, String attribute) {
        java.util.regex.Matcher nameMatcher = java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9-]*")
                .matcher(attribute);
        assertTrue(nameMatcher.find(), attribute);
        String name = nameMatcher.group();
        int nameIndex = actual.indexOf(name);
        while (nameIndex >= 0) {
            int marker = actual.lastIndexOf("~~>", nameIndex);
            if (marker >= 0 && nameIndex - marker - 3 <= 2) return;
            nameIndex = actual.indexOf(name, nameIndex + name.length());
        }
        throw new AssertionError("Expected an exact marker immediately adjacent to <" + attribute + "> in:\n" + actual);
    }

    private static void assertNoMarker(String source) {
        assertFalse(source.contains("~~("), source);
    }
}
