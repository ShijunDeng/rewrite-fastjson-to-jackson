package com.huawei.clouds.openrewrite.fsextra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class FsExtraSourceRiskTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksDeepImportsRequiresAndBuildAliases() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                javascript("const mkdirs = require('fs-extra/lib/mkdirs');\nconst external = ['fs-extra/lib/output'];\n",
                        source -> source.path("rollup.config.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("blocks deep imports")))),
                typescript("import { ensureFile } from 'fs-extra/lib/ensure';\n",
                        source -> source.path("src/deep.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("blocks deep imports")))));
    }

    @Test
    void marksNativeNamedImportsAtExactSpecifiers() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                typescript("import { copy, readFile as read, writeFileSync } from 'fs-extra';\n",
                        source -> source.path("src/native.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("readFile is a native Node fs API"));
                                    assertTrue(printed.contains("writeFileSync is a native Node fs API"));
                                    assertFalse(printed.contains("copy is a native"));
                                })));
    }

    @Test
    void distinguishesTypeOnlyNamesFromNativeRuntimeApis() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                typescript("import type { CopyOptions } from 'fs-extra';\n",
                        source -> source.path("src/types.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("not a documented runtime named export"));
                                    assertFalse(printed.contains("native Node fs API"));
                                })));
    }

    @Test
    void marksEsmDefaultAndNativeNamespaceUse() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                typescript("import fs from 'fs-extra/esm';\nimport * as fse from 'fs-extra/esm';\nfs.copy(a, b);\nfse.readFile(file, callback);\n",
                        source -> source.path("src/esm.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("default object contains only"));
                                    assertTrue(printed.contains("readFile is a native Node fs API"));
                                    assertTrue(printed.contains("copy behavior changed"));
                                })));
    }

    @Test
    void marksNamedAliasAndCommonJsCopySemantics() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                javascript("import { copy as cp } from 'fs-extra';\nconst fse = require('fs-extra');\ncp(source, target, { filter });\nfse.copySync(source, target, { preserveTimestamps: true });\n",
                        source -> source.path("src/copy.js").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("filter invocation and concurrency"));
                                    assertTrue(printed.contains("preserveTimestamps"));
                                })));
    }

    @Test
    void marksOnlyRemovedRemoveOptionsNotCallbackOrPromiseForms() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                typescript("import { remove, removeSync } from 'fs-extra';\nremove(path, { maxBusyTries: 3 }, callback);\nremoveSync(path, { glob: false });\nremove(path, callback);\nremove(path);\n",
                        source -> source.path("src/remove.ts").after(actual -> actual)
                                .afterRecipe(cu -> {
                                    String printed = cu.printAll();
                                    assertTrue(printed.contains("removed undocumented"));
                                    assertTrue(printed.contains("remove(path, callback)"));
                                })));
    }

    @Test
    void marksNullJsonOptionsInReadWriteAndOutputFamilies() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                typescript("import { readJson, writeJsonSync, outputJSON } from 'fs-extra';\nreadJson(file, null, callback);\nwriteJsonSync(file, value, null);\noutputJSON(file, value, null);\n",
                        source -> source.path("src/json.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("no longer accepts null")))));
    }

    @Test
    void marksOutputSnapshotTimingWithNormalOptions() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                javascript("import { outputJson } from 'fs-extra';\nconst pending = outputJson(file, state, { spaces: 2 });\nstate.ready = true;\nawait pending;\n",
                        source -> source.path("src/output.js").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("snapshots the object")))));
    }

    @Test
    void marksLinkAndSymlinkDestinationSemantics() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                typescript("import { ensureLink, ensureSymlinkSync } from 'fs-extra';\nensureLink(source, target);\nensureSymlinkSync(source, target, 'junction');\n",
                        source -> source.path("src/links.ts").after(actual -> actual)
                                .afterRecipe(cu -> assertTrue(cu.printAll().contains("existing destination's type")))));
    }

    @Test
    void leavesUnownedSameNamesAndSafeRootApisAlone() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                typescript("const copy = localCopy;\ncopy(a, b);\nconst fs = require('fs-extra');\nfs.pathExists(path);\nfs.readFile(path, callback);\n",
                        source -> source.path("src/local.ts")));
    }

    @Test
    void leavesShadowedImportedBindingAlone() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                typescript("import { copy } from 'fs-extra';\nfunction run(copy: any) { copy(a, b); }\n",
                        source -> source.path("src/shadowed.ts")));
    }

    @Test
    void leavesShadowedCommonJsNamespaceAlone() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                typescript("const fse = require('fs-extra');\nfunction run(fse: any) { fse.copy(a, b); }\n",
                        source -> source.path("src/shadowed-commonjs.ts")));
    }

    @Test
    void excludesInstalledGeneratedAndBuildOutputSource() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()),
                typescript("import { copy } from 'fs-extra';\ncopy(a, b);\n", source -> source.path("generated/client.ts")),
                javascript("const copy = require('fs-extra/lib/copy');\n", source -> source.path("install/copy.js")),
                javascript("const fs = require('fs-extra');\nfs.copy(a, b);\n", source -> source.path("dist/bundle.js")));
    }

    @Test
    void searchMarkersAreIdempotent() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraJavaScriptRisks()).cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { copy } from 'fs-extra/esm';\ncopy(a, b);\n",
                        source -> source.path("src/idempotent.ts").after(actual -> actual)));
    }
}
