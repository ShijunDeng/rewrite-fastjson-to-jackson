package com.huawei.clouds.openrewrite.hibernatevalidator;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

class HibernateValidatorAuditTest implements RewriteTest {
    private static final String API_AUDIT =
            "com.huawei.clouds.openrewrite.hibernatevalidator.AuditRemovedAndChangedHibernateValidatorApis";
    private static final String EL_AUDIT =
            "com.huawei.clouds.openrewrite.hibernatevalidator.AuditCustomViolationExpressionLanguage";
    private static final String MESSAGE_AUDIT =
            "com.huawei.clouds.openrewrite.hibernatevalidator.AuditValidationMessageBundlesForExpressionLanguage";

    @Test
    void marksRemovedSafeHtmlInSuomenRiistakeskusStyleEntity() {
        // Reduced from suomenriistakeskus/oma-riista-web at f3550bce98706dfe636232fb2765a44fa33f78ca:
        // https://github.com/suomenriistakeskus/oma-riista-web/blob/f3550bce98706dfe636232fb2765a44fa33f78ca/src/main/java/fi/riista/feature/news/News.java
        rewriteRun(
                spec -> spec
                        .recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(API_AUDIT))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.hibernate.validator.constraints;
                                public @interface SafeHtml {
                                    WhiteListType whitelistType();
                                    enum WhiteListType { NONE }
                                }
                                """
                        )),
                java(
                        """
                        package fi.riista.feature.news;

                        import org.hibernate.validator.constraints.SafeHtml;

                        class News {
                            @SafeHtml(whitelistType = SafeHtml.WhiteListType.NONE)
                            private String titleFi;
                        }
                        """,
                        """
                        package fi.riista.feature.news;

                        import org.hibernate.validator.constraints.SafeHtml;

                        class News {
                            @/*~~>*/SafeHtml(whitelistType = SafeHtml.WhiteListType.NONE)
                            private String titleFi;
                        }
                        """
                )
        );
    }

    @Test
    void marksSafeHtmlFluentDefinitionAndScriptConstraints() {
        rewriteRun(
                spec -> spec
                        .recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(API_AUDIT))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.hibernate.validator.cfg.defs; public class SafeHtmlDef {}",
                                "package org.hibernate.validator.constraints; public @interface ScriptAssert { String lang(); String script(); }",
                                "package org.hibernate.validator.constraints; public @interface ParameterScriptAssert { String lang(); String script(); }"
                        )),
                java(
                        """
                        import org.hibernate.validator.cfg.defs.SafeHtmlDef;
                        import org.hibernate.validator.constraints.ParameterScriptAssert;
                        import org.hibernate.validator.constraints.ScriptAssert;

                        @ScriptAssert(lang = "javascript", script = "_this.valid")
                        class LegacyChecks {
                            SafeHtmlDef html = new SafeHtmlDef();

                            @ParameterScriptAssert(lang = "javascript", script = "arg0 != null")
                            void check(String value) {}
                        }
                        """,
                        """
                        import org.hibernate.validator.cfg.defs.SafeHtmlDef;
                        import org.hibernate.validator.constraints.ParameterScriptAssert;
                        import org.hibernate.validator.constraints.ScriptAssert;

                        @/*~~>*/ScriptAssert(lang = "javascript", script = "_this.valid")
                        class LegacyChecks {
                            /*~~>*/SafeHtmlDef html = new /*~~>*/SafeHtmlDef();

                            @/*~~>*/ParameterScriptAssert(lang = "javascript", script = "arg0 != null")
                            void check(String value) {}
                        }
                        """
                )
        );
    }

    @Test
    void marksChangedGetterStrategyContractFromWalmartConcord() {
        // Reduced from walmartlabs/concord at 7fa56d815d28082cf5950f0e9688c385e8309757:
        // https://github.com/walmartlabs/concord/blob/7fa56d815d28082cf5950f0e9688c385e8309757/server/impl/src/main/java/com/walmartlabs/concord/server/boot/validation/DefaultGetterPropertySelectionStrategy.java
        rewriteRun(
                spec -> spec
                        .recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(API_AUDIT))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.hibernate.validator.spi.properties;
                                import java.util.Set;
                                public interface GetterPropertySelectionStrategy {
                                    Set<String> getGetterMethodNameCandidates(String propertyName);
                                }
                                """
                        )),
                java(
                        """
                        package com.walmartlabs.concord.server.boot.validation;

                        import java.util.Collections;
                        import java.util.Set;
                        import org.hibernate.validator.spi.properties.GetterPropertySelectionStrategy;

                        class DefaultGetterPropertySelectionStrategy implements GetterPropertySelectionStrategy {
                            @Override
                            public Set<String> getGetterMethodNameCandidates(String propertyName) {
                                return Collections.singleton("get" + propertyName);
                            }
                        }
                        """,
                        """
                        package com.walmartlabs.concord.server.boot.validation;

                        import java.util.Collections;
                        import java.util.Set;
                        import org.hibernate.validator.spi.properties.GetterPropertySelectionStrategy;

                        class DefaultGetterPropertySelectionStrategy implements GetterPropertySelectionStrategy {
                            /*~~(Hibernate Validator 8 changes this SPI return type from Set<String> to List<String>; update the implementation and preserve candidate order)~~>*/@Override
                            public Set<String> getGetterMethodNameCandidates(String propertyName) {
                                return Collections.singleton("get" + propertyName);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void leavesHibernateValidator8GetterStrategyContractUnmarked() {
        rewriteRun(
                spec -> spec
                        .recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(API_AUDIT))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package org.hibernate.validator.spi.properties;
                                import java.util.List;
                                public interface GetterPropertySelectionStrategy {
                                    List<String> getGetterMethodNameCandidates(String propertyName);
                                }
                                """
                        )),
                java(
                        """
                        import java.util.Collections;
                        import java.util.List;
                        import org.hibernate.validator.spi.properties.GetterPropertySelectionStrategy;

                        class CurrentStrategy implements GetterPropertySelectionStrategy {
                            @Override
                            public List<String> getGetterMethodNameCandidates(String propertyName) {
                                return Collections.singletonList(propertyName);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksExpressionVariablesAndMessageElFromSanntranStyleValidator() {
        // Reduced from sanntran/spring-boot-starter at 9d45eb8e6e371bb209260ce0366db1c3390b0b9e:
        // https://github.com/sanntran/spring-boot-starter/blob/9d45eb8e6e371bb209260ce0366db1c3390b0b9e/spring-boot-rest-api/src/main/java/net/ionoff/service/validation/validator/AbstractValidator.java
        rewriteRun(
                spec -> spec
                        .recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(EL_AUDIT))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                """
                                package javax.validation;
                                public interface ConstraintValidatorContext {
                                    <T> T unwrap(Class<T> type);
                                    Object buildConstraintViolationWithTemplate(String template);
                                }
                                """,
                                """
                                package org.hibernate.validator.constraintvalidation;
                                public interface HibernateConstraintValidatorContext {
                                    HibernateConstraintValidatorContext addExpressionVariable(String name, Object value);
                                }
                                """
                        )),
                java(
                        """
                        package net.ionoff.service.validation.validator;

                        import javax.validation.ConstraintValidatorContext;
                        import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

                        class AbstractValidator {
                            void addInvalidValue(ConstraintValidatorContext context, Object value) {
                                context.unwrap(HibernateConstraintValidatorContext.class)
                                        .addExpressionVariable("validatedValue", value);
                                context.buildConstraintViolationWithTemplate("must differ from ${validatedValue}");
                            }
                        }
                        """,
                        """
                        package net.ionoff.service.validation.validator;

                        import javax.validation.ConstraintValidatorContext;
                        import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

                        class AbstractValidator {
                            void addInvalidValue(ConstraintValidatorContext context, Object value) {
                                /*~~>*/context.unwrap(HibernateConstraintValidatorContext.class)
                                        .addExpressionVariable("validatedValue", value);
                                context.buildConstraintViolationWithTemplate(/*~~>*/"must differ from ${validatedValue}");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void customElAuditRequiresHibernateConstraintContextType() {
        rewriteRun(
                spec -> spec.recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(EL_AUDIT)),
                java(
                        """
                        class OrdinaryTemplate {
                            String message = "must differ from ${validatedValue}";
                        }
                        """
                )
        );
    }

    @Test
    void marksOnlyValidationMessageBundlesContainingEl() {
        rewriteRun(
                spec -> spec.recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(MESSAGE_AUDIT)),
                text(
                        "order.total=Total must be greater than ${minimum}\norder.id=ID is required\n",
                        "order.total=Total must be greater than ~~(${minimum})~~>${minimum}\norder.id=ID is required\n",
                        source -> source.path("src/main/resources/ValidationMessages.properties")
                ),
                text(
                        "order.total=Total must be greater than ${minimum}\n",
                        source -> source.path("src/main/resources/application.properties")
                ),
                text(
                        "order.id=ID is required\n",
                        source -> source.path("src/main/resources/ValidationMessages_zh_CN.properties")
                )
        );
    }

    @Test
    void apiAuditLeavesStableHibernateValidatorTypesUntouched() {
        rewriteRun(
                spec -> spec
                        .recipe(UpgradeHibernateValidatorTest.environment().activateRecipes(API_AUDIT))
                        .parser(JavaParser.fromJavaVersion().dependsOn(
                                "package org.hibernate.validator.constraints; public @interface URL {}"
                        )),
                java(
                        """
                        import org.hibernate.validator.constraints.URL;
                        class StableConstraint { @URL String homepage; }
                        """
                )
        );
    }
}
