package com.huawei.clouds.openrewrite.angularelements;

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

class AngularElementsDependencyTest implements RewriteTest {
    static final String STRICT =
            "com.huawei.clouds.openrewrite.angularelements.UpgradeAngularElementsTo20_3_25";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(STRICT));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("selectedDeclarations")
    void upgradesEveryWorkbookSourceAndScalarOperator(String declaration, String expected) {
        rewriteRun(json("{\"dependencies\":{\"@angular/elements\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"@angular/elements\":\"" + expected + "\"}}",
                source -> source.path("package.json")));
    }

    static Stream<Arguments> selectedDeclarations() {
        return AngularElementsSupport.SOURCE_VERSIONS.stream().sorted().flatMap(version ->
                Stream.of("", "^", "~").map(operator ->
                        Arguments.of(operator + version, operator + AngularElementsSupport.TARGET_VERSION)));
    }

    @Test
    void upgradesAllDirectDependencySections() {
        rewriteRun(json(
                "{\"dependencies\":{\"@angular/elements\":\"12.2.13\"},\"devDependencies\":{\"@angular/elements\":\"^12.2.16\"},\"peerDependencies\":{\"@angular/elements\":\"~13.1.3\"},\"optionalDependencies\":{\"@angular/elements\":\"13.3.12\"}}",
                "{\"dependencies\":{\"@angular/elements\":\"20.3.25\"},\"devDependencies\":{\"@angular/elements\":\"^20.3.25\"},\"peerDependencies\":{\"@angular/elements\":\"~20.3.25\"},\"optionalDependencies\":{\"@angular/elements\":\"20.3.25\"}}",
                source -> source.path("package.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=12.2.13", ">=12.2.13 <21", "12.2.13 || 20.3.25", "12.2.13 - 20.3.25",
            "12.x", "12.2.x", "*", "^12.2.13 || ^13.1.3", "~12.2.13 >=12.2.0"
    })
    void protectsComplexRanges(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "workspace:12.2.13", "workspace:^12.2.13", "npm:@company/elements@12.2.13",
            "github:angular/angular#12.2.13", "git+https://github.com/angular/angular.git#12.2.13",
            "file:../elements", "link:../elements", "https://example.test/elements.tgz", "latest", "$elements"
    })
    void protectsProtocolsAliasesTagsAndVariables(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12.2.12", "12.2.14", "13.0.0", "14.2.11", "15.2.8", "16.0.0", "19.2.15",
            "20.3.24", "20.3.25", "^20.3.25", "~20.3.25", "20.3.26", "21.0.0",
            "v12.2.13", "=12.2.13", " 12.2.13", "12.2.13 ", "12.2.13-next.0", "12.2.13+vendor"
    })
    void protectsUnlistedTargetAndDecoratedVersions(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void protectsCentralOwnersNestedObjectsNonStringsAndSimilarPackages() {
        rewriteRun(
                json("{\"overrides\":{\"@angular/elements\":\"12.2.13\"},\"resolutions\":{\"@angular/elements\":\"12.2.13\"},\"catalogs\":{\"web\":{\"@angular/elements\":\"12.2.13\"}}}",
                        source -> source.path("package.json")),
                json("{\"tool\":{\"dependencies\":{\"@angular/elements\":\"12.2.13\"}},\"dependencies\":{\"@angular/elements\":false}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"angular-elements\":\"12.2.13\",\"@angular/elements-extra\":\"12.2.13\"}}",
                        source -> source.path("package.json")));
    }

    @Test
    void protectsLockfilesOrdinaryJsonAndExcludedParents() {
        rewriteRun(
                json("{\"packages\":{\"\":{\"dependencies\":{\"@angular/elements\":\"12.2.13\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"@angular/elements\":\"12.2.13\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                json("{\"dependencies\":{\"@angular/elements\":\"12.2.13\"}}",
                        source -> source.path("generated-client/package.json")),
                json("{\"dependencies\":{\"@angular/elements\":\"12.2.13\"}}",
                        source -> source.path("install-cache/package.json")));
    }

    @Test
    void workspaceManifestsUpgradeIndependentlyAndAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"@angular/elements\":\"^14.2.12\"}}",
                        "{\"dependencies\":{\"@angular/elements\":\"^20.3.25\"}}",
                        source -> source.path("apps/widget/package.json")),
                json("{\"devDependencies\":{\"@angular/elements\":\"~15.2.9\"}}",
                        "{\"devDependencies\":{\"@angular/elements\":\"~20.3.25\"}}",
                        source -> source.path("packages/elements/package.json")));
    }

    @Test
    void strictRecipeIsDiscoverableAndValid() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(STRICT);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> STRICT.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"@angular/elements\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.angularelements")
                .scanYamlResources().build();
    }
}
