package com.huawei.clouds.openrewrite.shedlockspring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeShedLockSpringTest implements RewriteTest {
    static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.shedlockspring.UpgradeShedLockSpringDependencyTo7_2_1";
    static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.shedlockspring.MigrateShedLockSpringTo7_2_1";
    static final String ANNOTATION_RECIPE =
            "com.huawei.clouds.openrewrite.shedlockspring.MigrateLegacySchedulerLockAnnotations";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
    }

    @ParameterizedTest(name = "upgrades exact spreadsheet source {0}")
    @ValueSource(strings = {"2.2.0", "4.29.0", "4.33.0", "4.41.0", "4.44.0"})
    void upgradesEveryExplicitSpreadsheetVersion(String oldVersion) {
        rewriteRun(pomXml(
                pomDependency("<version>" + oldVersion + "</version>"),
                pomDependency("<version>7.2.1</version>")));
    }

    @Test
    void upgradesMavenDependencyManagementAndProfile() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>4.41.0</version></dependency></dependencies></dependencyManagement>
                  <profiles><profile><id>legacy</id><dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>2.2.0</version></dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>7.2.1</version></dependency></dependencies></dependencyManagement>
                  <profiles><profile><id>legacy</id><dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>7.2.1</version></dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void upgradesExclusiveLocalMavenProperty() {
        rewriteRun(pomXml(
                propertyPom("<shedlock.version>4.33.0</shedlock.version>", "${shedlock.version}", ""),
                propertyPom("<shedlock.version>7.2.1</shedlock.version>", "${shedlock.version}", "")));
    }

    @Test
    void isolatesSharedPropertyWithoutUpgradingProvider() {
        rewriteRun(pomXml(
                propertyPom("<shedlock.version>4.44.0</shedlock.version>", "${shedlock.version}",
                        "<dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.version}</version></dependency>"),
                propertyPom("<shedlock.version>4.44.0</shedlock.version>", "7.2.1",
                        "<dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.version}</version></dependency>")));
    }

    @Test
    void isolatesPropertyAlsoReferencedFromXmlAttribute() {
        rewriteRun(pomXml(
                propertyPom("<shedlock.version>4.44.0</shedlock.version>", "${shedlock.version}",
                        "<build><plugins><plugin><groupId>example</groupId><artifactId>metadata</artifactId><configuration><label value=\"${shedlock.version}\"/></configuration></plugin></plugins></build>"),
                propertyPom("<shedlock.version>4.44.0</shedlock.version>", "7.2.1",
                        "<build><plugins><plugin><groupId>example</groupId><artifactId>metadata</artifactId><configuration><label value=\"${shedlock.version}\"/></configuration></plugin></plugins></build>")));
    }

    @Test
    void preservesRealBaeldungSharedUnlistedProperty() {
        // Reduced from eugenp/tutorials at 245cf4c3d0f1b20b5e5748f6bbfe4d03c688f481.
        rewriteRun(pomXml(propertyPom("<shedlock.version>6.3.1</shedlock.version>", "${shedlock.version}",
                "<dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-provider-jdbc-template</artifactId><version>${shedlock.version}</version></dependency>")));
    }

    @Test
    void preservesExternalBomManagement() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-bom</artifactId><version>7.2.1</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void upgradesRealSpinnakerParenthesizedGradleDeclaration() {
        // Reduced from spinnaker/spinnaker at ab45221c7fea10e567d009a63980f18a154118e5.
        rewriteRun(buildGradle(
                gradle("implementation(\"net.javacrumbs.shedlock:shedlock-spring:4.44.0\")"),
                gradle("implementation(\"net.javacrumbs.shedlock:shedlock-spring:7.2.1\")")));
    }

    @Test
    void upgradesGroovyMapNotation() {
        rewriteRun(buildGradle(
                gradle("implementation group: 'net.javacrumbs.shedlock', name: 'shedlock-spring', version: '4.29.0'"),
                gradle("implementation group: 'net.javacrumbs.shedlock', name: 'shedlock-spring', version: '7.2.1'")));
    }

    @Test
    void upgradesKotlinDslLiteralWithToolingModel() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradleKts(
                        gradleKts("implementation(\"net.javacrumbs.shedlock:shedlock-spring:4.33.0\")"),
                        gradleKts("implementation(\"net.javacrumbs.shedlock:shedlock-spring:7.2.1\")")));
    }

    @Test
    void preservesRealSgExamUnlistedGradleVersion() {
        // Reduced from wells2333/sg-exam at 4a7215ace7f56555bc683e4a4c0188f86986fd9f.
        rewriteRun(buildGradle(gradle("implementation 'net.javacrumbs.shedlock:shedlock-spring:4.5.0'")));
    }

    @Test
    void preservesHiddenTargetAndNewerVersions() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>strict</artifactId><version>1</version><dependencies>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>4.42.0</version></dependency>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>7.2.1</version></dependency>
                  <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>7.2.2</version></dependency>
                </dependencies></project>
                """));
    }

    @Test
    void preservesDynamicGradleAndDocumentationLiterals() {
        rewriteRun(buildGradle(gradle(
                """
                def shedlockVersion = '4.44.0'
                def note = 'net.javacrumbs.shedlock:shedlock-spring:4.44.0'
                implementation "net.javacrumbs.shedlock:shedlock-spring:${shedlockVersion}"
                """)));
    }

    @Test
    void preservesCoreProvidersAndSimilarCoordinates() {
        rewriteRun(buildGradle(gradle(
                """
                implementation 'net.javacrumbs.shedlock:shedlock-core:4.44.0'
                implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.44.0'
                implementation 'example:shedlock-spring:4.44.0'
                implementation 'net.javacrumbs.shedlock:shedlock-spring-test:4.44.0'
                """)));
    }

    @Test
    void discoversAndValidatesAllPublicRecipes() {
        Environment environment = environment();
        for (String name : new String[]{DEPENDENCY_RECIPE, ANNOTATION_RECIPE, MIGRATION_RECIPE}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        }
    }

    static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.shedlockspring")
                .scanYamlResources()
                .build();
    }

    private static String pomDependency(String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scheduler</artifactId><version>1</version><dependencies>
                 <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId>%s</dependency>
               </dependencies></project>
               """.formatted(version);
    }

    private static String propertyPom(String property, String springVersion, String otherDependency) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>scheduler</artifactId><version>1</version>
                 <properties>%s</properties><dependencies>
                   <dependency><groupId>net.javacrumbs.shedlock</groupId><artifactId>shedlock-spring</artifactId><version>%s</version></dependency>
                   %s
                 </dependencies>
               </project>
               """.formatted(property, springVersion, otherDependency);
    }

    private static String gradle(String dependencyLines) {
        return """
               plugins { id 'java' }
               repositories { mavenCentral() }
               dependencies {
                 %s
               }
               """.formatted(dependencyLines);
    }

    private static String gradleKts(String dependencyLines) {
        return """
               plugins { java }
               repositories { mavenCentral() }
               dependencies {
                 %s
               }
               """.formatted(dependencyLines);
    }
}
