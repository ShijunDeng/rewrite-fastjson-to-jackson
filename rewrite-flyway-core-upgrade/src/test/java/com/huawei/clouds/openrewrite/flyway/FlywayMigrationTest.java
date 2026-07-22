package com.huawei.clouds.openrewrite.flyway;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;

class FlywayMigrationTest implements RewriteTest {
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.flyway.MigrateFlywayTo11_14_1";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(MIGRATION_RECIPE));
    }

    @Test
    void isolatesAPropertySharedWithAnUnrelatedDependency() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedFlywayCoreDependency()),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                          <properties><shared.version>9.20.0</shared.version></properties>
                          <dependencies>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>${shared.version}</version></dependency>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>${shared.version}</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId><artifactId>shared</artifactId><version>1</version>
                          <properties><shared.version>9.20.0</shared.version></properties>
                          <dependencies>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>11.14.1</version></dependency>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>${shared.version}</version></dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void preservesRangesKotlinCatalogVariablesAndUnlistedVersions() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedFlywayCoreDependency()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ranges</artifactId><version>1</version>
                          <dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>[9.0,10.0)</version></dependency></dependencies>
                        </project>
                        """),
                buildGradleKts("""
                        plugins { java }
                        val flywayVersion = "9.20.0"
                        dependencies { implementation("org.flywaydb:flyway-core:$flywayVersion") }
                        """, source -> source.path("variables.gradle.kts")),
                buildGradle("""
                        plugins { id 'java' }
                        dependencies { implementation 'org.flywaydb:flyway-core:8.2.2' }
                        """, source -> source.path("unlisted.gradle"))
        );
    }

    @Test
    void upgradesExclusiveHaloFlywayVersionVariable() {
        // Reduced from halo-dev/halo at fixed commit 6533089555d7915b2af38802f9797cd68ece4586.
        // https://github.com/halo-dev/halo/blob/6533089555d7915b2af38802f9797cd68ece4586/build.gradle#L100-L154
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedFlywayCoreDependency()),
                buildGradle(
                        """
                        ext {
                            flywayVersion = "7.15.0"
                        }
                        dependencies {
                            implementation "org.flywaydb:flyway-core:$flywayVersion"
                        }
                        """,
                        """
                        ext {
                            flywayVersion = "11.14.1"
                        }
                        dependencies {
                            implementation "org.flywaydb:flyway-core:$flywayVersion"
                        }
                        """
                )
        );
    }

    @Test
    void preservesFlywayVersionVariableSharedWithAnotherCoordinate() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedFlywayCoreDependency()),
                buildGradle("""
                        ext { flywayVersion = "7.15.0" }
                        dependencies {
                            implementation "org.flywaydb:flyway-core:$flywayVersion"
                            implementation "example:other:$flywayVersion"
                        }
                        """, source -> source.path("shared-variable.gradle"))
        );
    }

    @Test
    void isolatesAPropertySharedByFlywayAndAnotherMavenPlugin() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedFlywayBuildPlugins()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugins</artifactId><version>1</version>
                          <properties><plugin.version>7.15.0</plugin.version></properties>
                          <build><plugins>
                            <plugin><groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>${plugin.version}</version></plugin>
                          </plugins></build>
                          <dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>${plugin.version}</version></dependency></dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>plugins</artifactId><version>1</version>
                          <properties><plugin.version>7.15.0</plugin.version></properties>
                          <build><plugins>
                            <plugin><groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><version>11.14.1</version></plugin>
                          </plugins></build>
                          <dependencies><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>${plugin.version}</version></dependency></dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void upgradesKotlinPluginButNotUnlistedOrRedgatePlugins() {
        rewriteRun(
                spec -> spec.recipe(new UpgradeSelectedFlywayBuildPlugins()),
                buildGradleKts(
                        """
                        plugins {
                            id("org.flywaydb.flyway") version "9.16.3"
                            id("com.redgate.flyway") version "9.16.3"
                        }
                        """,
                        """
                        plugins {
                            id("org.flywaydb.flyway") version "11.14.1"
                            id("com.redgate.flyway") version "9.16.3"
                        }
                        """
                ),
                buildGradle("""
                        plugins { id 'org.flywaydb.flyway' version '8.2.2' }
                        """, source -> source.path("unlisted-plugin.gradle"))
        );
    }

    @Test
    void addsAllUnambiguousMavenDatabaseCompanions() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>all-databases</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>9.20.0</version><scope>runtime</scope></dependency>
                            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.7</version></dependency>
                            <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><version>9.4.0</version></dependency>
                            <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.0.jre11</version></dependency>
                            <dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc11</artifactId><version>23.9.0.25.07</version></dependency>
                            <dependency><groupId>com.ibm.db2</groupId><artifactId>jcc</artifactId><version>12.1.2.0</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>all-databases</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>11.14.1</version><scope>runtime</scope></dependency>
                            <dependency>
                              <groupId>org.flywaydb</groupId>
                              <artifactId>flyway-database-postgresql</artifactId>
                              <version>11.14.1</version>
                              <scope>runtime</scope>
                            </dependency>
                            <dependency>
                              <groupId>org.flywaydb</groupId>
                              <artifactId>flyway-mysql</artifactId>
                              <version>11.14.1</version>
                              <scope>runtime</scope>
                            </dependency>
                            <dependency>
                              <groupId>org.flywaydb</groupId>
                              <artifactId>flyway-sqlserver</artifactId>
                              <version>11.14.1</version>
                              <scope>runtime</scope>
                            </dependency>
                            <dependency>
                              <groupId>org.flywaydb</groupId>
                              <artifactId>flyway-database-oracle</artifactId>
                              <version>11.14.1</version>
                              <scope>runtime</scope>
                            </dependency>
                            <dependency>
                              <groupId>org.flywaydb</groupId>
                              <artifactId>flyway-database-db2</artifactId>
                              <version>11.14.1</version>
                              <scope>runtime</scope>
                            </dependency>
                            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.7</version></dependency>
                            <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><version>9.4.0</version></dependency>
                            <dependency><groupId>com.microsoft.sqlserver</groupId><artifactId>mssql-jdbc</artifactId><version>13.2.0.jre11</version></dependency>
                            <dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc11</artifactId><version>23.9.0.25.07</version></dependency>
                            <dependency><groupId>com.ibm.db2</groupId><artifactId>jcc</artifactId><version>12.1.2.0</version></dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void doesNotAddCompanionForManagedCoreAndDoesNotDuplicateExistingModule() {
        rewriteRun(
                spec -> spec.recipe(new AddFlywayDatabaseModules()),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion>
                          <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.1.3</version></parent>
                          <groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
                            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>
                          </dependencies>
                        </project>
                        """),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>present</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>11.14.1</version></dependency>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId><version>11.14.1</version></dependency>
                            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.7</version></dependency>
                          </dependencies>
                        </project>
                        """, source -> source.path("present-pom.xml"))
        );
    }

    @Test
    void migratesPropertiesExactlyAndPreservesLookalikes() {
        rewriteRun(
                spec -> spec.recipe(new MigrateFlywayProperties()),
                properties(
                        """
                        flyway.check.reportFilename=report.html
                        flyway.oracleKerberosConfigFile=/etc/krb5.conf
                        spring.flyway.oracle-kerberos-config-file=/etc/spring.krb5.conf
                        flyway.plugins.clean=all
                        flyway.plugins.clean.schemas.exclude=a,b
                        flyway.plugins.vault.url=https://vault.example
                        flyway.plugins.dapr.url=http://dapr
                        flyway.plugins.gcsm.project=my-project
                        flyway.locations=db/migration,filesystem:/opt/sql,classpath:custom,${EXTERNAL_LOCATION}
                        spring.flyway.locations=db/spring,filesystem:/opt/spring
                        example.flyway.check.reportFilename=keep
                        """,
                        """
                        flyway.reportFilename=report.html
                        flyway.kerberosConfigFile=/etc/krb5.conf
                        spring.flyway.kerberos-config-file=/etc/spring.krb5.conf
                        flyway.sqlserver.clean.mode=all
                        flyway.sqlserver.clean.schemas.exclude=a,b
                        flyway.vault.url=https://vault.example
                        flyway.dapr.url=http://dapr
                        flyway.gcsm.project=my-project
                        flyway.locations=classpath:db/migration,filesystem:/opt/sql,classpath:custom,${EXTERNAL_LOCATION}
                        spring.flyway.locations=classpath:db/spring,filesystem:/opt/spring
                        example.flyway.check.reportFilename=keep
                        """,
                        source -> source.path("flyway.conf")
                )
        );
    }

    @Test
    void migratesMavenFlywayPluginConfigurationOnly() {
        rewriteRun(
                spec -> spec.recipe(new MigrateFlywayBuildConfiguration()),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>config</artifactId><version>1</version>
                          <build><plugins>
                            <plugin><groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><configuration>
                              <checkReportFilename>report.html</checkReportFilename>
                              <oracleKerberosConfigFile>/etc/krb5.conf</oracleKerberosConfigFile>
                              <locations><location>db/migration</location><location>filesystem:/opt/sql</location></locations>
                            </configuration></plugin>
                            <plugin><groupId>example</groupId><artifactId>other</artifactId><configuration><checkReportFilename>keep</checkReportFilename></configuration></plugin>
                          </plugins></build>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>config</artifactId><version>1</version>
                          <build><plugins>
                            <plugin><groupId>org.flywaydb</groupId><artifactId>flyway-maven-plugin</artifactId><configuration>
                              <reportFilename>report.html</reportFilename>
                              <kerberosConfigFile>/etc/krb5.conf</kerberosConfigFile>
                              <locations><location>classpath:db/migration</location><location>filesystem:/opt/sql</location></locations>
                            </configuration></plugin>
                            <plugin><groupId>example</groupId><artifactId>other</artifactId><configuration><checkReportFilename>keep</checkReportFilename></configuration></plugin>
                          </plugins></build>
                        </project>
                        """
                )
        );
    }

    @Test
    void migratesGroovyAndKotlinFlywayBlocksOnly() {
        rewriteRun(
                spec -> spec.recipe(new MigrateFlywayBuildConfiguration()),
                buildGradle(
                        """
                        flyway {
                            checkReportFilename = 'report.html'
                            oracleKerberosConfigFile = '/etc/krb5.conf'
                            locations = ['db/migration', 'filesystem:/opt/sql']
                        }
                        checkReportFilename = 'keep'
                        """,
                        """
                        flyway {
                            reportFilename = 'report.html'
                            kerberosConfigFile = '/etc/krb5.conf'
                            locations = ['classpath:db/migration', 'filesystem:/opt/sql']
                        }
                        checkReportFilename = 'keep'
                        """
                ),
                buildGradleKts(
                        """
                        flyway {
                            checkReportFilename = "report.html"
                            locations = arrayOf("db/migration", "classpath:custom")
                        }
                        """,
                        """
                        flyway {
                            reportFilename = "report.html"
                            locations = arrayOf("classpath:db/migration", "classpath:custom")
                        }
                        """,
                        source -> source.path("build.gradle.kts")
                )
        );
    }

    @Test
    void preservesLegacyMigrateCountAndLeavesStatementMigrateAlone() {
        rewriteRun(
                spec -> spec.recipe(new MigrateFlywayJavaApi())
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.flywaydb.core;
                                public class Flyway {
                                    public int migrate() { return 0; }
                                }
                                """)),
                java(
                        """
                        import org.flywaydb.core.Flyway;

                        class MigrationRunner {
                            int run(Flyway flyway) {
                                flyway.migrate();
                                return flyway.migrate();
                            }
                        }
                        """,
                        """
                        import org.flywaydb.core.Flyway;

                        class MigrationRunner {
                            int run(Flyway flyway) {
                                flyway.migrate();
                                return flyway.migrate().migrationsExecuted;
                            }
                        }
                        """
                )
        );
    }

    @Test
    void renamesCreateSchemaEventAndConfigurationGetters() {
        rewriteRun(
                spec -> spec.recipe(recipe(MIGRATION_RECIPE))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.flywaydb.core.api.callback;
                                public enum Event { CREATE_SCHEMA, BEFORE_CREATE_SCHEMA }
                                """,
                                """
                                package org.flywaydb.core.api.configuration;
                                public interface Configuration {
                                    boolean getFailOnMissingTarget();
                                    boolean getDetectEncoding();
                                    boolean getCreateSchemas();
                                    boolean getFailOnMissingLocations();
                                }
                                """)),
                java(
                        """
                        import org.flywaydb.core.api.callback.Event;
                        import org.flywaydb.core.api.configuration.Configuration;

                        class ConfigurationReader {
                            boolean enabled(Configuration c) {
                                Event event = Event.CREATE_SCHEMA;
                                return c.getFailOnMissingTarget() || c.getDetectEncoding() ||
                                       c.getCreateSchemas() || c.getFailOnMissingLocations();
                            }
                        }
                        """,
                        """
                        import org.flywaydb.core.api.callback.Event;
                        import org.flywaydb.core.api.configuration.Configuration;

                        class ConfigurationReader {
                            boolean enabled(Configuration c) {
                                Event event = Event.BEFORE_CREATE_SCHEMA;
                                return c.isFailOnMissingTarget() || c.isDetectEncoding() ||
                                       c.isCreateSchemas() || c.isFailOnMissingLocations();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksLegacyJavaConfigurationDestructiveCallsAndWildcardLocations() {
        rewriteRun(
                spec -> spec.recipe(new FindFlywayJavaMigrationRisks())
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.flywaydb.core;
                                public class Flyway {
                                    public Flyway() {}
                                    public void setDataSource(String url, String user, String password) {}
                                    public void clean() {}
                                    public void repair() {}
                                }
                                """,
                                """
                                package org.flywaydb.core.api;
                                public class Location { public Location(String location) {} }
                                """)),
                java(
                        """
                        import org.flywaydb.core.Flyway;
                        import org.flywaydb.core.api.Location;

                        class LegacyRunner {
                            void run() {
                                Flyway flyway = new Flyway();
                                flyway.setDataSource("jdbc:h2:mem:test", "sa", "");
                                flyway.clean();
                                flyway.repair();
                                Location location = new Location("filesystem:**/migration");
                            }
                        }
                        """,
                        """
                        import org.flywaydb.core.Flyway;
                        import org.flywaydb.core.api.Location;

                        class LegacyRunner {
                            void run() {
                                Flyway flyway = /*~~(Mutable new Flyway() construction was removed; collect all setters into Flyway.configure() before load())~~>*/new Flyway();
                                /*~~(Mutable Flyway configuration was removed; preserve setter order and values in one FluentConfiguration chain before load())~~>*/flyway.setDataSource("jdbc:h2:mem:test", "sa", "");
                                /*~~(Flyway clean destroys objects in configured schemas; verify environment, cleanDisabled, credentials, and explicit approval)~~>*/flyway.clean();
                                /*~~(Flyway repair mutates schema-history checksums and states; compare info/validate output and use the same locations as migrate)~~>*/flyway.repair();
                                Location location = /*~~(Wildcard Location construction requires fromWildcardPath parser semantics; do not replace it with fromPath mechanically)~~>*/new Location("filesystem:**/migration");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksRemovedPropertyRisksButLeavesSafeValuesAlone() {
        rewriteRun(
                spec -> spec.recipe(new FindFlywayPropertiesRisks()),
                properties(
                        """
                        flyway.ignorePendingMigrations=true
                        flyway.check.password=${FLYWAY_CHECK_PASSWORD}
                        flyway.cleanOnValidationError=true
                        flyway.cleanDisabled=false
                        flyway.baselineOnMigrate=true
                        flyway.outOfOrder=true
                        flyway.locations=filesystem:/opt/sql
                        spring.flyway.locations=filesystem:/opt/spring
                        """,
                        """
                        ~~(Removed ignore*Migrations booleans must be merged into ignoreMigrationPatterns while preserving the *:future default intentionally)~~>flyway.ignorePendingMigrations=true
                        ~~(Removed check connection setting; choose a named environment or the intended standard connection without copying secrets into source)~~>flyway.check.password=${FLYWAY_CHECK_PASSWORD}
                        ~~(cleanOnValidationError is deprecated and can clean the wrong TOML environment; replace it with an explicitly approved validate-then-clean workflow)~~>flyway.cleanOnValidationError=true
                        ~~(clean is explicitly enabled; verify this cannot reach permanent or production schemas)~~>flyway.cleanDisabled=false
                        ~~(baselineOnMigrate can accept a non-empty schema without migration history; verify the baseline version and target database)~~>flyway.baselineOnMigrate=true
                        ~~(outOfOrder changes migration ordering; compare info/validate results against a production snapshot)~~>flyway.outOfOrder=true
                        ~~(filesystem locations discover SQL migrations only; add an explicit classpath location if Java migrations must still be discovered)~~>flyway.locations=filesystem:/opt/sql
                        ~~(filesystem locations discover SQL migrations only; add an explicit classpath location if Java migrations must still be discovered)~~>spring.flyway.locations=filesystem:/opt/spring
                        """,
                        source -> source.path("risky.conf")
                ),
                properties(
                        """
                        flyway.ignoreMigrationPatterns=*:future
                        flyway.cleanDisabled=true
                        flyway.baselineOnMigrate=false
                        flyway.outOfOrder=false
                        flyway.locations=classpath:db/migration,filesystem:/opt/sql
                        """,
                        source -> source.path("safe.conf")
                )
        );
    }

    @Test
    void marksMissingGradleCompanionAndAcceptsPresentCompanion() {
        // Reduced from testcontainers/testcontainers-java-spring-boot-quickstart at fixed commit c2fa0b2.
        // https://github.com/testcontainers/testcontainers-java-spring-boot-quickstart/blob/c2fa0b2287a97026e153c9d9ee3aab5b7e96ba02/build.gradle
        rewriteRun(
                spec -> spec.recipe(new FindFlywayDatabaseModuleRisks()),
                buildGradle(
                        """
                        dependencies {
                            implementation 'org.flywaydb:flyway-core'
                            runtimeOnly 'org.postgresql:postgresql:42.7.7'
                        }
                        """,
                        """
                        dependencies {
                            implementation 'org.flywaydb:flyway-core'
                            runtimeOnly /*~~(Flyway 11 requires org.flywaydb:flyway-database-postgresql on the relevant application/plugin runtime classpath)~~>*/'org.postgresql:postgresql:42.7.7'
                        }
                        """,
                        source -> source.path("missing.gradle")
                )
        );
        rewriteRun(
                spec -> spec.recipe(new FindFlywayDatabaseModuleRisks()),
                buildGradle("""
                        dependencies {
                            implementation 'org.flywaydb:flyway-database-postgresql:11.14.1'
                            runtimeOnly 'org.postgresql:postgresql:42.7.7'
                        }
                        """, source -> source.path("present.gradle"))
        );
    }

    @Test
    void renamesCreateSchemaCallbackFilesAndPreservesOtherCallbacks() {
        rewriteRun(
                spec -> spec.recipe(new MigrateFlywayCallbackFilenames()),
                text(
                        "select 1;\n",
                        "select 1;\n",
                        source -> source.path("src/main/resources/db/callback/createSchema.sql")
                                .afterRecipe(after -> assertEquals(
                                        Path.of("src/main/resources/db/callback/beforeCreateSchema.sql"),
                                        after.getSourcePath()))
                ),
                text(
                        "select 2;\n",
                        "select 2;\n",
                        source -> source.path("src/main/resources/db/callback/createSchema__audit.sql")
                                .afterRecipe(after -> assertEquals(
                                        Path.of("src/main/resources/db/callback/beforeCreateSchema__audit.sql"),
                                        after.getSourcePath()))
                ),
                text("select 3;\n", source -> source.path("src/main/resources/db/callback/afterMigrate.sql"))
        );
    }

    @Test
    void marksInvalidDefaultSqlNamesAndAcceptsValidOrUnrelatedFiles() {
        rewriteRun(
                spec -> spec.recipe(new FindFlywayMigrationFileRisks()),
                text(
                        "select 1;\n",
                        "~~(Filename violates Flyway's default migration naming; confirm custom prefixes/separator or rename before enabling validateMigrationNaming)~~>select 1;\n",
                        source -> source.path("src/main/resources/db/migration/V1_create_table.sql")
                                .afterRecipe(after -> assertTrue(after.getMarkers().findFirst(SearchResult.class).isPresent()))
                ),
                text("select 2;\n", source -> source.path("src/main/resources/db/migration/V1__create_table.sql")),
                text("select 3;\n", source -> source.path("src/main/resources/db/migration/R__refresh_view.sql")),
                text("select 4;\n", source -> source.path("src/main/resources/db/migration/schema.sql")),
                text("select 5;\n", source -> source.path("src/test/resources/sql/V1_wrong_but_not_a_flyway_location.sql"))
        );
    }

    @Test
    void recommendedRecipeIsIdempotentAfterACompleteMavenMigration() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>idempotent</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>9.19.4</version></dependency>
                            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.7</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>idempotent</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId><version>11.14.1</version></dependency>
                            <dependency>
                              <groupId>org.flywaydb</groupId>
                              <artifactId>flyway-database-postgresql</artifactId>
                              <version>11.14.1</version>
                            </dependency>
                            <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><version>42.7.7</version></dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    private static Recipe recipe(String name) {
        return Environment.builder().scanRuntimeClasspath().build().activateRecipes(name);
    }
}
