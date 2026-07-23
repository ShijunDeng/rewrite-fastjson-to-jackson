package com.huawei.clouds.openrewrite.logbackcore;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class LogbackCoreRecommendedRecipeTest implements RewriteTest {
    private static final String PREFIX = "com.huawei.clouds.openrewrite.logbackcore.";
    private static final String RECOMMENDED = PREFIX + "MigrateLogbackCoreTo1_5_34";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(RECOMMENDED));
    }

    @Test
    void discoversEveryPublicRecipeAndKeepsUpgradeFirst() {
        Environment environment = Environment.builder().scanRuntimeClasspath().build();
        String[] names = {
                PREFIX + "UpgradeLogbackCoreTo1_5_34",
                PREFIX + "MigrateDeterministicLogbackCore1_5_34",
                PREFIX + "FindLogbackCore1_5_34BuildRisks",
                PREFIX + "FindLogbackCore1_5_34SourceRisks",
                PREFIX + "FindLogbackCore1_5_34ConfigurationRisks",
                RECOMMENDED
        };
        for (String name : names) {
            assertEquals(name, environment.activateRecipes(name).getName());
        }
        Recipe aggregate = environment.activateRecipes(RECOMMENDED);
        assertEquals(5, aggregate.getRecipeList().size());
        assertEquals(PREFIX + "UpgradeLogbackCoreTo1_5_34",
                aggregate.getRecipeList().get(0).getName());
    }

    @Test
    void recommendedRecipeUpgradesOnlyWorkbookVersion() {
        rewriteRun(pomXml(
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>service</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>ch.qos.logback</groupId>
                            <artifactId>logback-core</artifactId>
                            <version>1.2.9</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
                """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>example</groupId>
                    <artifactId>service</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>ch.qos.logback</groupId>
                            <artifactId>logback-core</artifactId>
                            <version>1.5.34</version>
                        </dependency>
                    </dependencies>
                </project>
                """));
    }

    @Test
    void recommendedRecipeComposesAutoAndPreciseConfigurationMarks() {
        rewriteRun(xml("""
                <configuration scan="true">
                    <statusListener class="ch.qos.logback.core.status.OnConsoleStatusListener"/>
                    <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>
                    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                                <maxFileSize>100MB</maxFileSize>
                            </timeBasedFileNamingAndTriggeringPolicy>
                            <fileNamePattern>logs/app.%d{yyyy-MM-dd}.%i.zip</fileNamePattern>
                            <totalSizeCap>5GB</totalSizeCap>
                        </rollingPolicy>
                    </appender>
                </configuration>
                """, source -> source.path("src/main/resources/logback.xml").after(actual -> actual)
                .afterRecipe(after -> {
                    String result = after.printAll();
                    assertTrue(result.contains("ch.qos.logback.core.hook.DefaultShutdownHook"), result);
                    assertTrue(result.contains(
                            "ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy"), result);
                    assertTrue(result.contains(FindLogbackCore1534ConfigurationRisks.SCAN), result);
                    assertTrue(result.contains(FindLogbackCore1534ConfigurationRisks.STATUS), result);
                    assertTrue(result.contains(FindLogbackCore1534ConfigurationRisks.LIFECYCLE), result);
                    assertTrue(result.contains(FindLogbackCore1534ConfigurationRisks.ROLLING), result);
                })));
    }

    @Test
    void recommendedRecipeNeverDowngradesAndIsMarkerIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                xml("""
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>example</groupId>
                            <artifactId>future-service</artifactId>
                            <version>1</version>
                            <dependencies>
                                <dependency>
                                    <groupId>ch.qos.logback</groupId>
                                    <artifactId>logback-core</artifactId>
                                    <version>1.6.0</version>
                                </dependency>
                            </dependencies>
                        </project>
                        """, source -> source.path("pom.xml").after(actual -> actual)
                        .afterRecipe(after -> {
                            String result = after.printAll();
                            assertTrue(result.contains("<version>1.6.0</version>"), result);
                            assertTrue(result.contains("目标版本冲突（禁止降级）"), result);
                            assertEquals(1, occurrences(result,
                                    FindLogbackCore1534BuildRisks.DOWNGRADE_FORBIDDEN));
                        })));
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }
}
