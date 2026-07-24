package com.huawei.clouds.openrewrite.log4jcore;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.apache.commons.codec.ApacheBase64ToJavaBase64;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
import org.openrewrite.java.logging.log4j.LoggerSetLevelToConfiguratorRecipe;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class Log4jCoreOfficialRecipeReuseTest implements RewriteTest {
    private static final String LOCAL_RECOMMENDED =
            "com.huawei.clouds.openrewrite.log4jcore.MigrateLog4jCoreTo2_25_5";
    private static final String OFFICIAL_UPGRADE =
            "org.openrewrite.java.logging.log4j.UpgradeLog4J2DependencyVersion";
    private static final String OFFICIAL_LOG4J1_CLASS_RECIPE =
            "org.openrewrite.java.logging.log4j.LoggerSetLevelToConfiguratorRecipe";

    @Test
    void pinsEveryAuditedOfficialArtifact() throws Exception {
        assertArtifact(Recipe.class, "8.87.7",
                "af06bb1b159249695dc92187093cd0909da6c843",
                "a4fb7cd35ada08af9e9585a8d63de4d7b2f12b70af1dc506aff963a6f5434448");
        assertArtifact(ChangeMethodName.class, "8.88.0-SNAPSHOT",
                "ea77ee7c7471c17423726ae2612de17b6fc8b111",
                "015cca0c660685f8107ee1c173db1063302926bb5f7e4598ed908428b0a9550f");
        assertArtifact(LoggerSetLevelToConfiguratorRecipe.class, "3.30.0",
                "c357a7209d721078dc942a777b1d8cc95941f722",
                "366a1cd43ee8e0f4378cac52036831df07d74d1648222d0664de2c63f7e26827");
        assertArtifact(ApacheBase64ToJavaBase64.class, "2.28.0",
                "b0424eb13da62085a34a7e84a3987ac78227b70b",
                "1841723a57e3dad3a47777a311275f1d18fed8e197c99aa3526503e7c8a06d17");
    }

    @Test
    void builderMigrationUsesExactOfficialCoreDelegates() {
        List<Recipe> delegates = MigrateLoggerConfigFilterBuilders.officialCoreRecipes();
        assertEquals(3, delegates.size());
        assertEquals(List.of(
                "org.apache.logging.log4j.core.config.LoggerConfig.Builder " +
                "withtFilter(org.apache.logging.log4j.core.Filter)",
                "org.apache.logging.log4j.core.config.LoggerConfig.Builder " +
                "withFilter(org.apache.logging.log4j.core.Filter)",
                "org.apache.logging.log4j.core.config.LoggerConfig.RootLogger.Builder " +
                "withtFilter(org.apache.logging.log4j.core.Filter)"
        ), delegates.stream()
                .map(ChangeMethodName.class::cast)
                .map(ChangeMethodName::getMethodPattern)
                .toList());
        for (Recipe delegate : delegates) {
            ChangeMethodName rename = assertInstanceOf(ChangeMethodName.class, delegate);
            assertEquals("setFilter", rename.getNewMethodName());
            assertEquals(Boolean.FALSE, rename.getMatchOverrides());
            assertEquals(Boolean.TRUE, rename.getIgnoreDefinition());
        }
    }

    @Test
    void auditedOfficialClassAndCompositionActivateButAreExcludedFromThePatchRecipe() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();

        Recipe officialUpgrade = environment.activateRecipes(OFFICIAL_UPGRADE);
        assertEquals(OFFICIAL_UPGRADE, officialUpgrade.getName());
        UpgradeDependencyVersion broadUpgrade = flatten(officialUpgrade)
                .map(Log4jCoreOfficialRecipeReuseTest::unwrap)
                .filter(UpgradeDependencyVersion.class::isInstance)
                .map(UpgradeDependencyVersion.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("org.apache.logging.log4j", broadUpgrade.getGroupId());
        assertEquals("*", broadUpgrade.getArtifactId());
        assertEquals("2.x", broadUpgrade.getNewVersion());
        assertEquals(Boolean.TRUE, broadUpgrade.getOverrideManagedVersion());

        Recipe officialClassRecipe = environment.activateRecipes(OFFICIAL_LOG4J1_CLASS_RECIPE);
        assertEquals(OFFICIAL_LOG4J1_CLASS_RECIPE, officialClassRecipe.getName());

        Recipe local = environment.activateRecipes(LOCAL_RECOMMENDED);
        List<String> localChildren = local.getRecipeList().stream().map(Recipe::getName).toList();
        assertFalse(localChildren.contains(OFFICIAL_UPGRADE), localChildren.toString());
        assertFalse(localChildren.contains(OFFICIAL_LOG4J1_CLASS_RECIPE), localChildren.toString());
        assertTrue(localChildren.contains(
                "com.huawei.clouds.openrewrite.log4jcore.MigrateLog4jCore25"));
    }

    @Test
    void recommendedRuntimeTreeExposesOnlyTheThreeAcceptedOfficialLeaves() {
        Recipe recommended = Environment.builder().scanRuntimeClasspath().build()
                .activateRecipes(LOCAL_RECOMMENDED);
        List<ChangeMethodName> officialLeaves = flatten(recommended)
                .filter(ChangeMethodName.class::isInstance)
                .map(ChangeMethodName.class::cast)
                .toList();

        assertEquals(MigrateLoggerConfigFilterBuilders.officialCoreRecipes(), officialLeaves);
        assertFalse(flatten(recommended).anyMatch(UpgradeDependencyVersion.class::isInstance));
        assertFalse(flatten(recommended).anyMatch(recipe -> OFFICIAL_UPGRADE.equals(recipe.getName())));
        assertFalse(flatten(recommended).anyMatch(
                recipe -> OFFICIAL_LOG4J1_CLASS_RECIPE.equals(recipe.getName())));
    }

    @Test
    void rewriteApacheCatalogContainsNoLog4jRecipeToReuse() throws Exception {
        Path jar = artifact(ApacheBase64ToJavaBase64.class);
        try (JarFile artifact = new JarFile(jar.toFile());
             InputStream input = artifact.getInputStream(
                     artifact.getJarEntry("META-INF/rewrite/recipes.csv"))) {
            String catalog = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(catalog.toLowerCase(Locale.ROOT).contains("log4j"));
        }
    }

    @Test
    void officialCoreRenamesAndTheNarrowNoLookupsGapRunTogether() {
        rewriteRun(spec -> spec.recipe(new MigrateLog4jCore25())
                        .parser(JavaParser.fromJavaVersion().dependsOn(Log4jCoreTestApi.legacySources())),
                java("""
                        import org.apache.logging.log4j.core.Filter;
                        import org.apache.logging.log4j.core.config.LoggerConfig;
                        class Logging {
                            String pattern = "%d %m{nolookups}%n";
                            void configure(LoggerConfig.Builder logger,
                                           LoggerConfig.RootLogger.Builder root,
                                           Filter filter) {
                                logger.withtFilter(filter);
                                logger.withFilter(filter);
                                root.withtFilter(filter);
                            }
                        }
                        """, """
                        import org.apache.logging.log4j.core.Filter;
                        import org.apache.logging.log4j.core.config.LoggerConfig;
                        class Logging {
                            String pattern = "%d %m%n";
                            void configure(LoggerConfig.Builder logger,
                                           LoggerConfig.RootLogger.Builder root,
                                           Filter filter) {
                                logger.setFilter(filter);
                                logger.setFilter(filter);
                                root.setFilter(filter);
                            }
                        }
                        """));
    }

    private static Stream<Recipe> flatten(Recipe recipe) {
        Recipe effective = unwrap(recipe);
        return Stream.concat(
                Stream.of(effective),
                effective.getRecipeList().stream().flatMap(Log4jCoreOfficialRecipeReuseTest::flatten));
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
