package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class AngularFormsTemplateRiskTest implements RewriteTest {
    @Test
    void marksMixedNgModelReactiveDirectiveOnSameElement() {
        assertTemplate("<input [(ngModel)]=\"name\" formControlName=\"name\">", "src/mixed.html",
                "deprecated mixed registration");
    }

    @Test
    void doesNotMisclassifySeparateTemplateAndReactiveElementsFromPinnedAngularFixture() {
        rewriteRun(spec -> spec.recipe(new FindAngularFormsTemplateRisks()),
                text("<input [(ngModel)]=\"model\"><input [formControl]=\"formControl\">",
                        s -> s.path("angular/components/src/dev-app/input/input-demo.html")
                                .afterRecipe(after -> assertFalse(after.printAll().contains("deprecated mixed registration"), after.printAll()))));
    }

    @Test
    void marksReactiveDisabledBindingAtWholeElement() {
        assertTemplate("<input [formControl]=\"email\" [disabled]=\"locked\">", "src/disabled.html",
                "drive disabled state through the control");
    }

    @Test
    void marksStandaloneNgModelOptionsAndChangeEvent() {
        assertTemplate("<input [(ngModel)]=\"name\" [ngModelOptions]=\"{standalone: true}\" (ngModelChange)=\"changed($event)\">",
                "src/template-form.html", "standalone/updateOn behavior", "ngModelChange ordering");
    }

    @Test
    void marksSubmitResetAndNgFormReferences() {
        assertTemplate("<form #profile=\"ngForm\" (ngSubmit)=\"save()\" (reset)=\"clear()\"></form>",
                "src/profile.html", "submit/reset lifecycle");
    }

    @Test
    void marksSelectionAccessorBoundaries() {
        assertTemplate("<select [compareWith]=\"sameId\"></select><input type=\"radio\">", "src/choice.html",
                "Selection accessor equality/identity");
    }

    @Test
    void leavesSimilarNonHtmlAndUnrelatedTemplateAlone() {
        rewriteRun(spec -> spec.recipe(new FindAngularFormsTemplateRisks()),
                text("const sample = '<input [(ngModel)]=\"x\" formControlName=\"x\">';", s -> s.path("src/sample.ts")),
                text("<input name=\"email\" [value]=\"email\"><button>Save</button>", s -> s.path("src/static.html")));
    }

    @Test
    void markersRemainNonOverlappingAcrossCycles() {
        rewriteRun(spec -> spec.recipe(new FindAngularFormsTemplateRisks()).cycles(2),
                text("<input [(ngModel)]=\"x\" formControlName=\"x\" [disabled]=\"locked\">",
                        s -> s.path("src/mixed.html").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertTrue(out.contains("deprecated mixed registration"), out);
                            assertFalse(out.contains("drive disabled state through the control"), out);
                        })));
    }

    private void assertTemplate(String before, String path, String... messages) {
        rewriteRun(spec -> spec.recipe(new FindAngularFormsTemplateRisks()),
                text(before, s -> s.path(path).after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    for (String message : messages) assertTrue(out.contains(message), out);
                })));
    }
}
