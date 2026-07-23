package com.huawei.clouds.openrewrite.springwebmvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringWebMvcOfficialRecipeReuseTest {
    private static final String AUTO =
            "com.huawei.clouds.openrewrite.springwebmvc.MigrateDeterministicSpringWebMvc6Java";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springwebmvc",
                                  "org.openrewrite.java.spring.framework")
            .build();

    @Test
    void deterministicCompositionUsesOnlyAuditedOfficialSpringComponentsAndOneLocalGap() {
        DeclarativeRecipe recipe = assertInstanceOf(
                DeclarativeRecipe.class, ENVIRONMENT.activateRecipes(AUTO));
        List<Recipe> composition = composition(recipe);

        assertEquals(List.of(
                        "org.openrewrite.java.ChangePackage",
                        "org.openrewrite.java.spring.framework.MigrateWebMvcConfigurerAdapter",
                        MigrateHandlerInterceptorAdapterPreservingAsyncContract.class.getName(),
                        "org.openrewrite.java.spring.framework.MigrateResponseEntityExceptionHandlerHttpStatusToHttpStatusCode",
                        "org.openrewrite.java.spring.framework.MigrateResponseStatusException"),
                composition.stream().map(Recipe::getName).toList());
        assertEquals(List.of(IsSpringWebMvcNonGeneratedSource.class),
                recipe.getPreconditions().stream().map(Object::getClass).toList());

        ChangePackage servlet = assertInstanceOf(ChangePackage.class, composition.get(0));
        assertEquals("javax.servlet", servlet.getOldPackageName());
        assertEquals("jakarta.servlet", servlet.getNewPackageName());
        assertEquals(Boolean.TRUE, servlet.getRecursive());
    }

    @Test
    void runtimeTreeContainsPreciseNestedStatusRecipesAndRejectsBroadOrUnsafeRecipes() {
        Recipe recipe = ENVIRONMENT.activateRecipes(AUTO);
        Set<String> activated = flatten(recipe).map(Recipe::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(activated.contains(
                "org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetRawStatusCodeMethod"));
        assertTrue(activated.contains(
                "org.openrewrite.java.spring.framework.MigrateResponseStatusExceptionGetStatusCodeMethod"));

        Set<String> rejected = Set.of(
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_5_3",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_0",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_1",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_2",
                "org.openrewrite.java.migrate.jakarta.JakartaEE10",
                "org.openrewrite.java.spring.framework.MigrateHandlerInterceptor",
                "org.openrewrite.java.spring.framework.MigrateSpringAssert",
                "org.openrewrite.java.spring.framework.MigrateClientHttpResponseGetRawStatusCodeMethod",
                "org.openrewrite.java.spring.framework.MigrateMethodArgumentNotValidExceptionErrorMethod",
                "org.openrewrite.java.spring.boot3.MaintainTrailingSlashURLMappings");
        assertTrue(java.util.Collections.disjoint(activated, rejected));
        assertFalse(flatten(recipe).anyMatch(UpgradeDependencyVersion.class::isInstance));
    }

    private static List<Recipe> composition(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .filter(SpringWebMvcOfficialRecipeReuseTest::isCompositionRecipe)
                .map(SpringWebMvcOfficialRecipeReuseTest::unwrap)
                .toList();
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = unwrap(recipe);
        return Stream.concat(Stream.of(unwrapped), unwrapped.getRecipeList().stream()
                .filter(SpringWebMvcOfficialRecipeReuseTest::isCompositionRecipe)
                .flatMap(SpringWebMvcOfficialRecipeReuseTest::flatten));
    }

    private static boolean isCompositionRecipe(Recipe recipe) {
        return !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether".equals(recipe.getName());
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegate) {
            current = delegate.getDelegate();
        }
        return current;
    }
}
