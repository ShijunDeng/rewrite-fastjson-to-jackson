package com.huawei.clouds.openrewrite.logbackcore;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.ChangeMethodName;
import org.openrewrite.java.ChangeType;
import org.openrewrite.xml.ChangeTagAttribute;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogbackCoreOfficialRecipeReuseTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.logbackcore.";

    @Test
    void deterministicJavaMigrationActuallyComposesCoreRecipes() {
        List<Recipe> delegates = MigrateLogback1534Java.officialCoreRecipes();
        assertEquals(List.of(
                ChangeType.class,
                ChangeType.class,
                ChangeType.class,
                ChangeMethodName.class,
                ChangeMethodName.class
        ), delegates.stream().map(Recipe::getClass).toList());
        assertEquals(List.of(
                "ch.qos.logback.core.hook.DelayingShutdownHook",
                "ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP",
                "ch.qos.logback.core.joran.action.ActionConst"
        ), delegates.subList(0, 3).stream()
                .map(ChangeType.class::cast)
                .map(ChangeType::getOldFullyQualifiedTypeName)
                .toList());
        assertEquals(List.of("getTopURL", "setTopURL"), delegates.subList(3, 5).stream()
                .map(ChangeMethodName.class::cast)
                .map(ChangeMethodName::getNewMethodName)
                .toList());
    }

    @Test
    void deterministicXmlMigrationActuallyComposesExactCoreRecipes() {
        List<Recipe> delegates = MigrateLogback1534Configuration.officialCoreRecipes();
        assertEquals(List.of(ChangeTagAttribute.class, ChangeTagAttribute.class),
                delegates.stream().map(Recipe::getClass).toList());
        for (Recipe delegate : delegates) {
            ChangeTagAttribute change = (ChangeTagAttribute) delegate;
            assertEquals("//*", change.getElementName());
            assertEquals("class", change.getAttributeName());
            assertEquals(Boolean.TRUE, change.getRegex());
            assertTrue(change.getOldValue().startsWith("\\Q"));
            assertTrue(change.getOldValue().endsWith("\\E"));
        }
    }

    @Test
    void activatesAuditedLoggingArtifactButKeepsBroadRecipeOutOfAggregate() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        Recipe official = environment.activateRecipes(
                "org.openrewrite.java.logging.logback.Log4jToLogback");
        assertEquals("org.openrewrite.java.logging.logback.Log4jToLogback", official.getName());
        List<String> officialChildren = official.getRecipeList().stream().map(Recipe::getName).toList();
        assertTrue(officialChildren.contains(
                "org.openrewrite.java.logging.logback.Log4jAppenderToLogback"));
        assertTrue(officialChildren.contains(
                "org.openrewrite.java.logging.logback.Log4jLayoutToLogback"));

        Recipe deterministic = environment.activateRecipes(
                PREFIX + "MigrateDeterministicLogbackCore1_5_34");
        assertEquals(List.of(
                PREFIX + "MigrateLogback1534Java",
                PREFIX + "MigrateLogback1534Configuration"
        ), deterministic.getRecipeList().stream().map(Recipe::getName).toList());

        Recipe recommended = environment.activateRecipes(PREFIX + "MigrateLogbackCoreTo1_5_34");
        List<String> localChildren = recommended.getRecipeList().stream().map(Recipe::getName).toList();
        assertEquals(List.of(
                PREFIX + "UpgradeLogbackCoreTo1_5_34",
                PREFIX + "MigrateDeterministicLogbackCore1_5_34",
                PREFIX + "FindLogbackCore1_5_34BuildRisks",
                PREFIX + "FindLogbackCore1_5_34SourceRisks",
                PREFIX + "FindLogbackCore1_5_34ConfigurationRisks"
        ), localChildren);
        assertFalse(localChildren.contains(official.getName()));
    }
}
