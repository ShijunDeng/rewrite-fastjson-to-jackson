package com.huawei.clouds.openrewrite.jakartaservlet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class JakartaServletDependencyUpgradeTest implements RewriteTest {
    static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.jakartaservlet.UpgradeJakartaServletApiTo6_1_0";
    static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.jakartaservlet.MigrateJakartaServletApiTo6_1_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4.0.3", "4.0.4", "5.0.0", "6.0.0"})
    void upgradesOnlyEachSpreadsheetVersion(String source) {
        rewriteRun(pomXml(pom(dependency(source)), pom(dependency("6.1.0"))));
    }

    @Test
    void upgradesJenkinsDirectAndHapiManagedProfileShapes() {
        // jenkins-infra/crawler efb1b391762056a1a558ab2d340d840ed2aad527 uses direct 4.0.4.
        // hapifhir/hapi-hl7v2 de1503651040e592d529d43980c06b19b89e2c27 manages 6.0.0.
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                  <profiles><profile><id>legacy</id><dependencies>%s</dependencies></profile></profiles>
                </project>
                """.formatted(dependency("6.0.0"), dependency("4.0.4")),
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
                  <profiles><profile><id>legacy</id><dependencies>%s</dependencies></profile></profiles>
                </project>
                """.formatted(dependency("6.1.0"), dependency("6.1.0"))));
    }

    @Test
    void updatesAnExclusivelyOwnedLocalProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                  <properties><servlet.version>4.0.3</servlet.version></properties>
                  <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>${servlet.version}</version><scope>provided</scope></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>property</artifactId><version>1</version>
                  <properties><servlet.version>6.1.0</servlet.version></properties>
                  <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>${servlet.version}</version><scope>provided</scope></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void isolatesSharedPropertyIncludingMetadataToken() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><servlet.version>5.0.0</servlet.version><bnd.import>jakarta.servlet;version="${servlet.version}"</bnd.import></properties>
                  <dependencies>
                    <dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>${servlet.version}</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><servlet.version>5.0.0</servlet.version><bnd.import>jakarta.servlet;version="${servlet.version}"</bnd.import></properties>
                  <dependencies>
                    <dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version></dependency>
                  </dependencies>
                </project>
                """));
    }

    @Test
    void isolatesDependencyWhenPropertyHasMultipleDefinitions() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiles</artifactId><version>1</version>
                  <properties><servlet.version>4.0.4</servlet.version></properties>
                  <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>${servlet.version}</version></dependency></dependencies>
                  <profiles><profile><id>same</id><properties><servlet.version>4.0.4</servlet.version></properties></profile></profiles>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>profiles</artifactId><version>1</version>
                  <properties><servlet.version>4.0.4</servlet.version></properties>
                  <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version></dependency></dependencies>
                  <profiles><profile><id>same</id><properties><servlet.version>4.0.4</servlet.version></properties></profile></profiles>
                </project>
                """));
    }

    @Test
    void preservesVersionlessExternalManagementAndBom() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency><groupId>jakarta.platform</groupId><artifactId>jakarta.jakartaee-bom</artifactId><version>11.0.0</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><scope>provided</scope></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void preservesRangesDynamicUnlistedTargetAndLaterVersions() {
        rewriteRun(
                pomXml(pom(dependency("[5.0,7.0)"))),
                pomXml(pom(dependency("4.0.2")), spec -> spec.path("unlisted/pom.xml")),
                pomXml(pom(dependency("6.1.0")), spec -> spec.path("target/pom.xml")),
                pomXml(pom(dependency("6.2.0-M2")), spec -> spec.path("later/pom.xml")));
    }

    @Test
    void preservesSimilarCoordinates() {
        rewriteRun(pomXml(pom(
                """
                <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                <dependency><groupId>jakarta.servlet.jsp</groupId><artifactId>jakarta.servlet.jsp-api</artifactId><version>4.0.0</version></dependency>
                <dependency><groupId>org.eclipse.jetty.toolchain</groupId><artifactId>jetty-servlet-api</artifactId><version>4.0.4</version></dependency>
                """)));
    }

    @Test
    void upgradesGroovyStringAndMapLiterals() {
        // briandilley/jsonrpc4j 59ff0c955087a3fe1abfbf870ae27d60dbf6c9e2 uses the 5.0.0 literal shape.
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    compileOnly 'jakarta.servlet:jakarta.servlet-api:5.0.0'
                    testCompileOnly(group: 'jakarta.servlet', name: 'jakarta.servlet-api', version: '6.0.0')
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    compileOnly 'jakarta.servlet:jakarta.servlet-api:6.1.0'
                    testCompileOnly(group: 'jakarta.servlet', name: 'jakarta.servlet-api', version: '6.1.0')
                }
                """));
    }

    @Test
    void upgradesKotlinDirectLiteral() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { compileOnly("jakarta.servlet:jakarta.servlet-api:4.0.3") }
                """,
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { compileOnly("jakarta.servlet:jakarta.servlet-api:6.1.0") }
                """));
    }

    @Test
    void preservesGradleVariablesCatalogsDynamicCoordinatesAndDocumentationStrings() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                def servletVersion = '5.0.0'
                def documentation = 'jakarta.servlet:jakarta.servlet-api:5.0.0'
                dependencies {
                    compileOnly "jakarta.servlet:jakarta.servlet-api:${servletVersion}"
                    compileOnly libs.jakarta.servlet
                    compileOnly 'jakarta.servlet:jakarta.servlet-api:6.+'
                }
                """));
    }

    @Test
    void dependencyRecipeDoesNotChangeJavaSource() {
        rewriteRun(java("""
                import javax.servlet.Filter;
                class LegacyFilterHolder { Filter filter; }
                """));
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe migration = environment.activateRecipes(MIGRATION_RECIPE);
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> DEPENDENCY_RECIPE.equals(candidate.getName())));
        assertTrue(environment.listRecipes().stream().anyMatch(candidate -> MIGRATION_RECIPE.equals(candidate.getName())));
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(migration.validate().isValid(), () -> migration.validate().failures().toString());
    }

    static Environment environment() {
        return Environment.builder().scanRuntimeClasspath("com.huawei.clouds.openrewrite.jakartaservlet")
                .scanYamlResources().build();
    }

    private static String dependency(String version) {
        return "<dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>" +
               version + "</version><scope>provided</scope></dependency>";
    }

    private static String pom(String dependencies) {
        return "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies>" +
               dependencies + "</dependencies></project>";
    }
}
