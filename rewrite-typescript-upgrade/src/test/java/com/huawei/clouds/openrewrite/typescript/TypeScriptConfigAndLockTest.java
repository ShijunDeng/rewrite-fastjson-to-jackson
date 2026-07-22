package com.huawei.clouds.openrewrite.typescript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class TypeScriptConfigAndLockTest implements RewriteTest {
    private static final String CONFIG = "com.huawei.clouds.openrewrite.typescript.MigrateTypeScriptConfig";
    private static final String CONFIG_RISKS = "com.huawei.clouds.openrewrite.typescript.FindTypeScriptConfigRisks";
    private static final String MANIFEST_RISKS = "com.huawei.clouds.openrewrite.typescript.FindTypeScriptManifestRisks";
    private static final String LOCK_RISKS = "com.huawei.clouds.openrewrite.typescript.FindTypeScriptLockRisks";

    @Test
    void removesIterableLibrariesOnlyWhenDomAlreadyProvidesThem() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(CONFIG)),
                json(
                        """
                        {
                          "compilerOptions": {
                            "lib": ["dom", "dom.iterable", "dom.asynciterable", "es2020"]
                          }
                        }
                        """,
                        """
                        {
                          "compilerOptions": {
                            "lib": ["dom", "es2020"]
                          }
                        }
                        """,
                        source -> source.path("tsconfig.json")
                ));
    }

    @Test
    void deterministicConfigMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(CONFIG))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"compilerOptions\":{\"lib\":[\"DOM\",\"DOM.Iterable\",\"ES2025\"]}}",
                        "{\"compilerOptions\":{\"lib\":[\"DOM\",\"ES2025\"]}}",
                        source -> source.path("tsconfig.app.json")));
    }

    @Test
    void leavesIterableLibrariesWhenDomIsAbsentOrOwnershipIsNested() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(CONFIG)),
                json("{\"compilerOptions\":{\"lib\":[\"dom.iterable\",\"es2020\"]}}",
                        source -> source.path("tsconfig.json")),
                json("{\"tool\":{\"compilerOptions\":{\"lib\":[\"dom\",\"dom.iterable\"]}}}",
                        source -> source.path("tsconfig.json")),
                json("{\"compilerOptions\":{\"lib\":[\"dom\",\"dom.iterable\"]}}",
                        source -> source.path("compiler-options.json")),
                json("{\"compilerOptions\":{\"lib\":[\"dom\",\"dom.iterable\"]}}",
                        source -> source.path("node_modules/pkg/tsconfig.json")),
                json("{\"compilerOptions\":{\"lib\":[\"dom\",\"dom.iterable\"]}}",
                        source -> source.path("dist/tsconfig.json")),
                json("{\"compilerOptions\":{\"lib\":[\"dom\",\"dom.iterable\"]}}",
                        source -> source.path("build/tsconfig.json")),
                json("{\"compilerOptions\":{\"lib\":[\"dom\",\"dom.iterable\"]}}",
                        source -> source.path("generated/tsconfig.json")),
                json("{\"compilerOptions\":{\"lib\":[\"dom\",\"dom.iterable\"]}}",
                        source -> source.path("install/tsconfig.json"))
        );
    }

    @Test
    void marksChangedDefaultsAtTheCompilerOptionsOwner() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(CONFIG_RISKS)),
                json("{\"compilerOptions\":{}}", source -> source.path("tsconfig.json")
                        .after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "TypeScript 6 changes defaults");
                            assertContains(printed, "libReplacement");
                            assertContains(printed, "noUncheckedSideEffectImports");
                            assertContains(printed, "rootDir");
                            assertContains(printed, "types");
                        })),
                json("{}", source -> source.path("packages/empty/tsconfig.json")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "add compilerOptions"))));
    }

    @ParameterizedTest(name = "marks compiler option risk {0}")
    @MethodSource("compilerOptionRisks")
    void marksEachIncompatibleCompilerOptionAtItsValue(String member, String message) {
        String options = allExplicitSafeOptions() + "," + member;
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(CONFIG_RISKS)),
                json("{\"compilerOptions\":{" + options + "}}", source -> source.path("tsconfig.json")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> compilerOptionRisks() {
        return Stream.of(
                Arguments.of("\"target\":\"es5\"", "deprecates ES5 emit"),
                Arguments.of("\"downlevelIteration\":true", "downlevelIteration is deprecated"),
                Arguments.of("\"moduleResolution\":\"classic\"", "Legacy moduleResolution"),
                Arguments.of("\"moduleResolution\":\"node\"", "Legacy moduleResolution"),
                Arguments.of("\"moduleResolution\":\"node10\"", "Legacy moduleResolution"),
                Arguments.of("\"module\":\"amd\"", "legacy module emitter"),
                Arguments.of("\"module\":\"umd\"", "legacy module emitter"),
                Arguments.of("\"module\":\"system\"", "legacy module emitter"),
                Arguments.of("\"module\":\"systemjs\"", "legacy module emitter"),
                Arguments.of("\"baseUrl\":\".\"", "baseUrl is deprecated"),
                Arguments.of("\"esModuleInterop\":false", "false legacy interop/strictness"),
                Arguments.of("\"allowSyntheticDefaultImports\":false", "false legacy interop/strictness"),
                Arguments.of("\"alwaysStrict\":false", "false legacy interop/strictness"),
                Arguments.of("\"outFile\":\"dist/bundle.js\"", "outFile was removed"),
                Arguments.of("\"ignoreDeprecations\":\"6.0\"", "suppresses TypeScript 6 migration diagnostics"),
                Arguments.of("\"types\":[\"*\"]", "broad ambient discovery")
        );
    }

    @Test
    void leavesExplicitModernCompilerChoicesUnmarked() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(CONFIG_RISKS)),
                json("{\"compilerOptions\":{" + allExplicitSafeOptions() +
                                ",\"moduleResolution\":\"bundler\",\"esModuleInterop\":true}}",
                        source -> source.path("tsconfig.json").afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~(")))));
    }

    @Test
    void leavesNestedNonConfigAndGeneratedCompilerOptionsUnaudited() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(CONFIG_RISKS)),
                json("{\"tool\":{\"compilerOptions\":{\"target\":\"es5\",\"moduleResolution\":\"node\"}}}",
                        "/*~~(TypeScript 6 changes defaults for strict/module/target/noUncheckedSideEffectImports/libReplacement/types/rootDir; add compilerOptions and choose these values after checking runtime, globals, emit layout, and side-effect imports)~~>*/{\"tool\":{\"compilerOptions\":{\"target\":\"es5\",\"moduleResolution\":\"node\"}}}",
                        source -> source.path("tsconfig.json")),
                json("{\"compilerOptions\":{\"target\":\"es5\"}}",
                        source -> source.path("compiler-options.json")),
                json("{\"compilerOptions\":{\"target\":\"es5\"}}",
                        source -> source.path("node_modules/pkg/tsconfig.json")),
                json("{\"compilerOptions\":{\"target\":\"es5\"}}",
                        source -> source.path("dist/tsconfig.json")),
                json("{\"compilerOptions\":{\"target\":\"es5\"}}",
                        source -> source.path("build/tsconfig.json")),
                json("{\"compilerOptions\":{\"target\":\"es5\"}}",
                        source -> source.path("generated/tsconfig.json")),
                json("{\"compilerOptions\":{\"target\":\"es5\"}}",
                        source -> source.path("install/tsconfig.json"))
        );
    }

    @Test
    void marksRealTsNodeConfigurationAtFixedCommit() {
        // TypeStrong/ts-node v10.9.2 at 057ac1beb118f9c42d21e876a17320ad73ea6be2, reduced tsconfig.json.
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(CONFIG_RISKS)),
                json("{\"compilerOptions\":{\"target\":\"es2019\",\"lib\":[\"es2019\",\"dom\"],\"rootDir\":\".\",\"module\":\"commonjs\",\"moduleResolution\":\"node\",\"strict\":true,\"types\":[\"node\"]},\"include\":[\"src/**/*\"]}",
                        source -> source.path("tsconfig.json").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "Legacy moduleResolution");
                            assertContains(after.printAll(), "TypeScript 6 changes defaults");
                        })));
    }

    @ParameterizedTest(name = "marks manifest ownership/toolchain risk {0}")
    @MethodSource("manifestRisks")
    void marksManifestRuntimeToolchainAndScriptRisks(String manifest, String message) {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(MANIFEST_RISKS)),
                json(manifest, source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> manifestRisks() {
        return Stream.of(
                Arguments.of("{\"overrides\":{\"typescript\":\"4.7.4\"}}", "Central TypeScript ownership"),
                Arguments.of("{\"resolutions\":{\"typescript\":\"4.7.4\"}}", "Central TypeScript ownership"),
                Arguments.of("{\"catalog\":{\"typescript\":\"4.7.4\"}}", "Central TypeScript ownership"),
                Arguments.of("{\"devDependencies\":{\"typescript\":\"6.0.3\",\"ts-node\":\"10.9.2\"}}", "tool loads TypeScript"),
                Arguments.of("{\"devDependencies\":{\"typescript\":\"6.0.3\",\"@typescript-eslint/parser\":\"8.0.0\"}}", "tool loads TypeScript"),
                Arguments.of("{\"engines\":{\"node\":\">=12\"}}", "requires Node >=14.17"),
                Arguments.of("{\"engines\":{\"node\":\">=14.16\"}}", "requires Node >=14.17"),
                Arguments.of("{\"engines\":{\"node\":\">=12 || >=18\"}}", "requires Node >=14.17"),
                Arguments.of("{\"type\":\"commonjs\"}", "defaults module to esnext"),
                Arguments.of("{\"scripts\":{\"build\":\"tsc src/index.ts\"}}", "uses file arguments"),
                Arguments.of("{\"scripts\":{\"build\":\"tsc --target es5\"}}", "deprecated CLI options"),
                Arguments.of("{\"scripts\":{\"build\":\"npx tsc --outFile dist/app.js\"}}", "deprecated CLI options")
        );
    }

    @Test
    void leavesSupportedNodeAndProjectTscScriptUnmarked() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(MANIFEST_RISKS)),
                json("{\"engines\":{\"node\":\">=18.0.0\"},\"scripts\":{\"build\":\"tsc -p tsconfig.build.json\"},\"devDependencies\":{\"typescript\":\"6.0.3\"}}",
                        source -> source.path("package.json").afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~(")))),
                json("{\"engines\":{\"node\":\"^18.0.0\"}}",
                        source -> source.path("packages/caret/package.json")),
                json("{\"engines\":{\"node\":\"18.x\"}}",
                        source -> source.path("packages/x-range/package.json")),
                json("{\"engines\":{\"node\":\"~14.17.0\"}}",
                        source -> source.path("packages/tilde/package.json")),
                json("{\"engines\":{\"node\":\"20\"}}",
                        source -> source.path("packages/exact/package.json"))
        );
    }

    @Test
    void marksExactPackageLockOwnershipNodesWithoutInventingMetadata() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(LOCK_RISKS)),
                json(
                        """
                        {
                          "packages": {
                            "": { "devDependencies": { "typescript": "^4.7.4" } },
                            "node_modules/typescript": {
                              "version": "4.7.4",
                              "resolved": "https://registry.npmjs.org/typescript/-/typescript-4.7.4.tgz",
                              "integrity": "sha512-fixed-fixture"
                            }
                          }
                        }
                        """,
                        source -> source.path("package-lock.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(count(printed, "Lockfile still owns") >= 2, printed);
                            assertContains(printed, "sha512-fixed-fixture");
                            assertContains(printed, "typescript-4.7.4.tgz");
                        })));
    }

    @ParameterizedTest(name = "marks text lockfile {0}")
    @MethodSource("staleTextLocks")
    void marksStaleYarnAndPnpmLockfiles(String path, String lock) {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(LOCK_RISKS)),
                text(lock, source -> source.path(path).after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "Lockfile still owns a non-target TypeScript"))));
    }

    static Stream<Arguments> staleTextLocks() {
        return Stream.of(
                Arguments.of("yarn.lock", "typescript@^4.7.4:\n  version \"4.7.4\"\n  resolved \"https://registry.yarnpkg.com/typescript/-/typescript-4.7.4.tgz\"\n"),
                Arguments.of("pnpm-lock.yaml", "lockfileVersion: '9.0'\nimporters:\n  .:\n    devDependencies:\n      typescript:\n        specifier: ^4.7.4\n        version: 4.7.4\npackages:\n  typescript@4.7.4: {}\n")
        );
    }

    @Test
    void leavesTargetAndGeneratedLockfilesUntouched() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(LOCK_RISKS)),
                json("{\"packages\":{\"node_modules/typescript\":{\"version\":\"6.0.3\"}}}",
                        source -> source.path("package-lock.json")),
                text("typescript@^6.0.3:\n  version \"6.0.3\"\n", source -> source.path("yarn.lock")),
                text("lockfileVersion: '9.0'\nimporters:\n  .:\n    devDependencies:\n      typescript:\n        specifier: ^6.0.3\n        version: 6.0.3\npackages:\n  typescript@6.0.3: {}\n",
                        source -> source.path("pnpm-lock.yaml")),
                text("not-typescript@^4.7.4:\n  version \"4.7.4\"\n@types/typescript@4.7.4:\n  version \"4.7.4\"\ntypescript-eslint@4.7.4:\n  version \"4.7.4\"\n",
                        source -> source.path("similar/yarn.lock")),
                text("typescript@^4.7.4:\n  version \"4.7.4\"\n", source -> source.path("node_modules/pkg/yarn.lock")),
                json("{\"packages\":{\"node_modules/typescript\":{\"version\":\"4.7.4\"}}}",
                        source -> source.path("dist/package-lock.json")),
                text("typescript@^4.7.4:\n  version \"4.7.4\"\n", source -> source.path("build/yarn.lock")),
                text("typescript@^4.7.4:\n  version \"4.7.4\"\n", source -> source.path("generated/yarn.lock")),
                text("typescript@^4.7.4:\n  version \"4.7.4\"\n", source -> source.path("install/yarn.lock"))
        );
    }

    private static String allExplicitSafeOptions() {
        return "\"strict\":true,\"module\":\"esnext\",\"target\":\"es2025\"," +
               "\"noUncheckedSideEffectImports\":true,\"libReplacement\":false," +
               "\"types\":[\"node\"],\"rootDir\":\"src\"";
    }

    private static long count(String source, String text) {
        return source.split(java.util.regex.Pattern.quote(text), -1).length - 1L;
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
