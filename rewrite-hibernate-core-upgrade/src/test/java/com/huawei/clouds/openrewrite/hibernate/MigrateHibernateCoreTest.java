package com.huawei.clouds.openrewrite.hibernate;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class MigrateHibernateCoreTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.hibernate.UpgradeHibernateCoreDependencyTo7_2_12";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.hibernate.MigrateHibernateCoreTo7_2_12";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE))
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package org.hibernate;
                        public interface Session {
                            java.io.Serializable save(Object entity);
                            java.io.Serializable save(String entityName, Object entity);
                            void delete(Object entity);
                            void delete(String entityName, Object entity);
                            Object get(Class<?> type, java.io.Serializable id);
                            Object get(String entityName, java.io.Serializable id);
                            Object load(Class<?> type, java.io.Serializable id);
                            Object load(String entityName, java.io.Serializable id);
                        }
                        """,
                        """
                        package javax.persistence;
                        public @interface Entity {}
                        """,
                        """
                        package javax.persistence;
                        public @interface Id {}
                        """,
                        """
                        package javax.persistence;
                        public interface EntityManager {}
                        """,
                        """
                        package org.hibernate.annotations;
                        public @interface Cascade { CascadeType[] value(); }
                        """,
                        """
                        package org.hibernate.annotations;
                        public enum CascadeType { DELETE, REMOVE }
                        """,
                        """
                        package org.hibernate.dialect;
                        public class MySQL5Dialect {}
                        """,
                        """
                        package org.hibernate.type.descriptor.java;
                        public interface JavaTypeDescriptor<T> {}
                        """,
                        """
                        package org.hibernate.type.descriptor.sql;
                        public interface SqlTypeDescriptor {}
                        """
                ));
    }

    @Test
    void migratesOfficialHibernate5TemplatePropertyAndCoordinate() {
        // Reduced from Hibernate's official ORM 5 test-case template:
        // https://github.com/hibernate/hibernate-test-case-templates/blob/dd6d9afc51df72b389f3113fd04379996cc4af43/orm/hibernate-orm-5/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.hibernate.bugs</groupId>
                  <artifactId>hibernate-orm-5</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <version.org.hibernate>5.6.15.Final</version.org.hibernate>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.hibernate</groupId>
                      <artifactId>hibernate-core</artifactId>
                      <version>${version.org.hibernate}</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.hibernate.bugs</groupId>
                  <artifactId>hibernate-orm-5</artifactId>
                  <version>1.0.0</version>
                  <properties>
                    <version.org.hibernate>7.2.12.Final</version.org.hibernate>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.hibernate.orm</groupId>
                      <artifactId>hibernate-core</artifactId>
                      <version>${version.org.hibernate}</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesNewMavenCoordinate() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>hibernate-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.hibernate.orm</groupId>
                      <artifactId>hibernate-core</artifactId>
                      <version>6.4.4.Final</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>hibernate-app</artifactId>
                  <version>1</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.hibernate.orm</groupId>
                      <artifactId>hibernate-core</artifactId>
                      <version>7.2.12.Final</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void migratesManagedLegacyCoordinate() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>managed-hibernate-app</artifactId>
                  <version>1</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.hibernate</groupId>
                        <artifactId>hibernate-core</artifactId>
                        <version>5.4.28.Final</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>managed-hibernate-app</artifactId>
                  <version>1</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.hibernate.orm</groupId>
                        <artifactId>hibernate-core</artifactId>
                        <version>7.2.12.Final</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesQuarkusStyleManagedVersionProperty() {
        // Reduced from quarkusio/quarkus' application BOM:
        // https://github.com/quarkusio/quarkus/blob/04011e13d6b1c34f5d9cddfb355dcc4c918a1d11/bom/application/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-bom</artifactId>
                  <version>1</version>
                  <properties><hibernate-orm.version>6.6.0.Final</hibernate-orm.version></properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.hibernate.orm</groupId>
                        <artifactId>hibernate-core</artifactId>
                        <version>${hibernate-orm.version}</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-bom</artifactId>
                  <version>1</version>
                  <properties><hibernate-orm.version>7.2.12.Final</hibernate-orm.version></properties>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.hibernate.orm</groupId>
                        <artifactId>hibernate-core</artifactId>
                        <version>${hibernate-orm.version}</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void leavesLegacyGradleCoordinateWithoutSemanticModelUntouched() {
        // Reduced from openboxes/openboxes at 5632415f:
        // https://github.com/openboxes/openboxes/blob/5632415fba713129d81835c5b1498506091a1915/build.gradle
        // Group-ID changes require the GradleProject marker supplied by the Gradle Tooling API.
        // This unit test deliberately has no external Gradle installation and verifies safe fallback.
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation "org.hibernate:hibernate-core:5.4.15.Final"
                }
                """
        ));
    }

    @Test
    void leavesUnresolvedGradleVersionInterpolationUntouched() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                ext { hibernateVersion = '5.4.15.Final' }
                dependencies {
                    implementation "org.hibernate:hibernate-core:${hibernateVersion}"
                }
                """
        ));
    }

    @Test
    void upgradesTeamMatesStyleGradleDependency() {
        // Reduced from TEAMMATES/teammates at e8270607:
        // https://github.com/TEAMMATES/teammates/blob/e82706072141196191640375727edb302e54a55f/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation("org.hibernate.orm:hibernate-core:6.4.4.Final")
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation("org.hibernate.orm:hibernate-core:7.2.12.Final")
                }
                """
        ));
    }

    @Test
    void leavesTargetAndLaterVersionsUntouched() {
        rewriteRun(
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>current</artifactId><version>1</version><dependencies><dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-core</artifactId><version>7.2.12.Final</version></dependency></dependencies></project>
                        """
                ),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>later</artifactId><version>1</version><dependencies><dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-core</artifactId><version>7.2.23.Final</version></dependency></dependencies></project>
                        """,
                        spec -> spec.path("later-pom.xml")
                )
        );
    }

    @Test
    void leavesCompanionAndSimilarArtifactsUntouched() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>companions</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-envers</artifactId><version>5.6.15.Final</version></dependency>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>8.0.3.Final</version></dependency>
                    <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-core-jakarta</artifactId><version>5.6.15.Final</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void comprehensiveRecipeMigratesPersistenceDependency() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>jpa-app</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId><version>5.6.15.Final</version></dependency>
                            <dependency><groupId>javax.persistence</groupId><artifactId>javax.persistence-api</artifactId><version>2.2</version></dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>jpa-app</artifactId><version>1</version>
                          <dependencies>
                            <dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-core</artifactId><version>7.2.12.Final</version></dependency>
                            <dependency><groupId>jakarta.persistence</groupId><artifactId>jakarta.persistence-api</artifactId><version>3.2.0</version></dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeMigratesPersistenceImports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        package example;

                        import javax.persistence.Entity;
                        import javax.persistence.Id;

                        @Entity
                        class Customer {
                            @Id
                            Long id;
                            javax.persistence.EntityManager manager;
                        }
                        """,
                        """
                        package example;

                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;

                        @Entity
                        class Customer {
                            @Id
                            Long id;
                            jakarta.persistence.EntityManager manager;
                        }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeMigratesRemovedSessionMethods() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import org.hibernate.Session;

                        class Repository {
                            void write(Session session, Object entity) {
                                session.save(entity);
                                session.delete(entity);
                            }

                            Object read(Session session) {
                                session.get(String.class, "id");
                                return session.load(String.class, "id");
                            }
                        }
                        """,
                        """
                        import org.hibernate.Session;

                        class Repository {
                            void write(Session session, Object entity) {
                                session.persist(entity);
                                session.remove(entity);
                            }

                            Object read(Session session) {
                                session.find(String.class, "id");
                                return session.getReference(String.class, "id");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeMigratesDialectDescriptorsAndCascade() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import org.hibernate.annotations.Cascade;
                        import org.hibernate.annotations.CascadeType;
                        import org.hibernate.dialect.MySQL5Dialect;
                        import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
                        import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

                        class LegacyMapping {
                            MySQL5Dialect dialect;
                            JavaTypeDescriptor<String> javaType;
                            SqlTypeDescriptor sqlType;

                            @Cascade(CascadeType.DELETE)
                            Object child;
                        }
                        """,
                        """
                        import org.hibernate.annotations.Cascade;
                        import org.hibernate.annotations.CascadeType;
                        import org.hibernate.dialect.MySQLDialect;
                        import org.hibernate.type.descriptor.java.JavaType;
                        import org.hibernate.type.descriptor.jdbc.JdbcType;

                        class LegacyMapping {
                            MySQLDialect dialect;
                            JavaType<String> javaType;
                            JdbcType sqlType;

                            @Cascade(CascadeType.REMOVE)
                            Object child;
                        }
                        """
                )
        );
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
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.hibernate")
                .scanYamlResources()
                .build();
    }
}
