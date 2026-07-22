package com.huawei.clouds.openrewrite.jakartaservlet;

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

class JakartaServletApiUpgradeTest implements RewriteTest {
    private static final String DEPENDENCY_RECIPE =
            "com.huawei.clouds.openrewrite.jakartaservlet.UpgradeJakartaServletApiTo6_1_0";
    private static final String MIGRATION_RECIPE =
            "com.huawei.clouds.openrewrite.jakartaservlet.MigrateJakartaServletApiTo6_1_0";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(environment().activateRecipes(DEPENDENCY_RECIPE))
                .parser(servletParser());
    }

    @Test
    void upgradesJenkinsCrawlerMavenDependencyFrom4_0_4() {
        // Reduced from jenkins-infra/crawler at efb1b391:
        // https://github.com/jenkins-infra/crawler/blob/efb1b391762056a1a558ab2d340d840ed2aad527/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.jenkins-ci</groupId>
                  <artifactId>crawler</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>jakarta.servlet</groupId>
                      <artifactId>jakarta.servlet-api</artifactId>
                      <version>4.0.4</version>
                    </dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.jenkins-ci</groupId>
                  <artifactId>crawler</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>jakarta.servlet</groupId>
                      <artifactId>jakarta.servlet-api</artifactId>
                      <version>6.1.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesMavenVersionPropertyFrom4_0_3() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>servlet-property-app</artifactId>
                  <version>1</version>
                  <properties><servlet-api.version>4.0.3</servlet-api.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>jakarta.servlet</groupId>
                      <artifactId>jakarta.servlet-api</artifactId>
                      <version>${servlet-api.version}</version>
                      <scope>provided</scope>
                    </dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>servlet-property-app</artifactId>
                  <version>1</version>
                  <properties><servlet-api.version>6.1.0</servlet-api.version></properties>
                  <dependencies>
                    <dependency>
                      <groupId>jakarta.servlet</groupId>
                      <artifactId>jakarta.servlet-api</artifactId>
                      <version>${servlet-api.version}</version>
                      <scope>provided</scope>
                    </dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesHapiStyleManagedDependencyFrom6_0_0() {
        // Reduced from hapifhir/hapi-hl7v2 at de150365:
        // https://github.com/hapifhir/hapi-hl7v2/blob/de1503651040e592d529d43980c06b19b89e2c27/pom.xml
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>ca.uhn.hapi</groupId>
                  <artifactId>hapi-base</artifactId>
                  <version>1</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>jakarta.servlet</groupId>
                        <artifactId>jakarta.servlet-api</artifactId>
                        <version>6.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>ca.uhn.hapi</groupId>
                  <artifactId>hapi-base</artifactId>
                  <version>1</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>jakarta.servlet</groupId>
                        <artifactId>jakarta.servlet-api</artifactId>
                        <version>6.1.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """
        ));
    }

    @Test
    void upgradesDirectMavenDependencyFrom5_0_0AndPreservesScope() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>servlet-five</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>5.0.0</version><scope>provided</scope><optional>true</optional></dependency>
                  </dependencies>
                </project>
                """,
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>servlet-five</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version><scope>provided</scope><optional>true</optional></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void upgradesJsonRpc4jStyleGradleStringNotation() {
        // Reduced from briandilley/jsonrpc4j at 59ff0c95:
        // https://github.com/briandilley/jsonrpc4j/blob/59ff0c955087a3fe1abfbf870ae27d60dbf6c9e2/build.gradle
        rewriteRun(buildGradle(
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'jakarta.servlet:jakarta.servlet-api:5.0.0'
                }
                """,
                """
                plugins { id 'java-library' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'jakarta.servlet:jakarta.servlet-api:6.1.0'
                }
                """
        ));
    }

    @Test
    void upgradesGradleMapNotationFrom4_0_4() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    compileOnly group: 'jakarta.servlet', name: 'jakarta.servlet-api', version: '4.0.4'
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    compileOnly group: 'jakarta.servlet', name: 'jakarta.servlet-api', version: '6.1.0'
                }
                """
        ));
    }

    @Test
    void upgradesGradleVersionVariableFrom6_0_0() {
        rewriteRun(buildGradle(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def servletVersion = '6.0.0'
                dependencies {
                    compileOnly "jakarta.servlet:jakarta.servlet-api:${servletVersion}"
                }
                """,
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                def servletVersion = '6.1.0'
                dependencies {
                    compileOnly "jakarta.servlet:jakarta.servlet-api:${servletVersion}"
                }
                """
        ));
    }

    @Test
    void leavesTargetAndLaterVersionsUntouched() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>target</artifactId><version>1</version><dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version></dependency></dependencies></project>
                        """),
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>later-milestone</artifactId><version>1</version><dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.2.0-M2</version></dependency></dependencies></project>
                        """, spec -> spec.path("later-milestone-pom.xml"))
        );
    }

    @Test
    void leavesLegacyAndSimilarCoordinatesUntouched() {
        rewriteRun(pomXml(
                """
                <project>
                  <modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>other-servlet-apis</artifactId><version>1</version>
                  <dependencies>
                    <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>4.0.1</version></dependency>
                    <dependency><groupId>jakarta.servlet.jsp</groupId><artifactId>jakarta.servlet.jsp-api</artifactId><version>3.1.1</version></dependency>
                    <dependency><groupId>org.eclipse.jetty.toolchain</groupId><artifactId>jetty-servlet-api</artifactId><version>4.0.4</version></dependency>
                  </dependencies>
                </project>
                """
        ));
    }

    @Test
    void dependencyRecipeDoesNotChangeJavaxSource() {
        rewriteRun(java(
                """
                import javax.servlet.Filter;

                class LegacyFilterHolder {
                    Filter filter;
                }
                """
        ));
    }

    @Test
    void comprehensiveRecipeMigratesServlet4Namespace() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.servlet.Filter;
                        import javax.servlet.ServletRequest;
                        import javax.servlet.annotation.WebServlet;
                        import javax.servlet.descriptor.JspPropertyGroupDescriptor;
                        import javax.servlet.http.HttpServletRequest;

                        @WebServlet("/status")
                        class LegacyServletTypes {
                            Filter filter;
                            ServletRequest request;
                            HttpServletRequest httpRequest;
                            JspPropertyGroupDescriptor descriptor;
                        }
                        """,
                        """
                        import jakarta.servlet.Filter;
                        import jakarta.servlet.ServletRequest;
                        import jakarta.servlet.annotation.WebServlet;
                        import jakarta.servlet.descriptor.JspPropertyGroupDescriptor;
                        import jakarta.servlet.http.HttpServletRequest;

                        @WebServlet("/status")
                        class LegacyServletTypes {
                            Filter filter;
                            ServletRequest request;
                            HttpServletRequest httpRequest;
                            JspPropertyGroupDescriptor descriptor;
                        }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeMigratesRemovedJavaxMethodCalls() {
        // The request call is reduced from YaCy's RequestHeader compatibility wrapper:
        // https://github.com/yacy/yacy_search_server/blob/1f181065cebaabff33f961ecdf81fb2a57748053/source/net/yacy/cora/protocol/RequestHeader.java
        // The session method set is exercised by Yona's PlayServletSession adapter:
        // https://github.com/yona-projects/yona/blob/60a5ac40689fc36ee5b55eddedd345fc34878190/app/utils/PlayServletSession.java
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.servlet.http.HttpServletRequest;
                        import javax.servlet.http.HttpServletResponse;
                        import javax.servlet.http.HttpSession;

                        class LegacyCalls {
                            void migrate(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
                                request.isRequestedSessionIdFromUrl();
                                response.encodeUrl("/home");
                                response.encodeRedirectUrl("/login");
                                session.getValue("user");
                                session.getValueNames();
                                session.putValue("user", new Object());
                                session.removeValue("user");
                            }
                        }
                        """,
                        """
                        import jakarta.servlet.http.HttpServletRequest;
                        import jakarta.servlet.http.HttpServletResponse;
                        import jakarta.servlet.http.HttpSession;

                        class LegacyCalls {
                            void migrate(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
                                request.isRequestedSessionIdFromURL();
                                response.encodeURL("/home");
                                response.encodeRedirectURL("/login");
                                session.getAttribute("user");
                                session.getAttributeNames();
                                session.setAttribute("user", new Object());
                                session.removeAttribute("user");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeMigratesRemovedJakarta5MethodCalls() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import jakarta.servlet.http.HttpServletRequest;
                        import jakarta.servlet.http.HttpServletResponse;
                        import jakarta.servlet.http.HttpSession;

                        class JakartaFiveCalls {
                            void migrate(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
                                request.isRequestedSessionIdFromUrl();
                                response.encodeUrl("/home");
                                response.encodeRedirectUrl("/login");
                                session.getValue("user");
                                session.putValue("user", "alice");
                                session.removeValue("user");
                            }
                        }
                        """,
                        """
                        import jakarta.servlet.http.HttpServletRequest;
                        import jakarta.servlet.http.HttpServletResponse;
                        import jakarta.servlet.http.HttpSession;

                        class JakartaFiveCalls {
                            void migrate(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
                                request.isRequestedSessionIdFromURL();
                                response.encodeURL("/home");
                                response.encodeRedirectURL("/login");
                                session.getAttribute("user");
                                session.setAttribute("user", "alice");
                                session.removeAttribute("user");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeUpdatesStandardWrapperCalls() {
        // Wrapper-shaped usage is reduced from Gitblit's ServletRequestWrapper:
        // https://github.com/pdinc-oss/gitblit/blob/ee443d9da3939395243e2f81436ad4059c7b72bb/src/com/gitblit/ServletRequestWrapper.java
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import javax.servlet.http.HttpServletRequestWrapper;
                        import javax.servlet.http.HttpServletResponseWrapper;

                        class WrapperCalls {
                            void migrate(HttpServletRequestWrapper request, HttpServletResponseWrapper response) {
                                request.isRequestedSessionIdFromUrl();
                                response.encodeUrl("/home");
                                response.encodeRedirectUrl("/login");
                            }
                        }
                        """,
                        """
                        import jakarta.servlet.http.HttpServletRequestWrapper;
                        import jakarta.servlet.http.HttpServletResponseWrapper;

                        class WrapperCalls {
                            void migrate(HttpServletRequestWrapper request, HttpServletResponseWrapper response) {
                                request.isRequestedSessionIdFromURL();
                                response.encodeURL("/home");
                                response.encodeRedirectURL("/login");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeMigratesSessionMethodReferences() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        import jakarta.servlet.http.HttpSession;
                        import java.util.function.BiConsumer;
                        import java.util.function.Consumer;
                        import java.util.function.Function;

                        class SessionMethodReferences {
                            void migrate(HttpSession session) {
                                Function<String, Object> read = session::getValue;
                                BiConsumer<String, Object> write = session::putValue;
                                Consumer<String> remove = session::removeValue;
                            }
                        }
                        """,
                        """
                        import jakarta.servlet.http.HttpSession;
                        import java.util.function.BiConsumer;
                        import java.util.function.Consumer;
                        import java.util.function.Function;

                        class SessionMethodReferences {
                            void migrate(HttpSession session) {
                                Function<String, Object> read = session::getAttribute;
                                BiConsumer<String, Object> write = session::setAttribute;
                                Consumer<String> remove = session::removeAttribute;
                            }
                        }
                        """
                )
        );
    }

    @Test
    void comprehensiveRecipeMigratesFullyQualifiedServlet4Types() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        class FullyQualifiedTypes {
                            javax.servlet.http.HttpServletRequest request;
                            javax.servlet.annotation.WebServlet annotation;
                        }
                        """,
                        """
                        class FullyQualifiedTypes {
                            jakarta.servlet.http.HttpServletRequest request;
                            jakarta.servlet.annotation.WebServlet annotation;
                        }
                        """
                )
        );
    }

    @Test
    void leavesSameNamedApplicationMethodsAndStringsUntouched() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION_RECIPE)),
                java(
                        """
                        class ApplicationSession {
                            Object getValue(String name) { return name; }
                            void putValue(String name, Object value) {}

                            void use() {
                                getValue("javax.servlet.Filter");
                                putValue("key", "javax.servlet.http.HttpSession");
                            }
                        }
                        """
                )
        );
    }

    @Test
    void discoversAndValidatesBothRecipes() {
        Environment environment = environment();
        Recipe dependency = environment.activateRecipes(DEPENDENCY_RECIPE);
        Recipe migration = environment.activateRecipes(MIGRATION_RECIPE);

        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> DEPENDENCY_RECIPE.equals(candidate.getName())));
        assertTrue(environment.listRecipes().stream()
                .anyMatch(candidate -> MIGRATION_RECIPE.equals(candidate.getName())));
        assertTrue(dependency.validate().isValid(), () -> dependency.validate().failures().toString());
        assertTrue(migration.validate().isValid(), () -> migration.validate().failures().toString());
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.jakartaservlet")
                .scanYamlResources()
                .build();
    }

    private static JavaParser.Builder<?, ?> servletParser() {
        return JavaParser.fromJavaVersion().classpath("javax.servlet-api", "jakarta.servlet-api");
    }
}
