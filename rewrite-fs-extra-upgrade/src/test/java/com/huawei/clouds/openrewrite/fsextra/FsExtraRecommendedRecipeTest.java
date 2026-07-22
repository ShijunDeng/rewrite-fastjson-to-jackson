package com.huawei.clouds.openrewrite.fsextra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class FsExtraRecommendedRecipeTest implements RewriteTest {
    private static final String MIGRATION = "com.huawei.clouds.openrewrite.fsextra.MigrateFsExtraTo11_3_4";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(FsExtraDependencyTest.environment().activateRecipes(MIGRATION));
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesOfficialV8RuntimeAndJsonContractsAtPinnedTag() {
        // jprichardson/node-fs-extra 8.1.0 @ b7df7cce3f7ca5bc0ab85110aa997bd0ad33482f
        // package.json supports Node >=6.9 and exports lib/index.js without an exports map.
        rewriteRun(
                json("{\"engines\":{\"node\":\">=6.9\"},\"dependencies\":{\"fs-extra\":\"8.1.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("11.3.4"));
                                    assertTrue(printed.contains("Node.js >=14.14"));
                                })),
                javascript("const fs = require('fs-extra');\nfs.readJson(file, null, callback);\nfs.outputJson(file, state, { spaces: 2 });\n",
                        source -> source.path("src/config.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("no longer accepts null"));
                                    assertTrue(printed.contains("snapshots the object"));
                                })));
    }

    @Test
    void appliesOfficialV11EsmEntryAndMarksCopyBehaviorAtPinnedTarget() {
        // jprichardson/node-fs-extra 11.3.4 @ 353a29b18c883fa0f3997fd8be90a89077633af4
        // CHANGELOG.md and lib/esm.mjs define the exports-map, ESM, copy, runtime, and API boundaries.
        rewriteRun(
                json("{\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"fs-extra\":\"10.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript("import { copy, ensureDir as mkdir } from 'fs-extra';\nawait mkdir(output);\nawait copy(input, output, { filter });\n",
                        source -> source.path("src/build.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("from 'fs-extra/esm'"));
                                    assertTrue(printed.contains("filter invocation and concurrency"));
                                })));
    }

    @Test
    void detectsPatternFlyDeepImportFixtureAtPinnedCommit() {
        // patternfly/react-topology @ e795e7b46a764993a01b98085e06e072d2c6d626
        // packages/module/scripts/writeClassMaps.js uses the root API and fs-extra/lib/mkdirs together.
        rewriteRun(
                json("{\"engines\":{\"node\":\">=16\"},\"devDependencies\":{\"fs-extra\":\"10.1.0\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                javascript("const { join, basename, resolve, relative } = require('path');\nconst { outputFileSync, copyFileSync, copySync, appendFileSync, existsSync, readFileSync } = require('fs-extra');\nconst { ensureDirSync } = require('fs-extra/lib/mkdirs');\n",
                        source -> source.path("packages/module/scripts/writeClassMaps.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("blocks deep imports")))));
    }

    @Test
    void targetManifestAndSafePublicCommonJsUsageAreNoOp() {
        // jprichardson/node-fs-extra 11.3.4 package.json @ 353a29b18c883fa0f3997fd8be90a89077633af4
        rewriteRun(
                json("{\"engines\":{\"node\":\">=18.12\"},\"dependencies\":{\"fs-extra\":\"11.3.4\"}}",
                        source -> source.path("package.json")),
                javascript("const fs = require('fs-extra');\nif (fs.pathExistsSync(cache)) console.log('ready');\n",
                        source -> source.path("src/modern.js")));
    }

    @Test
    void marksLocksWithoutSynthesizingPackageManagerMetadata() {
        rewriteRun(
                json("{\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"fs-extra\":\"11.1.1\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                json("{\"packages\":{\"node_modules/fs-extra\":{\"version\":\"11.1.1\",\"integrity\":\"sha512-KEEP\"}}}",
                        source -> source.path("package-lock.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("Regenerate this npm lockfile")))),
                text("fs-extra@11.1.1:\n  resolution: {integrity: sha512-STAY}\n",
                        source -> source.path("pnpm-lock.yaml").after(actual -> actual)
                                .afterRecipe(file -> assertTrue(file.printAll().contains("Regenerate this lockfile")))));
    }

    @Test
    void followsPinnedOpenRewriteImportShapesAndIsDiscoverableAndIdempotent() {
        // openrewrite/rewrite-javascript @ 9e3b820e6a44808b095bb7e3aab670fd67de99a5
        // rewrite-javascript/src/test/java/org/openrewrite/javascript/tree/ImportTest.java
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"fs-extra\":\"11.1.1\"}}",
                        source -> source.path("package.json").after(actual -> actual)),
                typescript("import { ensureDir as makeDir , pathExists as exists } from \"fs-extra\";\n",
                        source -> source.path("src/imports.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("from \"fs-extra/esm\"")))));
        Recipe recipe = FsExtraDependencyTest.environment().activateRecipes(MIGRATION);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(FsExtraDependencyTest.environment().listRecipes().stream()
                .anyMatch(candidate -> MIGRATION.equals(candidate.getName())));
    }
}
