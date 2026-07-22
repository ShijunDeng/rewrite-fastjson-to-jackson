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

class AngularCompilerRiskMarkerTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void marksPinnedNgxTranslateParseTemplateCallAndAstType() {
        assertTs("import { parseTemplate, TmplAstNode } from '@angular/compiler';\nconst nodes: TmplAstNode[] = parseTemplate(source, path).nodes;\n",
                "biesbjerg/ngx-translate-extract/src/parsers/pipe.parser.ts",
                "parseTemplate compiler parser/AST", "TmplAstNode compiler parser/AST");
    }

    @Test
    void marksPinnedCodelyzerNamespaceParserConstruction() {
        assertTs("import * as compiler from '@angular/compiler';\nconst parser = new compiler.Parser(new compiler.Lexer());\nconst schema = new compiler.DomElementSchemaRegistry();\n",
                "mgechev/codelyzer/src/angular/templates/templateParser.ts",
                "Parser compiler parser/AST", "Lexer compiler parser/AST", "DomElementSchemaRegistry compiler parser/AST");
    }

    @Test
    void marksRuntimeCompilerReceiverAndJitFactory() {
        assertTs("import { Compiler, JitCompilerFactory } from '@angular/compiler';\nfunction load(compiler: Compiler) { return compiler.compileModuleAndAllComponentsAsync(moduleType); }\nconst factory = new JitCompilerFactory([]);\n",
                "src/runtime-compiler.ts", "deprecated runtime JIT", "Runtime Compiler.compileModuleAndAllComponentsAsync", "JitCompilerFactory participates");
    }

    @Test
    void marksPlatformBrowserDynamicAtCallNotImport() {
        assertTs("import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';\nplatformBrowserDynamic().bootstrapModule(AppModule);\n",
                "src/main.ts", "platformBrowserDynamic enables runtime JIT");
    }

    @Test
    void marksDynamicComponentCreationCall() {
        assertTs("import { createComponent } from '@angular/core';\nconst ref = createComponent(Dialog, {environmentInjector});\n",
                "src/dialog.ts", "Dynamic component creation needs explicit environment injector");
    }

    @Test
    void marksPrivateCompilerApiAtExactUse() {
        assertTs("import { ɵparseCookieValue as parseCookie } from '@angular/compiler';\nconst value = parseCookie(cookie, name);\n",
                "src/private-tool.ts", "private Angular compiler API");
    }

    @Test
    void marksNgModuleDeclarationsForStandaloneDefault() {
        assertTs("import { NgModule } from '@angular/core';\n@NgModule({declarations: [LegacyComponent], imports: [CommonModule]}) class LegacyModule {}\n",
                "src/legacy.module.ts", "standalone by default", "statically evaluable");
    }

    @Test
    void marksComponentTemplateHostImportsAndJitMetadata() {
        assertTs("import { Component } from '@angular/core';\n@Component({standalone: true, imports: [CommonModule], template: makeTemplate(), host: {'(click)': 'run()'}, jit: true}) class Dynamic {}\n",
                "src/dynamic.component.ts", "not statically analyzable/AOT-safe", "Host metadata", "Standalone template imports", "jit:true");
    }

    @Test
    void marksInlineAndExternalTemplatesPrecisely() {
        assertTs("import { Component } from '@angular/core';\n@Component({template: '<p>{{void}}</p>', templateUrl: './other.html'}) class C {}\n",
                "src/c.ts", "Inline template must pass Angular 20", "External template resolution");
    }

    @Test
    void marksHostListenerDecoratorAtAnnotation() {
        assertTs("import { Directive, HostListener } from '@angular/core';\n@Directive({selector: '[escape]'}) class Escape { @HostListener('window:keydown', ['$event']) onKey(event: KeyboardEvent) {} }\n",
                "src/escape.directive.ts", "stricter template type checking");
    }

    @Test
    void sameNamedForeignApisDecoratorsAndMethodsAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerTypeScriptRisks()),
                typescript("import { parseTemplate } from 'other-compiler';\nparseTemplate(source);\n", s -> s.path("src/foreign.ts")),
                typescript("function platformBrowserDynamic() { return platform; }\nplatformBrowserDynamic();\n", s -> s.path("src/local.ts")),
                typescript("import { Component } from 'other-core';\n@Component({template: createTemplate(), declarations: [C]}) class C {}\n", s -> s.path("src/decorator.ts")));
    }

    @Test
    void nestedSameNamedDecoratorMetadataIsNotMarked() {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerTypeScriptRisks()),
                typescript("import { NgModule } from '@angular/core';\n@NgModule({providers: [{imports: [Foreign], template: dynamic}]}) class M {}\n",
                        s -> s.path("src/nested.ts").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            assertTrue(out.contains("providers decorator metadata"), out);
                            assertFalse(out.contains("Inline template"), out);
                        })));
    }

    @Test
    void marksComplexPackageToolchainNgccAndCentralOwner() {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerJsonRisks()), json("""
                {"engines":{"node":"18.20.0"},"dependencies":{"@angular/compiler":">=10 <14","@angular/core":"12.2.17","tslib":"2.2.0"},
                 "devDependencies":{"@angular/compiler-cli":"12.2.17","@angular/cli":"12.2.17","typescript":"4.8.4"},
                 "scripts":{"postinstall":"ngcc && npm run build"},"pnpm":{"overrides":{"@angular/compiler":"10.0.14"}}}
                """, s -> s.path("package.json").after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    for (String message : new String[]{"complex range", "mutually compatible", "requires TypeScript", "requires tslib", "requires Node", "ngcc was removed", "Central package-manager"})
                        assertTrue(out.contains(message), out);
                })));
    }

    @Test
    void marksCompilerOptionsAtDirectValuesWithoutNestedFalsePositive() {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerJsonRisks()),
                json("{\"angularCompilerOptions\":{\"enableIvy\":false,\"strictTemplates\":false,\"strictStandalone\":false,\"fullTemplateTypeCheck\":true,\"compilationMode\":\"partial\",\"preserveWhitespaces\":true,\"extendedDiagnostics\":{\"checks\":{\"strictTemplates\":false}}}}",
                        s -> s.path("tsconfig.lib.json").after(actual -> actual).afterRecipe(after -> {
                            String out = after.printAll();
                            for (String message : new String[]{"removed View Engine", "strictTemplates is disabled", "strictStandalone is disabled", "superseded by strictTemplates", "partial for published libraries", "message IDs", "changes compiler diagnostics"})
                                assertTrue(out.contains(message), out);
                            int first = out.indexOf("strictTemplates is disabled");
                            assertTrue(first >= 0 && first == out.lastIndexOf("strictTemplates is disabled"), out);
                        })),
                json("{\"other\":{\"strictTemplates\":false,\"enableIvy\":false}}", s -> s.path("tsconfig.app.json")));
    }

    @Test
    void marksCustomBuilderAotFalseAndSsrTargets() {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerJsonRisks()), json(
                "{\"projects\":{\"web\":{\"architect\":{\"build\":{\"builder\":\"@nrwl/angular:webpack-browser\",\"options\":{\"aot\":false}},\"server\":{},\"prerender\":{}}}}}",
                s -> s.path("angular.json").after(actual -> actual).afterRecipe(after -> {
                    assertTrue(after.printAll().contains("Custom builder detected"), after.printAll());
                    assertTrue(after.printAll().contains("AOT is disabled"), after.printAll());
                    assertTrue(after.printAll().contains("SSR/prerender compilation"), after.printAll());
                })));
    }

    @Test
    void supportedToolchainAndUnrelatedPackagesAreNoOps() {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerJsonRisks()),
                json("{\"engines\":{\"node\":\"^20.19.0 || ^22.12.0 || >=24.0.0\"},\"dependencies\":{\"@angular/compiler\":\"^12.2.17\",\"@angular/core\":\"20.3.26\",\"tslib\":\"^2.8.0\"},\"devDependencies\":{\"@angular/compiler-cli\":\"20.3.26\",\"typescript\":\"5.9.2\"}}", s -> s.path("package.json")),
                json("{\"engines\":{\"node\":\"18.x\"},\"devDependencies\":{\"typescript\":\"4.8.4\"}}", s -> s.path("services/api/package.json")));
    }

    @Test
    void metadataLookalikeDoesNotActivatePackageAudit() {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerJsonRisks()),
                json("{\"metadata\":{\"@angular/compiler\":\"12.2.17\"},\"dependencies\":{\"@angular/core\":\"12.2.17\",\"typescript\":\"4.8.4\"}}",
                        s -> s.path("package.json")));
    }

    @Test
    void marksConflictingCompilationModeTypoInsteadOfCreatingDuplicateKey() {
        rewriteRun(spec -> spec.recipes(new MigrateDeterministicAngularCompilerJson(), new FindAngularCompilerJsonRisks()),
                json("{\"angularCompilerOptions\":{\"compliationMode\":\"full\",\"compilationMode\":\"partial\"}}",
                        s -> s.path("tsconfig.lib.json").after(actual -> actual).afterRecipe(after ->
                                assertTrue(after.printAll().contains("Misspelled compliationMode remains"), after.printAll()))));
    }

    @Test
    void recommendedRecipeCombinesDependencyAutoAndMarker() {
        Environment environment = Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angular")
                .scanYamlResources().build();
        rewriteRun(spec -> spec.recipe(environment.activateRecipes("com.huawei.clouds.openrewrite.angular.MigrateAngularCompilerTo20_3_26")),
                json("{\"scripts\":{\"postinstall\":\"ngcc\"},\"dependencies\":{\"@angular/compiler\":\"^12.2.17\"}}",
                        "{\"scripts\":{},\"dependencies\":{\"@angular/compiler\":\"20.3.26\"}}", s -> s.path("package.json")),
                typescript("import { NgModule } from '@angular/core';\n@NgModule({entryComponents: [], declarations: [C]}) class M {}\n",
                        s -> s.path("src/m.ts").after(actual -> actual).afterRecipe(after -> {
                            assertFalse(after.printAll().contains("entryComponents"), after.printAll());
                            assertTrue(after.printAll().contains("standalone by default"), after.printAll());
                        })));
    }

    private void assertTs(String before, String path, String... messages) {
        rewriteRun(spec -> spec.recipe(new FindAngularCompilerTypeScriptRisks()),
                typescript(before, s -> s.path(path).after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    for (String message : messages) assertTrue(out.contains(message), out);
                })));
    }
}
