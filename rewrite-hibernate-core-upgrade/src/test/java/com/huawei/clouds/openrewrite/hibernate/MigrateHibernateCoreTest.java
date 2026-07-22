package com.huawei.clouds.openrewrite.hibernate;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class MigrateHibernateCoreTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.hibernate.UpgradeHibernateCoreDependencyTo7_2_12";
    private static final String CONFIG_RECIPE =
            "com.huawei.clouds.openrewrite.hibernate.MigrateJakartaPersistenceConfigurationTo3_2";
    private static final String SOURCE_RECIPE =
            "com.huawei.clouds.openrewrite.hibernate.MigrateDeterministicHibernateSourceTo7";
    private static final String JAVA_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.hibernate.FindManualHibernate7JavaMigrationRisks";
    private static final String CONFIG_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.hibernate.FindManualHibernate7MappingAndConfigurationRisks";
    private static final String BUILD_RISK_RECIPE =
            "com.huawei.clouds.openrewrite.hibernate.FindManualHibernate7BuildBaselineRisks";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.hibernate.MigrateHibernateCoreTo7_2_12";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(recipe(DEPENDENCY_RECIPE))
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package org.hibernate;
                        public interface Session {
                            Object load(Class<?> type, java.io.Serializable id);
                            Object load(String entityName, java.io.Serializable id);
                            Object load(Class<?> type, java.io.Serializable id, LockMode mode);
                            java.io.Serializable save(Object entity);
                            void delete(Object entity);
                            Object get(Class<?> type, java.io.Serializable id);
                        }
                        """,
                        "package org.hibernate; public enum LockMode { READ, WRITE }",
                        "package javax.persistence; public @interface Entity {}",
                        "package javax.persistence; public @interface Id {}",
                        "package javax.persistence; public interface EntityManager {}",
                        "package org.hibernate.annotations; public @interface Cascade { CascadeType[] value(); }",
                        "package org.hibernate.annotations; public enum CascadeType { DELETE, REMOVE, SAVE_UPDATE }",
                        "package org.hibernate.type.descriptor.java; public interface JavaTypeDescriptor<T> {}",
                        "package org.hibernate.type.descriptor.sql; public interface SqlTypeDescriptor {}"
                ));
    }

    @Test
    void upgradesEveryLiteralVersionVisibleInTheSpreadsheetForBothMavenCoordinates() {
        String[] versions = {
                "5.4.15.Final", "5.4.24.Final", "5.4.25.Final", "5.4.28.Final", "5.5.6",
                "5.6.5.Final", "5.6.7.Final", "5.6.9.Final", "5.6.14.Final", "5.6.15.Final"
        };
        for (String version : versions) {
            rewriteRun(
                    xml(pom("org.hibernate", version), pom("org.hibernate.orm", "7.2.12.Final"),
                            source -> source.path("legacy-coordinate-" + version + "/pom.xml")),
                    xml(pom("org.hibernate.orm", version), pom("org.hibernate.orm", "7.2.12.Final"),
                            source -> source.path("new-coordinate-" + version + "/pom.xml"))
            );
        }
    }

    @Test
    void migratesOfficialHibernate5TemplateIsolatedPropertyAndCoordinate() {
        // Reduced from the official ORM 5 template at the fixed commit dd6d9afc:
        // https://github.com/hibernate/hibernate-test-case-templates/blob/dd6d9afc51df72b389f3113fd04379996cc4af43/orm/hibernate-orm-5/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.hibernate.bugs</groupId><artifactId>orm5</artifactId><version>1</version>
                  <properties><version.org.hibernate>5.6.15.Final</version.org.hibernate></properties>
                  <dependencies><dependency><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId><version>${version.org.hibernate}</version></dependency></dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>org.hibernate.bugs</groupId><artifactId>orm5</artifactId><version>1</version>
                  <properties><version.org.hibernate>7.2.12.Final</version.org.hibernate></properties>
                  <dependencies><dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-core</artifactId><version>${version.org.hibernate}</version></dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesSelectedManagedLiteralButPreservesBomManagedDependencyWithoutVersion() {
        rewriteRun(
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId><version>5.4.28.Final</version></dependency></dependencies></dependencyManagement>
                        </project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>managed</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-core</artifactId><version>7.2.12.Final</version></dependency></dependencies></dependencyManagement>
                        </project>
                        """,
                        source -> source.path("managed/pom.xml")
                ),
                xml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>bom</artifactId><version>1</version>
                          <dependencyManagement><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-dependencies</artifactId><version>3.5.4</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
                          <dependencies><dependency><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId></dependency></dependencies>
                        </project>
                        """,
                        source -> source.path("bom/pom.xml")
                )
        );
    }

    @Test
    void preservesSharedUnresolvedRangeAndUnlistedVersions() {
        // The Quarkus BOM used 6.6.0.Final at fixed commit 04011e13; it is deliberately outside the spreadsheet cells.
        // https://github.com/quarkusio/quarkus/blob/04011e13d6b1c34f5d9cddfb355dcc4c918a1d11/bom/application/pom.xml
        rewriteRun(xml(
                """
                <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>safety</artifactId><version>1</version>
                  <properties><shared.version>5.6.15.Final</shared.version></properties>
                  <dependencies>
                    <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId><version>${shared.version}</version></dependency>
                    <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-envers</artifactId><version>${shared.version}</version></dependency>
                    <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId><version>${missing.version}</version></dependency>
                    <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId><version>[5.4,6)</version></dependency>
                    <dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-core</artifactId><version>6.6.0.Final</version></dependency>
                  </dependencies>
                </project>
                """,
                source -> source.path("safety/pom.xml")
        ));
    }

    @Test
    void upgradesSelectedGroovyAndKotlinDirectLiterals() {
        rewriteRun(
                buildGradle(
                        """
                        plugins { id 'java' }
                        dependencies { implementation 'org.hibernate:hibernate-core:5.4.15.Final' }
                        """,
                        """
                        plugins { id 'java' }
                        dependencies { implementation 'org.hibernate.orm:hibernate-core:7.2.12.Final' }
                        """
                ),
                buildGradleKts(
                        """
                        plugins { java }
                        dependencies { testImplementation("org.hibernate.orm:hibernate-core:5.6.9.Final") }
                        """,
                        """
                        plugins { java }
                        dependencies { testImplementation("org.hibernate.orm:hibernate-core:7.2.12.Final") }
                        """
                )
        );
    }

    @Test
    void preservesRealRepositoryGradleInterpolationAndUnlistedVersion() {
        // Reduced from OpenBoxes at 5632415f (legacy coordinate + variable):
        // https://github.com/openboxes/openboxes/blob/5632415fba713129d81835c5b1498506091a1915/build.gradle
        // Reduced from TEAMMATES at e8270607 (new coordinate 6.4.4.Final):
        // https://github.com/TEAMMATES/teammates/blob/e82706072141196191640375727edb302e54a55f/build.gradle
        rewriteRun(
                buildGradle("""
                        plugins { id 'java' }
                        ext.hibernateVersion = '5.4.15.Final'
                        dependencies { implementation "org.hibernate:hibernate-core:${hibernateVersion}" }
                        """),
                buildGradle("""
                        plugins { id 'java' }
                        dependencies { implementation("org.hibernate.orm:hibernate-core:6.4.4.Final") }
                        """, source -> source.path("teammates.gradle")),
                buildGradleKts("""
                        plugins { java }
                        dependencies { implementation(libs.hibernate.core) }
                        """, source -> source.path("catalog.gradle.kts"))
        );
    }

    @Test
    void dependencyRecipeIsIdempotentAtTargetAndDoesNotDowngradeLaterVersions() {
        rewriteRun(
                spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
                pomXml(pom("org.hibernate", "5.6.15.Final"), pom("org.hibernate.orm", "7.2.12.Final")),
                pomXml(pom("org.hibernate.orm", "7.3.0.Final"), source -> source.path("later/pom.xml")),
                pomXml(pom("org.hibernate.orm", "7.2.12.Final"), source -> source.path("target/pom.xml"))
        );
    }

    @Test
    void migratesJakartaPackagesStringsDescriptorsLoadAndCascade() {
        rewriteRun(
                spec -> spec.recipe(recipe(SOURCE_RECIPE)),
                java(
                        """
                        import javax.persistence.Entity;
                        import javax.persistence.Id;
                        import org.hibernate.Session;
                        import org.hibernate.annotations.Cascade;
                        import org.hibernate.annotations.CascadeType;
                        import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
                        import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

                        @Entity class Customer {
                            @Id Long id;
                            javax.persistence.EntityManager manager;
                            JavaTypeDescriptor<String> javaType;
                            SqlTypeDescriptor jdbcType;
                            @Cascade(CascadeType.DELETE) Object child;
                            String setting = "javax.persistence.jdbc.url";
                            Object load(Session session) {
                                Object a = session.load(Customer.class, 1L);
                                return session.load("Customer", 2L);
                            }
                        }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;
                        import org.hibernate.Session;
                        import org.hibernate.annotations.Cascade;
                        import org.hibernate.annotations.CascadeType;
                        import org.hibernate.type.descriptor.java.JavaType;
                        import org.hibernate.type.descriptor.jdbc.JdbcType;

                        @Entity class Customer {
                            @Id Long id;
                            jakarta.persistence.EntityManager manager;
                            JavaType<String> javaType;
                            JdbcType jdbcType;
                            @Cascade(CascadeType.REMOVE) Object child;
                            String setting = "jakarta.persistence.jdbc.url";
                            Object load(Session session) {
                                Object a = session.getReference(Customer.class, 1L);
                                return session.getReference("Customer", 2L);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void leavesSessionGetAndBehaviorDependentOperationsUnchanged() {
        rewriteRun(
                spec -> spec.recipe(recipe(SOURCE_RECIPE)),
                java(
                        """
                        import org.hibernate.Session;
                        class Repository {
                            Object write(Session session, Object entity) { return session.save(entity); }
                            void delete(Session session, Object entity) { session.delete(entity); }
                            Object get(Session session) { return session.get(String.class, "id"); }
                            Object lockedLoad(Session session) { return session.load(String.class, "id", org.hibernate.LockMode.READ); }
                        }
                        """
                )
        );
    }

    @Test
    void migratesPersistenceAndOrmXmlSettingsAndSchemas() {
        rewriteRun(
                spec -> spec.recipe(recipe(CONFIG_RECIPE)),
                xml(
                        """
                        <persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence"
                                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence http://xmlns.jcp.org/xml/ns/persistence/persistence_2_2.xsd"
                                     version="2.2">
                          <persistence-unit name="app"><properties><property name="javax.persistence.jdbc.url" value="jdbc:h2:mem:test"/></properties></persistence-unit>
                        </persistence>
                        """,
                        """
                        <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_2.xsd"
                                     version="3.2">
                          <persistence-unit name="app"><properties><property name="jakarta.persistence.jdbc.url" value="jdbc:h2:mem:test"/></properties></persistence-unit>
                        </persistence>
                        """,
                        source -> source.path("src/main/resources/META-INF/persistence.xml")
                ),
                xml(
                        """
                        <entity-mappings xmlns="http://java.sun.com/xml/ns/persistence/orm"
                                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                         xsi:schemaLocation="http://java.sun.com/xml/ns/persistence/orm http://java.sun.com/xml/ns/persistence/orm_2_0.xsd"
                                         version="2.0"/>
                        """,
                        """
                        <entity-mappings xmlns="https://jakarta.ee/xml/ns/persistence/orm"
                                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                         xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence/orm https://jakarta.ee/xml/ns/persistence/orm_3_2.xsd"
                                         version="3.2"/>
                        """,
                        source -> source.path("src/main/resources/META-INF/orm.xml")
                )
        );
    }

    @Test
    void migratesPropertiesYamlAndPersistenceProviderServicePath() {
        rewriteRun(
                spec -> spec.recipe(recipe(CONFIG_RECIPE)),
                text(
                        "javax.persistence.schema-generation.database.action=validate\n",
                        "jakarta.persistence.schema-generation.database.action=validate\n",
                        source -> source.path("src/main/resources/hibernate.properties")
                ),
                text(
                        "jpa-key: javax.persistence.validation.mode\n",
                        "jpa-key: jakarta.persistence.validation.mode\n",
                        source -> source.path("src/main/resources/application.yml")
                ),
                text(
                        "com.example.CustomPersistenceProvider\n",
                        source -> source.path("src/main/resources/META-INF/services/javax.persistence.spi.PersistenceProvider")
                                .afterRecipe(after -> assertEquals(
                                        "src/main/resources/META-INF/services/jakarta.persistence.spi.PersistenceProvider",
                                        after.getSourcePath().toString()))
                )
        );
    }

    @Test
    void marksDropwizardSaveOrUpdateAndOpenBoxesResultTransformerFixtures() {
        // Dropwizard fixed commit ee35db5b:
        // https://github.com/dropwizard/dropwizard/blob/ee35db5b4aeb07acf59f7aea53616222444d8372/dropwizard-hibernate/src/main/java/io/dropwizard/hibernate/AbstractDAO.java#L176-L186
        // OpenBoxes fixed commit 5632415f:
        // https://github.com/openboxes/openboxes/blob/5632415fba713129d81835c5b1498506091a1915/grails-app/services/org/pih/warehouse/data/HibernateSessionService.groovy#L43-L52
        rewriteRun(
                spec -> spec.recipe(recipe(JAVA_RISK_RECIPE)),
                text(
                        """
                        import org.hibernate.Session;
                        class AbstractDAO<E> {
                          E persist(E entity) { currentSession().saveOrUpdate(entity); return entity; }
                          Session currentSession() { return null; }
                        }
                        """,
                        """
                        import org.hibernate.Session;
                        class AbstractDAO<E> {
                          E persist(E entity) { currentSession()~~>.saveOrUpdate(entity); return entity; }
                          Session currentSession() { return null; }
                        }
                        """,
                        source -> source.path("src/main/java/io/dropwizard/hibernate/AbstractDAO.java")
                ),
                text(
                        """
                        import org.hibernate.Session
                        import org.hibernate.transform.AliasToEntityMapResultTransformer
                        class HibernateSessionService {
                          def list(Session session, String sql) { session.createNativeQuery(sql).setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE).list() }
                        }
                        """,
                        """
                        import org.hibernate.Session
                        import org.hibernate.transform.~~>AliasToEntityMapResultTransformer
                        class HibernateSessionService {
                          def list(Session session, String sql) { session~~>.createNativeQuery(sql).~~>setResultTransformer(~~>AliasToEntityMapResultTransformer.INSTANCE).list() }
                        }
                        """,
                        source -> source.path("grails-app/services/HibernateSessionService.groovy")
                )
        );
    }

    @Test
    void marksOneDevInterceptorSpiAndRemovedCriteria() {
        // Reduced from OneDev at fixed commit 1d3f0ae6:
        // https://github.com/theonedev/onedev/blob/1d3f0ae62b73233970390fd4af5f836cdc4cdc82/server-core/src/main/java/io/onedev/server/persistence/HibernateInterceptor.java
        rewriteRun(
                spec -> spec.recipe(recipe(JAVA_RISK_RECIPE)),
                text(
                        """
                        import org.hibernate.EmptyInterceptor;
                        import org.hibernate.Criteria;
                        class HibernateInterceptor extends EmptyInterceptor {
                          void query(org.hibernate.Session session) { Criteria c = session.createCriteria(String.class); }
                        }
                        """,
                        """
                        import org.hibernate.~~>EmptyInterceptor;
                        import ~~>org.hibernate.Criteria;
                        class HibernateInterceptor extends ~~>EmptyInterceptor {
                          void query(org.hibernate.Session session) { ~~>Criteria c = session~~>.createCriteria(String.class); }
                        }
                        """,
                        source -> source.path("src/main/java/io/onedev/server/persistence/HibernateInterceptor.java")
                )
        );
    }

    @Test
    void marksVersionedDialectsCustomTypesQueriesAndRemovedAnnotations() {
        rewriteRun(
                spec -> spec.recipe(recipe(JAVA_RISK_RECIPE)),
                text(
                        """
                        import org.hibernate.dialect.MySQL5Dialect;
                        import org.hibernate.usertype.UserType;
                        import org.hibernate.annotations.Proxy;
                        @Proxy(lazy = false) class Extension implements UserType<Object> {
                          MySQL5Dialect dialect;
                          void query(org.hibernate.Session s) { s.createQuery("from Customer"); }
                        }
                        """,
                        """
                        import org.hibernate.dialect.~~>MySQL5Dialect;
                        import org.hibernate.usertype.~~>UserType;
                        import org.hibernate.annotations.Proxy;
                        ~~>@Proxy(lazy = false) class Extension implements ~~>UserType<Object> {
                          ~~>MySQL5Dialect dialect;
                          void query(org.hibernate.Session s) { s~~>.createQuery("from Customer"); }
                        }
                        """,
                        source -> source.path("src/main/java/example/Extension.java")
                )
        );
    }

    @Test
    void marksHbmDialectRemovedSettingsAndSchemaRisks() {
        rewriteRun(
                spec -> spec.recipe(recipe(CONFIG_RISK_RECIPE)),
                text(
                        "<hibernate-mapping><class name=\"Customer\"><generator class=\"native\"/></class></hibernate-mapping>",
                        "~~><hibernate-mapping><class name=\"Customer\">~~><generator class=\"native\"/></class></hibernate-mapping>",
                        source -> source.path("src/main/resources/Customer.hbm.xml")
                ),
                text(
                        """
                        hibernate.dialect=org.hibernate.dialect.MySQL5Dialect
                        hibernate.allow_refresh_detached_entity=true
                        hibernate.hbm2ddl.auto=update
                        """,
                        """
                        ~~>hibernate.dialect=org.hibernate.dialect.MySQL5Dialect
                        ~~>hibernate.allow_refresh_detached_entity=true
                        ~~>hibernate.hbm2ddl.auto=update
                        """,
                        source -> source.path("src/main/resources/hibernate.properties")
                )
        );
    }

    @Test
    void marksJava17AndCompanionBuildRisksButLeavesSupportedBaselineUnmarked() {
        rewriteRun(
                spec -> spec.recipe(recipe(BUILD_RISK_RECIPE)),
                text(
                        """
                        <project><properties><maven.compiler.release>11</maven.compiler.release></properties><dependencies>
                          <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-envers</artifactId><version>5.6.15.Final</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><properties>~~><maven.compiler.release>11</maven.compiler.release></properties><dependencies>
                          <dependency><groupId>org.hibernate</groupId>~~><artifactId>hibernate-envers</artifactId><version>5.6.15.Final</version></dependency>
                        </dependencies></project>
                        """,
                        source -> source.path("pom.xml")
                ),
                text(
                        "java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }",
                        source -> source.path("modern.gradle")
                )
        );
    }

    @Test
    void compositeUpgradesDependencyMigratesJpaAndMarksUnsafeSessionCall() {
        rewriteRun(
                spec -> spec.recipe(recipe(MIGRATION_RECIPE)),
                pomXml(
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies>
                          <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId><version>5.6.15.Final</version></dependency>
                          <dependency><groupId>javax.persistence</groupId><artifactId>javax.persistence-api</artifactId><version>2.2</version></dependency>
                        </dependencies></project>
                        """,
                        """
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>app</artifactId><version>1</version><dependencies>
                          <dependency><groupId>org.hibernate.orm</groupId><artifactId>hibernate-core</artifactId><version>7.2.12.Final</version></dependency>
                          <dependency><groupId>jakarta.persistence</groupId><artifactId>jakarta.persistence-api</artifactId><version>3.2.0</version></dependency>
                        </dependencies></project>
                        """
                ),
                java(
                        """
                        import javax.persistence.Entity;
                        import org.hibernate.Session;
                        @Entity class Customer { void save(Session session) { session.save(this); } }
                        """,
                        """
                        import jakarta.persistence.Entity;
                        import org.hibernate.Session;
                        @Entity class Customer { void save(Session session) { session~~>.save(this); } }
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesEveryRecipe() {
        Environment environment = environment();
        String[] names = {DEPENDENCY_RECIPE, CONFIG_RECIPE, SOURCE_RECIPE, JAVA_RISK_RECIPE,
                CONFIG_RISK_RECIPE, BUILD_RISK_RECIPE, MIGRATION_RECIPE};
        for (String name : names) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())), name);
            assertTrue(recipe.validate().isValid(), () -> name + ": " + recipe.validate().failures());
        }
        assertEquals("Migrate selected Hibernate Core applications to 7.2.12.Final",
                environment.activateRecipes(MIGRATION_RECIPE).getDisplayName());
    }

    private static String pom(String group, String version) {
        return """
               <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>hibernate-app</artifactId><version>1</version><dependencies>
                 <dependency><groupId>%s</groupId><artifactId>hibernate-core</artifactId><version>%s</version></dependency>
               </dependencies></project>
               """.formatted(group, version);
    }

    private static Recipe recipe(String name) {
        return environment().activateRecipes(name);
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.hibernate")
                .scanYamlResources()
                .build();
    }
}
