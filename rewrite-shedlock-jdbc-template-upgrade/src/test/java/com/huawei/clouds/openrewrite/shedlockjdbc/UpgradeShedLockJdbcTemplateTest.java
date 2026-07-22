package com.huawei.clouds.openrewrite.shedlockjdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeShedLockJdbcTemplateTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.shedlockjdbc.UpgradeShedLockJdbcTemplateTo7_2_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @ParameterizedTest(name = "upgrades spreadsheet version {0}")
    @ValueSource(strings = {"2.2.0", "4.29.0", "4.33.0", "4.44.0"})
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(pomXml(pomWithVersion(oldVersion), pomWithVersion("7.2.1")));
    }

    @Test
    void upgradesGradleTwoDotTwoFromCards() {
        // Reduced from shamilvasanov/Cards at 8bff0c8b:
        // https://github.com/shamilvasanov/Cards/blob/8bff0c8b21d9a6bca2c03514fef0cb68b5547bb0/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation('net.javacrumbs.shedlock:shedlock-spring:2.2.0')
                    implementation('net.javacrumbs.shedlock:shedlock-provider-jdbc-template:2.2.0')
                    implementation('org.redisson:redisson:3.17.0')
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation('net.javacrumbs.shedlock:shedlock-spring:2.2.0')
                    implementation('net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.2.1')
                    implementation('org.redisson:redisson:3.17.0')
                }
                """
        ));
    }

    @Test
    void upgradesSharedPropertyFromRieckpilBlogTutorials() {
        // Reduced from rieckpil/blog-tutorials at cc20cab5:
        // https://github.com/rieckpil/blog-tutorials/blob/cc20cab53eeb73c404b9bcc4a22b169571f4b403/spring-boot-shedlock/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>de.rieckpil.blog</groupId><artifactId>spring-boot-shedlock</artifactId><version>1</version>
                  <properties><shedlock.version>4.29.0</shedlock.version></properties>
                  <dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>${shedlock.version}</version></dependency>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.version}</version></dependency>
                    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.5</version><scope>runtime</scope></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>de.rieckpil.blog</groupId><artifactId>spring-boot-shedlock</artifactId><version>1</version>
                  <properties><shedlock.version>7.2.1</shedlock.version></properties>
                  <dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>${shedlock.version}</version></dependency>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.version}</version></dependency>
                    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.5</version><scope>runtime</scope></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesManagedSharedPropertyFromAlibabaSreWorks() {
        // Reduced from alibaba/SREWorks at 5eb36fa9:
        // https://github.com/alibaba/SREWorks/blob/5eb36fa9170fb737a06d9e690bc6df90a9924067/paas/appmanager/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>com.alibaba.sreworks</groupId><artifactId>appmanager</artifactId><version>1</version>
                  <properties><shedlock-spring.version>4.33.0</shedlock-spring.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>${shedlock-spring.version}</version></dependency>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock-spring.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>com.alibaba.sreworks</groupId><artifactId>appmanager</artifactId><version>1</version>
                  <properties><shedlock-spring.version>7.2.1</shedlock-spring.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>${shedlock-spring.version}</version></dependency>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock-spring.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesPropertyFromKonturioInsightsApi() {
        // Reduced from konturio/insights-api at c8252503:
        // https://github.com/konturio/insights-api/blob/c8252503d0adb699ffc300bc149fde51dffb5757/pom.xml
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>io.kontur</groupId><artifactId>insights-api</artifactId><version>1</version>
                  <properties><shedlock.version>4.44.0</shedlock.version><jib.source.image>openjdk:16-alpine</jib.source.image></properties>
                  <dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>${shedlock.version}</version></dependency>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.version}</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>io.kontur</groupId><artifactId>insights-api</artifactId><version>1</version>
                  <properties><shedlock.version>7.2.1</shedlock.version><jib.source.image>openjdk:16-alpine</jib.source.image></properties>
                  <dependencies>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>${shedlock.version}</version></dependency>
                    <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.version}</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesGradleStringNotation() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.29.0' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.2.1' }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotationAndPreservesConfiguration() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { testImplementation group: 'net.javacrumbs.shedlock', name: 'shedlock-provider-jdbc-template', version: '4.33.0' }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { testImplementation group: 'net.javacrumbs.shedlock', name: 'shedlock-provider-jdbc-template', version: '7.2.1' }
                """
        ));
    }

    @Test
    void upgradesMavenDependencyManagement() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>4.44.0</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>7.2.1</version>
                  </dependency></dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void preservesScopeOptionalClassifierAndExclusions() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>details</artifactId><version>1</version><dependencies><dependency>
                  <groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>4.44.0</version>
                  <scope>runtime</scope><optional>true</optional><classifier>tests</classifier>
                  <exclusions><exclusion><groupId>org.springframework</groupId><artifactId>spring-jdbc</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>details</artifactId><version>1</version><dependencies><dependency>
                  <groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>7.2.1</version>
                  <scope>runtime</scope><optional>true</optional><classifier>tests</classifier>
                  <exclusions><exclusion><groupId>org.springframework</groupId><artifactId>spring-jdbc</artifactId></exclusion></exclusions>
                </dependency></dependencies></project>
                """
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7.2.1", "7.2.2", "7.3.0", "7.7.0"})
    void doesNotChangeTargetOrNewerVersions(String version) {
        rewriteRun(pomXml(pomWithVersion(version)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.1.0", "2.2.1", "4.28.0", "4.41.0", "5.0.0", "6.10.0"})
    void leavesUnlistedVersionsUpgradeableWithoutDowngrade(String version) {
        // UpgradeDependencyVersion deliberately upgrades any older explicit version, not only spreadsheet rows.
        rewriteRun(pomXml(pomWithVersion(version), pomWithVersion("7.2.1")));
    }

    @Test
    void leavesShedLockSpringCoreBomAndOtherProvidersUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>others</artifactId><version>1</version><dependencies>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>4.44.0</version></dependency>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-core</artifactId><version>4.44.0</version></dependency>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-bom</artifactId><version>4.44.0</version><type>pom</type></dependency>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc</artifactId><version>4.44.0</version></dependency>
                </dependencies></project>
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
                    implementation 'example:shedlock-provider-jdbc-template:4.44.0'
                    implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template-test:4.44.0'
                }
                """
        ));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static String pomWithVersion(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scheduler</artifactId><version>1</version>
                 <dependencies><dependency>
                   <groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>%s</version>
                 </dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.shedlockjdbc")
                .scanYamlResources()
                .build();
    }
}
