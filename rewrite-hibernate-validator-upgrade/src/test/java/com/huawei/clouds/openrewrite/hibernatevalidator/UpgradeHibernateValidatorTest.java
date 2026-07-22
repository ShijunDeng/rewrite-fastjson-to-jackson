package com.huawei.clouds.openrewrite.hibernatevalidator;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeHibernateValidatorTest implements RewriteTest {
    private static final String RECIPE_NAME =
            "com.huawei.clouds.openrewrite.hibernatevalidator.UpgradeHibernateValidatorTo8";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(RECIPE_NAME))
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package javax.validation;
                        public @interface Valid {}
                        """,
                        """
                        package javax.validation;
                        public interface Validator {}
                        """,
                        """
                        package javax.validation.constraints;
                        public @interface NotNull {}
                        """
                ));
    }

    @Test
    void upgradesImplementationAndValidationApiDependencies() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>validation-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>org.hibernate.validator</groupId>
                              <artifactId>hibernate-validator</artifactId>
                              <version>6.2.5.Final</version>
                            </dependency>
                            <dependency>
                              <groupId>javax.validation</groupId>
                              <artifactId>validation-api</artifactId>
                              <version>2.0.1.Final</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """,
                        """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>example</groupId>
                          <artifactId>validation-app</artifactId>
                          <version>1.0.0</version>
                          <dependencies>
                            <dependency>
                              <groupId>org.hibernate.validator</groupId>
                              <artifactId>hibernate-validator</artifactId>
                              <version>8.0.3.Final</version>
                            </dependency>
                            <dependency>
                              <groupId>jakarta.validation</groupId>
                              <artifactId>jakarta.validation-api</artifactId>
                              <version>3.0.2</version>
                            </dependency>
                          </dependencies>
                        </project>
                        """
                )
        );
    }

    @Test
    void migratesValidationImportsAndQualifiedTypes() {
        rewriteRun(
                java(
                        """
                        package example;

                        import javax.validation.Valid;
                        import javax.validation.Validator;
                        import javax.validation.constraints.NotNull;

                        class ValidationService {
                            private final Validator validator;

                            ValidationService(Validator validator) {
                                this.validator = validator;
                            }

                            void validate(@Valid @NotNull Object value) {
                                javax.validation.Validator local = validator;
                            }
                        }
                        """,
                        """
                        package example;

                        import jakarta.validation.Valid;
                        import jakarta.validation.Validator;
                        import jakarta.validation.constraints.NotNull;

                        class ValidationService {
                            private final Validator validator;

                            ValidationService(Validator validator) {
                                this.validator = validator;
                            }

                            void validate(@Valid @NotNull Object value) {
                                jakarta.validation.Validator local = validator;
                            }
                        }
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesRecipe() {
        Environment environment = environment();
        Recipe recipe = environment.activateRecipes(RECIPE_NAME);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> RECIPE_NAME.equals(candidate.getName())));
        assertTrue(recipe.validate().isValid(), () -> recipe.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.hibernatevalidator")
                .scanYamlResources()
                .build();
    }
}
