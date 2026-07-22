package com.huawei.clouds.openrewrite.angular;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class AngularCompilerMigrationTest implements RewriteTest {
    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void removesEnableIvyTrueFromPinnedNgZorroFixture() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerJson()), json(
                "{\"extends\":\"./tsconfig.lib.json\",\"angularCompilerOptions\":{\"enableIvy\":true,\"compilationMode\":\"partial\"}}",
                "{\"extends\":\"./tsconfig.lib.json\",\"angularCompilerOptions\":{\"compilationMode\":\"partial\"}}",
                s -> s.path("NG-ZORRO/ng-zorro-antd/components/tsconfig.lib.prod.json")));
    }

    @Test
    void fixesPinnedCarbonCompilationModeTypo() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerJson()), json(
                "{\"angularCompilerOptions\":{\"enableIvy\":true,\"compliationMode\":\"partial\"}}",
                "{\"angularCompilerOptions\":{\"compilationMode\":\"partial\"}}",
                s -> s.path("carbon-design-system/carbon-components-angular/src/tsconfig.lib.json")));
    }

    @Test
    void removesPinnedNgccOnlyPostinstallScript() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerJson()), json(
                "{\"scripts\":{\"build\":\"ng build\",\"postinstall\":\"ngcc\"},\"devDependencies\":{\"@angular/compiler\":\"9.0.0\"}}",
                "{\"scripts\":{\"build\":\"ng build\"},\"devDependencies\":{\"@angular/compiler\":\"9.0.0\"}}",
                s -> s.path("thelgevold/angular-samples/package.json")));
    }

    @Test
    void removesNpxNgccOnlyCommandWithArguments() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerJson()), json(
                "{\"scripts\":{\"postinstall\":\"npx ngcc --properties es2015 browser module main\"}}",
                "{\"scripts\":{}}", s -> s.path("package.json")));
    }

    @Test
    void preservesIvyFalseChainedNgccAndSameNamedForeignConfig() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerJson()),
                json("{\"angularCompilerOptions\":{\"enableIvy\":false}}", s -> s.path("tsconfig.app.json")),
                json("{\"scripts\":{\"postinstall\":\"ngcc && npm run build\"}}", s -> s.path("package.json")),
                json("{\"angularCompilerOptions\":{\"enableIvy\":true}}", s -> s.path("settings.json")),
                json("{\"other\":{\"enableIvy\":true,\"compliationMode\":\"partial\"}}", s -> s.path("tsconfig.app.json")));
    }

    @Test
    void doesNotCreateDuplicateCompilationModeKey() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerJson()),
                json("{\"angularCompilerOptions\":{\"compliationMode\":\"full\",\"compilationMode\":\"partial\"}}",
                        s -> s.path("tsconfig.lib.json")));
    }

    @Test
    void removesEntryComponentsFromPinnedRealNgModuleFixture() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerSource()), typescript(
                "import { NgModule } from '@angular/core';\n@NgModule({declarations: [AppComponent], entryComponents: [], imports: [BrowserModule]}) export class AppModule {}\n",
                "import { NgModule } from '@angular/core';\n@NgModule({declarations: [AppComponent], imports: [BrowserModule]}) export class AppModule {}\n",
                s -> s.path("veerajongit/animator/src/app/app.module.ts")));
    }

    @Test
    void removesNonEmptyEntryComponentsWithAliasedDecorator() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerSource()), typescript(
                "import { NgModule as Module } from '@angular/core';\n@Module({entryComponents: [NzbDynamicContentComponent], declarations: [NzbDynamicContentComponent]}) class NzbModule {}\n",
                "import { NgModule as Module } from '@angular/core';\n@Module({ declarations: [NzbDynamicContentComponent]}) class NzbModule {}\n",
                s -> s.path("nowzoo/nowzoo-angular-bootstrap-lite/src/nzb.module.ts")));
    }

    @Test
    void preservesSamePropertyOutsideAttributedNgModule() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerSource()),
                typescript("const metadata = {entryComponents: [Dynamic]};\n", s -> s.path("src/metadata.ts")),
                typescript("import { NgModule } from 'other-core';\n@NgModule({entryComponents: [Dynamic]}) class Module {}\n", s -> s.path("src/foreign.ts")),
                typescript("import { Component, NgModule } from '@angular/core';\n@Component({entryComponents: [Dynamic]}) class C {}\n", s -> s.path("src/component.ts")),
                typescript("import { NgModule } from '@angular/core';\n@NgModule({providers: [{entryComponents: [Dynamic]}]}) class M {}\n", s -> s.path("src/nested.ts")));
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerSource()).cycles(2).expectedCyclesThatMakeChanges(1),
                typescript("import { NgModule } from '@angular/core';\n@NgModule({entryComponents: [], declarations: [C]}) class M {}\n",
                        "import { NgModule } from '@angular/core';\n@NgModule({ declarations: [C]}) class M {}\n", s -> s.path("src/m.ts")));
    }

    @Test
    void qualifiesAngular20ReservedVoidAndInProperties() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerTemplate()), text(
                "<p>{{void}}</p><p>{{ in.value | json }}</p><button [title]=\"void.label\" (click)=\"in()\"></button>",
                "<p>{{this.void}}</p><p>{{ this.in.value | json }}</p><button [title]=\"this.void.label\" (click)=\"this.in()\"></button>",
                s -> s.path("src/app.html")));
    }

    @Test
    void reservedWordMigrationPreservesOperatorsTextAndLongerIdentifiers() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerTemplate()), text(
                "<p title=\"void\">void in {{invoice}} {{key in object}} {{voided}}</p><p>{{this.void}}</p>",
                s -> s.path("src/app.html")));
    }

    @Test
    void reservedWordMigrationIgnoresHtmlCommentsAndRawScriptStyleContent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerTemplate()), text(
                "<!-- {{ in }} --><script>const sample = '{{ void }}';</script><style>.x::after { content: '{{ in }}'; }</style><p>{{ void }}</p>",
                "<!-- {{ in }} --><script>const sample = '{{ void }}';</script><style>.x::after { content: '{{ in }}'; }</style><p>{{ this.void }}</p>",
                s -> s.path("src/app.html")));
    }

    @Test
    void reservedWordTemplateMigrationIsIdempotentAndHtmlOnly() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicAngularCompilerTemplate()).cycles(2).expectedCyclesThatMakeChanges(1),
                text("{{ void }}", "{{ this.void }}", s -> s.path("src/app.html")),
                text("const template = '{{ void }}';", s -> s.path("src/app.ts")));
    }
}
