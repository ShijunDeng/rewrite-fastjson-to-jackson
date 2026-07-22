package com.huawei.clouds.openrewrite.jasypt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class JasyptDependencyPrecisionTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new UpgradeSelectedJasyptStarterDependency());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.1.1", "2.1.2", "3.0.3", "3.0.4", "3.0.5"})
    void upgradesEveryExactSpreadsheetVersion(String sourceVersion) {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom(sourceVersion), pom("4.0.3"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.18", "3.0.2", "3.0.6", "4.0.3", "4.0.4", "[3.0,4.0)", "LATEST"})
    void doesNotGuessUnlistedRangeDynamicCurrentOrFutureVersions(String version) {
        rewriteRun(xml(pom(version), source -> source.path("pom.xml")));
    }

    @Test
    void updatesAnExclusiveLocalMavenProperty() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><jasypt.version>3.0.3</jasypt.version></properties>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>${jasypt.version}</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><jasypt.version>4.0.3</jasypt.version></properties>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>${jasypt.version}</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void isolatesStarterWhenTheMavenPropertyIsShared() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><shared.version>3.0.5</shared.version></properties>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>${shared.version}</version>
                            </dependency>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot</artifactId>
                              <version>${shared.version}</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                          <properties><shared.version>3.0.5</shared.version></properties>
                          <dependencies>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId>
                              <artifactId>jasypt-spring-boot-starter</artifactId>
                              <version>4.0.3</version>
                            </dependency>
                            <dependency>
                              <groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot</artifactId>
                              <version>${shared.version}</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void leavesVersionlessExternalManagementUntouched() {
        rewriteRun(xml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.github.ulisesbocchio</groupId>
                      <artifactId>jasypt-spring-boot-starter</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """, source -> source.path("pom.xml")));
    }

    @Test
    void upgradesKotlinAndGroovyLiteralForms() {
        rewriteRun(
                buildGradleKts(
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.3")
                        }
                        """,
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:4.0.3")
                        }
                        """),
                buildGradle(
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies {
                            api group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: '2.1.1'
                        }
                        """,
                        """
                        plugins { id 'java' }
                        repositories { mavenCentral() }
                        dependencies {
                            api group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: '4.0.3'
                        }
                        """)
        );
    }

    @Test
    void leavesGradleVariablesAndCatalogAliasesForTheirOwningFiles() {
        rewriteRun(
                buildGradle("""
                        plugins { id 'java' }
                        def jasyptVersion = '3.0.5'
                        dependencies {
                            implementation "com.github.ulisesbocchio:jasypt-spring-boot-starter:$jasyptVersion"
                            testImplementation libs.jasypt
                        }
                        """)
        );
    }

    private static String pom(String version) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>app</artifactId><version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.github.ulisesbocchio</groupId>
                      <artifactId>jasypt-spring-boot-starter</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(version);
    }
}
