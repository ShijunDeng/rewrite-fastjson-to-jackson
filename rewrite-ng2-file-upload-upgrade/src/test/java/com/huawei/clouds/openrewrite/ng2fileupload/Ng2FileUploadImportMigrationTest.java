package com.huawei.clouds.openrewrite.ng2fileupload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class Ng2FileUploadImportMigrationTest implements RewriteTest {
    @ParameterizedTest(name = "normalizes {1} from {0}")
    @MethodSource("knownDeepImports")
    void normalizesEveryProvenNamedDeepImport(String module, String symbol) {
        rewriteRun(spec -> spec.recipe(new NormalizeNg2FileUploadPublicImports()),
                typescript("import { " + symbol + " } from '" + module + "';",
                        "import { " + symbol + " } from 'ng2-file-upload';",
                        source -> source.path("src/app.ts")));
    }

    static Stream<Arguments> knownDeepImports() {
        return Stream.of(
                Arguments.of("ng2-file-upload/file-upload/file-uploader.class", "Headers"),
                Arguments.of("ng2-file-upload/file-upload/file-uploader.class", "ParsedResponseHeaders"),
                Arguments.of("ng2-file-upload/file-upload/file-uploader.class", "FilterFunction"),
                Arguments.of("ng2-file-upload/file-upload/file-uploader.class", "FileUploaderOptions"),
                Arguments.of("ng2-file-upload/file-upload/file-uploader.class", "FileUploader"),
                Arguments.of("ng2-file-upload/file-upload/file-item.class", "FileItem"),
                Arguments.of("ng2-file-upload/file-upload/file-like-object.class", "FileLikeObject"),
                Arguments.of("ng2-file-upload/file-upload/file-drop.directive", "FileDropDirective"),
                Arguments.of("ng2-file-upload/file-upload/file-select.directive", "FileSelectDirective"),
                Arguments.of("ng2-file-upload/file-upload/file-upload.module", "FileUploadModule"));
    }

    @Test
    void preservesAliasesTypeOnlySyntaxAndQuoteStyle() {
        rewriteRun(spec -> spec.recipe(new NormalizeNg2FileUploadPublicImports()),
                typescript("import { FileUploader as Upload, FileUploaderOptions } from \"ng2-file-upload/file-upload/file-uploader.class\";",
                        "import { FileUploader as Upload, FileUploaderOptions } from \"ng2-file-upload\";",
                        source -> source.path("src/upload.ts")),
                typescript("import type { FileItem } from 'ng2-file-upload/file-upload/file-item.class';",
                        "import type { FileItem } from 'ng2-file-upload';",
                        source -> source.path("src/types.ts")));
    }

    @Test
    void mixedOrUnknownSymbolsPreventWholeImportRewrite() {
        rewriteRun(spec -> spec.recipe(new NormalizeNg2FileUploadPublicImports()),
                typescript("import { FileUploader, PrivateUploader } from 'ng2-file-upload/file-upload/file-uploader.class';", source -> source.path("src/mixed.ts")),
                typescript("import { FileItem } from 'ng2-file-upload/file-upload/file-uploader.class';", source -> source.path("src/wrong-file.ts")));
    }

    @Test
    void defaultNamespaceSideEffectExportAndRequireAreNeverAutoRewritten() {
        rewriteRun(spec -> spec.recipe(new NormalizeNg2FileUploadPublicImports()),
                typescript("import FileUploader from 'ng2-file-upload/file-upload/file-uploader.class';", source -> source.path("src/default.ts")),
                typescript("import * as upload from 'ng2-file-upload/file-upload/file-uploader.class';", source -> source.path("src/namespace.ts")),
                typescript("import 'ng2-file-upload/file-upload/file-uploader.class';", source -> source.path("src/side-effect.ts")),
                typescript("export { FileUploader } from 'ng2-file-upload/file-upload/file-uploader.class';", source -> source.path("src/export.ts")),
                javascript("const upload = require('ng2-file-upload/file-upload/file-uploader.class');", source -> source.path("src/require.js")));
    }

    @Test
    void rootSimilarDynamicAndArbitraryDeepImportsAreUntouched() {
        rewriteRun(spec -> spec.recipe(new NormalizeNg2FileUploadPublicImports()),
                typescript("import { FileUploader } from 'ng2-file-upload';", source -> source.path("src/root.ts")),
                typescript("import { FileUploader } from 'ng2-file-upload-extra/file-upload/file-uploader.class';", source -> source.path("src/similar.ts")),
                typescript("import { InternalThing } from 'ng2-file-upload/private/new';", source -> source.path("src/private.ts")),
                javascript("const moduleName = 'ng2-file-upload/file-upload/file-uploader.class';\nimport(moduleName);", source -> source.path("src/dynamic.js")));
    }

    @Test
    void skipsExcludedParentsButProcessesInstallLeaf() {
        rewriteRun(spec -> spec.recipe(new NormalizeNg2FileUploadPublicImports()),
                typescript("import { FileUploader } from 'ng2-file-upload/file-upload/file-uploader.class';", source -> source.path("generated-client/upload.ts")),
                typescript("import { FileUploader } from 'ng2-file-upload/file-upload/file-uploader.class';", source -> source.path("install-cache/upload.ts")),
                typescript("import { FileUploader } from 'ng2-file-upload/file-upload/file-uploader.class';",
                        "import { FileUploader } from 'ng2-file-upload';", source -> source.path("src/install.ts")),
                javascript("import { FileUploader } from 'ng2-file-upload/file-upload/file-uploader.class';",
                        "import { FileUploader } from 'ng2-file-upload';", source -> source.path("src/install.js")));
    }

    @Test
    void importNormalizationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new NormalizeNg2FileUploadPublicImports()).cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { FileUploader } from 'ng2-file-upload/file-upload/file-uploader.class';",
                        "import { FileUploader } from 'ng2-file-upload';", source -> source.path("src/uploader.ts")));
    }

    @Test
    void lowLevelUpgradeRecipeDoesNotChangeOrMarkSource() {
        rewriteRun(spec -> spec.recipe(Ng2FileUploadDependencyTest.environment().activateRecipes(
                        Ng2FileUploadDependencyTest.UPGRADE)),
                typescript("import { FileUploader } from 'ng2-file-upload/file-upload/file-uploader.class';\n" +
                           "const uploader = new FileUploader({ url: '/upload' });",
                        source -> source.path("src/uploader.ts")));
    }

    @Test
    void recommendedRecipeNormalizesBeforeDeepEntryMarker() {
        rewriteRun(spec -> spec.recipe(Ng2FileUploadDependencyTest.environment().activateRecipes(
                        Ng2FileUploadDependencyTest.MIGRATE)),
                typescript("import { FileUploader } from 'ng2-file-upload/file-upload/file-uploader.class';\nconst uploader = new FileUploader({ url: '/upload' });",
                        source -> source.path("src/uploader.ts").after(actual -> actual.replace(
                                "ng2-file-upload/file-upload/file-uploader.class", "ng2-file-upload"))
                                .afterRecipe(after -> {
                                    String printed = after.printAll();
                                    assertFalse(printed.contains("exports only its root"), printed);
                                    assertTrue(printed.contains("Review this FileUploader construction"), printed);
                                })));
    }
}
