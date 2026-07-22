package com.huawei.clouds.openrewrite.ngxcolorpicker;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;

class NgxColorPickerAutomaticMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateColorPickerModuleToStandaloneDirective());
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void migratesNgModuleImportsAndExports() {
        rewriteRun(typescript(
                """
                import { CommonModule } from '@angular/common';
                import { ColorPickerModule } from 'ngx-color-picker';
                @NgModule({
                  imports: [CommonModule, ColorPickerModule],
                  exports: [ColorPickerModule]
                })
                export class DesignerModule {}
                """,
                """
                import { CommonModule } from '@angular/common';
                import { ColorPickerDirective } from 'ngx-color-picker';
                @NgModule({
                  imports: [CommonModule, ColorPickerDirective],
                  exports: [ColorPickerDirective]
                })
                export class DesignerModule {}
                """,
                spec -> spec.path("src/designer.module.ts")
        ));
    }

    @Test
    void migratesStandaloneComponentAndTestBedScopes() {
        rewriteRun(
                typescript(
                        """
                        import { ColorPickerModule } from 'ngx-color-picker';
                        @Component({ standalone: true, imports: [ColorPickerModule] })
                        export class ThemeEditorComponent {}
                        """,
                        """
                        import { ColorPickerDirective } from 'ngx-color-picker';
                        @Component({ standalone: true, imports: [ColorPickerDirective] })
                        export class ThemeEditorComponent {}
                        """,
                        spec -> spec.path("src/theme-editor.component.ts")),
                typescript(
                        """
                        import { ColorPickerModule } from 'ngx-color-picker';
                        TestBed.configureTestingModule({ imports: [ColorPickerModule] });
                        """,
                        """
                        import { ColorPickerDirective } from 'ngx-color-picker';
                        TestBed.configureTestingModule({ imports: [ColorPickerDirective] });
                        """,
                        spec -> spec.path("src/theme-editor.component.spec.ts"))
        );
    }

    @Test
    void preservesNamedImportAliasAndOtherSpecifiers() {
        rewriteRun(typescript(
                """
                import {
                  ColorPickerModule as PickerImports,
                  ColorPickerService,
                  Rgba
                } from "ngx-color-picker";
                @NgModule({ imports: [PickerImports], exports: [PickerImports] })
                export class ThemeModule {}
                """,
                """
                import {
                  ColorPickerDirective as PickerImports,
                  ColorPickerService,
                  Rgba
                } from "ngx-color-picker";
                @NgModule({ imports: [PickerImports], exports: [PickerImports] })
                export class ThemeModule {}
                """,
                spec -> spec.path("src/theme.module.ts")
        ));
    }

    @Test
    void realTensorBoard13AtFixedCommit() {
        // tensorflow/tensorboard@1b86d2d579ad6b0c2554cc0f775d50f567e78ffb
        // package.json has exact 13.0.0; runs_table_module.ts has the module in imports.
        rewriteRun(typescript(
                """
                import {CommonModule} from '@angular/common';
                import {ColorPickerModule} from 'ngx-color-picker';
                import {MatSelectModule} from '@angular/material/select';
                @NgModule({imports: [ColorPickerModule, CommonModule, MatSelectModule]})
                export class RunsTableModule {}
                """,
                """
                import {CommonModule} from '@angular/common';
                import {ColorPickerDirective} from 'ngx-color-picker';
                import {MatSelectModule} from '@angular/material/select';
                @NgModule({imports: [ColorPickerDirective, CommonModule, MatSelectModule]})
                export class RunsTableModule {}
                """,
                spec -> spec.path("tensorboard/webapp/runs/views/runs_table/runs_table_module.ts")
        ));
    }

    @Test
    void realFuxa13AtFixedCommit() {
        // frangoteam/FUXA@2ec5d15dce43d6ca6d1642d754432e1f0d0c46b9
        // client/package.json has ^13.0.0; app.module.ts has a large NgModule imports list.
        rewriteRun(typescript(
                """
                import { BrowserModule } from '@angular/platform-browser';
                import { FormsModule } from '@angular/forms';
                import { ColorPickerModule } from 'ngx-color-picker';
                @NgModule({
                  imports: [BrowserModule, FormsModule, ColorPickerModule]
                })
                export class AppModule {}
                """,
                """
                import { BrowserModule } from '@angular/platform-browser';
                import { FormsModule } from '@angular/forms';
                import { ColorPickerDirective } from 'ngx-color-picker';
                @NgModule({
                  imports: [BrowserModule, FormsModule, ColorPickerDirective]
                })
                export class AppModule {}
                """,
                spec -> spec.path("client/src/app/app.module.ts")
        ));
    }

    @Test
    void realDragonDrop14StandaloneComponentAtFixedCommit() {
        // toniskobic/dragon-drop@27448e5f53806b079a3f9435e91bac6213299e2e
        // package.json has ^14.0.0 and this component imports ColorPickerModule.
        rewriteRun(typescript(
                """
                import { CommonModule } from '@angular/common';
                import { Component } from '@angular/core';
                import { ColorPickerModule } from 'ngx-color-picker';
                @Component({
                  selector: 'drd-theme-colors-picker',
                  standalone: true,
                  imports: [CommonModule, ColorPickerModule]
                })
                export class ThemeColorsPickerComponent {}
                """,
                """
                import { CommonModule } from '@angular/common';
                import { Component } from '@angular/core';
                import { ColorPickerDirective } from 'ngx-color-picker';
                @Component({
                  selector: 'drd-theme-colors-picker',
                  standalone: true,
                  imports: [CommonModule, ColorPickerDirective]
                })
                export class ThemeColorsPickerComponent {}
                """,
                spec -> spec.path("src/app/components/theme-colors-picker/theme-colors-picker.component.ts")
        ));
    }

    @Test
    void realDres14NgModuleAtFixedCommit() {
        // dres-dev/DRES@bd600c8d3e586e92356e9362d68634eb365e8a85
        // frontend/package.json has ^14.0.0; competition-builder.module.ts imports the module.
        rewriteRun(typescript(
                """
                import { CommonModule } from '@angular/common';
                import { ReactiveFormsModule } from '@angular/forms';
                import { ColorPickerModule } from 'ngx-color-picker';
                @NgModule({
                  imports: [ReactiveFormsModule, CommonModule, ColorPickerModule],
                  exports: [CompetitionBuilderComponent]
                })
                export class CompetitionBuilderModule {}
                """,
                """
                import { CommonModule } from '@angular/common';
                import { ReactiveFormsModule } from '@angular/forms';
                import { ColorPickerDirective } from 'ngx-color-picker';
                @NgModule({
                  imports: [ReactiveFormsModule, CommonModule, ColorPickerDirective],
                  exports: [CompetitionBuilderComponent]
                })
                export class CompetitionBuilderModule {}
                """,
                spec -> spec.path("frontend/src/app/competition/competition-builder/competition-builder.module.ts")
        ));
    }

    @Test
    void leavesAmbiguousAndUnrelatedShapesUntouched() {
        rewriteRun(
                typescript("""
                           import { ColorPickerDirective, ColorPickerModule } from 'ngx-color-picker';
                           @NgModule({ imports: [ColorPickerModule] }) export class DualModule {}
                           """, spec -> spec.path("src/dual.ts")),
                typescript("""
                           import { ColorPickerModule } from 'ngx-color-picker';
                           const wrapped = [ColorPickerModule];
                           @NgModule({ imports: [...wrapped] }) export class DynamicModule {}
                           """, spec -> spec.path("src/dynamic.ts")),
                typescript("""
                           import { ColorPickerModule } from 'ngx-color-picker';
                           const pluginDescriptor = { imports: [ColorPickerModule] };
                           """, spec -> spec.path("src/custom-object.ts")),
                typescript("""
                           import { ColorPickerModule } from '@iplab/ngx-color-picker';
                           @NgModule({ imports: [ColorPickerModule] }) export class ForeignModule {}
                           """, spec -> spec.path("src/foreign.ts")),
                typescript("""
                           import type { ColorPickerModule } from 'ngx-color-picker';
                           type Legacy = ColorPickerModule;
                           """, spec -> spec.path("src/type-only.ts")),
                typescript("export { ColorPickerModule } from 'ngx-color-picker';\n",
                        spec -> spec.path("src/reexport.ts"))
        );
    }

    @Test
    void leavesNestedMetadataWrapperAndNameCollisionUntouched() {
        rewriteRun(
                typescript("""
                           import { ColorPickerModule } from 'ngx-color-picker';
                           @NgModule({ providers: [{ imports: [ColorPickerModule] }] }) export class NestedModule {}
                           """, spec -> spec.path("src/nested.ts")),
                typescript("""
                           import { ColorPickerModule } from 'ngx-color-picker';
                           TestBed.configureTestingModule(wrap({ imports: [ColorPickerModule] }));
                           """, spec -> spec.path("src/wrapped-testbed.ts")),
                typescript("""
                           import { ColorPickerModule } from 'ngx-color-picker';
                           const ColorPickerDirective = customDirective();
                           @NgModule({ imports: [ColorPickerModule] }) export class NameCollisionModule {}
                           """, spec -> spec.path("src/name-collision.ts"))
        );
    }

    @Test
    void excludesGeneratedInstallAndCacheDirectoriesButNotLeafFilenames() {
        rewriteRun(
                typescript("""
                           import { ColorPickerModule } from 'ngx-color-picker';
                           @NgModule({imports:[ColorPickerModule]}) export class A {}
                           """, spec -> spec.path("generated-client/a.ts")),
                typescript("""
                           import { ColorPickerModule } from 'ngx-color-picker';
                           @NgModule({imports:[ColorPickerModule]}) export class A {}
                           """, spec -> spec.path("installation/a.ts")),
                typescript("""
                           import { ColorPickerModule } from 'ngx-color-picker';
                           @NgModule({imports:[ColorPickerModule]}) export class A {}
                           """, spec -> spec.path(".angular/cache/a.ts")),
                typescript(
                        "import { ColorPickerModule } from 'ngx-color-picker';\n@NgModule({imports:[ColorPickerModule]}) export class A {}\n",
                        "import { ColorPickerDirective } from 'ngx-color-picker';\n@NgModule({imports:[ColorPickerDirective]}) export class A {}\n",
                        spec -> spec.path("src/install.ts")),
                typescript(
                        "import { ColorPickerModule } from 'ngx-color-picker';\n@NgModule({imports:[ColorPickerModule]}) export class A {}\n",
                        "import { ColorPickerDirective } from 'ngx-color-picker';\n@NgModule({imports:[ColorPickerDirective]}) export class A {}\n",
                        spec -> spec.path("src/generated.ts"))
        );
    }

    @Test
    void isIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), typescript(
                "import { ColorPickerModule } from 'ngx-color-picker';\n@NgModule({imports:[ColorPickerModule]}) export class A {}\n",
                "import { ColorPickerDirective } from 'ngx-color-picker';\n@NgModule({imports:[ColorPickerDirective]}) export class A {}\n"
        ));
    }
}
