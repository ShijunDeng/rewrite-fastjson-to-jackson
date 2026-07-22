package com.huawei.clouds.openrewrite.typescript;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class TypeScriptRecommendedRecipeTest implements RewriteTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.typescript.MigrateTypeScriptTo6_0_3";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void recommendedRecipeCombinesAutoChangesAndExactMarkersAcrossProjectFiles() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(RECOMMENDED)),
                json("{\"engines\":{\"node\":\">=12\"},\"devDependencies\":{\"typescript\":\"^4.7.4\",\"ts-node\":\"10.9.2\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "\"typescript\":\"^6.0.3\"");
                            assertContains(printed, "tool loads TypeScript");
                            assertContains(printed, "requires Node >=14.17");
                        })),
                json("{\"packages\":{\"node_modules/typescript\":{\"version\":\"4.7.4\",\"integrity\":\"sha512-real-package-manager-value\"}}}",
                        source -> source.path("package-lock.json").after(actual -> actual).afterRecipe(after -> {
                            assertContains(after.printAll(), "Lockfile still owns");
                            assertContains(after.printAll(), "sha512-real-package-manager-value");
                        })),
                json("{\"compilerOptions\":{\"lib\":[\"dom\",\"dom.iterable\",\"es2020\"],\"moduleResolution\":\"node\"}}",
                        source -> source.path("tsconfig.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "[\"dom\",\"es2020\"]");
                            assertContains(printed, "Legacy moduleResolution");
                            assertContains(printed, "TypeScript 6 changes defaults");
                        })),
                typescript("module BuildTools { export const api = require('typescript'); }\n",
                        source -> source.path("tools/build.ts").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertContains(printed, "namespace BuildTools");
                            assertContains(printed, "require call loads TypeScript compiler APIs");
                        }))
        );
    }

    @Test
    void recommendedMarkersAndAutoChangesAreIdempotent() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(RECOMMENDED))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"devDependencies\":{\"typescript\":\"4.8.2\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "Unlisted TypeScript scalar"))),
                typescript("module Legacy { export const value = 1; }\n",
                        source -> source.path("src/legacy.ts").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "namespace Legacy")))
        );
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
