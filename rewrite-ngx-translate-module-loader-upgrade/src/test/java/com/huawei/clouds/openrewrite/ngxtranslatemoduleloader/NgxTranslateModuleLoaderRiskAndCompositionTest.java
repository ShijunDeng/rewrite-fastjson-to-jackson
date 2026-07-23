package com.huawei.clouds.openrewrite.ngxtranslatemoduleloader;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class NgxTranslateModuleLoaderRiskAndCompositionTest implements RewriteTest {
    private static final String UPGRADE =
            "com.huawei.clouds.openrewrite.ngxtranslatemoduleloader.UpgradeNgxTranslateModuleLoaderTo5_1_0";
    private static final String MIGRATE =
            "com.huawei.clouds.openrewrite.ngxtranslatemoduleloader.MigrateNgxTranslateModuleLoaderTo5_1_0";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksEveryRemovedMixedSpecifierItsReferencesAndLoaderConstruction() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderSourceRisks()), typescript(
                "import { Translation, TranslationKey as Key, ModuleTranslateLoader as Loader } from '@larscom/ngx-translate-module-loader';\nlet value: Translation; let key: Key; const loader = new Loader(http, options);\n",
                source -> source.path("src/translation.ts").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(2, occurrences(printed, "Translation was removed"), printed);
                    assertEquals(2, occurrences(printed, "TranslationKey was removed"), printed);
                    assertEquals(2, occurrences(printed, "Loader 5 fetches responseType text"), printed);
                })));
    }

    @Test
    void marksTheExactPublishedDeepEntryAndReexport() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderSourceRisks()),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader/lib/translation';\nlet value: Translation;\n",
                        source -> source.path("src/deep.ts").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("Private loader deep import was removed"), printed);
                            assertTrue(printed.contains("Translation was removed"), printed);
                        })),
                typescript("export { Translation, TranslationKey } from '@larscom/ngx-translate-module-loader';\n",
                        source -> source.path("src/public-api.ts").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("verify this re-export against the target public API"), after.printAll()))));
    }

    @Test
    void marksDefaultNamespaceAndSideEffectRootImports() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderSourceRisks()),
                typescript("import loader from '@larscom/ngx-translate-module-loader';", source -> source.path("src/default.ts")
                        .after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("Namespace/default/side-effect"), after.printAll()))),
                typescript("import * as loader from '@larscom/ngx-translate-module-loader';", source -> source.path("src/namespace.ts")
                        .after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("Namespace/default/side-effect"), after.printAll()))),
                typescript("import '@larscom/ngx-translate-module-loader';", source -> source.path("src/effect.ts")
                        .after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("Namespace/default/side-effect"), after.printAll()))));
    }

    @Test
    void marksRamdaOnlyWhenTheLoaderPackageIsUsedInTheSameCompilationUnit() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderSourceRisks()),
                typescript("import { mergeDeepRight } from 'ramda';\nimport { ModuleTranslateLoader } from '@larscom/ngx-translate-module-loader';\nconst merged = mergeDeepRight(a, b);\n",
                        source -> source.path("src/legacy-merge.ts").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("Loader 5 no longer declares Ramda"), after.printAll()))),
                typescript("import { mergeDeepRight } from 'ramda';\nconst merged = mergeDeepRight(a, b);\n",
                        source -> source.path("src/owned-ramda.ts")));
    }

    @Test
    void doesNotMarkUnownedSameNamesSimilarPackagesOrStringLiterals() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderSourceRisks()),
                typescript("type Translation = Record<string, string>; class ModuleTranslateLoader {}\nnew ModuleTranslateLoader();", source -> source.path("src/local.ts")),
                typescript("import { Translation } from '@company/ngx-translate-module-loader';\nlet value: Translation;", source -> source.path("src/similar.ts")),
                typescript("import { Translation } from '@larscom/ngx-translate-module-loader-extra';\nlet value: Translation;", source -> source.path("src/suffix.ts")),
                javascript("const note = '@larscom/ngx-translate-module-loader/lib/translation';", source -> source.path("src/note.js")));
    }

    @Test
    void doesNotMarkShadowedReferencesOrSameNamedPropertiesAsOwnedReferences() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderSourceRisks()),
                typescript("import { Translation as Shape } from '@larscom/ngx-translate-module-loader';\nfunction read(Shape: any) { return Shape.value; }\n",
                        source -> source.path("src/shadowed-type.ts").after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(), "Translation was removed"), after.printAll()))),
                typescript("import { Translation as Shape } from '@larscom/ngx-translate-module-loader';\nconst value = service.Shape; const config = { Shape: true };\n",
                        source -> source.path("src/properties.ts").after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(), "Translation was removed"), after.printAll()))),
                typescript("import { ModuleTranslateLoader as Loader } from '@larscom/ngx-translate-module-loader';\nfunction create(Loader: any) { return new Loader(); }\n",
                        source -> source.path("src/shadowed-loader.ts").after(actual -> actual).afterRecipe(after ->
                                assertEquals(1, occurrences(after.printAll(), "Loader 5 fetches responseType text"), after.printAll()))));
    }

    @Test
    void sourceRiskExcludesParentsButProcessesInstallLeaf() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderSourceRisks()),
                typescript("import { TranslationKey } from '@larscom/ngx-translate-module-loader';\nlet key: TranslationKey;", source -> source.path("GENERATED-client/key.ts")),
                typescript("import { TranslationKey } from '@larscom/ngx-translate-module-loader';\nlet key: TranslationKey;", source -> source.path("install-cache/key.ts")),
                typescript("import { TranslationKey } from '@larscom/ngx-translate-module-loader';\nlet key: TranslationKey;",
                        source -> source.path("src/install.ts").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("TranslationKey was removed"), after.printAll()))));
    }

    @Test
    void marksEveryDirectPeerAndToolchainFloorOnTheExactValue() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderManifestRisks()), json(
                """
                {"dependencies":{"@larscom/ngx-translate-module-loader":"3.1.2","@angular/core":"^15.2.0","@angular/common":"~14.3.0","@ngx-translate/core":"^14.0.0"},"devDependencies":{"@angular/cli":"15.2.9","typescript":"~4.8.4"},"engines":{"node":"^14.20.0"}}
                """,
                source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertTrue(printed.contains("/*~~(The workbook-selected loader declaration remains"), printed);
                    assertTrue(printed.contains("/*~~(@angular/core must be aligned"), printed);
                    assertTrue(printed.contains("/*~~(@angular/common must be aligned"), printed);
                    assertTrue(printed.contains("/*~~(@angular/cli must be aligned"), printed);
                    assertTrue(printed.contains("/*~~(ngx-translate-module-loader 5.1.0 peers"), printed);
                    assertTrue(printed.contains("/*~~(Angular 16 starts at TypeScript 4.9.3"), printed);
                    assertTrue(printed.contains("/*~~(Angular 16 starts at Node 16.14.0"), printed);
                    assertEquals(7, occurrences(printed, "/*~~("), printed);
                })));
    }

    @Test
    void complexPeerRangesAreMarkedBecauseCompatibilityCannotBeProved() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderManifestRisks()), json(
                "{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"5.1.0\",\"@angular/core\":\">=16 <19\",\"@ngx-translate/core\":\"16 || 17\"},\"devDependencies\":{\"typescript\":\">=4.9.3 <5.2\"},\"engines\":{\"node\":\">=16.14\"}}",
                source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                        assertEquals(4, occurrences(after.printAll(), "/*~~("), after.printAll()))));
    }

    @Test
    void marksCentralOverrideAndResolutionSelectorsAtTheirActualValues() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderManifestRisks()), json(
                """
                {"dependencies":{"@larscom/ngx-translate-module-loader":"5.1.0","@angular/core":"16.2.0","@ngx-translate/core":"16.0.4"},"overrides":{"@larscom/ngx-translate-module-loader@3.1.2":"3.1.2"},"resolutions":{"parent>@larscom/ngx-translate-module-loader":"3.1.1"},"pnpm":{"overrides":{"@larscom/ngx-translate-module-loader":"3.1.2"}}}
                """,
                source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertEquals(3, occurrences(printed, "Central package-manager ownership detected"), printed);
                    assertTrue(printed.contains("/*~~(Central package-manager ownership detected") && printed.contains("~~>*/\"3.1.2\""), printed);
                    assertTrue(printed.contains("~~>*/\"3.1.1\""), printed);
                })));
    }

    @Test
    void compatibleTargetManifestAndUnmanagedManifestsAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderManifestRisks()),
                json("{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"^5.1.0\",\"@angular/core\":\"^16.2.0\",\"@ngx-translate/core\":\"~16.0.4\"},\"devDependencies\":{\"typescript\":\"5.1.6\"},\"engines\":{\"node\":\"18.18.0\"}}", source -> source.path("package.json")),
                json("{\"dependencies\":{\"@angular/core\":\"14.0.0\",\"@ngx-translate/core\":\"14.0.0\"}}", source -> source.path("apps/other/package.json")));
    }

    @Test
    void manifestRiskExcludesGeneratedParentsButProcessesAWorkspaceChild() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderManifestRisks()),
                json("{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"3.1.2\",\"@angular/core\":\"14.0.0\"}}", source -> source.path("generated-app/package.json")),
                json("{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"3.1.2\",\"@angular/core\":\"14.0.0\"}}",
                        source -> source.path("apps/web/package.json").after(actual -> actual).afterRecipe(after ->
                                assertEquals(2, occurrences(after.printAll(), "/*~~("), after.printAll()))));
    }

    @Test
    void lowLevelPublicUpgradeDoesNotTouchOrMarkSource() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(UPGRADE)), typescript(
                "import { Translation, ModuleTranslateLoader } from '@larscom/ngx-translate-module-loader';\nlet value: Translation; new ModuleTranslateLoader(http, options);",
                source -> source.path("src/translation.ts")));
    }

    @Test
    void recommendedRecipeUpgradesMigratesAndMarksOnlyTheRemainingDecisions() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                json("{\"dependencies\":{\"@larscom/ngx-translate-module-loader\":\"^3.1.2\",\"@angular/core\":\"^15.2.0\",\"@ngx-translate/core\":\"^14.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual.replace("^3.1.2", "^5.1.0")).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertFalse(printed.contains("workbook-selected loader declaration remains"), printed);
                            assertEquals(2, occurrences(printed, "/*~~("), printed);
                        })),
                typescript("import type { Translation } from '@larscom/ngx-translate-module-loader';\nlet value: Translation;\n",
                        "import type { TranslationObject } from '@ngx-translate/core';\nlet value: TranslationObject;\n",
                        source -> source.path("src/types.ts")),
                typescript("import { ModuleTranslateLoader } from '@larscom/ngx-translate-module-loader';\nconst loader = new ModuleTranslateLoader(http, options);\n",
                        source -> source.path("src/loader.ts").after(actual -> actual).afterRecipe(after ->
                                assertEquals(2, occurrences(after.printAll(), "Loader 5 fetches responseType text"), after.printAll()))));
    }

    @Test
    void riskMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindNgxTranslateModuleLoaderSourceRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { TranslationKey } from '@larscom/ngx-translate-module-loader';\nlet key: TranslationKey;",
                        source -> source.path("src/key.ts").after(actual -> actual).afterRecipe(after ->
                                assertEquals(2, occurrences(after.printAll(), "TranslationKey was removed"), after.printAll()))));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxtranslatemoduleloader")
                .scanYamlResources().build();
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length()) count++;
        return count;
    }
}
