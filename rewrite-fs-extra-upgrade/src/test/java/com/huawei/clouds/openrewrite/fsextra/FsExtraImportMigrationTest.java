package com.huawei.clouds.openrewrite.fsextra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class FsExtraImportMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void routesPureNamedFsExtraExportsToOfficialEsmEntry() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicFsExtraImports()),
                typescript("import { copy, ensureDir as mkdir, readJson } from 'fs-extra';\n",
                        "import { copy, ensureDir as mkdir, readJson } from 'fs-extra/esm';\n",
                        source -> source.path("src/files.ts")));
    }

    @Test
    void preservesDoubleQuotesAndOpenRewriteImportSpacing() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicFsExtraImports()),
                typescript("import { copy as cp , outputFile as write } from \"fs-extra\";\n",
                        "import { copy as cp , outputFile as write } from \"fs-extra/esm\";\n",
                        source -> source.path("src/spaces.ts")));
    }

    @Test
    void leavesMixedNativeFsNamedImportsAlone() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicFsExtraImports()),
                typescript("import { copy, readFile, writeFileSync } from 'fs-extra';\n",
                        source -> source.path("src/mixed.ts")));
    }

    @Test
    void leavesDefaultNamespaceSideEffectAndCommonJsImportsAlone() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicFsExtraImports()),
                javascript("import fs from 'fs-extra';\nimport * as fse from 'fs-extra';\nimport 'fs-extra';\nconst legacy = require('fs-extra');\n",
                        source -> source.path("src/module.js")));
    }

    @Test
    void leavesDeepSimilarAndAlreadyModernImportsAlone() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicFsExtraImports()),
                typescript("import { ensureDir } from 'fs-extra/lib/mkdirs';\nimport { copy } from '@example/fs-extra';\nimport { move } from 'fs-extra/esm';\n",
                        source -> source.path("src/boundaries.ts")));
    }

    @Test
    void excludesGeneratedAndInstalledSources() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicFsExtraImports()),
                typescript("import { copy } from 'fs-extra';\n", source -> source.path("generated/copy.ts")),
                javascript("import { ensureDir } from 'fs-extra';\n", source -> source.path("node_modules/pkg/index.js")),
                javascript("import { move } from 'fs-extra';\n", source -> source.path("install/scripts/move.js")));
    }

    @Test
    void importMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicFsExtraImports())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { ensureDir, pathExists } from 'fs-extra';\n",
                        "import { ensureDir, pathExists } from 'fs-extra/esm';\n",
                        source -> source.path("src/idempotent.ts")));
    }
}
