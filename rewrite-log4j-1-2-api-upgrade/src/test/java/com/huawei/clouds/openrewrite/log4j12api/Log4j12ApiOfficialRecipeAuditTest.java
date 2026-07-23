package com.huawei.clouds.openrewrite.log4j12api;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.dependencies.ChangeDependency;
import org.openrewrite.java.dependencies.RemoveDependency;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.logging.log4j.LoggerSetLevelToConfiguratorRecipe;
import org.openrewrite.apache.commons.codec.ApacheBase64ToJavaBase64;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Log4j12ApiOfficialRecipeAuditTest {
    private static final String OFFICIAL_UPGRADE =
            "org.openrewrite.java.logging.log4j.UpgradeLog4J2DependencyVersion";
    private static final String OFFICIAL_AGGREGATE =
            "org.openrewrite.java.logging.log4j.Log4j1ToLog4j2";

    @Test
    void pinsEveryAuditedOfficialArtifact() throws Exception {
        assertArtifact(LoggerSetLevelToConfiguratorRecipe.class, "3.30.0",
                "c357a7209d721078dc942a777b1d8cc95941f722",
                "366a1cd43ee8e0f4378cac52036831df07d74d1648222d0664de2c63f7e26827");
        assertArtifact(ApacheBase64ToJavaBase64.class, "2.28.0",
                "b0424eb13da62085a34a7e84a3987ac78227b70b",
                "1841723a57e3dad3a47777a311275f1d18fed8e197c99aa3526503e7c8a06d17");
        assertArtifact(Recipe.class, "8.87.7",
                "af06bb1b159249695dc92187093cd0909da6c843",
                "a4fb7cd35ada08af9e9585a8d63de4d7b2f12b70af1dc506aff963a6f5434448");
        assertArtifact(ChangeType.class, "8.88.0-SNAPSHOT",
                "ea77ee7c7471c17423726ae2612de17b6fc8b111",
                "015cca0c660685f8107ee1c173db1063302926bb5f7e4598ed908428b0a9550f");
        assertEquals("2.25.5", org.apache.log4j.Category.class.getPackage()
                .getImplementationVersion());
        assertEquals("4dd812dc5a6343f542a9e0046b1ec78ecf10bdd5a8c15745101cdd8b9aa24974",
                sha256(org.apache.log4j.Category.class));
    }

    @Test
    void selectedRecipeDirectlyComposesFiveExactOfficialLeaves() {
        List<Recipe> children = effectiveChildren(activate(Log4j12ApiTestSupport.SAFE_SOURCE));
        assertEquals(List.of(
                        ChangeMethodTargetToStatic.class,
                        ChangeMethodTargetToStatic.class,
                        LoggerSetLevelToConfiguratorRecipe.class,
                        ChangeType.class,
                        ChangeType.class),
                children.stream().map(Recipe::getClass).toList());

        List<ChangeMethodTargetToStatic> staticTargets = children.subList(0, 2).stream()
                .map(ChangeMethodTargetToStatic.class::cast).toList();
        assertEquals(List.of(
                        "org.apache.log4j.Logger getLogger(..)",
                        "org.apache.log4j.Logger getRootLogger()"),
                staticTargets.stream().map(ChangeMethodTargetToStatic::getMethodPattern).toList());
        assertTrue(staticTargets.stream().allMatch(change ->
                "org.apache.logging.log4j.LogManager".equals(
                        change.getFullyQualifiedTargetTypeName())));

        List<ChangeType> types = children.subList(3, 5).stream()
                .map(ChangeType.class::cast).toList();
        assertEquals(List.of(
                        "org.apache.log4j.Logger->org.apache.logging.log4j.Logger",
                        "org.apache.log4j.Level->org.apache.logging.log4j.Level"),
                types.stream().map(type -> type.getOldFullyQualifiedTypeName() + "->" +
                        type.getNewFullyQualifiedTypeName()).toList());
        assertTrue(types.stream().allMatch(type -> Boolean.TRUE.equals(type.getIgnoreDefinition())));
    }

    @Test
    void officialBroadUpgradeIsRuntimeVisibleAndPreciselyRejected() {
        Recipe official = activate(OFFICIAL_UPGRADE);
        UpgradeDependencyVersion broad = recipeTree(official).stream()
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .findFirst().orElseThrow();
        assertEquals("org.apache.logging.log4j", broad.getGroupId());
        assertEquals("*", broad.getArtifactId());
        assertEquals("2.x", broad.getNewVersion());
        assertEquals(Boolean.TRUE, broad.getOverrideManagedVersion());

        List<Recipe> local = recipeTree(activate(Log4j12ApiTestSupport.RECOMMENDED));
        assertFalse(local.stream().anyMatch(recipe -> OFFICIAL_UPGRADE.equals(recipe.getName())));
        assertFalse(local.stream().anyMatch(UpgradeDependencyVersion.class::isInstance));
    }

    @Test
    void officialFrameworkAggregateIsVisibleButItsBroadMutationsAreRejected() {
        List<Recipe> official = recipeTree(activate(OFFICIAL_AGGREGATE));
        assertTrue(official.stream().anyMatch(ChangePackage.class::isInstance));
        assertTrue(official.stream().anyMatch(AddDependency.class::isInstance));
        assertTrue(official.stream().anyMatch(RemoveDependency.class::isInstance));
        assertTrue(official.stream().anyMatch(ChangeDependency.class::isInstance));
        assertTrue(official.stream().anyMatch(recipe -> OFFICIAL_UPGRADE.equals(recipe.getName())));

        List<Recipe> local = recipeTree(activate(Log4j12ApiTestSupport.RECOMMENDED));
        assertFalse(local.stream().anyMatch(recipe -> OFFICIAL_AGGREGATE.equals(recipe.getName())));
        assertFalse(local.stream().anyMatch(AddDependency.class::isInstance));
        assertFalse(local.stream().anyMatch(RemoveDependency.class::isInstance));
        assertFalse(local.stream().anyMatch(ChangeDependency.class::isInstance));
        assertFalse(local.stream().anyMatch(ChangePackage.class::isInstance));
        assertEquals(0, local.stream()
                .filter(LoggerSetLevelToConfiguratorRecipe.class::isInstance).count());
        assertEquals(1, recipeTree(activate(Log4j12ApiTestSupport.WITH_OWNED_CORE)).stream()
                .filter(LoggerSetLevelToConfiguratorRecipe.class::isInstance).count());
    }

    @Test
    void rewriteApacheCatalogContainsNoLog4jRecipeToReuse() throws Exception {
        Path jar = artifact(ApacheBase64ToJavaBase64.class);
        try (JarFile artifact = new JarFile(jar.toFile());
             InputStream input = artifact.getInputStream(
                     artifact.getJarEntry("META-INF/rewrite/recipes.csv"))) {
            String catalog = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(catalog.toLowerCase(java.util.Locale.ROOT).contains("log4j"));
        }
    }

    @Test
    void defaultAndOwnedCoreTreesKeepStrictOrderAndSeparateBackendSelection() {
        Recipe recommended = activate(Log4j12ApiTestSupport.RECOMMENDED);
        assertEquals(List.of(
                        Log4j12ApiTestSupport.UPGRADE,
                        Log4j12ApiTestSupport.PREFIX + "FindLog4j12ApiBuildRisks",
                        Log4j12ApiTestSupport.PREFIX + "FindLog4j12ApiSourceRisks",
                        Log4j12ApiTestSupport.PREFIX + "FindLog4j12ApiConfigurationRisks"),
                effectiveChildren(recommended).stream().map(Recipe::getName).toList());

        List<Recipe> defaultTree = recipeTree(recommended);
        assertEquals(1, defaultTree.stream()
                .filter(UpgradeSelectedLog4j12ApiDependency.class::isInstance).count());
        assertEquals(0, defaultTree.stream()
                .filter(ChangeMethodTargetToStatic.class::isInstance).count());
        assertEquals(0, defaultTree.stream().filter(ChangeType.class::isInstance).count());
        assertEquals(0, defaultTree.stream()
                .filter(LoggerSetLevelToConfiguratorRecipe.class::isInstance).count());

        Recipe withOwnedCore = activate(Log4j12ApiTestSupport.WITH_OWNED_CORE);
        assertEquals(List.of(
                        Log4j12ApiTestSupport.UPGRADE,
                        Log4j12ApiTestSupport.SAFE_SOURCE,
                        Log4j12ApiTestSupport.PREFIX + "FindLog4j12ApiBuildRisks",
                        Log4j12ApiTestSupport.PREFIX + "FindLog4j12ApiSourceRisks",
                        Log4j12ApiTestSupport.PREFIX + "FindLog4j12ApiConfigurationRisks"),
                effectiveChildren(withOwnedCore).stream().map(Recipe::getName).toList());

        List<Recipe> optInTree = recipeTree(withOwnedCore);
        assertEquals(1, optInTree.stream()
                .filter(UpgradeSelectedLog4j12ApiDependency.class::isInstance).count());
        assertEquals(2, optInTree.stream()
                .filter(ChangeMethodTargetToStatic.class::isInstance).count());
        assertEquals(2, optInTree.stream().filter(ChangeType.class::isInstance).count());
        assertEquals(1, optInTree.stream()
                .filter(LoggerSetLevelToConfiguratorRecipe.class::isInstance).count());
    }

    private static Recipe activate(String name) {
        return unwrap(Log4j12ApiTestSupport.activate(name));
    }

    private static List<Recipe> effectiveChildren(Recipe recipe) {
        return recipe.getRecipeList().stream().map(Log4j12ApiOfficialRecipeAuditTest::unwrap)
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
        for (Recipe child : effectiveChildren(recipe)) collect(child, recipes);
    }

    private static Recipe unwrap(Recipe recipe) {
        Recipe unwrapped = recipe;
        while (unwrapped instanceof Recipe.DelegatingRecipe delegating) {
            unwrapped = delegating.getDelegate();
        }
        return unwrapped;
    }

    private static void assertArtifact(
            Class<?> type, String version, String commit, String checksum) throws Exception {
        assertEquals(version, type.getPackage().getImplementationVersion());
        assertEquals(commit, manifestAttribute(type, "Full-Change"));
        assertEquals(checksum, sha256(type));
    }

    private static String manifestAttribute(Class<?> type, String name) throws Exception {
        Path jar = artifact(type);
        try (JarFile artifact = new JarFile(jar.toFile())) {
            String value = artifact.getManifest().getMainAttributes().getValue(name);
            if (value == null) throw new IOException("Missing " + name + " in " + jar);
            return value;
        }
    }

    private static String sha256(Class<?> type) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(artifact(type))) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path artifact(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}
