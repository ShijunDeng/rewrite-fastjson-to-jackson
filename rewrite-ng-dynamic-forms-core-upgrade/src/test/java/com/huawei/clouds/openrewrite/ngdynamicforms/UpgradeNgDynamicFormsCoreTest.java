package com.huawei.clouds.openrewrite.ngdynamicforms;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.javascript.rpc.JavaScriptRewriteRpc;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.typescript;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UpgradeNgDynamicFormsCoreTest implements RewriteTest {
    private static final String STRICT="com.huawei.clouds.openrewrite.ngdynamicforms.UpgradeNgDynamicFormsCoreTo18_0_0";
    private static final String RECOMMENDED="com.huawei.clouds.openrewrite.ngdynamicforms.MigrateNgDynamicFormsCoreTo18_0_0";

    @AfterAll
    static void stopRpc() {
        JavaScriptRewriteRpc.shutdownCurrent();
    }

    @Test
    void yamlRecipesAreDiscoverableWithExpectedComposition() {
        Environment environment=environment();
        assertEquals(1,environment.activateRecipes(STRICT).getRecipeList().size());
        assertEquals(5,environment.activateRecipes(RECOMMENDED).getRecipeList().size());
    }

    @Test
    void strictRecipeChangesOnlyTheDependency() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(STRICT)),
                json("{\"dependencies\":{\"@angular/core\":\"13.3.10\",\"@ng-dynamic-forms/core\":\"^15.0.0\",\"@ng-dynamic-forms/ui-basic\":\"15.0.0\"}}",
                        "{\"dependencies\":{\"@angular/core\":\"13.3.10\",\"@ng-dynamic-forms/core\":\"18.0.0\",\"@ng-dynamic-forms/ui-basic\":\"15.0.0\"}}",s->s.path("package.json")),
                typescript("import {DynamicFormsCoreModule} from '@ng-dynamic-forms/core';const x=DynamicFormsCoreModule.forRoot();",s->s.path("src/app.ts")),
                text("<dynamic-basic-form [group]=\"group\" [model]=\"model\"></dynamic-basic-form>",s->s.path("src/app.html")));
    }

    @Test
    void recommendedRecipeCombinesAutoAndExactMarkers() {
        rewriteRun(spec -> spec.recipe(environment().activateRecipes(RECOMMENDED)),
                json("{\"dependencies\":{\"@angular/core\":\"13.3.10\",\"@angular/forms\":\"13.3.10\",\"@ng-dynamic-forms/core\":\"^15.0.0\",\"rxjs\":\"6.6.7\"}}",
                        s->s.path("package.json").after(actual->actual).afterRecipe(after->{String out=after.printAll();assertTrue(out.contains("\"@ng-dynamic-forms/core\":\"18.0.0\""),out);assertTrue(out.contains("aligned to Angular 16"),out);assertTrue(out.contains("peers on RxJS"),out);})),
                typescript("import {DynamicFormsCoreModule,DynamicInputModel} from '@ng-dynamic-forms/core';const imports=[DynamicFormsCoreModule.forRoot()];const model=new DynamicInputModel({id:'x'});",
                        s->s.path("src/app.ts").after(actual->actual).afterRecipe(after->{String out=after.printAll();assertTrue(out.contains("[DynamicFormsCoreModule]"),out);assertTrue(out.contains("DynamicInputModel configuration crosses"),out);})),
                text("<dynamic-basic-form [group]=\"group\" [model]=\"model\"></dynamic-basic-form>",s->s.path("src/app.html").after(actual->actual).afterRecipe(after->assertTrue(after.printAll().contains("standalone component"),after.printAll()))));
    }

    @Test
    void recommendedRecipeIsIdempotent() {
        rewriteRun(spec->spec.recipe(environment().activateRecipes(RECOMMENDED)).cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@ng-dynamic-forms/core\":\"17.0.0\"}}","{\"dependencies\":{\"@ng-dynamic-forms/core\":\"18.0.0\"}}",s->s.path("package.json")),
                typescript("import {DynamicFormsCoreModule} from '@ng-dynamic-forms/core';const x=DynamicFormsCoreModule.forRoot();",s->s.path("src/app.ts").after(actual->actual)));
    }

    private static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.ngdynamicforms").scanYamlResources().build();
    }
}
