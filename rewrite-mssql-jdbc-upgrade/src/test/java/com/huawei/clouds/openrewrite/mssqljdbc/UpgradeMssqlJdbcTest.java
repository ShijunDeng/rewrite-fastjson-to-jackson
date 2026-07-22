package com.huawei.clouds.openrewrite.mssqljdbc;

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

class UpgradeMssqlJdbcTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.mssqljdbc.UpgradeMssqlJdbcTo13_2_1Jre11";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME));
    }

    @ParameterizedTest(name = "upgrades spreadsheet version {0}")
    @ValueSource(strings = {
            "7.2.2.jre8", "9.4.1.jre11", "10.2.1.jre8",
            "10.2.3.jre8", "10.2.3.jre17", "11.2.2.jre11"
    })
    void upgradesEverySpreadsheetVersion(String oldVersion) {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>sqlserver-app</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.microsoft.sqlserver</groupId>
                      <artifactId>mssql-jdbc</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(oldVersion),
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>sqlserver-app</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.microsoft.sqlserver</groupId>
                      <artifactId>mssql-jdbc</artifactId>
                      <version>13.2.1.jre11</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesPropertyFromApacheFlinkJdbcConnector() {
        // Reduced from apache/flink-connector-jdbc at 140f179d:
        // https://github.com/apache/flink-connector-jdbc/blob/140f179d019aba6a3f52e17d180c8d329ccdb8b6/flink-connector-jdbc-sqlserver/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.apache.flink</groupId><artifactId>flink-connector-jdbc-sqlserver</artifactId><version>4.0</version>
                  <properties><sqlserver.version>10.2.1.jre8</sqlserver.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId>
                      <version>${sqlserver.version}</version><scope>provided</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.testcontainers</groupId><artifactId>mssqlserver</artifactId><version>1.21.0</version><scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.apache.flink</groupId><artifactId>flink-connector-jdbc-sqlserver</artifactId><version>4.0</version>
                  <properties><sqlserver.version>13.2.1.jre11</sqlserver.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId>
                      <version>${sqlserver.version}</version><scope>provided</scope>
                    </dependency>
                    <dependency>
                      <groupId>org.testcontainers</groupId><artifactId>mssqlserver</artifactId><version>1.21.0</version><scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesDirectProvidedDependencyFromUsaceDataQuery() {
        // Reduced from USACE/data-query at e106e507:
        // https://github.com/USACE/data-query/blob/e106e50751fd8f7e4c6b524468d3015058d5e678/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>mil.army.usace</groupId><artifactId>data-query</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId>
                      <version>11.2.2.jre11</version><scope>provided</scope>
                    </dependency>
                    <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><version>2.2.224</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>mil.army.usace</groupId><artifactId>data-query</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId>
                      <version>13.2.1.jre11</version><scope>provided</scope>
                    </dependency>
                    <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><version>2.2.224</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesManagedPropertyFromAbsaInception() {
        // Reduced from AbsaOSS/inception at e752c4b0:
        // https://github.com/AbsaOSS/inception/blob/e752c4b0f1d9843b749a9389b58a0deb0f558f22/src/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>za.co.absa</groupId><artifactId>inception</artifactId><version>1</version>
                  <properties><mssql-jdbc.version>9.4.1.jre11</mssql-jdbc.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency>
                      <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId>
                      <version>${mssql-jdbc.version}</version>
                    </dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>za.co.absa</groupId><artifactId>inception</artifactId><version>1</version>
                  <properties><mssql-jdbc.version>13.2.1.jre11</mssql-jdbc.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency>
                      <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId>
                      <version>${mssql-jdbc.version}</version>
                    </dependency>
                  </dependencies></dependencyManagement>
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
                dependencies {
                    runtimeOnly 'com.microsoft.sqlserver:mssql-jdbc:10.2.3.jre17'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly 'com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11'
                }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotationAndPreservesConfiguration() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    testImplementation group: 'com.microsoft.sqlserver', name: 'mssql-jdbc', version: '7.2.2.jre8'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    testImplementation group: 'com.microsoft.sqlserver', name: 'mssql-jdbc', version: '13.2.1.jre11'
                }
                """
        ));
    }

    @Test
    void preservesMavenScopeOptionalExclusionsAndClassifier() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>details</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.3.jre8</version>
                    <classifier>sources</classifier><scope>runtime</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>com.azure</groupId><artifactId>*</artifactId></exclusion></exclusions>
                  </dependency></dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>details</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.1.jre11</version>
                    <classifier>sources</classifier><scope>runtime</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>com.azure</groupId><artifactId>*</artifactId></exclusion></exclusions>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesBootManagedVersionlessDependency() {
        // Real projects commonly let Spring Boot manage this coordinate; the recipe intentionally does not override it.
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.1.3</version></parent>
                  <groupId>example</groupId><artifactId>boot-app</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><scope>runtime</scope>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesTargetVersion() {
        rewriteRun(pomXml(pomWithVersion("13.2.1.jre11")));
    }

    @Test
    void doesNotDowngradeNewerVersion() {
        rewriteRun(pomXml(pomWithVersion("13.4.0.jre11")));
    }

    @Test
    void preservesDifferentTargetRuntimeVariant() {
        rewriteRun(pomXml(pomWithVersion("13.2.1.jre8")));
    }

    @Test
    void doesNotChangeSimilarCoordinates() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>other</artifactId><version>1</version>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>example</groupId><artifactId>mssql-jdbc</artifactId><version>10.2.1.jre8</version></dependency>
                    <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc-auth</artifactId><version>10.2.1</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
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
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>driver</artifactId><version>1</version>
                 <dependencies><dependency>
                   <groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>%s</version>
                 </dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.mssqljdbc")
                .scanYamlResources()
                .build();
    }
}
