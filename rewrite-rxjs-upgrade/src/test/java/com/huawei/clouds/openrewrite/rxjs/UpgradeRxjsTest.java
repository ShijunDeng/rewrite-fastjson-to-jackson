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
                {"peerDependencies":{"@angular/core":">=16.0.0","rxjs":"7.8.2"},"devDependencies":{/*~~>*/"typescript":"5.9.3"}}
                """,
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves unsupported declaration {0}")
    @ValueSource(strings = {
            "6.5.4", "6.6.6", "6.6.8", "7.8.2", "7.8.1", "8.0.0",
            ">=6.5.5 <7", "^6.5.5 || ^7.0.0", "6.x", "latest", "6.6.7-beta.0"
    })
    void leavesUnsupportedDeclarations(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"rxjs\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @ParameterizedTest(name = "leaves protocol declaration {0}")
    @ValueSource(strings = {
            "workspace:^6.6.7", "npm:rxjs@6.6.7", "file:../rxjs", "git+https://github.com/ReactiveX/rxjs.git#6.6.7"
    })
    void leavesProtocolsAndAliases(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"rxjs\":\"" + declaration + "\"}}",
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesOverridesNestedValuesAndNonScalarValuesUntouched() {
        rewriteRun(json(
                """
                {
                  "overrides":{"rxjs":"6.6.7"},
                  "resolutions":{"rxjs":"6.5.5"},
                  "dependencies":{"wrapper":{"rxjs":"6.6.7"},"rxjs":{"version":"6.6.7"}},
                  "devDependencies":{"rxjs":["6.6.7"]}
                }
                """,
                spec -> spec.path("package.json")
        ));
    }

    @Test
    void leavesLockfilesBackupsAndOtherJsonUntouched() {
        rewriteRun(
                json("{\"dependencies\":{\"rxjs\":\"6.6.7\"}}", spec -> spec.path("package-lock.json")),
                json("{\"dependencies\":{\"rxjs\":\"6.6.7\"}}", spec -> spec.path("package.json.backup")),
                json("{\"dependencies\":{\"rxjs\":\"6.6.7\"}}", spec -> spec.path("config/dependencies.json"))
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
                        "import { Observable } from 'rxjs';\nconst result = stream.toPromise();\nroot.add(first).add(second);\n",
                        "import { Observable } from 'rxjs';\nconst result = stream~~>.toPromise();\nroot~~>.add(first).add(second);\n",
                        source -> source.path("src/legacy.ts")
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
                        "import { Observable, map, of as observableOf } from 'rxjs';\nreturn this.buildAsObservable()~~>.toPromise();\n",
                        source -> source.path("src/lib/packagr.ts")
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
    void auditMarksRxjsCompatAndTypeScriptVersionOwners() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT_RECIPE)),
                json(
                        "{\"dependencies\":{\"rxjs-compat\":\"6.6.7\"},\"devDependencies\":{\"typescript\":\"3.9.10\"}}",
                        "{\"dependencies\":{/*~~>*/\"rxjs-compat\":\"6.6.7\"},\"devDependencies\":{/*~~>*/\"typescript\":\"3.9.10\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void discoversAndValidatesAllPublicRecipes() {
        Environment environment = environment();
        for (String name : new String[]{MIGRATION_RECIPE, DEPENDENCY_RECIPE, SOURCE_RECIPE, AUDIT_RECIPE}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> name + ": " + recipe.validate().failures());
        }
    }

    private static Stream<Arguments> selectedDeclarations() {
        return Stream.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")
                .flatMap(section -> Stream.of("6.5.5", "6.6.7")
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
