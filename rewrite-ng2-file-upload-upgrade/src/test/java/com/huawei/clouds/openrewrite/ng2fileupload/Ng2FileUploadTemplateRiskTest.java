package com.huawei.clouds.openrewrite.ng2fileupload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.test.SourceSpecs.text;

class Ng2FileUploadTemplateRiskTest implements RewriteTest {
    @ParameterizedTest(name = "select syntax {0}")
    @ValueSource(strings = {
            "ng2FileSelect", "[ng2FileSelect]", "bind-ng2FileSelect",
            "ng2FileDrop", "[ng2FileDrop]", "bind-ng2FileDrop"
    })
    void marksEverySelectorSyntax(String selector) {
        assertTemplateMarker("<input " + selector + " [uploader]=\"uploader\">", "standalone:false");
    }

    @ParameterizedTest(name = "uploader syntax {0}")
    @ValueSource(strings = {"[uploader]", "bind-uploader", "uploader"})
    void marksUploaderOnlyOnOwnedDirectiveTag(String uploader) {
        assertTemplateMarker("<input ng2FileSelect " + uploader + "=\"files\">", "owns queue and transport state");
    }

    @ParameterizedTest(name = "select event syntax {0}")
    @ValueSource(strings = {"(onFileSelected)", "on-onFileSelected", "onFileSelected"})
    void marksFileSelectedEvents(String event) {
        assertTemplateMarker("<input ng2FileSelect " + event + "=\"selected($event)\">",
                "emits browser File objects");
    }

    @ParameterizedTest(name = "drop event syntax {0}")
    @ValueSource(strings = {"(fileOver)", "on-fileOver", "fileOver", "(onFileDrop)", "on-onFileDrop", "onFileDrop"})
    void marksDropEvents(String event) {
        assertTemplateMarker("<div ng2FileDrop " + event + "=\"dropped($event)\"></div>",
                "crosses drag/drop browser input");
    }

    @Test
    void sameAttributesWithoutDirectiveCommentsTextAndSimilarNamesAreIgnored() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTemplateRisks()),
                text("""
                        <!-- <input ng2FileSelect [uploader]="uploader"> -->
                        <p>ng2FileSelect [uploader] and onFileDrop are documented here.</p>
                        <custom-upload [uploader]="uploader" (fileOverLater)="later()" ng2FileSelector></custom-upload>
                        """, source -> source.path("src/safe.component.html").afterRecipe(after ->
                        assertFalse(after.printAll().contains("~~("), after.printAll()))));
    }

    @Test
    void realAqualityTemplateIsRecognized() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTemplateRisks()),
                // aquality-automation/aquality-tracking-ui@90f313a6fe7b954e9b80b10302edf8bba2167154
                text("""
                        <div ng2FileDrop class="drop-zone" [uploader]="uploader">
                          <input #fileInput type="file" ng2FileSelect [uploader]="uploader" multiple
                            accept=".txt,.csv,.pdf,.jpg,.png,.zip" />
                          <button (click)="uploader.uploadAll()">Upload All</button>
                        </div>
                        """, source -> source.path("fixtures/aquality/uploader.element.html")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "standalone:false");
                            assertContains(printed, "owns queue and transport state");
                        })));
    }

    @Test
    void realBotanicTemplateEventsAreRecognized() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTemplateRisks()),
                // ghillert/botanic-ng@e824ceaa59a08d938bffb6b4f74ca12576d6c18a
                text("""
                        <div ng2FileDrop
                          [ngClass]="{'nv-file-over': hasBaseDropZoneOver}"
                          (fileOver)="fileOverBase($event)"
                          [uploader]="uploader">Base drop zone</div>
                        <input type="file" ng2FileSelect [uploader]="uploader" multiple />
                        """, source -> source.path("fixtures/botanic/plant-details.component.html")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "crosses drag/drop browser input");
                            assertContains(after.printAll(), "standalone:false");
                        })));
    }

    @Test
    void realBlueriqModernTemplateIsRecognized() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTemplateRisks()),
                // blueriq/blueriq-material@4626d39a1d014abf3d96b0338b8299e21e340f69
                text("""
                        <input #fileInput [multiple]="bqFileUpload.multiple" [uploader]="ngFileUploader"
                          [accept]="bqFileUpload.allowedExtensions | dottedFileExtensions" hidden ng2FileSelect type="file">
                        @if (isBusy) { <mat-progress-bar value="{{ngFileUploader.progress}}"></mat-progress-bar> }
                        """, source -> source.path("fixtures/blueriq/file-upload.component.html")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "owns queue and transport state");
                            assertContains(after.printAll(), "standalone:false");
                        })));
    }

    @Test
    void skipsGeneratedAndInstallParentsButProcessesInstallLeaf() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTemplateRisks()),
                text("<input ng2FileSelect [uploader]=\"uploader\">", source -> source.path("generated-client/template.html")),
                text("<input ng2FileSelect [uploader]=\"uploader\">", source -> source.path("install-cache/template.html")),
                text("<input ng2FileSelect [uploader]=\"uploader\">", source -> source.path("src/install.html")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), "standalone:false"))));
    }

    @Test
    void templateMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTemplateRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                text("<input ng2FileSelect [uploader]=\"uploader\">", source -> source.path("src/upload.component.html")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            String message = "standalone:false";
                            assertTrue(printed.indexOf(message) == printed.lastIndexOf(message), printed);
                        })));
    }

    private void assertTemplateMarker(String html, String message) {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTemplateRisks()),
                text(html, source -> source.path("src/upload.component.html").after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
