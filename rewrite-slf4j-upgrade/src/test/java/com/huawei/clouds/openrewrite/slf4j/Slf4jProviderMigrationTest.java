package com.huawei.clouds.openrewrite.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class Slf4jProviderMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(UpgradeSlf4jTest.environment().activateRecipes(UpgradeSlf4jTest.MIGRATION_RECIPE));
    }

    @Test
    void alignsSdkmanApiAndSimpleProvider() {
        // Reduced from sdkman/sdkman-cli at 1c8f4cb9101a7cbc2da6453c12bd547531bde29f:
        // https://github.com/sdkman/sdkman-cli/blob/1c8f4cb9101a7cbc2da6453c12bd547531bde29f/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    testImplementation('org.slf4j:slf4j-api:1.7.32')
                    testImplementation('org.slf4j:slf4j-simple:1.7.32')
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    testImplementation('org.slf4j:slf4j-api:2.0.17')
                    testImplementation('org.slf4j:slf4j-simple:2.0.17')
                }
                """
        ));
    }

    @Test
    void alignsProviderWithoutChangingSharedProperty() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><slf4j.version>1.7.36</slf4j.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>${slf4j.version}</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>${slf4j.version}</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>${slf4j.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                  <properties><slf4j.version>1.7.36</slf4j.version></properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-simple</artifactId><version>2.0.17</version></dependency>
                    <dependency><groupId>org.slf4j</groupId><artifactId>jul-to-slf4j</artifactId><version>${slf4j.version}</version></dependency>
                  </dependencies></dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void followsOfficialSlf4jLog4j12Relocation() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>reload4j</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.36</version></dependency>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-log4j12</artifactId><version>1.7.36</version><scope>runtime</scope></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>reload4j</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-reload4j</artifactId><version>2.0.17</version><scope>runtime</scope></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void followsOfficialSlf4jLog4j12RelocationInGradle() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:1.7.35'
                    runtimeOnly 'org.slf4j:slf4j-log4j12:1.7.35'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:2.0.17'
                    runtimeOnly 'org.slf4j:slf4j-reload4j:2.0.17'
                }
                """
        ));
    }

    @Test
    void selectsLog4jSlf4j2ProviderAndPreservesVersionAndScope() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>log4j</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.0</version></dependency>
                  <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-slf4j-impl</artifactId><version>2.20.0</version><scope>runtime</scope></dependency>
                </dependencies></project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>log4j</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                  <dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-slf4j2-impl</artifactId><version>2.20.0</version><scope>runtime</scope></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void selectsLog4jSlf4j2ProviderInKotlinDsl() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()).typeValidationOptions(TypeValidation.none()),
                buildGradleKts(
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation("org.slf4j:slf4j-api:1.7.36")
                            runtimeOnly("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
                        }
                        """,
                        """
                        plugins { java }
                        repositories { mavenCentral() }
                        dependencies {
                            implementation("org.slf4j:slf4j-api:2.0.17")
                            runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
                        }
                        """
                )
        );
    }

    @Test
    void marksLog4jVersionBeforeSlf4j2ProviderWasAvailable() {
        // Shape reduced from IONOS-Core/dim at 2bbdd1f74731f4400465ae548142a78871922152:
        // https://github.com/IONOS-Core/dim/blob/2bbdd1f74731f4400465ae548142a78871922152/pdns-output/pdns-output/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:1.7.32'
                    runtimeOnly 'org.apache.logging.log4j:log4j-slf4j-impl:2.17.1'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:2.0.17'
                    runtimeOnly /*~~(log4j-slf4j-impl targets SLF4J 1.x; upgrade Log4j to 2.19+ and use log4j-slf4j2-impl)~~>*/'org.apache.logging.log4j:log4j-slf4j-impl:2.17.1'
                }
                """
        ));
    }

    @Test
    void leavesAlreadyCompatibleSingleProviderUntouched() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ready</artifactId><version>1</version><dependencies>
                  <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.17</version></dependency>
                  <dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.5.18</version><scope>runtime</scope></dependency>
                </dependencies></project>
                """
        ));
    }

    @Test
    void changesOnlyGradleDependencyLiteral() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def migrationNote = 'org.apache.logging.log4j:log4j-slf4j-impl:2.20.0'
                dependencies {
                    implementation 'org.slf4j:slf4j-api:2.0.6'
                    runtimeOnly 'org.apache.logging.log4j:log4j-slf4j-impl:2.20.0'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def migrationNote = 'org.apache.logging.log4j:log4j-slf4j-impl:2.20.0'
                dependencies {
                    implementation 'org.slf4j:slf4j-api:2.0.17'
                    runtimeOnly 'org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0'
                }
                """
        ));
    }
}
