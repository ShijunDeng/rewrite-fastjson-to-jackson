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
    void leavesSharedMavenPropertyAndConsumerUntouched() {
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

    @Test
    void leavesGroovyMapVersionAndClassifierVariablesToTheirOwner() {
        rewriteRun(
                buildGradle("""
                        plugins { id 'java' }
                        def jasyptVersion = '3.0.5'
                        def variant = 'tests'
                        dependencies {
                            implementation group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: jasyptVersion
                            testImplementation group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: '3.0.5', classifier: variant
                        }
                        """)
        );
    }

    @Test
    void ignoresStarterCoordinateInsideMavenPluginDependencies() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>plugin-host</artifactId><version>1</version>
                  <build><plugins><plugin>
                    <groupId>example</groupId><artifactId>codegen-plugin</artifactId><version>1</version>
                    <dependencies><dependency>
                      <groupId>com.github.ulisesbocchio</groupId>
                      <artifactId>jasypt-spring-boot-starter</artifactId>
                      <version>3.0.5</version>
                    </dependency></dependencies>
                    <configuration><profile><dependencies><dependency>
                      <groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot-starter</artifactId><version>3.0.5</version>
                    </dependency></dependencies></profile></configuration>
                  </plugin></plugins></build>
                </project>
                """),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shadow</artifactId><version>1</version>
                          <properties><jasypt.version>2.1.2</jasypt.version></properties>
                          <dependencies><dependency><groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot-starter</artifactId><version>${jasypt.version}</version></dependency></dependencies>
                          <profiles><profile><id>override</id><properties><jasypt.version>3.0.5</jasypt.version></properties></profile></profiles>
                        </project>
                        """, source -> source.path("shadow/pom.xml")),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicate</artifactId><version>1</version>
                          <properties><jasypt.version>2.1.2</jasypt.version><jasypt.version>3.0.5</jasypt.version></properties>
                          <dependencies><dependency><groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot-starter</artifactId><version>${jasypt.version}</version></dependency></dependencies>
                        </project>
                        """, source -> source.path("duplicate/pom.xml"))
        );
    }

    @Test
    void ignoresGradleLookalikesOutsideDependenciesBlock() {
        rewriteRun(buildGradle("""
                plugins { id 'java' }
                def documentation = 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
                migrationExamples {
                    implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5'
                }
                dependencies {
                    generatedFixture { implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5' }
                }
                """));
    }

    @Test
    void leavesClassifierVariantsUntouchedInMavenAndGradle() {
        rewriteRun(
                pomXml("""
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>classified</artifactId><version>1</version>
                          <dependencies><dependency>
                            <groupId>com.github.ulisesbocchio</groupId>
                            <artifactId>jasypt-spring-boot-starter</artifactId>
                            <version>3.0.4</version><classifier>tests</classifier>
                          </dependency></dependencies>
                        </project>
                        """),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>non-jar</artifactId><version>1</version><dependencies><dependency>
                          <groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot-starter</artifactId>
                          <version>3.0.4</version><type>test-jar</type>
                        </dependency></dependencies></project>
                        """, source -> source.path("non-jar/pom.xml")),
                buildGradle("""
                        plugins { id 'java' }
                        dependencies {
                            implementation 'com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.4:tests'
                            implementation group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: '3.0.4', classifier: 'tests'
                            implementation group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: '3.0.4', ext: 'zip'
                            implementation([group: 'com.github.ulisesbocchio', name: 'jasypt-spring-boot-starter', version: '3.0.4', type: 'test-jar'])
                        }
                        """)
        );
    }

    @Test
    void leavesPropertySharedWithPluginAttributeUntouched() {
        rewriteRun(pomXml("""
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>shared-owner</artifactId><version>1</version>
                  <properties><jasypt.version>3.0.3</jasypt.version></properties>
                  <dependencies><dependency>
                    <groupId>com.github.ulisesbocchio</groupId>
                    <artifactId>jasypt-spring-boot-starter</artifactId>
                    <version>${jasypt.version}</version>
                  </dependency></dependencies>
                  <build><plugins><plugin>
                    <groupId>example</groupId><artifactId>metadata-plugin</artifactId><version>1</version>
                    <configuration><option value="${jasypt.version}"/></configuration>
                  </plugin></plugins></build>
                </project>
                """));
    }

    @Test
    void upgradesOneRootPropertyUsedOnlyByMultipleStarterDeclarations() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>profiles</artifactId><version>1</version>
                  <properties><jasypt.version>2.1.2</jasypt.version></properties>
                  <dependencies><dependency>
                    <groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot-starter</artifactId>
                    <version>${jasypt.version}</version>
                  </dependency></dependencies>
                  <profiles><profile><id>integration</id><dependencies><dependency>
                    <groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot-starter</artifactId>
                    <version>${jasypt.version}</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>profiles</artifactId><version>1</version>
                  <properties><jasypt.version>4.0.3</jasypt.version></properties>
                  <dependencies><dependency>
                    <groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot-starter</artifactId>
                    <version>${jasypt.version}</version>
                  </dependency></dependencies>
                  <profiles><profile><id>integration</id><dependencies><dependency>
                    <groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot-starter</artifactId>
                    <version>${jasypt.version}</version>
                  </dependency></dependencies></profile></profiles>
                </project>
                """));
    }

    @Test
    void updatesOnlyTheRootPropertyNotNestedPluginConfiguration() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>nested-properties</artifactId><version>1</version>
                  <properties><jasypt.version>3.0.5</jasypt.version></properties>
                  <dependencies><dependency>
                    <groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot-starter</artifactId>
                    <version>${jasypt.version}</version>
                  </dependency></dependencies>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>x</artifactId><version>1</version>
                    <configuration><properties><jasypt.version>3.0.5</jasypt.version></properties></configuration>
                  </plugin></plugins></build>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>nested-properties</artifactId><version>1</version>
                  <properties><jasypt.version>4.0.3</jasypt.version></properties>
                  <dependencies><dependency>
                    <groupId>com.github.ulisesbocchio</groupId><artifactId>jasypt-spring-boot-starter</artifactId>
                    <version>${jasypt.version}</version>
                  </dependency></dependencies>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>x</artifactId><version>1</version>
                    <configuration><properties><jasypt.version>3.0.5</jasypt.version></properties></configuration>
                  </plugin></plugins></build>
                </project>
                """));
    }

    @Test
    void ignoresGeneratedBuildFiles() {
        rewriteRun(xml(pom("3.0.5"), source -> source.path("target/generated/pom.xml")));
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
