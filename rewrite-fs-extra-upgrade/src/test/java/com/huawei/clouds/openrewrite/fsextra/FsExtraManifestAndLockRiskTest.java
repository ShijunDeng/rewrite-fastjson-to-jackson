package com.huawei.clouds.openrewrite.fsextra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class FsExtraManifestAndLockRiskTest implements RewriteTest {
    @Test
    void marksMissingNodeRuntimeAtDependencyValue() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraManifestRisks()),
                json("{\"dependencies\":{\"fs-extra\":\"11.3.4\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("Node.js >=14.14")))));
    }

    @ParameterizedTest
    @ValueSource(strings = {">=14.14", "^14.14.0", "~14.14.0", ">=16", "18.x", ">=16 || >=18"})
    void acceptsClearlyCompatibleNodeRanges(String range) {
        rewriteRun(spec -> spec.recipe(new FindFsExtraManifestRisks()),
                json("{\"engines\":{\"node\":\"" + range + "\"},\"dependencies\":{\"fs-extra\":\"11.3.4\"}}",
                        source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {">=10", ">=12", ">=14", "^14.0.0", "10 || >=18", "*", "latest",
            ">=14.14.0-beta.1", ">=18 garbage", ">=14.14 <15", "14.14.0 - 16.0.0"})
    void marksOldOrAmbiguousNodeRanges(String range) {
        rewriteRun(spec -> spec.recipe(new FindFsExtraManifestRisks()),
                json("{\"engines\":{\"node\":\"" + range + "\"},\"dependencies\":{\"fs-extra\":\"11.3.4\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("engine declaration"));
                                    assertTrue(printed.contains("requires Node.js"));
                                })));
    }

    @ParameterizedTest
    @ValueSource(strings = {">=8.1.0", "8.1.0 || 10.1.0", "10.x", "workspace:^10.1.0",
            "npm:@company/fs-extra@10.1.0", "11.3.4-beta.1", "11.2.0"})
    void marksSkippedComplexAndUnlistedDeclarations(String declaration) {
        rewriteRun(spec -> spec.recipe(new FindFsExtraManifestRisks()),
                json("{\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"fs-extra\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> assertTrue(document.printAll().contains("Strict migration skipped")))));
    }

    @Test
    void marksTypesOverridesResolutionsAndPhysicalLibPaths() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraManifestRisks()),
                json("{\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"fs-extra\":\"11.3.4\",\"@types/fs-extra\":\"11.0.4\"},\"overrides\":{\"fs-extra\":\"10.1.0\"},\"resolutions\":{\"fs-extra\":\"11.1.1\"},\"scripts\":{\"mock\":\"node -r fs-extra/lib/index.js test.js\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("@types/fs-extra release"));
                                    assertTrue(printed.contains("override or resolution"));
                                    assertTrue(printed.contains("exports only the package root"));
                                })));
    }

    @Test
    void marksNestedNpmOverridesButNotNestedLookalikeConfiguration() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraManifestRisks()),
                json("{\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"fs-extra\":\"11.3.4\"},\"overrides\":{\"tool\":{\"fs-extra\":\"10.1.0\"}},\"tool\":{\"overrides\":{\"fs-extra\":\"10.1.0\"}}}",
                        source -> source.path("package.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertEquals(1, occurrences(printed, "override or resolution"));
                                    assertTrue(printed.contains("\"tool\":{\"overrides\":{\"fs-extra\":\"10.1.0\"}}"));
                                })));
    }

    @Test
    void marksNpmV1AndV3LockEntriesButPreservesIntegrity() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraManifestRisks()),
                json("{\"dependencies\":{\"fs-extra\":{\"version\":\"10.1.0\",\"resolved\":\"https://registry.npmjs.org/fs-extra/-/fs-extra-10.1.0.tgz\",\"integrity\":\"sha512-KEEP\"}}}",
                        source -> source.path("package-lock.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertTrue(printed.contains("Regenerate this npm lockfile"));
                                    assertTrue(printed.contains("sha512-KEEP"));
                                })),
                json("{\"packages\":{\"node_modules/fs-extra\":{\"version\":\"11.1.1\",\"integrity\":\"sha512-STAY\"}}}",
                        source -> source.path("nested/package-lock.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    assertTrue(document.printAll().contains("Regenerate this npm lockfile"));
                                    assertTrue(document.printAll().contains("sha512-STAY"));
                                })),
                json("{\"packages\":{\"node_modules/parent/node_modules/fs-extra\":{\"version\":\"10.1.0\"},\"node_modules/@scope/fs-extra\":{\"version\":\"10.1.0\"}}}",
                        source -> source.path("package-lock.json").after(actual -> actual)
                                .afterRecipe(document -> {
                                    String printed = document.printAll();
                                    assertEquals(1, occurrences(printed, "Regenerate this npm lockfile"));
                                    assertTrue(printed.contains("node_modules/parent/node_modules/fs-extra"));
                                    assertTrue(printed.contains("node_modules/@scope/fs-extra"));
                                })));
    }

    @Test
    void marksExactYarnAndPnpmKeysWithoutMarkingTypesOrSimilarPackages() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraTextLockRisks()),
                text("fs-extra@^10.1.0:\n  version \"10.1.0\"\n@types/fs-extra@11.0.4:\n  version \"11.0.4\"\n@scope/fs-extra@10.1.0:\n  version \"10.1.0\"\nmy-fs-extra@1.0.0:\n  version \"1.0.0\"\n",
                        source -> source.path("yarn.lock").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("Regenerate this lockfile"));
                                    assertFalse(printed.contains("@types/~~>"));
                                    assertFalse(printed.contains("@scope/~~>"));
                                    assertFalse(printed.contains("my-/*~~"));
                                })),
                text("/fs-extra/10.1.0:\n  resolution: {integrity: sha512-KEEP}\n/@types/fs-extra/11.0.4:\n  resolution: {integrity: sha512-TYPES}\n/@scope/fs-extra/10.1.0:\n  resolution: {integrity: sha512-SCOPED}\n",
                        source -> source.path("pnpm-lock.yaml").after(actual -> actual)
                                .afterRecipe(file -> {
                                    String printed = file.printAll();
                                    assertTrue(printed.contains("Regenerate this lockfile"));
                                    assertTrue(printed.contains("sha512-KEEP"));
                                    assertTrue(printed.contains("sha512-TYPES"));
                                })));
    }

    @ParameterizedTest
    @ValueSource(strings = {"generated/package-lock.json", "install/yarn.lock", "node_modules/pkg/pnpm-lock.yaml",
            "dist/package-lock.json", ".yarn/cache/yarn.lock"})
    void excludesInstalledGeneratedAndBuiltLocks(String path) {
        rewriteRun(spec -> spec.recipe(new FindFsExtraManifestRisks()),
                json("{\"dependencies\":{\"fs-extra\":{\"version\":\"10.1.0\"}}}", source -> source.path(path)));
        rewriteRun(spec -> spec.recipe(new FindFsExtraTextLockRisks()),
                text("fs-extra@10.1.0:\n  version: 10.1.0\n", source -> source.path(path)));
    }

    @Test
    void unrelatedManifestAndModernTargetAreNoOp() {
        rewriteRun(spec -> spec.recipe(new FindFsExtraManifestRisks()),
                json("{\"engines\":{\"node\":\">=18.12\"},\"dependencies\":{\"fs-extra\":\"11.3.4\"}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"fs-extra\":\"10.1.0\"}}", source -> source.path("fixture.json")),
                json("{\"dependencies\":{\"fs-extra-promise\":\"1.0.0\"}}", source -> source.path("package.json")));
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
