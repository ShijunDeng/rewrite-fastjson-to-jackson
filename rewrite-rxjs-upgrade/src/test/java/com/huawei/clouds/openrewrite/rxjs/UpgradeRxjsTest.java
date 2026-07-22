package com.huawei.clouds.openrewrite.rxjs;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeRxjsTest implements RewriteTest {
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.rxjs.MigrateRxjsTo7_8_2";
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.rxjs.UpgradeRxjsDependencyTo7_8_2";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.rxjs.MigrateDeterministicRxjsSourceTo7";
    private static final String AUDIT_RECIPE =
            "com.huawei.clouds.openrewrite.rxjs.AuditRxjs7Compatibility";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(MIGRATION_RECIPE));
    }

    @ParameterizedTest(name = "upgrades {0} rxjs {1}")
    @MethodSource("selectedDeclarations")
    void upgradesSelectedDeclarations(String section, String declaration) {
        rewriteRun(json(
                "{\"" + section + "\":{\"rxjs\":\"" + declaration + "\"}}",
                "{\"" + section + "\":{\"rxjs\":\"7.8.2\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesPackageFromBeemanAngularElementsChatWidget() {
        // Reduced from beeman/angular-elements-chat-widget at b4759c2662874a7e8c84bfbcd84cc7b4209569a0.
        // https://github.com/beeman/angular-elements-chat-widget/blob/b4759c2662874a7e8c84bfbcd84cc7b4209569a0/package.json
        rewriteRun(json(
                """
                {"dependencies":{"@angular/core":"~10.2.3","rxjs":"6.5.5","zone.js":"~0.10.2"}}
                """,
                """
                {"dependencies":{"@angular/core":"~10.2.3","rxjs":"7.8.2","zone.js":"~0.10.2"}}
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void upgradesPackageFromNgxMatSelectSearch() {
        // Reduced from bithost-gmbh/ngx-mat-select-search at 0892f54ff6c865cbe3cc9fcd709eeb6a23f4f607.
        // https://github.com/bithost-gmbh/ngx-mat-select-search/blob/0892f54ff6c865cbe3cc9fcd709eeb6a23f4f607/package.json
        rewriteRun(json(
                """
                {"peerDependencies":{"@angular/core":">=16.0.0","rxjs":"^6.6.7"},"devDependencies":{"typescript":"5.9.3"}}
                """,
                """
                {"peerDependencies":{"@angular/core":">=16.0.0","rxjs":"7.8.2"},"devDependencies":{"typescript":"5.9.3"}}
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves unsupported declaration {0}")
    @ValueSource(strings = {
            "6.5.4", "6.6.6", "6.6.8", "7.2.9", "7.7.0", "7.8.2", "7.8.3", "8.0.0",
            ">=6.5.5 <7", "^6.5.5 || ^7.0.0", "6.x", "latest", "6.6.7-beta.0"
    })
    void leavesUnsupportedDeclarations(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE)), json(
                "{\"dependencies\":{\"rxjs\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves protocol declaration {0}")
    @ValueSource(strings = {
            "workspace:^6.6.7", "npm:rxjs@6.6.7", "file:../rxjs", "git+https://github.com/ReactiveX/rxjs.git#6.6.7"
    })
    void leavesProtocolsAndAliases(String declaration) {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE)), json(
                "{\"dependencies\":{\"rxjs\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOverridesNestedValuesAndNonScalarValuesUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE)), json(
                """
                {
                  "overrides":{"rxjs":"6.6.7"},
                  "resolutions":{"rxjs":"6.5.5"},
                  "config":{"dependencies":{"rxjs":"7.8.1"}},
                  "dependencies":{"wrapper":{"rxjs":"6.6.7"},"rxjs":{"version":"6.6.7"}},
                  "devDependencies":{"rxjs":["6.6.7"]}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesLockfilesBackupsAndOtherJsonUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE)),
                json("{\"dependencies\":{\"rxjs\":\"6.6.7\"}}", spec -> spec.path("package-lock.json")),
                json("{\"dependencies\":{\"rxjs\":\"6.6.7\"}}", spec -> spec.path("package.json.backup")),
                json("{\"dependencies\":{\"rxjs\":\"6.6.7\"}}", spec -> spec.path("config/dependencies.json"))
        );
    }

    @Test
    void leavesGeneratedAndVendoredPackageJsonUntouched() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE)),
                json("{\"dependencies\":{\"rxjs\":\"7.8.1\"}}",
                        source -> source.path("node_modules/example/package.json")),
                json("{\"dependencies\":{\"rxjs\":\"7.8.1\"}}",
                        source -> source.path("dist/package.json"))
        );
    }

    @Test
    void dependencyUpgradeIsIdempotent() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"rxjs\":\"7.8.1\"}}",
                        "{\"dependencies\":{\"rxjs\":\"7.8.2\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void migratesObservableDeepImportFromOctoDash() {
        // Reduced from UnchartedBull/OctoDash at 2096721d7d08a7af88c4bd6aa389348b5b4ba002.
        // https://github.com/UnchartedBull/OctoDash/blob/2096721d7d08a7af88c4bd6aa389348b5b4ba002/src/model/files.model.ts
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        "import { Observable } from 'rxjs/internal/Observable';\nexport interface Directory { files: Observable<string[]>; }\n",
                        "import { Observable } from 'rxjs';\nexport interface Directory { files: Observable<string[]>; }\n",
                        source -> source.path("src/model/files.model.ts")
                )
        );
    }

    @Test
    void migratesKnownCreationAndOperatorDeepImports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import { of } from 'rxjs/internal/observable/of';
                        import { map, filter } from "rxjs/internal/operators";
                        """,
                        """
                        import { of } from 'rxjs';
                        import { map, filter } from "rxjs/operators";
                        """,
                        source -> source.path("src/stream.ts")
                )
        );
    }

    @Test
    void preservesAliasesWhenMigratingKnownDeepImports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        "import { Observable as Stream } from 'rxjs/internal/Observable';\nimport { of as makeOne } from 'rxjs/internal/observable/of';\n",
                        "import { Observable as Stream } from 'rxjs';\nimport { of as makeOne } from 'rxjs';\n",
                        source -> source.path("src/aliases.ts")
                )
        );
    }

    @Test
    void migratesNestedOperatorExportsButLeavesOpaqueModuleObjectsForAudit() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        export { map as transform } from 'rxjs/internal/operators/map';
                        const observableModule = require("rxjs/internal/Observable");
                        const filterModule = import('rxjs/internal/operators/filter');
                        """,
                        """
                        export { map as transform } from 'rxjs/operators';
                        const observableModule = require("rxjs/internal/Observable");
                        const filterModule = import('rxjs/internal/operators/filter');
                        """,
                        source -> source.path("src/modules.ts")
                )
        );
    }

    @Test
    void deepImportMigrationRejectsUnknownMismatchedDefaultAndGeneratedSource() {
        String source = """
                        import { Subject } from 'rxjs/internal/Observable';
                        import { Observable, InternalOperator } from 'rxjs/internal/Observable';
                        import Observable from 'rxjs/internal/Observable';
                        import { custom } from 'rxjs/internal/operators/custom';
                        const documentation = "rxjs/internal/operators/map";
                        // import { map } from 'rxjs/internal/operators/map';
                        """;
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(source, input -> input.path("src/unknown.ts")),
                text("import { Observable } from 'rxjs/internal/Observable';\n",
                        input -> input.path("dist/generated.ts"))
        );
    }

    @Test
    void migratesAjaxRequestTypeToAjaxConfig() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import { ajax, AjaxRequest } from 'rxjs/ajax';
                        export function request(config: AjaxRequest) { return ajax(config); }
                        """,
                        """
                        import { ajax, AjaxConfig } from 'rxjs/ajax';
                        export function request(config: AjaxConfig) { return ajax(config); }
                        """,
                        source -> source.path("src/http.ts")
                )
        );
    }

    @Test
    void ajaxMigrationPreservesCommentsStringsAndTemplates() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import { ajax, AjaxRequest } from 'rxjs/ajax';
                        // AjaxRequest documents the old public name.
                        const label = 'AjaxRequest';
                        const template = `AjaxRequest`;
                        export function request(config: AjaxRequest) { return ajax(config); }
                        """,
                        """
                        import { ajax, AjaxConfig } from 'rxjs/ajax';
                        // AjaxRequest documents the old public name.
                        const label = 'AjaxRequest';
                        const template = `AjaxRequest`;
                        export function request(config: AjaxConfig) { return ajax(config); }
                        """,
                        source -> source.path("src/http.ts")
                )
        );
    }

    @Test
    void migratesOnlyUnambiguousLiteralThrowErrorCalls() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import { throwError } from 'rxjs';
                        const a = throwError('failed');
                        const b = throwError(new Error("broken"));
                        const c = throwError(errorFromServer);
                        """,
                        """
                        import { throwError } from 'rxjs';
                        const a = throwError(() => 'failed');
                        const b = throwError(() => new Error("broken"));
                        const c = throwError(errorFromServer);
                        """,
                        source -> source.path("src/errors.ts")
                )
        );
    }

    @Test
    void throwErrorMigrationPreservesCommentsStringsPropertiesAndAliasedImports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import { throwError } from 'rxjs';
                        // throwError('documentation');
                        const example = "throwError('text')";
                        service.throwError('local');
                        const failure = throwError('actual');
                        """,
                        """
                        import { throwError } from 'rxjs';
                        // throwError('documentation');
                        const example = "throwError('text')";
                        service.throwError('local');
                        const failure = throwError(() => 'actual');
                        """,
                        source -> source.path("src/errors.ts")
                ),
                text(
                        "import { throwError as fail } from 'rxjs';\nconst value = throwError('local');\n",
                        source -> source.path("src/aliased.ts")
                )
        );
    }

    @Test
    void sourceMigrationSkipsShadowedBindingsAndPropertyNames() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        """
                        import { AjaxRequest } from 'rxjs/ajax';
                        function nested() { const AjaxRequest = localType; return AjaxRequest; }
                        export const request = (value: AjaxRequest) => value;
                        """,
                        source -> source.path("src/shadowed-ajax.ts")
                ),
                text(
                        """
                        import { throwError } from 'rxjs';
                        const nested = (throwError: (value: string) => unknown) => throwError('local');
                        const actual = throwError('rxjs');
                        """,
                        source -> source.path("src/shadowed-error.ts")
                ),
                text(
                        "import { AjaxRequest } from 'rxjs/ajax';\nimport { AjaxConfig } from './local-config';\nconst value: AjaxRequest = load();\n",
                        source -> source.path("src/conflicting-import.ts")
                ),
                text(
                        """
                        import { AjaxRequest } from 'rxjs/ajax';
                        const property = namespace.AjaxRequest;
                        const config: AjaxRequest = property;
                        """,
                        """
                        import { AjaxConfig } from 'rxjs/ajax';
                        const property = namespace.AjaxRequest;
                        const config: AjaxConfig = property;
                        """,
                        source -> source.path("src/property.ts")
                )
        );
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text(
                        "import { of } from 'rxjs/internal/observable/of';\nimport { throwError } from 'rxjs';\nconst failed = throwError('x');\n",
                        "import { of } from 'rxjs';\nimport { throwError } from 'rxjs';\nconst failed = throwError(() => 'x');\n",
                        source -> source.path("src/idempotent.ts")
                )
        );
    }

    @Test
    void doesNotRewriteLookalikeSourceWithoutRxjsImports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE_RECIPE)),
                text(
                        "function throwError(value: unknown) { return value; }\nconst AjaxRequest = 'local';\nthrowError('failed');\n",
                        source -> source.path("src/local.ts")
                )
        );
    }

    @Test
    void auditMarksToPromiseAndSubscriptionAddChaining() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                text(
                        "import { Observable, Subscription } from 'rxjs';\nconst result = stream.toPromise();\nroot.add(first).add(second);\n",
                        source -> source.path("src/legacy.ts").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("choose firstValueFrom or lastValueFrom"), printed);
                            assertTrue(printed.contains("Subscription.add returns void"), printed);
                        })
                )
        );
    }

    @Test
    void auditMarksToPromiseFromNgPackagr() {
        // Reduced from ng-packagr/ng-packagr at 0143b11efbbbebb997f8425ede6211dfa99381f2.
        // https://github.com/ng-packagr/ng-packagr/blob/0143b11efbbbebb997f8425ede6211dfa99381f2/src/lib/packagr.ts#L1-L95
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                text(
                        "import { Observable, map, of as observableOf } from 'rxjs';\nreturn this.buildAsObservable().toPromise();\n",
                        source -> source.path("src/lib/packagr.ts").after(actual -> actual)
                                .afterRecipe(after -> assertTrue(
                                        after.printAll().contains("choose firstValueFrom or lastValueFrom"),
                                        after.printAll()))
                )
        );
    }

    @Test
    void auditDoesNotMarkUnrelatedLookalikesWithoutRxjsImports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                text(
                        "const promise = localStream.toPromise();\nlocalSubscription.add(one).add(two);\n",
                        source -> source.path("src/local.ts")
                )
        );
    }

    @Test
    void auditExplainsImportedSemanticRisksAtExactSnippets() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                text(
                        """
                        import { Observable, throwError, defer, iif, race, zip } from 'rxjs';
                        import { publish, finalize } from 'rxjs/operators';
                        const old = Observable.create(observer => observer.complete());
                        const error = throwError(problem);
                        const delayed = defer(() => undefined);
                        const selected = iif(flag, ready$, undefined);
                        const connected = publish()(source$);
                        const finished = finalize(cleanup)(source$);
                        const winner = race(one$, two$);
                        const paired = zip(one$, two$);
                        stream._subscribe = customSubscribe;
                        """,
                        source -> source.path("src/semantic-risks.ts").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("Observable.create was removed"), printed);
                            assertTrue(printed.contains("throwError should receive a lazy factory"), printed);
                            assertTrue(printed.contains("no longer accepts undefined"), printed);
                            assertTrue(printed.contains("Legacy multicast/publish APIs"), printed);
                            assertTrue(printed.contains("notifier/finalization ordering"), printed);
                            assertTrue(printed.contains("race/zip edge behavior"), printed);
                            assertTrue(printed.contains("private/internal field"), printed);
                        })
                )
        );
    }

    @Test
    void auditExplainsRemainingInternalImports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                text(
                        "import { CustomThing } from 'rxjs/internal/custom/CustomThing';\n",
                        source -> source.path("src/internal.ts").after(actual -> actual)
                                .afterRecipe(after -> assertTrue(
                                        after.printAll().contains("internal entry points are unsupported"),
                                        after.printAll()))
                )
        );
    }

    @Test
    void auditIgnoresCommentsStringsPropertiesAndValidFactories() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                text(
                        """
                        import { throwError as rxThrow } from 'rxjs';
                        // stream.toPromise(); rxThrow(problem);
                        const docs = "rxjs/internal/Observable and .toPromise()";
                        service.rxThrow(problem);
                        const first = rxThrow(() => problem);
                        const second = rxThrow((context) => context.problem);
                        """,
                        source -> source.path("src/safe.ts")
                )
        );
    }

    @Test
    void sourceRiskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text(
                        "import { Observable } from 'rxjs';\nreturn source.toPromise();\n",
                        source -> source.path("src/idempotent-risk.ts").after(actual -> actual)
                )
        );
    }

    @Test
    void auditMarksRxjsCompatAndTypeScriptVersionOwners() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                json(
                        "{\"dependencies\":{\"rxjs-compat\":\"6.6.7\"},\"devDependencies\":{\"typescript\":\"3.9.10\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("rxjs-compat is not an RxJS 7 migration strategy"), printed);
                            assertTrue(printed.contains("TypeScript 3 predates"), printed);
                        })
                )
        );
    }

    @Test
    void packageAuditMarksUnresolvedAndCentralOwnershipButNotModernTooling() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                json(
                        """
                        {"dependencies":{"rxjs":">=6.5.5 <8"},"devDependencies":{"typescript":"^5.9.3"},"pnpm":{"overrides":{"rxjs":"6.6.7"}}}
                        """,
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String printed = after.printAll();
                            assertTrue(printed.contains("range, protocol, alias, unlisted, or newer"), printed);
                            assertTrue(printed.contains("Central RxJS version ownership detected"), printed);
                            assertFalse(printed.contains("TypeScript 3 predates"), printed);
                        })
                )
        );
    }

    @Test
    void packageAuditLeavesTargetAndModernTypeScriptUnmarked() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                json(
                        "{\"dependencies\":{\"rxjs\":\"7.8.2\"},\"devDependencies\":{\"typescript\":\"5.9.3\"}}",
                        source -> source.path("package.json")
                ),
                json(
                        "{\"devDependencies\":{\"typescript\":\"3.9.10\"}}",
                        source -> source.path("services/api/package.json")
                ),
                json(
                        "{\"config\":{\"dependencies\":{\"rxjs\":\"6.6.7\",\"typescript\":\"3.9.10\"}}}",
                        source -> source.path("nested/package.json")
                ),
                json(
                        "{\"config\":{\"rxjs\":\"6.6.7\"},\"devDependencies\":{\"typescript\":\"3.9.10\"}}",
                        source -> source.path("unrelated/package.json")
                )
        );
    }

    @Test
    void packageRiskMarkersAreIdempotent() {
        rewriteRun(
                spec -> spec.recipe(new FindRxjs7JsonRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json(
                        "{\"dependencies\":{\"rxjs-compat\":\"6.6.7\"}}",
                        source -> source.path("package.json").after(actual -> actual)
                )
        );
    }

    @Test
    void discoversAndValidatesAllPublicRecipes() {
        Environment environment = environment();
        for (String name : new String[]{
                MIGRATION_RECIPE, DEPENDENCY_RECIPE, SOURCE_RECIPE, AUDIT_RECIPE,
                "com.huawei.clouds.openrewrite.rxjs.MigrateAjaxRequestToAjaxConfig",
                "com.huawei.clouds.openrewrite.rxjs.MigrateLiteralThrowErrorFactories",
                "com.huawei.clouds.openrewrite.rxjs.AuditRxjs7SourceCompatibility",
                "com.huawei.clouds.openrewrite.rxjs.AuditRxjs7ProjectCompatibility"
        }) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
        }
    }

    private static Stream<Arguments> selectedDeclarations() {
        return Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                .flatMap(section -> Stream.of(
                                "6.5.5", "6.6.7", "7.3.0", "7.4.0", "7.5.5",
                                "7.5.6", "7.5.7", "7.6.0", "7.8.0", "7.8.1")
                        .flatMap(version -> Stream.of("", "^", "~")
                                .map(prefix -> Arguments.of(section, prefix + version))));
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.rxjs")
                .scanYamlResources()
                .build();
    }
}
