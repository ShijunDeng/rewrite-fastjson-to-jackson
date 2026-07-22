package com.huawei.clouds.openrewrite.ngxcolorpicker;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;

class NgxColorPickerRiskAndCompositionTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksRemovedModuleAliasDeepEntryAndStandaloneDeclarationExactly() {
        rewriteRun(spec -> spec.recipe(new FindNgxColorPickerSourceRisks()), typescript(
                """
                import {
                  ColorPickerModule as PickerModule,
                  ColorPickerDirective as PickerDirective
                } from 'ngx-color-picker';
                import { helper } from 'ngx-color-picker/lib/helpers';
                @NgModule({ declarations: [PickerDirective], imports: [PickerModule] })
                export class LegacyModule {}
                """,
                source -> source.path("src/legacy.module.ts").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "ColorPickerModule was removed");
                    assertContains(out, "~~>*/PickerModule");
                    assertContains(out, "Private ngx-color-picker deep import");
                    assertContains(out, "~~>*/'ngx-color-picker/lib/helpers'");
                    assertContains(out, "PickerDirective is standalone");
                    assertContains(out, "~~>*/PickerDirective]");
                })
        ));
    }

    @Test
    void marksEveryResidualModuleUseAfterAmbiguousAutomaticBoundary() {
        rewriteRun(spec -> spec.recipe(new FindNgxColorPickerSourceRisks()), typescript(
                """
                import { ColorPickerModule as PickerModule } from 'ngx-color-picker';
                const wrapped = [PickerModule];
                @NgModule({ imports: [...wrapped] })
                export class DynamicModule {}
                """,
                source -> source.path("src/dynamic.module.ts").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "ColorPickerModule was removed");
                    assertTrue(markerCount(out) >= 2, out);
                    assertContains(out, "~~>*/PickerModule]");
                })
        ));
    }

    @Test
    void marksLegacyInlineSliderBindingOnlyWhenPackageIsUsed() {
        rewriteRun(spec -> spec.recipe(new FindNgxColorPickerSourceRisks()),
                typescript(
                        """
                        import { SliderDirective } from 'ngx-color-picker';
                        @Component({
                          imports: [SliderDirective],
                          template: '<div [slider]="mode" [rgX]="1"></div>'
                        })
                        export class PaletteComponent {}
                        """,
                        source -> source.path("src/palette.component.ts").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertContains(out, "no longer declares a slider input");
                            assertContains(out, "~~>*/'<div [slider]=\"mode\" [rgX]=\"1\"></div>'");
                        })),
                typescript(
                        """
                        @Component({template: '<company-slider [slider]="mode"></company-slider>'})
                        export class CompanyComponent {}
                        """,
                        source -> source.path("src/company.component.ts")
                                .afterRecipe(after -> assertNoMarker(after.printAll())))
        );
    }

    @Test
    void foreignSameNamesAndOrdinaryRootImportsRemainUnmarked() {
        rewriteRun(spec -> spec.recipe(new FindNgxColorPickerSourceRisks()),
                typescript("""
                           import { ColorPickerModule } from '@iplab/ngx-color-picker';
                           @NgModule({declarations:[ColorPickerDirective],imports:[ColorPickerModule]}) class Foreign {}
                           """, source -> source.path("src/foreign.ts")
                        .afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("""
                           import { ColorPickerService, Rgba } from 'ngx-color-picker';
                           const color: Rgba = new Rgba(1, 2, 3, 1);
                           """, source -> source.path("src/service.ts")
                        .afterRecipe(after -> assertNoMarker(after.printAll())))
        );
    }

    @Test
    void sourceAuditExcludesGeneratedInstallAndCachePathsButNotLeafNames() {
        rewriteRun(spec -> spec.recipe(new FindNgxColorPickerSourceRisks()),
                typescript("import {ColorPickerModule} from 'ngx-color-picker';\n",
                        source -> source.path("generated-sdk/a.ts").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import {ColorPickerModule} from 'ngx-color-picker';\n",
                        source -> source.path("installer/a.ts").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import {ColorPickerModule} from 'ngx-color-picker';\n",
                        source -> source.path(".m2/cache/a.ts").afterRecipe(after -> assertNoMarker(after.printAll()))),
                typescript("import {ColorPickerModule} from 'ngx-color-picker';\n",
                        source -> source.path("src/install.ts").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "ColorPickerModule was removed"))),
                typescript("import {ColorPickerModule} from 'ngx-color-picker';\n",
                        source -> source.path("src/generated.ts").after(actual -> actual)
                                .afterRecipe(after -> assertContains(after.printAll(), "ColorPickerModule was removed")))
        );
    }

    @ParameterizedTest(name = "marks manifest risk {0} {1}")
    @MethodSource("manifestRisks")
    void marksManifestCompatibilityValueAtExactLiteral(String name, String declaration, String message) {
        rewriteRun(spec -> spec.recipe(new FindNgxColorPickerManifestRisks()), json(
                "{\"dependencies\":{\"ngx-color-picker\":\"20.1.1\",\"" + name + "\":\"" + declaration + "\"}}",
                source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, message);
                    assertContains(out, "~~>*/\"" + declaration + "\"");
                })
        ));
    }

    static Stream<Arguments> manifestRisks() {
        return Stream.of(
                Arguments.of("@angular/core", "~14.2.0", "Angular 19+ compatible set"),
                Arguments.of("@angular/common", "^18.2.12", "Angular 19+ compatible set"),
                Arguments.of("@angular/forms", "13.3.0", "Angular 19+ compatible set"),
                Arguments.of("@angular/cli", "18.2.0", "Angular 19+ compatible set"),
                Arguments.of("@angular-devkit/build-angular", "^17.3.0", "Angular 19+ compatible set"),
                Arguments.of("typescript", "~4.8.4", "starts at TypeScript 5.5"),
                Arguments.of("typescript", "5.4.5", "starts at TypeScript 5.5")
        );
    }

    @Test
    void marksEngineCentralOwnerAndUnselectedDeclaration() {
        rewriteRun(spec -> spec.recipe(new FindNgxColorPickerManifestRisks()), json(
                """
                {
                  "engines":{"node":"16.20.0"},
                  "dependencies":{"ngx-color-picker":">=14 <21"},
                  "pnpm":{"overrides":{"ngx-color-picker":"14.0.0"}}
                }
                """,
                source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "choose the 20.1.1 constraint explicitly");
                    assertContains(out, "starts at Node 18.19.1");
                    assertContains(out, "Central package-manager ownership detected");
                    assertContains(out, "~~>*/\"16.20.0\"");
                    assertContains(out, "~~>*/\"14.0.0\"");
                })
        ));
    }

    @Test
    void compatibleTargetManifestAndUnrelatedManifestRemainUnmarked() {
        rewriteRun(spec -> spec.recipe(new FindNgxColorPickerManifestRisks()),
                json("""
                     {"engines":{"node":"^20.11.1"},"dependencies":{"ngx-color-picker":"20.1.1","@angular/core":"^19.2.0","@angular/common":"^19.2.0","@angular/forms":"^19.2.0"},"devDependencies":{"typescript":"~5.8.2"}}
                     """, source -> source.path("package.json").afterRecipe(after -> assertNoMarker(after.printAll()))),
                json("""
                     {"engines":{"node":"12.0.0"},"dependencies":{"@angular/core":"9.1.0"}}
                     """, source -> source.path("services/api/package.json")
                        .afterRecipe(after -> assertNoMarker(after.printAll())))
        );
    }

    @Test
    void marksEveryAmbiguousComplexToolchainConstraintPrecisely() {
        rewriteRun(spec -> spec.recipe(new FindNgxColorPickerManifestRisks()), json(
                """
                {
                  "engines":{"node":">=20 <23"},
                  "dependencies":{
                    "ngx-color-picker":"20.1.1",
                    "@angular/core":">=19 <21",
                    "@angular/common":"workspace:^19.2.0",
                    "@angular/forms":"catalog:angular"
                  },
                  "devDependencies":{"typescript":">=5.5 <5.9"}
                }
                """,
                source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertTrue(markerCount(out) == 5, out);
                    for (String value : new String[]{">=20 <23", ">=19 <21", "workspace:^19.2.0",
                            "catalog:angular", ">=5.5 <5.9"}) {
                        assertContains(out, "~~>*/\"" + value + "\"");
                    }
                })
        ));
    }

    @Test
    void lowLevelYamlRecipeChangesOnlyTheDependency() {
        Environment environment = environment();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.ngxcolorpicker.UpgradeNgxColorPickerTo20_1_1")),
                json("{\"dependencies\":{\"ngx-color-picker\":\"13.0.0\",\"@angular/core\":\"14.2.0\"}}",
                        "{\"dependencies\":{\"ngx-color-picker\":\"20.1.1\",\"@angular/core\":\"14.2.0\"}}",
                        source -> source.path("package.json")),
                typescript("""
                           import {ColorPickerModule} from 'ngx-color-picker';
                           @NgModule({imports:[ColorPickerModule]}) export class AppModule {}
                           """, source -> source.path("src/app.module.ts"))
        );
    }

    @Test
    void recommendedRecipeComposesThePublicUpgradeAndAuditBoundaries() {
        var recipe = environment().activateRecipes(
                "com.huawei.clouds.openrewrite.ngxcolorpicker.MigrateNgxColorPickerTo20_1_1");
        assertEquals("com.huawei.clouds.openrewrite.ngxcolorpicker.UpgradeNgxColorPickerTo20_1_1",
                recipe.getRecipeList().get(0).getName());
        assertEquals("com.huawei.clouds.openrewrite.ngxcolorpicker.AuditNgxColorPicker20Compatibility",
                recipe.getRecipeList().get(2).getName());
    }

    @Test
    void recommendedRecipeUpgradesMigratesAndMarksRealRepositoryShape() {
        Environment environment = environment();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes(
                        "com.huawei.clouds.openrewrite.ngxcolorpicker.MigrateNgxColorPickerTo20_1_1")),
                json("{\"dependencies\":{\"@angular/core\":\"^16.2.12\",\"ngx-color-picker\":\"^14.0.0\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertContains(out, "\"ngx-color-picker\":\"20.1.1\"");
                            assertContains(out, "Angular 19+ compatible set");
                        })),
                typescript("""
                           import {ColorPickerModule} from 'ngx-color-picker';
                           @NgModule({imports:[ColorPickerModule],exports:[ColorPickerModule]}) export class SharedModule{}
                           """, source -> source.path("src/app/shared/shared.module.ts").after(actual -> actual)
                        .afterRecipe(after -> {
                            String out = after.printAll();
                            assertContains(out, "ColorPickerDirective");
                            assertFalse(out.contains("ColorPickerModule"), out);
                            assertNoMarker(out);
                        }))
        );
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngxcolorpicker")
                .scanYamlResources().build();
    }

    private static int markerCount(String source) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf("~~>", index)) >= 0) {
            count++;
            index += 3;
        }
        return count;
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertNoMarker(String source) {
        assertFalse(source.contains("~~("), source);
    }
}
