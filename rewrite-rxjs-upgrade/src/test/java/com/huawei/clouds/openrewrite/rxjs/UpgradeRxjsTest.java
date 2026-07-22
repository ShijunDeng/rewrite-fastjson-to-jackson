package com.huawei.clouds.openrewrite.rxjs;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeRxjsTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.rxjs.UpgradeRxjsTo7_8_2";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @Test
    void upgradesEveryDirectDependencySectionInPackageJson() {
        rewriteRun(
                json(
                        """
                        {
                          "name": "rxjs-app",
                          "dependencies": {
                            "rxjs": "^6.5.5"
                          },
                          "devDependencies": {
                            "rxjs": "~6.6.7"
                          },
                          "peerDependencies": {
                            "rxjs": ">=6.5.5 <7"
                          },
                          "optionalDependencies": {
                            "rxjs": "6.6.7"
                          }
                        }
                        """,
                        """
                        {
                          "name": "rxjs-app",
                          "dependencies": {
                            "rxjs": "7.8.2"
                          },
                          "devDependencies": {
                            "rxjs": "7.8.2"
                          },
                          "peerDependencies": {
                            "rxjs": "7.8.2"
                          },
                          "optionalDependencies": {
                            "rxjs": "7.8.2"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void doesNotModifyOtherJsonFiles() {
        rewriteRun(
                json(
                        """
                        {
                          "dependencies": {
                            "rxjs": "6.6.7"
                          }
                        }
                        """,
                        spec -> spec.path("config/dependencies.json")
                )
        );
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.rxjs")
                .scanYamlResources()
                .build();
    }
}
