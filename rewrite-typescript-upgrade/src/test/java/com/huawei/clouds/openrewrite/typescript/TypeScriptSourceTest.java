package com.huawei.clouds.openrewrite.typescript;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class TypeScriptSourceTest implements RewriteTest {
    private static final String SOURCE = "com.huawei.clouds.openrewrite.typescript.MigrateTypeScriptSource";
    private static final String SOURCE_RISKS = "com.huawei.clouds.openrewrite.typescript.FindTypeScriptSourceRisks";
    private static final String RECOMMENDED = "com.huawei.clouds.openrewrite.typescript.MigrateTypeScriptTo6_0_3";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @ParameterizedTest(name = "migrates legacy internal module syntax {0}")
    @MethodSource("legacyInternalModules")
    void migratesLegacyInternalModulesToNamespaces(String before, String after) {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(SOURCE)),
                typescript(before, after, source -> source.path("src/legacy.ts")));
    }

    static Stream<Arguments> legacyInternalModules() {
        return Stream.of(
                Arguments.of("module Utilities { export const answer = 42; }\n",
                        "namespace Utilities { export const answer = 42; }\n"),
                Arguments.of("export module Model { export interface User { id: string } }\n",
                        "export namespace Model { export interface User { id: string } }\n"),
                Arguments.of("declare module Internal { export const version: string; }\n",
                        "declare namespace Internal { export const version: string; }\n")
        );
    }

    @Test
    void leavesAmbientExternalModuleDeclarationsUntouched() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(SOURCE)),
                typescript("declare module 'legacy-plugin' { export function load(): void; }\n",
                        source -> source.path("src/types.d.ts")));
    }

    @Test
    void replacesStaticImportAssertionWithImportAttributes() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(SOURCE)),
                typescript("import data from './data.json' assert { type: 'json' };\n",
                        "import data from './data.json' with { type: 'json' };\n",
                        source -> source.path("src/data.ts")));
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(SOURCE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("module Legacy { export const value = 1; }\n",
                        "namespace Legacy { export const value = 1; }\n",
                        source -> source.path("src/idempotent.ts")));
    }

    @ParameterizedTest(name = "marks compiler integration {0}")
    @MethodSource("compilerIntegrations")
    void marksDirectCompilerAndLanguageServiceImports(String label, String source) {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(SOURCE_RISKS)),
                typescript(source, input -> input.path("tools/" + label + ".ts")
                        .after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "compiler/language-service"))));
    }

    static Stream<Arguments> compilerIntegrations() {
        return Stream.of(
                Arguments.of("namespace-import", "import * as ts from 'typescript';\nconst file = ts.createSourceFile('a.ts', '', ts.ScriptTarget.Latest);\n"),
                Arguments.of("default-import", "import ts from 'typescript';\nconst printer = ts.createPrinter();\n"),
                Arguments.of("named-import", "import { createProgram } from 'typescript';\nconst program = createProgram([], {});\n"),
                Arguments.of("internal-subpath", "import * as ts from 'typescript/lib/tsserverlibrary';\nconst project = ts.server;\n"),
                Arguments.of("import-equals", "import ts = require('typescript');\nconst host = ts.createCompilerHost({});\n")
        );
    }

    @Test
    void marksCommonJsCompilerApiRequireAtTheCall() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(SOURCE_RISKS)),
                javascript("const ts = require('typescript');\nconst printer = ts.createPrinter();\n",
                        input -> input.path("tools/compiler.js").after(actual -> actual).afterRecipe(after ->
                                assertContains(after.printAll(), "require call loads TypeScript compiler APIs"))));
    }

    @ParameterizedTest(name = "marks removed/deprecated directive {0}")
    @MethodSource("directives")
    void marksRecognizedTripleSlashDirectives(String label, String source, String message) {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(SOURCE_RISKS)),
                typescript(source, input -> input.path("src/" + label + ".ts")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> directives() {
        return Stream.of(
                Arguments.of("no-default", "/// <reference no-default-lib=\"true\" />\ndeclare const custom: string;\n",
                        "no longer supports the no-default-lib"),
                Arguments.of("amd-module", "/// <amd-module name=\"legacy\" />\nexport const value = 1;\n",
                        "AMD directives accompany"),
                Arguments.of("amd-dependency", "/// <amd-dependency path=\"legacy\" name=\"legacy\" />\nexport const value = 1;\n",
                        "AMD directives accompany")
        );
    }

    @Test
    void leavesSimilarModulesRequiresStringsAndCommentsUntouched() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(SOURCE_RISKS)),
                typescript("import ts from '@vendor/typescript';\nconst name = 'typescript';\nconst api = require(name);\n// <reference no-default-lib=\"true\" />\nconst docs = '<amd-module name=\"x\" />';\n",
                        input -> input.path("src/safe.ts").afterRecipe(after ->
                                assertFalse(after.printAll().contains("~~(")))));
    }

    @Test
    void leavesGeneratedSourceTreesUntouched() {
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(RECOMMENDED)),
                typescript("module Generated { export const x = 1; }\nimport * as ts from 'typescript';\n",
                        source -> source.path("node_modules/pkg/index.ts")),
                typescript("module Generated { export const x = 1; }\nimport * as ts from 'typescript';\n",
                        source -> source.path("dist/index.ts")),
                typescript("module Generated { export const x = 1; }\nimport * as ts from 'typescript';\n",
                        source -> source.path("build/index.ts")),
                typescript("module Generated { export const x = 1; }\nimport * as ts from 'typescript';\n",
                        source -> source.path("generated/index.ts")),
                typescript("module Generated { export const x = 1; }\nimport * as ts from 'typescript';\n",
                        source -> source.path("install/index.ts")),
                typescript("module Generated { export const x = 1; }\nimport * as ts from 'typescript';\n",
                        source -> source.path(".mvn/generated/index.ts")),
                typescript("module Generated { export const x = 1; }\nimport * as ts from 'typescript';\n",
                        source -> source.path("vendor/index.ts"))
        );
    }

    @Test
    void realGithubTimerFixtureMigratesLegacyModule() {
        // halilkocaerkek/timers.ts gist at 0269600bd8e054fd969e8a5cf017e3ee014fdecb, reduced body.
        rewriteRun(spec -> spec.recipe(TypeScriptDependencyTest.environment().activateRecipes(SOURCE)),
                typescript("module Utilities { export class Timer { public Start(): void {} } }\n",
                        "namespace Utilities { export class Timer { public Start(): void {} } }\n",
                        source -> source.path("timers.ts")));
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }
}
