package com.huawei.clouds.openrewrite.slf4j;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class Slf4jJavaRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSlf4jJavaRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package org.slf4j.spi;
                        public interface LoggingEventBuilder {
                            LoggingEventBuilder setMessage(String message);
                            LoggingEventBuilder addArgument(Object argument);
                            LoggingEventBuilder setCause(Throwable cause);
                            void log();
                            void log(String message, Object... arguments);
                        }
                        """,
                        """
                        package org.slf4j;
                        import org.slf4j.spi.LoggingEventBuilder;
                        public interface Logger {
                            LoggingEventBuilder atTrace();
                            LoggingEventBuilder atDebug();
                            LoggingEventBuilder atInfo();
                            LoggingEventBuilder atWarn();
                            LoggingEventBuilder atError();
                        }
                        """,
                        """
                        package org.slf4j.spi;
                        public interface SLF4JServiceProvider {}
                        """,
                        """
                        package org.slf4j.spi;
                        public interface LoggerFactoryBinder {}
                        """,
                        """
                        package org.slf4j;
                        public interface ILoggerFactory {}
                        """
                ));
    }

    @Test
    void marksRealDbeaverStaticLoggerBinderImplementation() {
        // Reduced from dbeaver/dbeaver at e0d43b9ec3e725f635930cba9f1a92b8d7ad46bf:
        // https://github.com/dbeaver/dbeaver/blob/e0d43b9ec3e725f635930cba9f1a92b8d7ad46bf/plugins/org.jkiss.dbeaver.slf4j/src/org/slf4j/impl/StaticLoggerBinder.java
        rewriteRun(java(
                """
                package org.slf4j.impl;

                import org.slf4j.ILoggerFactory;
                import org.slf4j.spi.LoggerFactoryBinder;

                public class StaticLoggerBinder implements LoggerFactoryBinder, ILoggerFactory {
                    private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();
                    public static StaticLoggerBinder getSingleton() { return SINGLETON; }
                }
                """,
                """
                package org.slf4j.impl;

                import org.slf4j.ILoggerFactory;
                import org.slf4j.spi.LoggerFactoryBinder;

                /*~~(SLF4J 2 ignores Static*Binder implementations; implement SLF4JServiceProvider and register it with ServiceLoader)~~>*/public class StaticLoggerBinder implements LoggerFactoryBinder, ILoggerFactory {
                    private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();
                    public static StaticLoggerBinder getSingleton() { return SINGLETON; }
                }
                """
        ));
    }

    @Test
    void marksStaticBinderAccessAndReflection() {
        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().dependsOn(
                        """
                        package org.slf4j.impl;
                        public class StaticLoggerBinder {
                            public static StaticLoggerBinder getSingleton() { return null; }
                            public Object getLoggerFactory() { return null; }
                        }
                        """)),
                java(
                """
                import org.slf4j.impl.StaticLoggerBinder;

                class LegacyBindingAccess {
                    Object factory() {
                        return StaticLoggerBinder.getSingleton().getLoggerFactory();
                    }
                    String binderName() {
                        return "org.slf4j.impl.StaticLoggerBinder";
                    }
                }
                """,
                """
                import org.slf4j.impl.StaticLoggerBinder;

                class LegacyBindingAccess {
                    Object factory() {
                        return /*~~(SLF4J 2 removed the Static*Binder contract; replace this provider-internal access with the public API or a service provider)~~>*/StaticLoggerBinder.getSingleton().getLoggerFactory();
                    }
                    String binderName() {
                        return /*~~(Reflective Static*Binder lookup is incompatible with SLF4J 2 ServiceLoader providers)~~>*/"org.slf4j.impl.StaticLoggerBinder";
                    }
                }
                """
                )
        );
    }

    @Test
    void marksCustomServiceProviderForContractReview() {
        rewriteRun(java(
                """
                import org.slf4j.spi.SLF4JServiceProvider;

                class CompanyLoggingProvider implements SLF4JServiceProvider {}
                """,
                """
                import org.slf4j.spi.SLF4JServiceProvider;

                /*~~(Custom SLF4JServiceProvider: verify requested API version, early MDC initialization, all factories, initialize(), and ServiceLoader registration)~~>*/class CompanyLoggingProvider implements SLF4JServiceProvider {}
                """
        ));
    }

    @Test
    void marksExplicitProviderSystemProperty() {
        rewriteRun(java(
                """
                class ProviderSelection {
                    void configure() {
                        System.setProperty("slf4j.provider", "com.example.CompanyLoggingProvider");
                    }
                }
                """,
                """
                class ProviderSelection {
                    void configure() {
                        /*~~(Explicit slf4j.provider selection: verify this class implements the SLF4J 2 service provider contract and is visible to LoggerFactory's class loader)~~>*/System.setProperty("slf4j.provider", "com.example.CompanyLoggingProvider");
                    }
                }
                """
        ));
    }

    @Test
    void marksDiscardedFluentChainsThatNeverLog() {
        rewriteRun(java(
                """
                import org.slf4j.Logger;

                class AuditService {
                    void audit(Logger logger, String user) {
                        logger.atInfo().setMessage("login").addArgument(user);
                        logger.atWarn();
                    }
                }
                """,
                """
                import org.slf4j.Logger;

                class AuditService {
                    void audit(Logger logger, String user) {
                        /*~~(This discarded SLF4J fluent chain never calls log(); no logging event will be emitted)~~>*/logger.atInfo().setMessage("login").addArgument(user);
                        /*~~(This discarded SLF4J fluent chain never calls log(); no logging event will be emitted)~~>*/logger.atWarn();
                    }
                }
                """
        ));
    }

    @Test
    void leavesTerminatedAndStoredFluentBuildersUnmarked() {
        rewriteRun(java(
                """
                import org.slf4j.Logger;
                import org.slf4j.spi.LoggingEventBuilder;

                class AuditService {
                    void audit(Logger logger, String user) {
                        logger.atInfo().setMessage("login").addArgument(user).log();
                        logger.atInfo().log("login {}", user);
                        LoggingEventBuilder builder = logger.atWarn();
                        builder.setMessage("later").log();
                    }
                }
                """
        ));
    }

    @Test
    void leavesOtherFluentLoggingApisUnmarked() {
        rewriteRun(java(
                """
                class XLogger {
                    XLogger atInfo() { return this; }
                    XLogger setMessage(String value) { return this; }
                }
                class OtherApi {
                    void use(XLogger logger) {
                        logger.atInfo().setMessage("not slf4j");
                    }
                }
                """
        ));
    }
}
