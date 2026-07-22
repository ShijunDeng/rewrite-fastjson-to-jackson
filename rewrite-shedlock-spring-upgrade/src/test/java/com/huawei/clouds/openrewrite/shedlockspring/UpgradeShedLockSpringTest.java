package com.huawei.clouds.openrewrite.shedlockspring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeShedLockSpringTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.shedlockspring.UpgradeShedLockSpringDependencyTo7_2_1";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.shedlockspring.MigrateShedLockSpringTo7_2_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(MIGRATION_RECIPE))
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package net.javacrumbs.shedlock.core;
                        public @interface SchedulerLock {
                            String name() default "";
                            long lockAtMostFor() default -1;
                            String lockAtMostForString() default "";
                            long lockAtLeastFor() default -1;
                            String lockAtLeastForString() default "";
                        }
                        """,
                        """
                        package net.javacrumbs.shedlock.spring.annotation;
                        public @interface SchedulerLock {
                            String name() default "";
                            String lockAtMostFor() default "";
                            String lockAtLeastFor() default "";
                        }
                        """
                ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.2.0", "4.29.0", "4.33.0", "4.41.0", "4.44.0"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>scheduler</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>net.javacrumbs.shedlock</groupId>
                      <artifactId>shedlock-spring</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(oldVersion),
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>scheduler</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>net.javacrumbs.shedlock</groupId>
                      <artifactId>shedlock-spring</artifactId>
                      <version>7.2.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesMavenVersionPropertyFromBaeldungTutorials() {
        // Reduced from eugenp/tutorials at 245cf4c3:
        // https://github.com/eugenp/tutorials/blob/245cf4c3d0f1b20b5e5748f6bbfe4d03c688f481/spring-boot-modules/spring-boot-libraries/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.baeldung</groupId>
                  <artifactId>spring-boot-libraries</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <shedlock.version>6.3.1</shedlock.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>net.javacrumbs.shedlock</groupId>
                      <artifactId>shedlock-spring</artifactId>
                      <version>${shedlock.version}</version>
                    </dependency>
                    <dependency>
                      <groupId>net.javacrumbs.shedlock</groupId>
                      <artifactId>shedlock-provider-jdbc-template</artifactId>
                      <version>${shedlock.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.baeldung</groupId>
                  <artifactId>spring-boot-libraries</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <shedlock.version>7.2.1</shedlock.version>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>net.javacrumbs.shedlock</groupId>
                      <artifactId>shedlock-spring</artifactId>
                      <version>${shedlock.version}</version>
                    </dependency>
                    <dependency>
                      <groupId>net.javacrumbs.shedlock</groupId>
                      <artifactId>shedlock-provider-jdbc-template</artifactId>
                      <version>${shedlock.version}</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesGradleStringNotationFromSgExam() {
        // Reduced from wells2333/sg-exam at 4a7215ac:
        // https://github.com/wells2333/sg-exam/blob/4a7215ace7f56555bc683e4a4c0188f86986fd9f/sg-job/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'net.javacrumbs.shedlock:shedlock-core:4.5.0'
                    implementation 'net.javacrumbs.shedlock:shedlock-spring:4.5.0'
                    implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.5.0'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'net.javacrumbs.shedlock:shedlock-core:4.5.0'
                    implementation 'net.javacrumbs.shedlock:shedlock-spring:7.2.1'
                    implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.5.0'
                }
                """
        ));
    }

    @Test
    void upgradesGradleParenthesizedNotationFromSpinnaker() {
        // Reduced from spinnaker/spinnaker at ab45221c:
        // https://github.com/spinnaker/spinnaker/blob/ab45221c7fea10e567d009a63980f18a154118e5/orca/orca-sql/orca-sql.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation("net.javacrumbs.shedlock:shedlock-spring:4.44.0")
                    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.44.0")
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation("net.javacrumbs.shedlock:shedlock-spring:7.2.1")
                    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.44.0")
                }
                """
        ));
    }

    @Test
    void upgradesGradleKotlinDslDeclaration() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    implementation("net.javacrumbs.shedlock:shedlock-spring:4.33.0")
                }
                """
        ));
    }

    @Test
    void upgradesMavenDependencyManagement() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>net.javacrumbs.shedlock</groupId>
                        <artifactId>shedlock-spring</artifactId>
                        <version>4.41.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>net.javacrumbs.shedlock</groupId>
                        <artifactId>shedlock-spring</artifactId>
                        <version>7.2.1</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void movesSchedulerLockAnnotationExportedByShedLockTwo() {
        // Reduced from epam/cloud-pipeline at 17daf5f6:
        // https://github.com/epam/cloud-pipeline/blob/17daf5f68ba893b067c6846a5dfaba93f8f964bc/api/src/main/java/com/epam/pipeline/manager/access/AccessCodeCleaner.java
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.core.SchedulerLock;

                class AccessCodeCleanerCore {
                    @SchedulerLock(name = "AccessCodeCleanerCore_monitor", lockAtMostForString = "PT10M", lockAtLeastForString = "PT1M")
                    void monitor() {}
                }
                """,
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

                class AccessCodeCleanerCore {
                    @SchedulerLock(name = "AccessCodeCleanerCore_monitor", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
                    void monitor() {}
                }
                """
        ));
    }

    @Test
    void preservesCurrentSchedulerLockPackageAndAttributes() {
        rewriteRun(java(
                """
                import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

                class BillingJob {
                    @SchedulerLock(name = "billing", lockAtMostFor = "10m", lockAtLeastFor = "5m")
                    void bill() {}
                }
                """
        ));
    }

    @Test
    void doesNotDowngradeNewerMavenVersion() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>newer</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>net.javacrumbs.shedlock</groupId>
                      <artifactId>shedlock-spring</artifactId>
                      <version>7.2.2</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesTargetVersion() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>target</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>net.javacrumbs.shedlock</groupId>
                      <artifactId>shedlock-spring</artifactId>
                      <version>7.2.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void doesNotUpgradeProviderOrCoreArtifacts() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>providers</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>net.javacrumbs.shedlock</groupId>
                      <artifactId>shedlock-core</artifactId>
                      <version>4.44.0</version>
                    </dependency>
                    <dependency>
                      <groupId>net.javacrumbs.shedlock</groupId>
                      <artifactId>shedlock-provider-jdbc-template</artifactId>
                      <version>4.44.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void doesNotChangeSimilarCoordinates() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'example:shedlock-spring:4.44.0'
                    implementation 'net.javacrumbs.shedlock:shedlock-spring-test:4.44.0'
                }
                """
        ));
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = environment();
        Recipe dependencyRecipe = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe migrationRecipe = environment.activateRecipes(MIGRATION_RECIPE);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> DEPENDENCY_RECIPE.equals(candidate.getName())));
        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> MIGRATION_RECIPE.equals(candidate.getName())));
        assertTrue(dependencyRecipe.validate().isValid(), () -> dependencyRecipe.validate().failures().toString());
        assertTrue(migrationRecipe.validate().isValid(), () -> migrationRecipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.shedlockspring")
                .scanYamlResources()
                .build();
    }
}
