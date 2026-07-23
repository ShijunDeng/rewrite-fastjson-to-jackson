package com.huawei.clouds.openrewrite.nettycodechttp;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettyCodecHttpOfficialRecipeReuseTest {
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.nettycodechttp.MigrateNettyCodecHttpTo4_1_136";
    private static final String OFFICIAL_42 = "org.openrewrite.netty.UpgradeNetty_4_1_to_4_2";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath("com.huawei.clouds.openrewrite.nettycodechttp",
                                  "org.openrewrite.netty",
                                  "org.openrewrite.java.netty")
            .build();

    @Test
    void officialBundleHasNoHttpDecoderConfigMigration() {
        List<String> officialRecipes = ENVIRONMENT.listRecipes().stream()
                .map(Recipe::getName)
                .filter(name -> name.startsWith("org.openrewrite.netty.") ||
                                name.startsWith("org.openrewrite.java.netty."))
                .sorted()
                .toList();

        assertTrue(officialRecipes.contains("org.openrewrite.netty.UpgradeNetty_3_2_to_4_1"));
        assertTrue(officialRecipes.contains(OFFICIAL_42));
        assertFalse(officialRecipes.stream().anyMatch(name -> name.contains("HttpDecoderConfig")),
                () -> "Unexpected official decoder migration: " + officialRecipes);
    }

    @Test
    void official42AggregateIsBroadAndSemanticallyOutsideTheTargetBranch() {
        Recipe official = ENVIRONMENT.activateRecipes(OFFICIAL_42);
        Set<String> officialTree = flatten(official)
                .map(Recipe::getName)
                .collect(Collectors.toSet());

        assertTrue(officialTree.contains("org.openrewrite.java.dependencies.UpgradeDependencyVersion"));
        assertTrue(officialTree.contains("org.openrewrite.java.dependencies.ChangeDependency"));
        assertTrue(officialTree.contains("org.openrewrite.java.ChangeType"));
        assertTrue(officialTree.contains("org.openrewrite.java.ChangePackage"));
        assertTrue(officialTree.contains(
                "org.openrewrite.java.netty.EventLoopGroupToMultiThreadIoEventLoopGroupRecipes"));

        UpgradeDependencyVersion allNetty = assertInstanceOf(UpgradeDependencyVersion.class,
                flatten(official).filter(UpgradeDependencyVersion.class::isInstance)
                        .findFirst().orElseThrow());
        assertEquals("io.netty", allNetty.getGroupId());
        assertEquals("*", allNetty.getArtifactId());
        assertEquals("4.2.x", allNetty.getNewVersion());
    }

    @Test
    void runtimeTreeKeepsOnlyStrictLocal41MigrationAndRiskRecipes() {
        Recipe recommended = ENVIRONMENT.activateRecipes(RECOMMENDED);
        assertEquals(List.of(
                        "com.huawei.clouds.openrewrite.nettycodechttp.FindNettyCodecHttp41136BuildRisks",
                        "com.huawei.clouds.openrewrite.nettycodechttp.UpgradeNettyCodecHttpTo4_1_136",
                        MigrateValidatedHttpDecoderConstructors.class.getName(),
                        "com.huawei.clouds.openrewrite.nettycodechttp.FindNettyCodecHttp41136SourceRisks"),
                recommended.getRecipeList().stream().map(Recipe::getName).toList());

        Set<String> activated = flatten(recommended).map(Recipe::getName).collect(Collectors.toSet());
        assertTrue(activated.contains(UpgradeSelectedNettyCodecHttpDependency.class.getName()));
        assertFalse(activated.contains(OFFICIAL_42));
        assertFalse(activated.contains("org.openrewrite.java.dependencies.UpgradeDependencyVersion"));
        assertFalse(activated.contains("org.openrewrite.java.dependencies.ChangeDependency"));
        assertFalse(activated.contains(
                "org.openrewrite.java.netty.EventLoopGroupToMultiThreadIoEventLoopGroupRecipes"));
        assertFalse(flatten(recommended).anyMatch(UpgradeDependencyVersion.class::isInstance));
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = unwrap(recipe);
        return Stream.concat(Stream.of(unwrapped), unwrapped.getRecipeList().stream()
                .filter(NettyCodecHttpOfficialRecipeReuseTest::isCompositionRecipe)
                .flatMap(NettyCodecHttpOfficialRecipeReuseTest::flatten));
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
