package com.huawei.clouds.openrewrite.bcpkixjdk18on;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.migrate.UpgradeJavaVersion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialBcPkixRecipeAuditTest {
    private static final String DETERMINISTIC =
            "com.huawei.clouds.openrewrite.bcpkixjdk18on.MigrateDeterministicBcPkix1_81_1Java";
    private static final String RECOMMENDED =
            "com.huawei.clouds.openrewrite.bcpkixjdk18on.MigrateBcPkixJdk18onTo1_81_1";
    private static final String OFFICIAL_JDK18ON =
            "org.openrewrite.java.migrate.BounceCastleFromJdk15OntoJdk18On";
    private static final String OFFICIAL_JDK15TO18 =
            "org.openrewrite.java.migrate.BouncyCastleFromJdk15OnToJdk15to18";
    private static final Environment ENVIRONMENT = Environment.builder()
            .scanRuntimeClasspath(
                    "com.huawei.clouds.openrewrite.bcpkixjdk18on",
                    "org.openrewrite.java",
                    "org.openrewrite.java.migrate")
            .build();

    @Test
    void pinsTheAuditedCoreAndMigrateJavaArtifacts() throws Exception {
        assertEquals("8.87.5", Recipe.class.getPackage().getImplementationVersion());
        assertEquals("b3008cc4a1f0c43f562da16e5933a2a56d9bc568",
                manifestAttribute(Recipe.class, "Full-Change"));
        assertEquals("3.40.0", UpgradeJavaVersion.class.getPackage().getImplementationVersion());
        assertEquals("658481254a6ee678f5f162e51d8d49ee01c75877",
                manifestAttribute(UpgradeJavaVersion.class, "Full-Change"));
    }

    @Test
    void officialCatalogContainsOnlyTheTwoBouncyCastleLineageAggregates() {
        Set<String> official = ENVIRONMENT.listRecipes().stream()
                .map(Recipe::getName)
                .filter(name -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.contains("bouncycastle") || lower.contains("bouncecastle");
                })
                .collect(Collectors.toSet());

        assertEquals(Set.of(OFFICIAL_JDK18ON, OFFICIAL_JDK15TO18), official);
    }

    @Test
    void officialJdk18OnAggregateIsTheRejectedFourteenArtifactLatestReleaseMigration() {
        List<String> oldArtifacts = List.of(
                "bcprov-jdk15on", "bcutil-jdk15on", "bcpkix-jdk15on", "bcmail-jdk15on",
                "bcjmail-jdk15on", "bcpg-jdk15on", "bctls-jdk15on",
                "bcprov-jdk15to18", "bcutil-jdk15to18", "bcpkix-jdk15to18", "bcmail-jdk15to18",
                "bcjmail-jdk15to18", "bcpg-jdk15to18", "bctls-jdk15to18");
        List<Recipe> leaves = effectiveChildren(activate(OFFICIAL_JDK18ON));

        assertEquals(oldArtifacts.size(), leaves.size());
        for (int i = 0; i < oldArtifacts.size(); i++) {
            ChangeDependency change = assertInstanceOf(ChangeDependency.class, leaves.get(i));
            String oldArtifact = oldArtifacts.get(i);
            assertChangeDependency(change, oldArtifact,
                    oldArtifact.replace("-jdk15to18", "-jdk18on")
                            .replace("-jdk15on", "-jdk18on"));
        }
    }

    @Test
    void officialPreJava8AggregateIsTheRejectedSevenArtifactLatestReleaseMigration() {
        List<String> oldArtifacts = List.of(
                "bcprov-jdk15on", "bcutil-jdk15on", "bcpkix-jdk15on", "bcmail-jdk15on",
                "bcjmail-jdk15on", "bcpg-jdk15on", "bctls-jdk15on");
        List<Recipe> leaves = effectiveChildren(activate(OFFICIAL_JDK15TO18));

        assertEquals(oldArtifacts.size(), leaves.size());
        for (int i = 0; i < oldArtifacts.size(); i++) {
            String oldArtifact = oldArtifacts.get(i);
            assertChangeDependency(assertInstanceOf(ChangeDependency.class, leaves.get(i)),
                    oldArtifact, oldArtifact.replace("-jdk15on", "-jdk15to18"));
        }
    }

    @Test
    void localDeterministicTreeDirectlyReusesTheExactCoreChangeTypeLeaf() {
        Recipe deterministic = activate(DETERMINISTIC);
        assertEquals(List.of(ChangeType.class.getName(), MigrateBcPkix1811Java.class.getName()),
                effectiveChildren(deterministic).stream().map(Recipe::getName).toList());

        List<Recipe> tree = recipeTree(deterministic);
        ChangeType type = only(tree, ChangeType.class);
        assertEquals("org.bouncycastle.pkcs.DeltaCertificateRequestAttribute",
                type.getOldFullyQualifiedTypeName());
        assertEquals("org.bouncycastle.pkcs.DeltaCertificateRequestAttributeValue",
                type.getNewFullyQualifiedTypeName());
        assertTrue(Boolean.TRUE.equals(type.getIgnoreDefinition()));
        assertEquals(1, tree.stream().filter(MigrateBcPkix1811Java.class::isInstance).count());
    }

    @Test
    void recommendedTreeExcludesBothBroadAggregatesAndGenericDependencySelectors() {
        Recipe recommended = activate(RECOMMENDED);
        assertEquals(List.of(
                        "com.huawei.clouds.openrewrite.bcpkixjdk18on.UpgradeBcPkixJdk18onTo1_81_1",
                        DETERMINISTIC,
                        "com.huawei.clouds.openrewrite.bcpkixjdk18on.FindBcPkix1_81_1BuildRisks",
                        "com.huawei.clouds.openrewrite.bcpkixjdk18on.FindBcPkix1_81_1SourceAndConfigurationRisks"),
                effectiveChildren(recommended).stream().map(Recipe::getName).toList());

        List<Recipe> tree = recipeTree(recommended);
        Set<String> names = tree.stream().map(Recipe::getName).collect(Collectors.toSet());
        assertFalse(names.contains(OFFICIAL_JDK18ON));
        assertFalse(names.contains(OFFICIAL_JDK15TO18));
        assertFalse(tree.stream().anyMatch(ChangeDependency.class::isInstance));
        assertFalse(tree.stream().anyMatch(UpgradeDependencyVersion.class::isInstance));
        assertEquals(1, tree.stream().filter(UpgradeSelectedBcPkixDependency.class::isInstance).count());
        assertEquals(1, tree.stream().filter(ChangeType.class::isInstance).count());
        assertEquals(1, tree.stream().filter(MigrateBcPkix1811Java.class::isInstance).count());
    }

    private static void assertChangeDependency(
            ChangeDependency change, String oldArtifact, String newArtifact) {
        assertEquals("org.bouncycastle", change.getOldGroupId());
        assertEquals(oldArtifact, change.getOldArtifactId());
        assertNull(change.getNewGroupId());
        assertEquals(newArtifact, change.getNewArtifactId());
        assertEquals("latest.release", change.getNewVersion());
        assertNull(change.getVersionPattern());
        assertNull(change.getOverrideManagedVersion());
        assertNull(change.getChangeManagedDependency());
    }

    private static Recipe activate(String name) {
        return unwrap(ENVIRONMENT.activateRecipes(name));
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        return recipe.getRecipeList().stream()
                .map(OfficialBcPkixRecipeAuditTest::unwrap)
                .filter(child -> !child.getClass().getName().endsWith("PreconditionBellwether"))
                .toList();
    }

    private static List<Recipe> recipeTree(Recipe root) {
        List<Recipe> recipes = new ArrayList<>();
        collect(unwrap(root), recipes);
        return recipes;
    }

    private static void collect(Recipe recipe, List<Recipe> recipes) {
        recipes.add(recipe);
        for (Recipe child : effectiveChildren(recipe)) {
            collect(child, recipes);
        }
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe current = recipe;
        while (current instanceof Recipe.DelegatingRecipe delegating) {
            current = delegating.getDelegate();
        }
        return current;
    }

    private static <T extends Recipe> T only(List<Recipe> tree, Class<T> type) {
        List<T> matches = tree.stream().filter(type::isInstance).map(type::cast).toList();
        assertEquals(1, matches.size(), "Expected exactly one " + type.getName());
        return matches.get(0);
    }

    private static String manifestAttribute(Class<?> type, String name) throws Exception {
        Path jar = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (JarFile artifact = new JarFile(jar.toFile())) {
            String value = artifact.getManifest().getMainAttributes().getValue(name);
            if (value == null) {
                throw new IOException("Missing manifest attribute " + name + " in " + jar);
            }
            return value;
        }
    }
}
