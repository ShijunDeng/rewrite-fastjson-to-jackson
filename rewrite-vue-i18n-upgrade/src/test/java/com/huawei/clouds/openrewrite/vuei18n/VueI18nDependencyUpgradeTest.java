package com.huawei.clouds.openrewrite.vuei18n;

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

class VueI18nDependencyUpgradeTest implements RewriteTest {
    private static final String STRICT =
            "com.huawei.clouds.openrewrite.vuei18n.UpgradeVueI18nTo11_3_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(STRICT));
    }

    @ParameterizedTest(name = "upgrades XLSX declaration {0}")
    @MethodSource("selectedDeclarations")
    void upgradesEveryVisibleVersionWithOnlyExactCaretAndTilde(String declaration) {
        rewriteRun(json(
                "{\"dependencies\":{\"vue-i18n\":\"" + declaration + "\"}}",
                "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                source -> source.path("package.json")
        ));
    }

    @Test
    void upgradesAllFourDirectSectionsAndWorkspaceManifests() {
        rewriteRun(
                json("{\"dependencies\":{\"vue-i18n\":\"7.3.2\"},\"devDependencies\":{\"vue-i18n\":\"^8.11.2\"},\"peerDependencies\":{\"vue-i18n\":\"~8.24.4\"},\"optionalDependencies\":{\"vue-i18n\":\"8.27.1\"}}",
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"},\"devDependencies\":{\"vue-i18n\":\"11.3.0\"},\"peerDependencies\":{\"vue-i18n\":\"11.3.0\"},\"optionalDependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                        source -> source.path("package.json")),
                json("{\"dependencies\":{\"vue-i18n\":\"^8.26.7\"}}",
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                        source -> source.path("packages/ui/package.json"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ">=8.11.2 <9", "8.11.2 || 8.24.4", "8.20.0 - 8.27.1", "8.x", "8.*", "*",
            "workspace:^8.27.1", "npm:vue-i18n@8.24.4", "file:../vue-i18n", "link:../vue-i18n",
            "git+ssh://git@github.com/intlify/vue-i18n.git#v8.27.1", "github:intlify/vue-i18n#v8.27.1",
            "https://example.test/vue-i18n.tgz", "latest", "next", "$i18nVersion", "v8.27.1",
            "=8.24.4", " 8.11.2", "8.11.2 ", "8.27.1-beta.1", "8.27.1+company.1", ""
    })
    void leavesComplexProtocolDecoratedAndDynamicDeclarationsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "6.1.1", "7.3.1", "7.3.3", "8.11.1", "8.11.3", "8.27.0", "8.27.2",
            "9.0.0", "9.14.5", "10.0.8", "11.0.0", "11.2.8", "11.3.0", "^11.3.0",
            "~11.3.0", "11.3.1", "11.4.0", "12.0.0"
    })
    void leavesUnlistedTargetAndNewerVersionsUntouched(String declaration) {
        assertNoOp(declaration);
    }

    @Test
    void leavesNestedOwnersLocksOtherJsonAndSimilarPackagesUntouched() {
        rewriteRun(
                json("{\"overrides\":{\"vue-i18n\":\"8.27.1\"},\"resolutions\":{\"vue-i18n\":\"8.24.4\"},\"catalog\":{\"vue-i18n\":\"8.11.2\"}}",
                        source -> source.path("package.json")),
                json("{\"packages\":{\"\":{\"dependencies\":{\"vue-i18n\":\"8.27.1\"}}}}",
                        source -> source.path("package-lock.json")),
                json("{\"dependencies\":{\"vue-i18n\":\"8.27.1\"}}",
                        source -> source.path("fixtures/dependencies.json")),
                json("{\"dependencies\":{\"vue-i18n-bridge\":\"8.27.1\",\"petite-vue-i18n\":\"8.27.1\",\"Vue-I18n\":\"8.27.1\"}}",
                        source -> source.path("package.json"))
        );
    }

    @Test
    void strictRecipeIsIdempotentDiscoverableAndValid() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"vue-i18n\":\"~8.25.0\"}}",
                        "{\"dependencies\":{\"vue-i18n\":\"11.3.0\"}}",
                        source -> source.path("package.json"))
        );
        Recipe recipe = environment().activateRecipes(STRICT);
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        assertTrue(environment().listRecipes().stream().anyMatch(candidate -> STRICT.equals(candidate.getName())));
    }

    private void assertNoOp(String declaration) {
        rewriteRun(json("{\"dependencies\":{\"vue-i18n\":\"" + declaration + "\"}}",
                source -> source.path("package.json")));
    }

    private static Stream<Arguments> selectedDeclarations() {
        return Stream.of(
                        "7.3.2", "8.11.2", "8.20.0", "8.22.1", "8.22.4",
                        "8.24.3", "8.24.4", "8.25.0", "8.26.7", "8.27.1")
                .flatMap(version -> Stream.of(version, "^" + version, "~" + version))
                .map(Arguments::of);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.vuei18n")
                .scanYamlResources()
                .build();
    }
}
