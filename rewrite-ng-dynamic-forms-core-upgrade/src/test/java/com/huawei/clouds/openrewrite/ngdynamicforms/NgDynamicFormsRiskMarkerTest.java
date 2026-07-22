package com.huawei.clouds.openrewrite.ngdynamicforms;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class NgDynamicFormsRiskMarkerTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksPinnedElectronMailerModelsServiceAndMaterialModule() {
        assertTs("""
                import { DynamicFormModel, DynamicInputModel, DynamicCheckboxModel, DynamicFormGroupModel, DynamicFormService } from '@ng-dynamic-forms/core';
                import { DynamicFormsMaterialUIModule } from '@ng-dynamic-forms/ui-material';
                const model: DynamicFormModel=[new DynamicInputModel({id:'host',additional:{appearance:'fill'}}),new DynamicFormGroupModel({id:'auth',group:[]}),new DynamicCheckboxModel({id:'secure'})];
                export class MailConfiguration { constructor(private formService: DynamicFormService) {} form=this.formService.createFormGroup(model); }
                """, "dhrn/electron-mailer-poc/apps/ng-mailer/src/app/mail-configuration/mail-configuration.component.ts",
                "DynamicFormsMaterialUIModule was removed", "DynamicInputModel configuration crosses",
                "DynamicFormGroupModel configuration crosses", "createFormGroup crosses the v16 UntypedFormGroup");
    }

    @Test
    void marksPinnedAngularFormBuilderBasicModuleAndModels() {
        assertTs("""
                import { DynamicFormModel, DynamicInputModel, DynamicRadioGroupModel, DynamicCheckboxModel, DynamicFormService } from '@ng-dynamic-forms/core';
                import { DynamicFormsBasicUIModule } from '@ng-dynamic-forms/ui-basic';
                const formService: DynamicFormService=inject(DynamicFormService);
                const model: DynamicFormModel=[new DynamicInputModel({id:'input'}),new DynamicRadioGroupModel({id:'radio',options:[]}),new DynamicCheckboxModel({id:'agree'})];
                const group=formService.createFormGroup(model);
                """, "Patrick5078/Angular-form-builder/src/app/components/view-dynamic-form/view-dynamic-form.component.ts",
                "DynamicFormsBasicUIModule was removed", "DynamicRadioGroupModel configuration crosses",
                "createFormGroup crosses the v16 UntypedFormGroup");
    }

    @Test
    void marksPinnedMdsoarCustomDynamicFormBaseClass() {
        assertTs("""
                import { DynamicFormComponent, DynamicFormComponentService, DynamicFormControlEvent, DynamicFormLayout, DynamicTemplateDirective } from '@ng-dynamic-forms/core';
                export class DsDynamicFormComponent extends DynamicFormComponent {
                  constructor(changeDetectorRef: ChangeDetectorRef, componentService: DynamicFormComponentService){super(changeDetectorRef,componentService)}
                }
                """, "umd-lib/mdsoar-angular/src/app/shared/form/builder/ds-dynamic-form-ui/ds-dynamic-form.component.ts",
                "Custom class extends DynamicFormComponent", "standalone imports", "untyped FormGroup inputs");
    }

    @Test
    void pinnedMdsoarLayoutUtilityWithoutChangedApiIsNoOp() {
        rewriteRun(spec -> spec.recipe(new FindNgDynamicFormsTypeScriptRisks()), typescript("""
                import { DynamicFormControlLayout, DynamicFormControlLayoutConfig } from '@ng-dynamic-forms/core';
                export function setLayout(model:any,key:string,value:string){model.layout={} as DynamicFormControlLayout;model.layout[key]={} as DynamicFormControlLayoutConfig;}
                """, s -> s.path("umd-lib/mdsoar-angular/src/app/shared/form/builder/parsers/parser.utils.ts")
                .afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksServiceJsonDetectionLookupAndArrayMutations() {
        assertTs("""
                import { DynamicFormService } from '@ng-dynamic-forms/core';
                function update(service: DynamicFormService, model:any, array:any){service.fromJSON(data);service.detectChanges();service.findById('x',model);service.addFormArrayGroup(array,model);service.clearFormArray(array,model);}
                """, "src/forms.ts", "JSON revival constructs runtime model classes", "detectChanges is required",
                "findById returns or resolves", "addFormArrayGroup crosses", "clearFormArray crosses");
    }

    @Test
    void marksNonstandardCoreForRootStandaloneDeclarationsDeepImportAndKendo() {
        assertTs("""
                import { DynamicFormsCoreModule, DynamicListDirective, DynamicTemplateDirective } from '@ng-dynamic-forms/core';
                import { DynamicInputModel } from '@ng-dynamic-forms/core/lib/model/input/dynamic-input.model';
                import { DynamicFormsKendoUIModule } from '@ng-dynamic-forms/ui-kendo';
                @NgModule({declarations:[DynamicListDirective,DynamicTemplateDirective],imports:[DynamicFormsCoreModule.forRoot({providers:[custom]})]}) export class LegacyModule {}
                """, "src/legacy.module.ts", "Private @ng-dynamic-forms/core deep import", "removed the Kendo renderer",
                "forRoot with arguments", "DynamicListDirective is standalone in v18");
    }

    @Test
    void sameNamedForeignServicesModelsModulesAndClassesAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindNgDynamicFormsTypeScriptRisks()),
                typescript("import { DynamicFormService, DynamicInputModel, DynamicFormComponent } from '@company/forms';\nconst service:DynamicFormService=make();service.createFormGroup([]);const m=new DynamicInputModel({});class Custom extends DynamicFormComponent{}\n",
                        s -> s.path("src/foreign.ts").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("class DynamicFormService{createFormGroup(x:any){}} class DynamicInputModel{} const s=new DynamicFormService();s.createFormGroup([]);new DynamicInputModel();\n",
                        s -> s.path("src/local.ts").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import {DynamicFormsCoreModule} from '@ng-dynamic-forms/core';\nfunction configure(DynamicFormsCoreModule:any){return DynamicFormsCoreModule.forRoot();}\n",
                        s -> s.path("src/shadowed.ts").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void marksPinnedMaterialAndBasicTemplatesAtExactOpeningTag() {
        rewriteRun(spec -> spec.recipe(new FindNgDynamicFormsTemplateRisks()),
                text("<mat-tab><dynamic-material-form [group]=\"formGroup\" [model]=\"formModel\"></dynamic-material-form></mat-tab>",
                        s -> s.path("dhrn/electron-mailer-poc/apps/ng-mailer/src/app/mail-configuration/mail-configuration.component.html")
                                .after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("removed renderer UIModules"), after.printAll()))),
                text("<form [formGroup]=\"formGroup\"><dynamic-basic-form [group]=\"formGroup\" [model]=\"formModel\"></dynamic-basic-form></form>",
                        s -> s.path("Patrick5078/Angular-form-builder/src/app/components/view-dynamic-form/view-dynamic-form.component.html")
                                .after(actual -> actual).afterRecipe(after -> assertTrue(after.printAll().contains("standalone component matching this selector"), after.printAll()))));
    }

    @Test
    void marksKendoDynamicListAndProjectedTemplateTags() {
        rewriteRun(spec -> spec.recipe(new FindNgDynamicFormsTemplateRisks()), text("""
                <dynamic-kendo-form [group]="group" [model]="model"></dynamic-kendo-form>
                <input [dynamicList]="model.id">
                <ng-template modelId="email" align="START">Prefix</ng-template>
                """, s -> s.path("src/form.html").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(out.contains("Kendo renderer no longer exists"), out);
                    assertTrue(out.contains("DynamicListDirective is standalone"), out);
                    assertTrue(out.contains("DynamicTemplateDirective is standalone"), out);
                })));
    }

    @Test
    void unrelatedTemplatesAndTsStringsAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindNgDynamicFormsTemplateRisks()),
                text("<form><input list=\"cities\"><ng-template #row>Row</ng-template><company-dynamic-form></company-dynamic-form></form>", s -> s.path("src/plain.html")),
                text("<!-- <dynamic-material-form></dynamic-material-form> -->\n<script>const x='<dynamic-basic-form>';</script>\n<style>/* <dynamic-kendo-form> */</style>", s -> s.path("src/commented.html")),
                text("const html='<dynamic-material-form></dynamic-material-form>';", s -> s.path("src/string.ts")));
    }

    @Test
    void marksPinnedElectronPackagePeerAndRendererRisks() {
        rewriteRun(spec -> spec.recipe(new FindNgDynamicFormsJsonRisks()), json("""
                {"engines":{"node":"14.21.3"},"dependencies":{"@angular/core":"^12.2.0","@angular/forms":"^12.2.0",
                 "@angular/material":"^12.2.0","@ng-dynamic-forms/core":"^14.0.0","@ng-dynamic-forms/ui-material":"^14.0.0",
                 "core-js":"^3.15.0","rxjs":"~6.6.0"},"devDependencies":{"typescript":"~4.3.5"}}
                """, s -> s.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String out=after.printAll();
                    for(String message:new String[]{"unlisted, patched, newer", "aligned to Angular 16", "peers on RxJS ^7.5.7",
                            "peers on core-js ^3.31.0", "standalone release", "supports TypeScript", "requires Node"})
                        assertTrue(out.contains(message),out);
                })));
    }

    @Test
    void marksPinnedMdsoarAngular17AndTypeScript54Mismatch() {
        rewriteRun(spec -> spec.recipe(new FindNgDynamicFormsJsonRisks()), json("""
                {"dependencies":{"@angular/common":"^17.3.11","@angular/core":"^17.3.11","@angular/forms":"^17.3.11",
                 "@ng-dynamic-forms/core":"16.0.0","rxjs":"^7.8.2","core-js":"^3.37.0"},
                 "devDependencies":{"@angular/compiler-cli":"^17.3.11","typescript":"~5.4.5"}}
                """, s -> s.path("umd-lib/mdsoar-angular/package.json").after(actual -> actual).afterRecipe(after -> {
                    String out=after.printAll();
                    assertTrue(out.contains("aligned to Angular 16"),out);
                    assertTrue(out.contains("supports TypeScript >=4.9.3 and <5.2"),out);
                })));
    }

    @Test
    void marksKendoCentralOwnerAndRendererSpecificRuntime() {
        rewriteRun(spec -> spec.recipe(new FindNgDynamicFormsJsonRisks()), json("""
                {"dependencies":{"@ng-dynamic-forms/core":"18.0.0","@ng-dynamic-forms/ui-kendo":"17.0.0","ngx-mask":"^13.0.0"},
                 "pnpm":{"overrides":{"@ng-dynamic-forms/core":"17.0.0"}}}
                """, s -> s.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String out=after.printAll();
                    assertTrue(out.contains("removed the Kendo renderer"),out);
                    assertTrue(out.contains("renderer-specific peer"),out);
                    assertTrue(out.contains("Central package-manager ownership"),out);
                })));
    }

    @Test
    void supportedTargetPeersAndUnrelatedPackagesAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindNgDynamicFormsJsonRisks()),
                json("{\"engines\":{\"node\":\"^16.14.0 || >=18.10.0\"},\"dependencies\":{\"@angular/common\":\"^16.1.3\",\"@angular/core\":\"^16.1.3\",\"@angular/forms\":\"^16.1.3\",\"@ng-dynamic-forms/core\":\"18.0.0\",\"core-js\":\"^3.31.0\",\"rxjs\":\"^7.5.7\"},\"devDependencies\":{\"typescript\":\"~5.1.6\"}}",
                        s -> s.path("package.json").afterRecipe(after -> assertNoMarker(after.printAll()))),
                json("{\"engines\":{\"node\":\"12.0.0\"},\"dependencies\":{\"@angular/core\":\"12.2.0\"}}",
                        s -> s.path("services/api/package.json").afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    private void assertTs(String before, String path, String... messages) {
        rewriteRun(spec -> spec.recipe(new FindNgDynamicFormsTypeScriptRisks()),
                typescript(before, s -> s.path(path).after(actual -> actual).afterRecipe(after -> {
                    String out=after.printAll();
                    for(String message:messages) assertTrue(out.contains(message),out);
                })));
    }

    private static void assertNoMarker(String source) {
        assertFalse(source.contains("~~("),source);
    }
}
