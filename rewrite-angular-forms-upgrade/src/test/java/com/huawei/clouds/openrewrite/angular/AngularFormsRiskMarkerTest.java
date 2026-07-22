package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class AngularFormsRiskMarkerTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksUntypedConstructorsAtExactConstruction() {
        assertTs("import { FormControl, FormGroup } from '@angular/forms';\nconst name = new FormControl(''); const form = new FormGroup({name});\n",
                "src/profile.ts", "untyped under the pre-v14 model", "Untyped compatibility migration");
    }

    @Test
    void marksDeprecatedOptionsPlusThirdAsyncValidatorCombination() {
        assertTs("import { FormControl } from '@angular/forms';\nconst c = new FormControl('', {validators: required}, asyncValidator);\n",
                "src/async.ts", "drops/deprecates the third argument", "cancellation behavior");
    }

    @Test
    void marksConflictingLegacyAndNonNullableOptionsInsteadOfRewritingThem() {
        Environment environment = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources().build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes("com.huawei.clouds.openrewrite.angular.MigrateAngularFormsTo20_3_26")),
                typescript("import { FormControl } from '@angular/forms';\nnew FormControl('', {initialValueIsDefault: true, nonNullable: false});\n",
                        s -> s.path("src/conflicting-options.ts").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertTrue(out.contains("initialValueIsDefault remains"), out);
                            assertTrue(out.contains("nonNullable: false"), out);
                        })));
    }

    @Test
    void marksPinnedBigQueryRawValueAndValueChangesFixture() {
        assertTs("import { FormGroup } from '@angular/forms';\nfunction sync(formGroup: FormGroup) { formGroup.valueChanges.subscribe(() => Object.assign(rule, formGroup.getRawValue())); }\n",
                "GoogleCloudPlatform/bigquery-geo-viz/src/app/rule/rule.component.ts",
                "emission timing relative to parent", "getRawValue includes disabled controls");
    }

    @Test
    void marksValueResetPatchAndDisabledStateCalls() {
        assertTs("import { FormControl } from '@angular/forms';\nfunction update(control: FormControl) { control.setValue('a'); control.patchValue('b'); control.reset(); control.disable({emitEvent: false}); control.enable(); }\n",
                "src/control.ts", "typed form value/nullability shape", "changes control state and event ordering");
    }

    @Test
    void marksValueVersusRawValueOnTypedReceiver() {
        assertTs("import { FormGroup } from '@angular/forms';\nfunction save(profile: FormGroup) { return [profile.value, profile.getRawValue()]; }\n",
                "src/save.ts", "excludes disabled descendants", "different typed DTO");
    }

    @Test
    void marksAsyncValidatorMutationAndImplementation() {
        assertTs("import { AsyncValidator, AbstractControl, FormControl } from '@angular/forms';\nclass Unique implements AsyncValidator { validate(c: AbstractControl) { return api(c.value); } }\nfunction setup(control: FormControl) { control.setAsyncValidators(new Unique()); }\n",
                "src/unique.validator.ts", "AsyncValidator.validate", "async validation lifetime");
    }

    @Test
    void marksCvaMethodsAtImplementingClassFromPinnedFixtureStyle() {
        assertTs("import { ControlValueAccessor } from '@angular/forms';\nclass Toggle implements ControlValueAccessor { writeValue(v: unknown) {} registerOnChange(fn: any) {} registerOnTouched(fn: any) {} setDisabledState(disabled: boolean) {} }\n",
                "valor-software/ngx-bootstrap/src/buttons/button-checkbox.directive.ts",
                "CVA writeValue", "CVA registerOnChange", "initial enabled state by default");
    }

    @Test
    void marksPinnedMockoonLegacyConfigAtPropertyAndCall() {
        assertTs("import { ReactiveFormsModule } from '@angular/forms';\nReactiveFormsModule.withConfig({callSetDisabledState: 'whenDisabledForLegacyCode'});\n",
                "mockoon/packages/app/src/renderer/main.ts", "Legacy CVA disabled-state opt-out", "Forms module configuration controls CVA");
    }

    @Test
    void marksStandaloneFormsComponentConfiguration() {
        assertTs("import { Component } from '@angular/core';\nimport { ReactiveFormsModule } from '@angular/forms';\n@Component({standalone: true, imports: [ReactiveFormsModule]}) class Editor {}\n",
                "src/editor.component.ts", "Standalone component/forms provider scope");
    }

    @Test
    void sameNamedForeignTypesMethodsAndNonImplementingClassesAreNotMarked() {
        rewriteRun(spec -> spec.recipe(new FindAngularFormsTypeScriptRisks()),
                typescript("class FormControl {}\nconst control = new FormControl(); control.disable(); control.getRawValue();\n", s -> s.path("src/local.ts")),
                typescript("import { ControlValueAccessor } from '@angular/forms';\nclass Serializer { writeValue(v: unknown) {} setDisabledState(v: boolean) {} }\n",
                        s -> s.path("src/serializer.ts").afterRecipe(after -> assertFalse(after.printAll().contains("~~("), after.printAll()))),
                typescript("import { FormControl } from 'other-forms';\nfunction read(transform: any) { return transform.value; }\n", s -> s.path("src/foreign.ts")));
    }

    @Test
    void sameNamedStandalonePropertyOutsideComponentMetadataIsNotMarked() {
        rewriteRun(spec -> spec.recipe(new FindAngularFormsTypeScriptRisks()),
                typescript("import { FormsModule } from '@angular/forms';\nconst fixture = {standalone: true};\n",
                        s -> s.path("src/fixture.ts")));
    }

    @Test
    void marksPackagePeersToolchainComplexOwnerAndTsconfigFlags() {
        rewriteRun(spec -> spec.recipe(new FindAngularFormsJsonRisks()),
                json("""
                        {"engines":{"node":"18.20.0"},"dependencies":{"@angular/forms":">=10 <14","@angular/core":"12.2.17","rxjs":"6.4.0"},
                         "devDependencies":{"typescript":"4.8.4"},"pnpm":{"overrides":{"@angular/forms":"10.0.14"}}}
                """, s -> s.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            for (String message : new String[]{"complex range", "peers must be aligned", "requires TypeScript", "supports RxJS", "requires Node", "Central package-manager"})
                                assertTrue(out.contains(message), out);
                        })),
                json("{\"angularCompilerOptions\":{\"strictTemplates\":false,\"strictInputTypes\":false}}",
                        s -> s.path("tsconfig.app.json").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("strictTemplates is disabled"), after.printAll());
                            assertTrue(after.printAll().contains("strictInputTypes is disabled"), after.printAll());
                        })));
    }

    @Test
    void supportedToolchainAndUnrelatedPackagesAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindAngularFormsJsonRisks()),
                json("{\"engines\":{\"node\":\"^20.19.0 || ^22.12.0 || >=24.0.0\"},\"dependencies\":{\"@angular/forms\":\"^12.2.17\",\"@angular/core\":\"20.3.26\",\"rxjs\":\"^7.8.0\"},\"devDependencies\":{\"typescript\":\"5.9.2\"}}", s -> s.path("package.json")),
                json("{\"engines\":{\"node\":\"18.x\"},\"devDependencies\":{\"typescript\":\"4.8.4\"}}", s -> s.path("services/api/package.json")));
    }

    @Test
    void marksCustomBuilderAndSsrWorkspaceTargets() {
        rewriteRun(spec -> spec.recipe(new FindAngularFormsJsonRisks()),
                json("{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@nrwl/angular:webpack-browser\"},\"server\":{},\"prerender\":{}}}}}",
                        s -> s.path("angular.json").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("Custom builder detected"), after.printAll());
                            assertTrue(after.printAll().contains("SSR/prerender forms"), after.printAll());
                        })));
    }

    @Test
    void recommendedRecipeCombinesAutoAndMarkers() {
        Environment environment = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources().build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes("com.huawei.clouds.openrewrite.angular.MigrateAngularFormsTo20_3_26")),
                json("{\"dependencies\":{\"@angular/forms\":\"^12.2.17\"}}", "{\"dependencies\":{\"@angular/forms\":\"20.3.26\"}}", s -> s.path("package.json")),
                typescript("import { UntypedFormControl } from '@angular/forms';\nconst c = new UntypedFormControl('', {initialValueIsDefault: true}); c.reset();\n",
                        s -> s.path("src/form.ts").after(actual -> actual).afterRecipe(after -> {
                            assertTrue(after.printAll().contains("nonNullable"), after.printAll());
                            assertTrue(after.printAll().contains("typed form value/nullability shape"), after.printAll());
                        })));
    }

    private void assertTs(String before, String path, String... messages) {
        rewriteRun(spec -> spec.recipe(new FindAngularFormsTypeScriptRisks()),
                typescript(before, s -> s.path(path).after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    for (String message : messages) assertTrue(out.contains(message), out);
                })));
    }
}
