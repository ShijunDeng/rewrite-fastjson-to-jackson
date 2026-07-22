package com.huawei.clouds.openrewrite.hikaricp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class UpgradeHikariCPTest implements RewriteTest {
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.hikaricp.MigrateHikariCPTo6_3_3";
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.hikaricp.UpgradeHikariCPTo6_3_3";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(MIGRATION_RECIPE))
                .parser(JavaParser.fromJavaVersion().classpath("HikariCP"));
    }

    @ParameterizedTest(name = "Maven upgrades spreadsheet version {0}")
    @ValueSource(strings = {"3.3.0", "3.4.5", "4.0.3"})
    void upgradesEverySpreadsheetVersionInMaven(String oldVersion) {
        rewriteRun(pomXml(directPom(oldVersion), directPom("6.3.3")));
    }

    @Test
    void strictDependencyUpgradeIsIdempotent() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(directPom("4.0.3"), directPom("6.3.3"))
        );
    }

    @ParameterizedTest(name = "Gradle upgrades spreadsheet version {0}")
    @ValueSource(strings = {"3.3.0", "3.4.5", "4.0.3"})
    void upgradesEverySpreadsheetVersionInGradle(String oldVersion) {
        rewriteRun(buildGradle(
                gradleBuild("implementation", oldVersion),
                gradleBuild("implementation", "6.3.3")
        ));
    }

    @ParameterizedTest(name = "Gradle Kotlin upgrades spreadsheet version {0}")
    @ValueSource(strings = {"3.3.0", "3.4.5", "4.0.3"})
    void upgradesEverySpreadsheetVersionInGradleKotlin(String oldVersion) {
        rewriteRun(buildGradleKts(
                "plugins { java }\ndependencies { implementation(\"com.zaxxer:HikariCP:%s\") }".formatted(oldVersion),
                "plugins { java }\ndependencies { implementation(\"com.zaxxer:HikariCP:6.3.3\") }",
                source -> source.path("kotlin/build.gradle.kts")
        ));
    }

    @ParameterizedTest(name = "preserves Gradle configuration {0}")
    @ValueSource(strings = {"api", "implementation", "runtimeOnly", "compileOnly", "testImplementation"})
    void preservesGradleConfiguration(String configuration) {
        rewriteRun(buildGradle(
                gradleBuild(configuration, "4.0.3"),
                gradleBuild(configuration, "6.3.3")
        ));
    }

    @Test
    void upgradesMavenVersionProperty() {
        rewriteRun(pomXml(
                propertyPom("hikaricp.version", "3.3.0"),
                propertyPom("hikaricp.version", "6.3.3")
        ));
    }

    @Test
    void upgradesCaseSensitiveMavenVersionPropertyName() {
        rewriteRun(pomXml(
                propertyPom("HikariCP.version", "3.4.5"),
                propertyPom("HikariCP.version", "6.3.3")
        ));
    }

    @Test
    void preservesMavenPropertySharedOutsideHikariDependency() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>shared-property</artifactId><version>1</version>
                  <name>${database.version}</name>
                  <properties><database.version>3.4.5</database.version></properties>
                  <dependencies><dependency>
                    <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>${database.version}</version>
                  </dependency></dependencies>
                </project>
                """));
    }

    @Test
    void preservesAmbiguousDuplicateMavenPropertyDefinitions() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>duplicate-property</artifactId><version>1</version>
                  <properties><hikari.version>3.4.5</hikari.version></properties>
                  <dependencies><dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>${hikari.version}</version></dependency></dependencies>
                  <profiles><profile><id>alternate</id><properties><hikari.version>4.0.3</hikari.version></properties></profile></profiles>
                </project>
                """));
    }

    @Test
    void upgradesDirectDependencyManagementEntry() {
        rewriteRun(pomXml(
                managedPom("4.0.3"),
                managedPom("6.3.3")
        ));
    }

    @Test
    void upgradesDependencyManagementVersionProperty() {
        rewriteRun(pomXml(
                managedPropertyPom("3.4.5"),
                managedPropertyPom("6.3.3")
        ));
    }

    @Test
    void upgradesDependencyInsideActiveMavenProfile() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>profiled-pool</artifactId><version>1</version>
                  <profiles><profile><id>database</id><activation><activeByDefault>true</activeByDefault></activation>
                    <dependencies><dependency>
                      <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>3.3.0</version>
                    </dependency></dependencies>
                  </profile></profiles>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>profiled-pool</artifactId><version>1</version>
                  <profiles><profile><id>database</id><activation><activeByDefault>true</activeByDefault></activation>
                    <dependencies><dependency>
                      <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>6.3.3</version>
                    </dependency></dependencies>
                  </profile></profiles>
                </project>
                """
        ));
    }

    @Test
    void upgradesPropertyDeclaredInsideActiveMavenProfile() {
        rewriteRun(pomXml(
                profilePropertyPom("4.0.3"),
                profilePropertyPom("6.3.3")
        ));
    }

    @Test
    void preservesMavenDependencyMetadataOnMainJar() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>pool-details</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>3.4.5</version>
                    <type>jar</type><scope>runtime</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>
                  </dependency></dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>pool-details</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>6.3.3</version>
                    <type>jar</type><scope>runtime</scope><optional>true</optional>
                    <exclusions><exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion></exclusions>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesMultipleExplicitMavenOccurrences() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>multi-pool</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>3.3.0</version></dependency>
                    <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>4.0.3</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>multi-pool</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>6.3.3</version></dependency>
                    <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>6.3.3</version><scope>test</scope></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesGradleMapNotationAndPreservesConfiguration() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly group: 'com.zaxxer', name: 'HikariCP', version: '3.3.0'
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    runtimeOnly group: 'com.zaxxer', name: 'HikariCP', version: '6.3.3'
                }
                """
        ));
    }

    @Test
    void preservesGradleInterpolatedVersionVariableForStrictSourceSemantics() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        def hikariVersion = '3.4.5'
                        dependencies { api "com.zaxxer:HikariCP:$hikariVersion" }
                        """
                )
        );
    }

    @Test
    void preservesGradleMapVersionVariableForStrictSourceSemantics() {
        rewriteRun(
                spec -> spec.beforeRecipe(withToolingApi()),
                buildGradle(
                        """
                        plugins { id 'java-library' }
                        repositories { mavenCentral() }
                        def hikariVersion = '4.0.3'
                        dependencies {
                            runtimeOnly group: 'com.zaxxer', name: 'HikariCP', version: hikariVersion
                        }
                        """
                )
        );
    }

    @Test
    void preservesGradleAndKotlinRangesDynamicAndInterpolatedVersions() {
        rewriteRun(
                buildGradle("""
                        plugins { id 'java' }
                        def hikariVersion = '4.0.3'
                        dependencies {
                            implementation "com.zaxxer:HikariCP:$hikariVersion"
                            implementation 'com.zaxxer:HikariCP:[3.4,5.0)'
                            implementation 'com.zaxxer:HikariCP:4.+'
                        }
                        """),
                buildGradleKts("""
                        plugins { java }
                        val hikariVersion = "4.0.3"
                        dependencies { implementation("com.zaxxer:HikariCP:$hikariVersion") }
                        """, source -> source.path("variables/build.gradle.kts"))
        );
    }

    @Test
    void upgradesAxRinfinityOopMavenDependency() {
        // Reduced from AxRinfinity/OOP at 8b836552:
        // https://github.com/AxRinfinity/OOP/blob/8b83655242269105df65aef7f1d4de117f1672cf/pom.xml#L25-L30
        rewriteRun(pomXml(
                directRealPom("database-app", "3.4.5", "<scope>runtime</scope>"),
                directRealPom("database-app", "6.3.3", "<scope>runtime</scope>")
        ));
    }

    @Test
    void upgradesEvolveumMidpointManagedDependency() {
        // Reduced from Evolveum/midpoint at f6a8e484:
        // https://github.com/Evolveum/midpoint/blob/f6a8e48436f71690b7cdf38786951b6a562ad744/pom.xml#L532-L536
        rewriteRun(pomXml(managedPom("4.0.3"), managedPom("6.3.3")));
    }

    @Test
    void upgradesTechEmpowerGeminiVersionProperty() {
        // Reduced from TechEmpower/gemini at fdf8d8a2:
        // https://github.com/TechEmpower/gemini/blob/fdf8d8a24c6d94f8f303d61804cd36f444d96f87/pom.xml#L87-L88
        rewriteRun(pomXml(
                propertyPom("hikaricp.version", "3.4.5"),
                propertyPom("hikaricp.version", "6.3.3")
        ));
    }

    @Test
    void upgradesWalletLegacyGradleCompileDependency() {
        // Reduced from joaolucasl/wallet at d5f3c4f2:
        // https://github.com/joaolucasl/wallet/blob/d5f3c4f2897db79b5e6bb08c5640fe098bacbddb/build.gradle#L44-L56
        rewriteRun(buildGradle(
                """
                repositories { mavenCentral() }
                dependencies {
                    compile "io.ktor:ktor-server-core:1.3.2"
                    compile "com.zaxxer:HikariCP:3.4.5"
                    compile "org.postgresql:postgresql:42.2.12"
                }
                """,
                """
                repositories { mavenCentral() }
                dependencies {
                    compile "io.ktor:ktor-server-core:1.3.2"
                    compile "com.zaxxer:HikariCP:6.3.3"
                    compile "org.postgresql:postgresql:42.2.12"
                }
                """
        ));
    }

    @Test
    void upgradesVolunteerPortalLegacyGradleRuntimeDependency() {
        // Reduced from AtlasOfLivingAustralia/volunteer-portal at afcc2b3c:
        // https://github.com/AtlasOfLivingAustralia/volunteer-portal/blob/afcc2b3cfd001a4d4de4e043b15582196c897aae/build.gradle.backup.txt#L199-L209
        rewriteRun(buildGradle(
                """
                repositories { mavenCentral() }
                dependencies {
                    runtime "org.postgresql:postgresql:42.3.1"
                    runtime "com.zaxxer:HikariCP:4.0.3"
                }
                """,
                """
                repositories { mavenCentral() }
                dependencies {
                    runtime "org.postgresql:postgresql:42.3.1"
                    runtime "com.zaxxer:HikariCP:6.3.3"
                }
                """
        ));
    }

    @Test
    void upgradesEormKotlinDslWithoutRequiringSemanticModel() {
        // Reduced from 4o4E/EOrm at 5755ae39. The strict literal visitor does not require
        // a GradleProject marker and preserves the surrounding Kotlin DSL structure.
        // https://github.com/4o4E/EOrm/blob/5755ae3941d481f9e32239bb4a488af35b3a441e/eorm-core/build.gradle.kts#L1-L13
        rewriteRun(buildGradleKts(
                """
                plugins { kotlin("jvm") version "2.0.21" }
                repositories { mavenCentral() }
                dependencies {
                    implementation("org.slf4j:slf4j-api:2.0.16")
                    implementation("com.zaxxer:HikariCP:4.0.3")
                    implementation(kotlin("reflect"))
                }
                """,
                """
                plugins { kotlin("jvm") version "2.0.21" }
                repositories { mavenCentral() }
                dependencies {
                    implementation("org.slf4j:slf4j-api:2.0.16")
                    implementation("com.zaxxer:HikariCP:6.3.3")
                    implementation(kotlin("reflect"))
                }
                """
        ));
    }

    @Test
    void combinesAdjacentCredentialSettersAtomically() {
        rewriteRun(java(
                """
                import com.zaxxer.hikari.HikariConfig;

                class PoolFactory {
                    void configure(HikariConfig config, String username, String password) {
                        config.setUsername(username);
                        config.setPassword(password);
                    }
                }
                """,
                """
                import com.zaxxer.hikari.HikariConfig;
                import com.zaxxer.hikari.util.Credentials;

                class PoolFactory {
                    void configure(HikariConfig config, String username, String password) {
                        config.setCredentials(Credentials.of(username, password));
                    }
                }
                """
        ));
    }

    @Test
    void migratesCloudbreakDynamicCredentialSubclass() {
        // Reduced from hortonworks/cloudbreak before its production HikariCP 6.3.3 fix:
        // https://github.com/hortonworks/cloudbreak/blob/9e75d595c46134661c6adf36afda57f73b806a00/service-common/src/main/java/com/sequenceiq/cloudbreak/database/RdsIamAuthBasedHikariDataSource.java
        // The real fix is commit 92b69e5979521b36f4ebfddcfd0b4e9d694dae4c.
        rewriteRun(java(
                """
                import com.zaxxer.hikari.HikariDataSource;

                class RdsIamAuthBasedHikariDataSource extends HikariDataSource {
                    private final TokenProvider authenticationTokenProvider;

                    RdsIamAuthBasedHikariDataSource(TokenProvider authenticationTokenProvider) {
                        this.authenticationTokenProvider = authenticationTokenProvider;
                    }

                    @Override
                    public String getPassword() {
                        return authenticationTokenProvider.getToken(getJdbcUrl(), getUsername());
                    }
                }

                interface TokenProvider {
                    String getToken(String jdbcUrl, String username);
                }
                """,
                """
                import com.zaxxer.hikari.HikariDataSource;
                import com.zaxxer.hikari.util.Credentials;

                class RdsIamAuthBasedHikariDataSource extends HikariDataSource {
                    private final TokenProvider authenticationTokenProvider;

                    RdsIamAuthBasedHikariDataSource(TokenProvider authenticationTokenProvider) {
                        this.authenticationTokenProvider = authenticationTokenProvider;
                    }

                    @Override
                    public String getPassword() {
                        return authenticationTokenProvider.getToken(getJdbcUrl(), getUsername());
                    }

                    @Override
                    public Credentials getCredentials() {
                        return Credentials.of(getUsername(), getPassword());
                    }
                }

                interface TokenProvider {
                    String getToken(String jdbcUrl, String username);
                }
                """
        ));
    }

    @Test
    void preservesExistingCredentialsOverride() {
        rewriteRun(java(
                """
                import com.zaxxer.hikari.HikariDataSource;
                import com.zaxxer.hikari.util.Credentials;

                class DynamicDataSource extends HikariDataSource {
                    @Override
                    public String getPassword() {
                        return "rotated";
                    }

                    @Override
                    public Credentials getCredentials() {
                        return Credentials.of(getUsername(), getPassword());
                    }
                }
                """
        ));
    }

    @Test
    void leavesNonAdjacentCredentialUpdatesForReview() {
        rewriteRun(java(
                """
                import com.zaxxer.hikari.HikariConfig;

                class PoolFactory {
                    void configure(HikariConfig config, String username, String password) {
                        config.setUsername(username);
                        validate(username);
                        config.setPassword(password);
                    }

                    void validate(String username) {
                    }
                }
                """
        ));
    }

    @Test
    void marksRealSQLExceptionOverridePolicyFromSreworks() {
        // Reduced from alibaba/SREWorks at 5eb36fa9170fb737a06d9e690bc6df90a9924067:
        // https://github.com/alibaba/SREWorks/blob/5eb36fa9170fb737a06d9e690bc6df90a9924067/paas/appmanager/tesla-appmanager-spring/src/main/java/com/alibaba/tesla/appmanager/spring/config/DatasourceExceptionConfig.java
        rewriteRun(java(
                """
                import com.zaxxer.hikari.SQLExceptionOverride;
                import java.sql.SQLException;

                public class DatasourceExceptionConfig implements SQLExceptionOverride {
                    @Override
                    public Override adjudicate(SQLException sqlException) {
                        return Override.CONTINUE_EVICT;
                    }
                }
                """,
                """
                import com.zaxxer.hikari.SQLExceptionOverride;
                import java.sql.SQLException;

                /*~~(HikariCP 6.2 no longer evicts SQLTimeoutException by default; decide whether this policy must return MUST_EVICT)~~>*/public class DatasourceExceptionConfig implements SQLExceptionOverride {
                    @Override
                    public Override adjudicate(SQLException sqlException) {
                        return Override.CONTINUE_EVICT;
                    }
                }
                """
        ));
    }

    @Test
    void marksDirectConfigMxBeanImplementation() {
        rewriteRun(java(
                """
                import com.zaxxer.hikari.HikariConfigMXBean;

                abstract class CustomConfigView implements HikariConfigMXBean {
                }
                """,
                """
                import com.zaxxer.hikari.HikariConfigMXBean;

                /*~~(HikariCP 6 adds HikariConfigMXBean.setCredentials(Credentials); implement an atomic credential update)~~>*/abstract class CustomConfigView implements HikariConfigMXBean {
                }
                """
        ));
    }

    @Test
    void marksTemporaryLegacyCredentialSystemProperty() {
        rewriteRun(java(
                """
                class Bootstrap {
                    void configure() {
                        System.setProperty("com.zaxxer.hikari.legacy.supportUserPassDataSourceOverride", "true");
                    }
                }
                """,
                """
                class Bootstrap {
                    void configure() {
                        System.setProperty(/*~~(Temporary HikariCP legacy credential override detected; migrate the DataSource subclass to getCredentials())~~>*/"com.zaxxer.hikari.legacy.supportUserPassDataSourceOverride", "true");
                    }
                }
                """
        ));
    }

    @Test
    void preservesDisabledKeepaliveInSpringProperties() {
        rewriteRun(properties(
                """
                spring.datasource.url=jdbc:postgresql://localhost/app
                spring.datasource.hikari.maximum-pool-size=20
                """,
                """
                # HikariCP 6.2.1 defaults keepalive to 120000ms; zero preserves the 3.x/4.x behavior
                spring.datasource.hikari.keepalive-time=0
                spring.datasource.url=jdbc:postgresql://localhost/app
                spring.datasource.hikari.maximum-pool-size=20
                """,
                spec -> spec.path("src/main/resources/application.properties")
        ));
    }

    @Test
    void preservesDisabledKeepaliveInSpringYaml() {
        rewriteRun(yaml(
                """
                spring:
                  datasource:
                    url: jdbc:postgresql://localhost/app
                    hikari:
                      maximum-pool-size: 20
                """,
                """
                spring:
                  datasource:
                    url: jdbc:postgresql://localhost/app
                    hikari:
                      maximum-pool-size: 20
                      keepalive-time: 0
                """,
                spec -> spec.path("src/main/resources/application.yml")
        ));
    }

    @Test
    void preservesExplicitKeepalivePolicy() {
        rewriteRun(
                properties(
                        """
                        spring.datasource.hikari.maximum-pool-size=20
                        spring.datasource.hikari.keepalive-time=60000
                        """,
                        spec -> spec.path("src/main/resources/application.properties")
                ),
                yaml(
                        """
                        spring:
                          datasource:
                            hikari:
                              maximum-pool-size: 20
                              keepalive-time: 60s
                        """,
                        spec -> spec.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void doesNotTreatGenericMaximumPoolSizeAsSpringHikariConfiguration() {
        rewriteRun(properties(
                "maximum-pool-size=20\n",
                spec -> spec.path("src/main/resources/database.properties")
        ));
    }

    @Test
    void marksMavenJava8Baseline() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>java8-pool</artifactId><version>1</version>
                  <properties><java.version>1.8</java.version></properties>
                  <dependencies><dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>6.3.3</version></dependency></dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>java8-pool</artifactId><version>1</version>
                  <properties><java.version><!--~~(HikariCP 6.3.3 requires Java 11 or newer; upgrade the build toolchain and runtime together)~~>-->1.8</java.version></properties>
                  <dependencies><dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>6.3.3</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void doesNotMarkJavaBaselineWithoutAnOwnedHikariDependency() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>unrelated-java8</artifactId><version>1</version>
                  <properties><java.version>8</java.version></properties>
                  <dependencies><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.7</version></dependency></dependencies>
                </project>
                """));
    }

    @Test
    void dependencyOnlyRecipeDoesNotRewriteCredentialsOrConfiguration() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE)),
                java(
                        """
                        import com.zaxxer.hikari.HikariConfig;

                        class PoolFactory {
                            void configure(HikariConfig config, String username, String password) {
                                config.setUsername(username);
                                config.setPassword(password);
                            }
                        }
                        """
                ),
                properties(
                        "spring.datasource.hikari.maximum-pool-size=20\n",
                        spec -> spec.path("src/main/resources/application.properties")
                )
        );
    }

    @Test
    void preservesMavenBomManagedVersionlessDependency() {
        rewriteRun(org.openrewrite.xml.Assertions.xml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.4.5</version></parent>
                  <groupId>example</groupId><artifactId>boot-pool</artifactId><version>1</version>
                  <dependencies><dependency>
                    <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId>
                  </dependency></dependencies>
                </project>
                """, source -> source.path("bom-managed/pom.xml")
        ));
    }

    @Test
    void upgradesLocallyManagedVersionAndKeepsConsumerVersionless() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>local-bom</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>4.0.3</version>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId></dependency></dependencies>
                </project>
                """,
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>local-bom</artifactId><version>1</version>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>6.3.3</version>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies><dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void preservesGradleVersionlessDependency() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies { implementation 'com.zaxxer:HikariCP' }
                """
        ));
    }

    @Test
    void preservesGeneratedBuildDescriptorsAndBaselineMetadata() {
        rewriteRun(
                pomXml(directPom("4.0.3"), source -> source.path("target/pom.xml")),
                buildGradle(
                        "plugins { id 'java' }\ndependencies { implementation 'com.zaxxer:HikariCP:3.4.5' }",
                        source -> source.path("build/generated/build.gradle")
                ),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>generated-baseline</artifactId><version>1</version>
                          <properties><java.version>8</java.version></properties>
                        </project>
                        """, source -> source.path("target/generated/pom.xml"))
        );
    }

    @Test
    void preservesGeneratedJavaMigrationCandidates() {
        rewriteRun(java(
                """
                import com.zaxxer.hikari.HikariConfig;

                class GeneratedPoolFactory {
                    void configure(HikariConfig config, String username, String password) {
                        config.setUsername(username);
                        config.setPassword(password);
                        System.setProperty("com.zaxxer.hikari.legacy.supportUserPassDataSourceOverride", "true");
                    }
                }
                """,
                source -> source.path("build/generated/sources/GeneratedPoolFactory.java")
        ));
    }

    @Test
    void leavesSupportedJavaBaselinesAndUnrelatedXmlPropertiesUnmarked() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>java17</artifactId><version>1</version>
                          <properties><java.version>17</java.version><maven.compiler.release>21</maven.compiler.release></properties>
                        </project>
                        """),
                org.openrewrite.xml.Assertions.xml(
                        "<configuration><properties><java.version>8</java.version></properties></configuration>",
                        source -> source.path("src/main/resources/database.xml")
                )
        );
    }

    @ParameterizedTest(name = "does not downgrade HikariCP {0}")
    @ValueSource(strings = {"6.3.3", "7.0.0", "7.0.1", "7.0.2"})
    void preservesTargetAndNewerVersions(String version) {
        rewriteRun(pomXml(directPom(version)));
    }

    @Test
    void preservesUnlistedOlderVersion() {
        rewriteRun(org.openrewrite.xml.Assertions.xml(
                directPom("5.1.0"), source -> source.path("unlisted/pom.xml")));
    }

    @Test
    void doesNotChangeSimilarOrLegacyCoordinates() {
        rewriteRun(org.openrewrite.xml.Assertions.xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>similar</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP-java6</artifactId><version>2.3.13</version></dependency>
                    <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP-java7</artifactId><version>2.4.13</version></dependency>
                    <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-hikaricp</artifactId><version>5.6.15.Final</version></dependency>
                  </dependencies>
                </project>
                """, source -> source.path("legacy/pom.xml")
        ));
    }

    @Test
    void doesNotChangeWrongGroupInGradle() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'example:HikariCP:4.0.3'
                }
                """
        ));
    }

    @Test
    void doesNotTreatSameCoordinateMavenPluginAsDependency() {
        rewriteRun(pomXml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugin</artifactId><version>1</version>
                  <build><plugins><plugin>
                    <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>4.0.3</version>
                  </plugin></plugins></build>
                </project>
                """
        ));
    }

    @Test
    void preservesPluginDependenciesAndNonMainArtifacts() {
        rewriteRun(pomXml("""
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>non-main-artifacts</artifactId><version>1</version>
                  <properties><plugin.hikari.version>3.4.5</plugin.hikari.version></properties>
                  <dependencies>
                    <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>3.4.5</version><classifier>sources</classifier></dependency>
                    <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>4.0.3</version><type>test-jar</type></dependency>
                  </dependencies>
                  <build><plugins><plugin><groupId>example</groupId><artifactId>codegen</artifactId><version>1</version>
                    <dependencies><dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>${plugin.hikari.version}</version></dependency></dependencies>
                  </plugin></plugins></build>
                </project>
                """));
    }

    @Test
    void doesNotTreatGroovyMethodNamedImplementationOutsideDependenciesAsADeclaration() {
        rewriteRun(buildGradle("""
                plugins { id 'java' }
                void implementation(String value) { println value }
                implementation('com.zaxxer:HikariCP:4.0.3')
                """));
    }

    @Test
    void doesNotTreatKotlinMethodNamedImplementationOutsideDependenciesAsADeclaration() {
        rewriteRun(buildGradleKts("""
                plugins { java }
                fun implementation(value: String) = println(value)
                implementation("com.zaxxer:HikariCP:4.0.3")
                """, source -> source.path("custom/build.gradle.kts")));
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        assertEquals(3, UpgradeSelectedHikariCPDependency.SOURCE_VERSIONS.size());
        for (String name : new String[]{MIGRATION_RECIPE, DEPENDENCY_RECIPE}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
        }
    }

    private static String directPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>hikari-app</artifactId><version>1</version>
                 <dependencies><dependency>
                   <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>%s</version>
                 </dependency></dependencies>
               </project>
               """.formatted(version);
    }

    private static String directRealPom(String artifactId, String version, String metadata) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>%s</artifactId><version>1</version>
                 <dependencies>
                   <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>6.4.0.jre8</version></dependency>
                   <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>%s</version>%s</dependency>
                   <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>1.7.32</version></dependency>
                 </dependencies>
               </project>
               """.formatted(artifactId, version, metadata);
    }

    private static String propertyPom(String propertyName, String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>property-pool</artifactId><version>1</version>
                 <properties><%1$s>%2$s</%1$s></properties>
                 <dependencies><dependency>
                   <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>${%1$s}</version>
                 </dependency></dependencies>
               </project>
               """.formatted(propertyName, version);
    }

    private static String managedPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>managed-pool</artifactId><version>1</version>
                 <dependencyManagement><dependencies><dependency>
                   <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>%s</version>
                 </dependency></dependencies></dependencyManagement>
               </project>
               """.formatted(version);
    }

    private static String managedPropertyPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>managed-property-pool</artifactId><version>1</version>
                 <properties><hikaricp.version>%s</hikaricp.version></properties>
                 <dependencyManagement><dependencies><dependency>
                   <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>${hikaricp.version}</version>
                 </dependency></dependencies></dependencyManagement>
               </project>
               """.formatted(version);
    }

    private static String profilePropertyPom(String version) {
        return """
               <project>
                 <modelVersion>4.0.0</modelVersion>
                 <groupId>example</groupId><artifactId>profile-property-pool</artifactId><version>1</version>
                 <profiles><profile><id>database</id><activation><activeByDefault>true</activeByDefault></activation>
                   <properties><hikari.profile.version>%s</hikari.profile.version></properties>
                   <dependencies><dependency>
                     <groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>${hikari.profile.version}</version>
                   </dependency></dependencies>
                 </profile></profiles>
               </project>
               """.formatted(version);
    }

    private static String gradleBuild(String configuration, String version) {
        return """
               plugins { id 'java-library' }
               repositories { mavenCentral() }
               dependencies { %s 'com.zaxxer:HikariCP:%s' }
               """.formatted(configuration, version);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.hikaricp")
                .scanYamlResources()
                .build();
    }
}
