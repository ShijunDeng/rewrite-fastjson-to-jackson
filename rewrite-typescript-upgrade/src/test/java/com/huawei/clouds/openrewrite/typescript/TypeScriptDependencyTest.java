package com.huawei.clouds.openrewrite.typescript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class TypeScriptDependencyTest implements RewriteTest {
    static final String UPGRADE = "com.huawei.clouds.openrewrite.typescript.UpgradeTypeScriptDependencyTo6_0_3";
    static final String MIGRATE = "com.huawei.clouds.openrewrite.typescript.MigrateTypeScriptTo6_0_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(UPGRADE));
    }

    @ParameterizedTest(name = "upgrades XLSX source {0} to {1}")
    @MethodSource("selectedDeclarations")
    void upgradesEveryVisibleXlsxVersionAndPreservesOperator(String before, String after) {
        rewriteRun(json(
                "{\"devDependencies\":{\"typescript\":\"" + before + "\"}}",
                "{\"devDependencies\":{\"typescript\":\"" + after + "\"}}",
                source -> source.path("package.json")
        ));
    }

    static Stream<Arguments> selectedDeclarations() {
        return Stream.of("3.8.3", "3.9.5", "4.1.2", "4.2.3", "4.4.4",
                        "4.5.5", "4.6.2", "4.6.4", "4.7.4")
                .flatMap(version -> Stream.of(
                        Arguments.of(version, "6.0.3"),
                        Arguments.of("^" + version, "^6.0.3"),
                        Arguments.of("~" + version, "~6.0.3")
                ));
    }

    @Test
    void upgradesAllDirectDependencySectionsWithoutChangingFormatting() {
        rewriteRun(json(
                """
                {
                  "dependencies": { "typescript": "3.8.3" },
                  "devDependencies": { "typescript": "^4.5.5" },
                  "peerDependencies": { "typescript": "~4.6.4" },
                  "optionalDependencies": { "typescript": "4.7.4" }
                }
                """,
                """
                {
                  "dependencies": { "typescript": "6.0.3" },
                  "devDependencies": { "typescript": "^6.0.3" },
                  "peerDependencies": { "typescript": "~6.0.3" },
                  "optionalDependencies": { "typescript": "6.0.3" }
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @ParameterizedTest(name = "does not infer hidden/other version {0}")
    @ValueSource(strings = {
            "4.8.2", "^4.8.2", "~4.8.2", "4.9.5", "5.0.4", "5.9.3",
            "6.0.0", "6.0.3", "^6.0.3", "~6.0.3", "6.1.0",
            "4.8.2 ... (共14个版本)"
    })
    void lowLevelRecipeLeavesUnlistedCollapsedTargetAndOtherScalarsUntouched(String declaration) {
        assertLowLevelNoOp(declaration);
    }

    @ParameterizedTest(name = "does not rewrite complex declaration {0}")
    @ValueSource(strings = {
            ">=4.7.4", ">=4.6.4 <5", "3.8.3 || 4.7.4", "3.8.3 - 4.7.4",
            "4.x", "4.7.x", "*", "=4.7.4", "v4.7.4", "4.7.4-beta.1",
            "4.7.4+vendor.1", "workspace:^4.7.4", "workspace:*", "catalog:",
            "npm:@vendor/typescript@4.7.4", "file:../typescript", "link:../typescript",
            "git+https://github.com/microsoft/TypeScript.git#v4.7.4",
            "https://example.test/typescript-4.7.4.tgz", "latest", "next",
            "${TYPESCRIPT_VERSION}", "{{typescriptVersion}}"
    })
    void lowLevelRecipeLeavesComplexPrereleaseDynamicAndProtocolDeclarationsUntouched(String declaration) {
        assertLowLevelNoOp(declaration);
    }

    @Test
    void changesOnlyPackageJsonDirectDeclarations() {
        rewriteRun(
                json("{\"overrides\":{\"typescript\":\"4.7.4\"},\"resolutions\":{\"typescript\":\"4.7.4\"},\"catalog\":{\"typescript\":\"4.7.4\"}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"owner\":{\"typescript\":\"4.7.4\"}}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"typescript\":\"4.7.4\"}}",
                        source -> source.path("dependency-fixture.json")),
                json("{\"dependencies\":{\"TypeScript\":\"4.7.4\",\"typescript-eslint\":\"4.7.4\",\"@vendor/typescript\":\"4.7.4\"}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"devDependencies\":{\"typescript\":\"4.7.4\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"devDependencies\":{\"typescript\":\"4.7.4\"}}",
                        source -> source.path("node_modules/demo/package.json")),
                json("{\"devDependencies\":{\"typescript\":\"4.7.4\"}}",
                        source -> source.path("dist/package.json")),
                json("{\"devDependencies\":{\"typescript\":\"4.7.4\"}}",
                        source -> source.path("build/package.json")),
                json("{\"devDependencies\":{\"typescript\":\"4.7.4\"}}",
                        source -> source.path("generated/package.json")),
                json("{\"devDependencies\":{\"typescript\":\"4.7.4\"}}",
                        source -> source.path("install/package.json"))
        );
    }

    @ParameterizedTest(name = "recommended recipe marks unresolved declaration {0}")
    @MethodSource("markedDeclarations")
    void recommendedRecipeMarksEveryNonOwnedDeclarationPrecisely(String declaration, String message) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                json("{\"devDependencies\":{\"typescript\":\"" + declaration + "\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> markedDeclarations() {
        return Stream.of(
                Arguments.of("4.8.2", "Unlisted TypeScript scalar"),
                Arguments.of("4.8.2 ... (共14个版本)", "Complex TypeScript comparator"),
                Arguments.of(">=4.7.4 <5", "Complex TypeScript comparator"),
                Arguments.of("3.8.3 || 4.7.4", "Complex TypeScript comparator"),
                Arguments.of("3.8.3 - 4.7.4", "Complex TypeScript comparator"),
                Arguments.of("4.7.4-beta.1", "Prerelease TypeScript declaration"),
                Arguments.of("workspace:^4.7.4", "Protocol, alias, tag, or dynamic"),
                Arguments.of("catalog:", "Protocol, alias, tag, or dynamic"),
                Arguments.of("latest", "Protocol, alias, tag, or dynamic")
        );
    }

    @Test
    void recommendedRecipeUpgradesOwnedScalarInsteadOfMarkingIt() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(MIGRATE)),
                json("{\"devDependencies\":{\"typescript\":\"^4.5.5\"}}",
                        "{\"devDependencies\":{\"typescript\":\"^6.0.3\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void realGithubPackageFixturePreservesCaretPolicy() {
        // GitHub gist 0ab10d59310b539520d3603b39b4229a, fixed commit recorded in README.
        rewriteRun(json(
                """
                {
                  "scripts": {
                    "build": "tsup",
                    "start": "node dist/index.js"
                  },
                  "devDependencies": {
                    "tsup": "^5.11.13",
                    "typescript": "4.5.5"
                  }
                }
                """,
                """
                {
                  "scripts": {
                    "build": "tsup",
                    "start": "node dist/index.js"
                  },
                  "devDependencies": {
                    "tsup": "^5.11.13",
                    "typescript": "6.0.3"
                  }
                }
                """,
                source -> source.path("package.json")
        ));
    }

    @Test
    void realTsNodePackageFixtureChangesOnlyOwnedScalar() {
        // TypeStrong/ts-node v10.9.2 at 057ac1beb118f9c42d21e876a17320ad73ea6be2, reduced package.json.
        rewriteRun(json(
                "{\"devDependencies\":{\"typedoc\":\"^0.22.10\",\"typescript\":\"4.7.4\"},\"peerDependencies\":{\"typescript\":\">=2.7\"}}",
                "{\"devDependencies\":{\"typedoc\":\"^0.22.10\",\"typescript\":\"6.0.3\"},\"peerDependencies\":{\"typescript\":\">=2.7\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradeRecipeIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"devDependencies\":{\"typescript\":\"~4.6.2\"}}",
                        "{\"devDependencies\":{\"typescript\":\"~6.0.3\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void bothRecipesAreDiscoverableAndValid() {
        Environment environment = environment();
        for (String name : new String[]{UPGRADE, MIGRATE}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        }
    }

    private void assertLowLevelNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"typescript\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.typescript")
                .scanYamlResources()
                .build();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
