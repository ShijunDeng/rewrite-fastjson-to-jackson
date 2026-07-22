package com.huawei.clouds.openrewrite.hibernatevalidator;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;

class HibernateValidatorMigrationTest implements RewriteTest {
    private static final String MIGRATION_RECIPE = UpgradeHibernateValidatorTest.MIGRATION_RECIPE;
    private static final String XML_RECIPE =
            "com.huawei.clouds.openrewrite.hibernatevalidator.MigrateBeanValidationXmlTo3_0";
    private static final String SERVICE_RECIPE =
            "com.huawei.clouds.openrewrite.hibernatevalidator.MigrateValidationServiceLoaderFiles";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(MIGRATION_RECIPE));
    }

    @Test
    void migratesValidationAndElDependenciesWithoutForcingExternalBomVersions() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>validation-app</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>6.2.5.Final</version></dependency>
                    <dependency><groupId>javax.validation</groupId><artifactId>validation-api</artifactId><version>2.0.1.Final</version></dependency>
                    <dependency><groupId>javax.el</groupId><artifactId>javax.el-api</artifactId><version>3.0.0</version></dependency>
                    <dependency><groupId>org.glassfish</groupId><artifactId>javax.el</artifactId><version>3.0.1-b12</version></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>validation-app</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>org.hibernate.validator</groupId><artifactId>hibernate-validator</artifactId><version>8.0.3.Final</version></dependency>
                    <dependency><groupId>jakarta.validation</groupId><artifactId>jakarta.validation-api</artifactId><version>3.0.2</version></dependency>
                    <dependency><groupId>jakarta.el</groupId><artifactId>jakarta.el-api</artifactId><version>5.0.0</version></dependency>
                    <dependency><groupId>org.glassfish.expressly</groupId><artifactId>expressly</artifactId><version>5.0.0</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void doesNotRepurposeAValidationApiVersionPropertySharedWithProjectMetadata() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>shared-api-version</artifactId><version>1</version>
                  <properties><shared.version>2.0.1.Final</shared.version></properties>
                  <name>${shared.version}</name>
                  <dependencies><dependency>
                    <groupId>javax.validation</groupId><artifactId>validation-api</artifactId><version>${shared.version}</version>
                  </dependency></dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>shared-api-version</artifactId><version>1</version>
                  <properties><shared.version>2.0.1.Final</shared.version></properties>
                  <name>${shared.version}</name>
                  <dependencies><dependency>
                    <groupId>jakarta.validation</groupId><artifactId>jakarta.validation-api</artifactId><version>3.0.2</version>
                  </dependency></dependencies>
                </project>
                """
        ));
    }

    @Test
    void migratesDirectGradleValidationAndExpresslyCoordinates() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'javax.validation:validation-api:2.0.1.Final'
                    runtimeOnly 'org.glassfish:jakarta.el:4.0.2'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'jakarta.validation:jakarta.validation-api:3.0.2'
                    runtimeOnly 'org.glassfish.expressly:expressly:5.0.0'
                }
                """
        ));
    }

    @Test
    void migratesDirectKotlinGradleElApiCoordinate() {
        rewriteRun(buildGradleKts(
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    implementation("javax.el:javax.el-api:3.0.1-b06")
                }
                """,
                """
                plugins { java }
                repositories { mavenCentral() }
                dependencies {
                    implementation("jakarta.el:jakarta.el-api:5.0.0")
                }
                """
        ));
    }

    @Test
    void migratesValidationAndElJavaPackages() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package javax.validation; public @interface Valid {}",
                        "package javax.validation; public interface Validator {}",
                        "package javax.validation.constraints; public @interface NotNull {}",
                        "package javax.el; public abstract class ExpressionFactory {}"
                )),
                java(
                        """
                        package example;

                        import javax.el.ExpressionFactory;
                        import javax.validation.Valid;
                        import javax.validation.Validator;
                        import javax.validation.constraints.NotNull;

                        class ValidationService {
                            Validator validator;
                            ExpressionFactory expressions;
                            void validate(@Valid @NotNull Object value) {
                                javax.validation.Validator local = validator;
                            }
                        }
                        """,
                        """
                        package example;

                        import jakarta.el.ExpressionFactory;
                        import jakarta.validation.Valid;
                        import jakarta.validation.Validator;
                        import jakarta.validation.constraints.NotNull;

                        class ValidationService {
                            Validator validator;
                            ExpressionFactory expressions;
                            void validate(@Valid @NotNull Object value) {
                                jakarta.validation.Validator local = validator;
                            }
                        }
                        """
                )
        );
    }

    @Test
    void leavesOtherJavaxPackagesCommentsAndBusinessStringsUntouched() {
        rewriteRun(java(
                """
                import javax.xml.parsers.DocumentBuilder;

                class Unrelated {
                    // javax.validation.Validator is documentation, not a typed reference.
                    String apiName = "javax.validation.Validator";
                    String template = "${customer.name}";
                    DocumentBuilder parser;
                }
                """
        ));
    }

    @Test
    void migratesSignalServerStyleValidationXmlWithRelativeSchemaLocation() {
        // Reduced from signalapp/Signal-Server at 088037b4121621896931c0ee5beedb72fb74e5b9:
        // https://github.com/signalapp/Signal-Server/blob/088037b4121621896931c0ee5beedb72fb74e5b9/service/src/main/resources/META-INF/validation.xml
        rewriteRun(
                spec -> spec.recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(XML_RECIPE)),
                xml(
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <validation-config
                            xmlns="http://xmlns.jcp.org/xml/ns/validation/configuration"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/validation/configuration validation-configuration-2.0.xsd"
                            version="2.0">
                          <constraint-mapping>META-INF/validation/constraints-custom.xml</constraint-mapping>
                        </validation-config>
                        """,
                        """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <validation-config
                            xmlns="https://jakarta.ee/xml/ns/validation/configuration"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="https://jakarta.ee/xml/ns/validation/configuration https://jakarta.ee/xml/ns/validation/validation-configuration-3.0.xsd"
                            version="3.0">
                          <constraint-mapping>META-INF/validation/constraints-custom.xml</constraint-mapping>
                        </validation-config>
                        """,
                        source -> source.path("service/src/main/resources/META-INF/validation.xml")
                )
        );
    }

    @Test
    void recommendedRecipeCombinesSafeMigrationAndRiskSearches() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        "package javax.validation.constraints; public @interface NotNull {}",
                        "package org.hibernate.validator.constraints; public @interface SafeHtml {}"
                )),
                java(
                        """
                        import javax.validation.constraints.NotNull;
                        import org.hibernate.validator.constraints.SafeHtml;

                        class LegacyForm {
                            @NotNull @SafeHtml String content;
                        }
                        """,
                        """
                        import jakarta.validation.constraints.NotNull;
                        import org.hibernate.validator.constraints.SafeHtml;

                        class LegacyForm {
                            @NotNull @/*~~>*/SafeHtml String content;
                        }
                        """
                ),
                text(
                        "order.total=must be at least ${minimum}\n",
                        "order.total=must be at least ~~(${minimum})~~>${minimum}\n",
                        source -> source.path("src/main/resources/ValidationMessages.properties")
                )
        );
    }

    @Test
    void migratesConstraintMappingNamespaceSchemaVersionAndStandardAnnotation() {
        rewriteRun(
                spec -> spec.recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(XML_RECIPE)),
                xml(
                        """
                        <constraint-mappings
                            xmlns="http://xmlns.jcp.org/xml/ns/validation/mapping"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/validation/mapping http://xmlns.jcp.org/xml/ns/validation/validation-mapping-2.0.xsd"
                            version="2.0">
                          <bean class="example.Order">
                            <field name="number">
                              <constraint annotation="javax.validation.constraints.NotNull"/>
                              <constraint annotation="example.constraints.Checksum"/>
                            </field>
                          </bean>
                        </constraint-mappings>
                        """,
                        """
                        <constraint-mappings
                            xmlns="https://jakarta.ee/xml/ns/validation/mapping"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="https://jakarta.ee/xml/ns/validation/mapping https://jakarta.ee/xml/ns/validation/validation-mapping-3.0.xsd"
                            version="3.0">
                          <bean class="example.Order">
                            <field name="number">
                              <constraint annotation="jakarta.validation.constraints.NotNull"/>
                              <constraint annotation="example.constraints.Checksum"/>
                            </field>
                          </bean>
                        </constraint-mappings>
                        """,
                        source -> source.path("src/main/resources/META-INF/order-constraints.xml")
                )
        );
    }

    @Test
    void leavesJakartaValidationXmlAndOrdinaryXmlUntouched() {
        rewriteRun(
                spec -> spec.recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(XML_RECIPE)),
                xml(
                        """
                        <validation-config xmlns="https://jakarta.ee/xml/ns/validation/configuration"
                                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                           xsi:schemaLocation="https://jakarta.ee/xml/ns/validation/configuration https://jakarta.ee/xml/ns/validation/validation-configuration-3.0.xsd"
                                           version="3.0"/>
                        """,
                        source -> source.path("src/main/resources/META-INF/validation.xml")
                ),
                xml(
                        """
                        <application><property name="annotation">javax.validation.constraints.NotNull</property></application>
                        """,
                        source -> source.path("src/main/resources/application.xml")
                )
        );
    }

    @Test
    void renamesValidationProviderServiceDescriptor() {
        assertServiceRename(
                "src/main/resources/META-INF/services/javax.validation.spi.ValidationProvider",
                "src/main/resources/META-INF/services/jakarta.validation.spi.ValidationProvider"
        );
    }

    @Test
    void renamesConstraintValidatorServiceDescriptor() {
        assertServiceRename(
                "src/main/resources/META-INF/services/javax.validation.ConstraintValidator",
                "src/main/resources/META-INF/services/jakarta.validation.ConstraintValidator"
        );
    }

    @Test
    void renamesValueExtractorServiceDescriptor() {
        assertServiceRename(
                "src/main/resources/META-INF/services/javax.validation.valueextraction.ValueExtractor",
                "src/main/resources/META-INF/services/jakarta.validation.valueextraction.ValueExtractor"
        );
    }

    @Test
    void leavesSimilarServiceAndProviderImplementationTextUntouched() {
        rewriteRun(
                spec -> spec.recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(SERVICE_RECIPE)),
                text(
                        "example.validation.Provider\n",
                        source -> source.path("src/main/resources/META-INF/services/example.ValidationProvider")
                )
        );
    }

    private void assertServiceRename(String beforePath, String afterPath) {
        rewriteRun(
                spec -> spec.recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(SERVICE_RECIPE)),
                text(
                        "example.validation.CustomProvider\n",
                        "example.validation.CustomProvider\n",
                        source -> source.path(beforePath).afterRecipe(document ->
                                assertEquals(Path.of(afterPath), document.getSourcePath()))
                )
        );
    }
}
