package com.huawei.clouds.openrewrite.ng2fileupload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class Ng2FileUploadSourceRiskTest implements RewriteTest {
    @ParameterizedTest(name = "deep import {0}")
    @ValueSource(strings = {
            "ng2-file-upload/file-upload/file-uploader.class",
            "ng2-file-upload/file-upload/file-item.class",
            "ng2-file-upload/file-upload/file-like-object.class",
            "ng2-file-upload/file-upload/file-drop.directive",
            "ng2-file-upload/file-upload/file-select.directive",
            "ng2-file-upload/file-upload/file-upload.module",
            "ng2-file-upload/private/internal", "ng2-file-upload/fesm2015/ng2-file-upload.mjs"
    })
    void marksEveryDeepEntryWhenAutoCannotOrHasNotRun(String module) {
        assertSourceMarker("import { Value } from '" + module + "';", "src/deep.ts", "exports only its root");
    }

    @ParameterizedTest(name = "CommonJS entry {0}")
    @ValueSource(strings = {
            "ng2-file-upload", "ng2-file-upload/file-upload/file-uploader.class",
            "ng2-file-upload/file-upload/file-item.class"
    })
    void marksCommonJsRequireForEsmOnlyTarget(String module) {
        assertSourceMarker("const upload = require('" + module + "');", "src/loader.js", "only an ESM root entry");
    }

    @Test
    void marksDefaultImportButAllowsRootNamedNamespaceAndTypeOnlyImports() {
        assertSourceMarker("import upload from 'ng2-file-upload';", "src/default.ts", "no documented default export");
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                typescript("import { FileUploaderOptions } from 'ng2-file-upload';\nlet options: FileUploaderOptions;", source -> source.path("src/named.ts")),
                typescript("import * as upload from 'ng2-file-upload';\nlet options: upload.FileUploaderOptions;", source -> source.path("src/namespace.ts")),
                typescript("import type { FileSelectDirective } from 'ng2-file-upload';\nlet directive: FileSelectDirective;", source -> source.path("src/type-only.ts")));
    }

    @ParameterizedTest(name = "non-standalone directive {0}")
    @ValueSource(strings = {"FileSelectDirective", "FileDropDirective"})
    void marksRuntimeDirectiveImports(String directive) {
        assertSourceMarker("import { Component } from '@angular/core';\nimport { " + directive + " } from 'ng2-file-upload';\n@Component({ standalone: true, imports: [" + directive + "] })\nclass UploadComponent {}",
                "src/component.ts", "standalone:false");
    }

    @Test
    void marksDirectiveInNgModuleDeclarationsAndTestBedImports() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                typescript("import { NgModule } from '@angular/core';\nimport { FileSelectDirective } from 'ng2-file-upload';\n@NgModule({ declarations: [FileSelectDirective] }) class FeatureModule {}",
                        source -> source.path("src/feature.module.ts").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Angular declarations metadata"))),
                typescript("import { FileDropDirective } from 'ng2-file-upload';\nTestBed.configureTestingModule({ imports: [FileDropDirective] });",
                        source -> source.path("src/upload.spec.ts").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Angular imports metadata"))));
    }

    @Test
    void directiveTypeQueryImportWithoutAngularMetadataIsNotMarked() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                typescript("import { FileSelectDirective } from 'ng2-file-upload';\nlet query: FileSelectDirective;",
                        source -> source.path("src/query.ts").afterRecipe(after ->
                                assertFalse(after.printAll().contains("standalone:false"), after.printAll()))));
    }

    @Test
    void marksStaticAndDynamicDeepReexports() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                typescript("export { FileUploader } from 'ng2-file-upload/file-upload/file-uploader.class';",
                        source -> source.path("src/public-api.ts").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "exports only its root"))),
                typescript("const modulePromise = import('ng2-file-upload/private/runtime');",
                        source -> source.path("src/lazy.ts").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "exports only its root"))));
    }

    @ParameterizedTest(name = "security/body/filter option {0}")
    @ValueSource(strings = {
            "url", "method", "authToken", "authTokenHeader", "headers", "withCredentials",
            "disableMultipart", "formatDataFunction", "formatDataFunctionIsAsync", "itemAlias",
            "additionalParameter", "parametersBeforeFiles", "filters", "allowedFileType",
            "allowedMimeType", "allowedFileExtensions", "maxFileSize", "queueLimit"
    })
    void marksExactUploaderOptions(String option) {
        assertSourceMarker("import { FileUploader } from 'ng2-file-upload';\nconst uploader = new FileUploader({ " + option + ": value });",
                "src/uploader.ts", option);
    }

    @Test
    void marksConstructionEvenWhenNoSensitiveOptionIsLiteral() {
        assertSourceMarker("import { FileUploader as Upload } from 'ng2-file-upload';\nconst uploader = new Upload(options);",
                "src/factory.ts", "Review this FileUploader construction");
    }

    @ParameterizedTest(name = "queue/transport method {0}")
    @ValueSource(strings = {
            "uploadAll", "uploadItem", "cancelAll", "cancelItem", "addToQueue", "removeFromQueue",
            "clearQueue", "setOptions", "getNotUploadedItems", "getReadyItems"
    })
    void marksOwnedUploaderQueueAndTransportCalls(String method) {
        assertSourceMarker("import { FileUploader } from 'ng2-file-upload';\nconst uploader = new FileUploader(options);\nuploader." + method + "(item);",
                "src/calls.ts", method + " mutates or sends the upload queue");
    }

    @ParameterizedTest(name = "lifecycle callback {0}")
    @ValueSource(strings = {
            "onAfterAddingFile", "onBeforeUploadItem", "onBuildItemForm", "onWhenAddingFileFailed",
            "onAfterAddingAll", "onProgressItem", "onProgressAll", "onSuccessItem", "onErrorItem",
            "onCancelItem", "onCompleteItem", "onCompleteAll"
    })
    void marksOwnedUploaderLifecycleCallbacks(String callback) {
        assertSourceMarker("import { FileUploader } from 'ng2-file-upload';\nconst uploader = new FileUploader(options);\nuploader." + callback + " = () => {};",
                "src/callbacks.ts", callback + " owns upload lifecycle behavior");
    }

    @Test
    void marksCredentialAssignmentInsideOwnedUploaderFile() {
        assertSourceMarker("import { FileUploader } from 'ng2-file-upload';\nconst uploader = new FileUploader(options);\nuploader.onBeforeUploadItem = item => { item.withCredentials = false; };",
                "src/auth.ts", "withCredentials changes cross-origin");
    }

    @Test
    void marksChangedTransformResponseOverrideAndProtectedInternals() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                typescript("""
                        import { FileUploader, ParsedResponseHeaders } from 'ng2-file-upload';
                        export class CustomUploader extends FileUploader {
                          protected _transformResponse(response: string, headers: ParsedResponseHeaders): string {
                            const parsed = this._parseHeaders('x');
                            return response + parsed['content-type'];
                          }
                        }
                        """, source -> source.path("src/custom-uploader.ts").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "changed protected _transformResponse");
                            assertContains(printed, "protected/internal _parseHeaders");
                        })));
    }

    @Test
    void customSubclassItselfAndInternalFileAccessAreReported() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                typescript("""
                        import { FileUploader, FileItem } from 'ng2-file-upload';
                        class CustomUploader extends FileUploader {
                          name(item: FileItem) { return item._file.name; }
                          send(item: FileItem) { this._onCompleteItem(item, '', 200, {}); }
                        }
                        """, source -> source.path("src/custom.ts").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "protected internal _file");
                            assertContains(printed, "protected/internal _onCompleteItem");
                        })));
    }

    @Test
    void sameNamedUnownedAndShadowedValuesAreNotMarked() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                typescript("class FileUploader { uploadAll() {} }\nnew FileUploader().uploadAll();", source -> source.path("src/unowned.ts")),
                typescript("import { FileUploader } from 'ng2-file-upload';\nfunction run(FileUploader: any) { return new FileUploader({ url: '/x' }); }",
                        source -> source.path("src/shadow.ts").afterRecipe(after ->
                                assertFalse(after.printAll().contains("Review this FileUploader construction"), after.printAll()))),
                typescript("import { FileUploader } from 'ng2-file-upload';\nconst other = { uploadAll() {} };\nother.uploadAll();",
                        source -> source.path("src/other.ts")));
    }

    @Test
    void unrelatedStringsDynamicRequiresSimilarPackagesAndExcludedParentsAreIgnored() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                javascript("const text = 'ng2-file-upload/private';", source -> source.path("src/string.js")),
                typescript("import { FileUploader } from 'ng2-file-upload-extra';\nnew FileUploader({});", source -> source.path("src/similar.ts")),
                javascript("const upload = require(name);\nupload.uploadAll();", source -> source.path("src/dynamic.js")),
                typescript("import { FileUploader } from 'ng2-file-upload';\nnew FileUploader({ url: '/x' });", source -> source.path("generated/src/file.ts")),
                typescript("import { FileUploader } from 'ng2-file-upload';\nnew FileUploader({ url: '/x' });", source -> source.path("install-cache/file.ts")));
    }

    @Test
    void realAqualityUploaderOwnershipIsDetected() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                // aquality-automation/aquality-tracking-ui@90f313a6fe7b954e9b80b10302edf8bba2167154
                typescript("""
                        import { FileUploader } from 'ng2-file-upload';
                        export class UploaderComponent {
                          public uploader: FileUploader;
                          init(URL: string, authToken: string) {
                            this.uploader = new FileUploader({
                              url: URL, authToken, removeAfterUpload: true, maxFileSize: 10485760,
                              allowedMimeType: ['image/png', 'application/pdf']
                            });
                            this.uploader.onBeforeUploadItem = item => { item.withCredentials = false; };
                            this.uploader.onSuccessItem = item => this.uploaded.emit(item);
                            this.uploader.onWhenAddingFileFailed = (item, filter) => this.failed(item, filter);
                          }
                        }
                        """, source -> source.path("fixtures/aquality/uploader.element.ts").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "Review this FileUploader construction");
                            assertContains(printed, "authToken controls upload endpoint");
                            assertContains(printed, "allowedMimeType is a client-side filter only");
                            assertContains(printed, "onWhenAddingFileFailed owns upload lifecycle");
                            assertContains(printed, "withCredentials changes cross-origin");
                        })));
    }

    @Test
    void realBotanicUploaderCallbackIsDetected() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                // ghillert/botanic-ng@e824ceaa59a08d938bffb6b4f74ca12576d6c18a
                typescript("""
                        import { FileItem, FileUploader, ParsedResponseHeaders } from 'ng2-file-upload';
                        export class PlantDetailsComponent {
                          public uploader: FileUploader;
                          init(plantId: number) {
                            this.uploader = new FileUploader({ url: '/upload/plants/' + plantId });
                            this.uploader.onCompleteItem = (item: FileItem, response: string, status: number, headers: ParsedResponseHeaders) => {
                              console.log(response, status, headers);
                            };
                          }
                        }
                        """, source -> source.path("fixtures/botanic/plant-details.component.ts")
                        .after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "onCompleteItem owns upload lifecycle behavior");
                            assertContains(after.printAll(), "url controls upload endpoint");
                        })));
    }

    @Test
    void realBlueriqCustomUploaderInternalsAreDetected() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                // blueriq/blueriq-material@4626d39a1d014abf3d96b0338b8299e21e340f69
                typescript("""
                        import { FileItem, FileLikeObject, FileUploader, FileUploaderOptions } from 'ng2-file-upload';
                        export class CustomFileUploader extends FileUploader {
                          uploadAll(): void {
                            const xhr = new XMLHttpRequest();
                            const headers = this._parseHeaders(xhr.getAllResponseHeaders());
                            const response = this._transformResponse(xhr.response);
                            this._onCompleteItem(this.queue[0], response, xhr.status, headers);
                          }
                          _fileTypeFilter(item: FileLikeObject): boolean { return !!item.name; }
                        }
                        """, source -> source.path("fixtures/blueriq/custom-file-uploader.ts")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "protected/internal _parseHeaders");
                            assertContains(printed, "protected/internal _transformResponse");
                            assertContains(printed, "protected/internal _onCompleteItem");
                        })));
    }

    @Test
    void sourceMarkersAreIdempotentAndInstallLeafIsEligible() {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { FileUploader } from 'ng2-file-upload';\nconst uploader = new FileUploader({ url: '/upload' });",
                        source -> source.path("src/install.ts").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            String message = "Review this FileUploader construction";
                            assertTrue(printed.indexOf(message) == printed.lastIndexOf(message), printed);
                        })));
    }

    private void assertSourceMarker(String code, String path, String message) {
        rewriteRun(spec -> spec.recipe(new FindNg2FileUploadTypeScriptRisks()),
                typescript(code, source -> source.path(path).after(actual -> actual)
                        .afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
