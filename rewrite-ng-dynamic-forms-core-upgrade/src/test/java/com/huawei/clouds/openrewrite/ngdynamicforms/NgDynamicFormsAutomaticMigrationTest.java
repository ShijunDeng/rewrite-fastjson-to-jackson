package com.huawei.clouds.openrewrite.ngdynamicforms;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.javascript.Assertions.typescript;

class NgDynamicFormsAutomaticMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDeterministicNgDynamicFormsSource());
    }

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void removesOfficialNoArgumentForRootWrapperFromNgModuleImports() {
        rewriteRun(typescript(
                "import { DynamicFormsCoreModule } from '@ng-dynamic-forms/core';\n@NgModule({imports:[DynamicFormsCoreModule.forRoot()]}) export class AppModule {}\n",
                "import { DynamicFormsCoreModule } from '@ng-dynamic-forms/core';\n@NgModule({imports:[DynamicFormsCoreModule]}) export class AppModule {}\n",
                s -> s.path("src/app/app.module.ts")));
    }

    @Test
    void supportsAliasedCoreModuleAndImportProvidersFrom() {
        rewriteRun(typescript(
                "import { DynamicFormsCoreModule as DynamicCore } from \"@ng-dynamic-forms/core\";\nexport const config={providers:[importProvidersFrom(DynamicCore.forRoot())]};\n",
                "import { DynamicFormsCoreModule as DynamicCore } from \"@ng-dynamic-forms/core\";\nexport const config={providers:[importProvidersFrom(DynamicCore)]};\n",
                s -> s.path("src/app/app.config.ts")));
    }

    @Test
    void leavesForeignSameNameAndNonstandardArgumentsAlone() {
        rewriteRun(
                typescript("import { DynamicFormsCoreModule } from '@company/forms';\nconst x=DynamicFormsCoreModule.forRoot();\n", s -> s.path("src/foreign.ts")),
                typescript("import { DynamicFormsCoreModule } from '@ng-dynamic-forms/core';\nconst x=DynamicFormsCoreModule.forRoot({providers:[custom]});\n", s -> s.path("src/custom.ts")),
                typescript("import { DynamicFormsCoreModule } from '@ng-dynamic-forms/core';\nfunction configure(DynamicFormsCoreModule:any){return DynamicFormsCoreModule.forRoot();}\n", s -> s.path("src/shadowed.ts")),
                typescript("class DynamicFormsCoreModule { static forRoot(){} }\nconst x=DynamicFormsCoreModule.forRoot();\n", s -> s.path("src/local.ts"))
        );
    }

    @Test
    void deterministicSourceMigrationIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), typescript(
                "import { DynamicFormsCoreModule } from '@ng-dynamic-forms/core';\nconst modules=[DynamicFormsCoreModule.forRoot()];\n",
                "import { DynamicFormsCoreModule } from '@ng-dynamic-forms/core';\nconst modules=[DynamicFormsCoreModule];\n",
                s -> s.path("src/core.ts")));
    }
}
