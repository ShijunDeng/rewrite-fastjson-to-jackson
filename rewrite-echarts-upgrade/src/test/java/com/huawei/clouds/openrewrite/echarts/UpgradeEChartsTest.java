package com.huawei.clouds.openrewrite.echarts;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;

class UpgradeEChartsTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.echarts.UpgradeEChartsTo6_1_0";

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
                          "name": "echarts-app",
                          "dependencies": {
                            "echarts": "^4.8.0"
                          },
                          "devDependencies": {
                            "echarts": "~4.9.0"
                          },
                          "peerDependencies": {
                            "echarts": ">=5.0.2 <6"
                          },
                          "optionalDependencies": {
                            "echarts": "5.4.1"
                          }
                        }
                        """,
                        """
                        {
                          "name": "echarts-app",
                          "dependencies": {
                            "echarts": "6.1.0"
                          },
                          "devDependencies": {
                            "echarts": "6.1.0"
                          },
                          "peerDependencies": {
                            "echarts": "6.1.0"
                          },
                          "optionalDependencies": {
                            "echarts": "6.1.0"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
                )
        );
    }

    @Test
    void doesNotModifyOtherJsonFilesOrDependencies() {
        rewriteRun(
                json(
                        """
                        {
                          "dependencies": {
                            "echarts": "4.9.0",
                            "ngx-echarts": "14.0.0"
                          }
                        }
                        """,
                        spec -> spec.path("config/dependencies.json")
                ),
                json(
                        """
                        {
                          "dependencies": {
                            "ngx-echarts": "14.0.0"
                          }
                        }
                        """,
                        spec -> spec.path("package.json")
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
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.echarts")
                .scanYamlResources()
                .build();
    }
}
