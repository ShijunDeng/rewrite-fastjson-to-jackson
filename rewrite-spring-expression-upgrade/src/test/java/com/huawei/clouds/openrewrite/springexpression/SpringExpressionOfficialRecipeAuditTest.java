package com.huawei.clouds.openrewrite.springexpression;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.gradle.UpdateJavaCompatibility;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.maven.AddProperty;
import org.openrewrite.maven.UpdateMavenProjectPropertyJavaVersion;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringExpressionOfficialRecipeAuditTest {
    private static final String CONFIGURE =
            "com.huawei.clouds.openrewrite.springexpression.ConfigureSpringExpression6Build";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.springexpression.MigrateSpringExpressionTo6_2_19";
    private static final String OFFICIAL_62 =
            "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_2";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath("com.huawei.clouds.openrewrite.springexpression",
                                  "org.openrewrite.java.spring.framework")
            .build();

    @Test
    void buildCompositionDirectlyUsesFourAuditedCoreLeavesWithExactOptions() {
        DeclarativeRecipe configure = assertInstanceOf(
                DeclarativeRecipe.class, ENVIRONMENT.activateRecipes(CONFIGURE));
        assertEquals(List.of(FindSpringExpressionBuildOwner.class),
                configure.getPreconditions().stream().map(Object::getClass).toList());

        List<Recipe> leaves = effectiveChildren(configure);
        assertEquals(List.of(
                        "org.openrewrite.maven.UpdateMavenProjectPropertyJavaVersion",
                        "org.openrewrite.maven.AddProperty",
                        "org.openrewrite.gradle.UpdateJavaCompatibility",
                        "org.openrewrite.gradle.UpdateJavaCompatibility"),
                leaves.stream().map(Recipe::getName).toList());

        UpdateMavenProjectPropertyJavaVersion javaVersion = assertInstanceOf(
                UpdateMavenProjectPropertyJavaVersion.class, leaves.get(0));
        assertEquals(17, javaVersion.getVersion());

        AddProperty parameters = assertInstanceOf(AddProperty.class, leaves.get(1));
        assertEquals("maven.compiler.parameters", parameters.getKey());
        assertEquals("true", parameters.getValue());
        assertEquals(Boolean.TRUE, parameters.getPreserveExistingValue());
        assertEquals(Boolean.FALSE, parameters.getTrustParent());

        UpdateJavaCompatibility source = assertInstanceOf(
                UpdateJavaCompatibility.class, leaves.get(2));
        assertCompatibility(source, UpdateJavaCompatibility.CompatibilityType.source);
        UpdateJavaCompatibility target = assertInstanceOf(
                UpdateJavaCompatibility.class, leaves.get(3));
        assertCompatibility(target, UpdateJavaCompatibility.CompatibilityType.target);
    }

    @Test
    void officialSpring62AggregateProvesWhyItCannotBeReused() {
        Recipe official = ENVIRONMENT.activateRecipes(OFFICIAL_62);
        List<Recipe> direct = effectiveChildren(official);

        assertEquals("org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_1",
                direct.get(0).getName());
        UpgradeDependencyVersion broadSpringUpgrade = direct.stream()
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("org.springframework", broadSpringUpgrade.getGroupId());
        assertEquals("*", broadSpringUpgrade.getArtifactId());
        assertEquals("6.2.x", broadSpringUpgrade.getNewVersion());
        assertNull(broadSpringUpgrade.getOverrideManagedVersion());

        Set<String> officialTree = flatten(official).map(Recipe::getName)
                .collect(Collectors.toSet());
        assertTrue(officialTree.contains(
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_1"));
        assertTrue(officialTree.contains(
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_0"));
        assertTrue(officialTree.contains("org.openrewrite.java.migrate.jakarta.JakartaEE10"));
        assertTrue(officialTree.contains(
                "org.openrewrite.java.spring.kafka.UpgradeSpringKafka_3_0"));
        assertTrue(officialTree.contains(
                "org.openrewrite.java.spring.data.UpgradeSpringData_3_0"));
    }

    @Test
    void recommendedRuntimeTreeRejectsAggregatesSelectorsAndCrossCoordinateChanges() {
        Recipe recommended = ENVIRONMENT.activateRecipes(RECOMMENDED);
        Set<String> selectedTree = flatten(recommended).map(Recipe::getName)
                .collect(Collectors.toSet());

        Set<String> excluded = Set.of(
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_0",
                "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_1",
                OFFICIAL_62,
                "org.openrewrite.java.migrate.jakarta.JakartaEE10",
                "org.openrewrite.java.spring.kafka.UpgradeSpringKafka_3_0",
                "org.openrewrite.java.spring.data.UpgradeSpringData_3_0",
                "org.openrewrite.java.dependencies.UpgradeDependencyVersion");
        assertTrue(java.util.Collections.disjoint(selectedTree, excluded), selectedTree.toString());
        assertFalse(flatten(recommended).map(SpringExpressionOfficialRecipeAuditTest::unwrap)
                .anyMatch(UpgradeDependencyVersion.class::isInstance));
        assertTrue(selectedTree.contains(UpgradeSelectedSpringExpressionDependency.class.getName()));
        assertTrue(selectedTree.contains(
                "org.openrewrite.maven.UpdateMavenProjectPropertyJavaVersion"));
        assertTrue(selectedTree.contains("org.openrewrite.maven.AddProperty"));
        assertTrue(selectedTree.contains("org.openrewrite.gradle.UpdateJavaCompatibility"));
    }

    @Test
    void fixedOfficialSpringCatalogHasNoSpelSpecificMigrationLeaf() {
        List<String> candidates = ENVIRONMENT.listRecipes().stream()
                .map(Recipe::getName)
                .filter(name -> name.startsWith("org.openrewrite.java.spring."))
                .filter(name -> {
                    String lower = name.toLowerCase(java.util.Locale.ROOT);
                    return lower.contains("spel") || lower.contains("expression");
                })
                .sorted()
                .toList();
        assertTrue(candidates.isEmpty(), candidates.toString());
    }

    private static void assertCompatibility(
            UpdateJavaCompatibility recipe,
            UpdateJavaCompatibility.CompatibilityType compatibilityType) {
        assertEquals(17, recipe.getVersion());
        assertEquals(compatibilityType, recipe.getCompatibilityType());
        assertNull(recipe.getDeclarationStyle());
        assertEquals(Boolean.FALSE, recipe.getAllowDowngrade());
        assertEquals(Boolean.FALSE, recipe.getAddIfMissing());
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .map(SpringExpressionOfficialRecipeAuditTest::unwrap)
                .filter(child -> !child.getClass().getName().endsWith("PreconditionBellwether"))
                .toList();
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe unwrapped = unwrap(recipe);
        return Stream.concat(Stream.of(unwrapped), unwrapped.getRecipeList().stream()
                .filter(SpringExpressionOfficialRecipeAuditTest::isCompositionRecipe)
                .flatMap(SpringExpressionOfficialRecipeAuditTest::flatten));
    }

    private static boolean isCompositionRecipe(Recipe recipe) {
        return !"org.openrewrite.config.DeclarativeRecipe$PreconditionBellwether".equals(
                recipe.getName());
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) {
            current = delegating.getDelegate();
        }
        return current;
    }
}
