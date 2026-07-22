package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.test.SourceSpecs.text;

class AngularFormsSourceMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDeterministicAngularFormsSource());
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void followsOfficialAliasedTypedFormsCompatibilityFixture() {
        rewriteRun(typescript(
                "import { FormControl as FC } from '@angular/forms';\nconst name = new FC('');\n",
                "import { UntypedFormControl as FC } from '@angular/forms';\nconst name = new FC('');\n",
                s -> s.path("packages/core/schematics/test/typed_forms_spec.ts")));
    }

    @Test
    void migratesAllFourOfficialUntypedCompatibilityAliases() {
        rewriteRun(typescript(
                "import { FormControl as C, FormGroup as G, FormArray as A, FormBuilder as B } from '@angular/forms';\nconst c = new C(''); const g = new G({}); const a = new A([]); const b = new B();\n",
                "import { UntypedFormControl as C, UntypedFormGroup as G, UntypedFormArray as A, UntypedFormBuilder as B } from '@angular/forms';\nconst c = new C(''); const g = new G({}); const a = new A([]); const b = new B();\n",
                s -> s.path("src/aliases.ts")));
    }

    @Test
    void doesNotConvertAliasThatAlreadyHasAnExplicitGeneric() {
        rewriteRun(typescript(
                "import { FormControl as FC } from '@angular/forms';\nconst name = new FC<string>('');\n",
                s -> s.path("src/typed.ts")));
    }

    @Test
    void migratesDeprecatedInitialValueOptionFromPinnedNgxFormlyFixture() {
        rewriteRun(typescript(
                "import { UntypedFormControl } from '@angular/forms';\nconst control = new UntypedFormControl({ value, disabled: true }, { initialValueIsDefault: true });\n",
                "import { UntypedFormControl } from '@angular/forms';\nconst control = new UntypedFormControl({ value, disabled: true }, { nonNullable: true });\n",
                s -> s.path("ngx-formly/ngx-formly/src/core/src/lib/extensions/field-form/field-form.ts")));
    }

    @Test
    void supportsQuotedInitialValueOptionAndAliasedControl() {
        rewriteRun(typescript(
                "import { FormControl as FC } from '@angular/forms';\nnew FC('', {'initialValueIsDefault': true});\n",
                "import { UntypedFormControl as FC } from '@angular/forms';\nnew FC('', {'nonNullable': true});\n",
                s -> s.path("src/quoted.ts")));
    }

    @Test
    void removesOnlyRedundantAlwaysCvaConfiguration() {
        rewriteRun(typescript(
                "import { ReactiveFormsModule } from '@angular/forms';\nReactiveFormsModule.withConfig({callSetDisabledState: 'always'});\n",
                "import { ReactiveFormsModule } from '@angular/forms';\nReactiveFormsModule.withConfig({});\n",
                s -> s.path("src/app.module.ts")));
    }

    @Test
    void preservesLegacyCvaOptOutAndSameNamedForeignOptions() {
        rewriteRun(
                typescript("import { ReactiveFormsModule } from '@angular/forms';\nReactiveFormsModule.withConfig({callSetDisabledState: 'whenDisabledForLegacyCode'});\n", s -> s.path("mockoon/packages/app/src/renderer/main.ts")),
                typescript("const options = {initialValueIsDefault: true, callSetDisabledState: 'always'};\n", s -> s.path("src/foreign.ts")),
                typescript("import { FormControl } from '@angular/forms';\nnew FormControl('', {metadata: {initialValueIsDefault: true}});\n", s -> s.path("src/nested.ts")),
                typescript("import { FormControl as FC } from 'other-forms';\nnew FC('', {initialValueIsDefault: true});\n", s -> s.path("src/other.ts"))
        );
    }

    @Test
    void doesNotCreateDuplicateNonNullableOption() {
        rewriteRun(typescript(
                "import { FormControl } from '@angular/forms';\nnew FormControl('', {initialValueIsDefault: true, nonNullable: false});\n",
                s -> s.path("src/conflicting-options.ts")));
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), typescript(
                "import { FormControl as FC } from '@angular/forms';\nnew FC('', {initialValueIsDefault: true});\n",
                "import { UntypedFormControl as FC } from '@angular/forms';\nnew FC('', {nonNullable: true});\n",
                s -> s.path("src/form.ts")));
    }

    @Test
    void migratesEmailFalseTemplateBooleanCoercion() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularFormsTemplate()),
                text("<input email=\"false\"><input email='false'><input [email]=\"'false'\">",
                        "<input [email]=\"false\"><input [email]=\"false\"><input [email]=\"false\">",
                        s -> s.path("src/app.html")));
    }

    @Test
    void leavesDynamicTrueAndNonHtmlEmailValuesAlone() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularFormsTemplate()),
                text("<input email><input [email]=\"flag\"><input email=\"true\"><x data-email=\"false\" attr.email=\"false\">email=\"false\"</x>", s -> s.path("src/app.html")),
                text("const html = '<input email=\"false\">';", s -> s.path("src/app.ts")));
    }

    @Test
    void templateMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularFormsTemplate()).cycles(2).expectedCyclesThatMakeChanges(1),
                text("<input email=\"false\">", "<input [email]=\"false\">", s -> s.path("src/app.html")));
    }
}
