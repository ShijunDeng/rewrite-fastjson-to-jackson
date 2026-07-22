package com.huawei.clouds.openrewrite.antvx6;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class AntvX6JsonAndLockRiskTest implements RewriteTest {
    @Test
    void marksSkippedComplexAndUnlistedMainDeclarations() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JsonRisks()),
                json("{\"dependencies\":{\"@antv/x6\":\">=2.11.3 <4\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("Strict migration skipped")))));
    }

    @Test
    void marksEveryOfficialConsolidatedPackageAndShapeAlignment() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JsonRisks()),
                json("{\"dependencies\":{\"@antv/x6\":\"3.1.7\",\"@antv/x6-plugin-selection\":\"2.2.2\",\"@antv/x6-common\":\"2.1.0\",\"@antv/x6-react-shape\":\"2.2.3\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("merged into @antv/x6"));
                                    assertTrue(printed.contains("compatible 3.x release"));
                                })));
    }

    @Test
    void marksNodeEngineToolchainWhenX6IsPresent() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JsonRisks()),
                json("{\"engines\":{\"node\":\">=16\"},\"dependencies\":{\"@antv/x6\":\"3.1.7\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("Node.js >=20.0.0")))));
    }

    @Test
    void doesNotMarkUnrelatedNodeEngineOrTargetOnlyManifest() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JsonRisks()),
                json("{\"engines\":{\"node\":\">=16\"},\"dependencies\":{\"react\":\"19.0.0\"}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"@antv/x6\":\"^3.1.7\"}}",
                        source -> source.path("apps/graph/package.json")));
    }

    @Test
    void marksPackageLockV1AndV3EntriesButNeverChangesHashes() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JsonRisks()),
                json("{\"lockfileVersion\":1,\"dependencies\":{\"@antv/x6\":{\"version\":\"2.11.3\",\"resolved\":\"https://registry.example/x6.tgz\",\"integrity\":\"sha512-old\"}}}",
                        source -> source.path("package-lock.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("Regenerate this npm lockfile"));
                                    assertTrue(printed.contains("sha512-old"));
                                    assertTrue(printed.contains("2.11.3"));
                                })),
                json("{\"lockfileVersion\":3,\"packages\":{\"\":{\"dependencies\":{\"@antv/x6\":\"^2.11.3\"}},\"node_modules/@antv/x6\":{\"version\":\"2.11.3\",\"integrity\":\"sha512-pinned\"}}}",
                        source -> source.path("apps/ui/package-lock.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("sha512-pinned")))));
    }

    @Test
    void marksYarnAndPnpmEntriesAsPlainText() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6TextLockRisks()),
                text("@antv/x6@^2.11.3:\n  version \"2.11.3\"\n  integrity sha512-old\n",
                        source -> source.path("yarn.lock").after(actual -> actual)
                                .afterRecipe(file -> {
                                    assertTrue(file.printAll().contains("Regenerate this lockfile"));
                                    assertTrue(file.printAll().contains("sha512-old"));
                                })),
                text("lockfileVersion: '9.0'\npackages:\n  '@antv/x6@2.11.3':\n    resolution: {integrity: sha512-old}\n",
                        source -> source.path("pnpm-lock.yaml").after(actual -> actual)
                                .afterRecipe(file -> assertTrue(file.printAll().contains("Regenerate this lockfile")))));
    }

    @Test
    void marksInternalTsconfigAliasesAndIgnoresPublicAndUnrelatedAliases() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JsonRisks()),
                json("{\"compilerOptions\":{\"paths\":{\"x6-internal\":[\"./node_modules/@antv/x6/lib/index\"],\"x6\":[\"@antv/x6\"],\"other\":[\"@example/x6/lib/index\"]}}}",
                        source -> source.path("tsconfig.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("public @antv/x6 entry point"));
                                    assertTrue(printed.contains("@example/x6/lib/index"));
                                })));
    }

    @Test
    void ignoresLocksAndConfigInExcludedTrees() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6JsonRisks()),
                json("{\"dependencies\":{\"@antv/x6\":{\"version\":\"2.11.3\"}}}",
                        source -> source.path("node_modules/demo/package-lock.json")),
                json("{\"compilerOptions\":{\"paths\":{\"x6\":[\"@antv/x6/lib/index\"]}}}",
                        source -> source.path("dist/tsconfig.json")));
        rewriteRun(spec -> spec.recipe(new FindAntvX6TextLockRisks()),
                text("@antv/x6@2.11.3:\n", source -> source.path("build/yarn.lock")));
    }

    @Test
    void lockRiskRecipesDoNotMatchSimilarPackages() {
        rewriteRun(spec -> spec.recipe(new FindAntvX6TextLockRisks()),
                text("@example/x6@2.11.3:\n  version 2.11.3\n@antv/x6-react-shape@2.11.3:\n  version 2.11.3\n",
                        source -> source.path("yarn.lock")));
        rewriteRun(spec -> spec.recipe(new FindAntvX6JsonRisks()),
                json("{\"dependencies\":{\"@example/x6\":{\"version\":\"2.11.3\"}}}",
                        source -> source.path("package-lock.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("Regenerate this npm lockfile")))));
    }
}
