package com.huawei.clouds.openrewrite.fastjson2;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeDiscoveryTest {
    @Test
    void discoversAndValidatesPublicRecipe() {
        Environment environment = Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.fastjson2")
                .scanYamlResources()
                .build();

        Recipe recipe = environment.activateRecipes(MigrateFastjson2ToJackson.class.getName());

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> MigrateFastjson2ToJackson.class.getName().equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }
}
