package com.huawei.clouds.openrewrite.jakartaservlet;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

class JakartaServletRecommendedRecipeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(JakartaServletDependencyUpgradeTest.environment().activateRecipes(
                        JakartaServletDependencyUpgradeTest.MIGRATION_RECIPE))
                .parser(JakartaServletSourceMigrationTest.servletParser());
    }

    @Test
    void recommendedRecipeNeverManufacturesJakartaNamesForRemovedTypes() {
        rewriteRun(java(
                """
                import javax.servlet.Filter;
                import javax.servlet.SingleThreadModel;
                import javax.servlet.http.HttpSessionContext;
                import javax.servlet.http.HttpUtils;

                abstract class LegacyServlet implements SingleThreadModel {
                    Filter filter;
                    HttpSessionContext sessions;
                    HttpUtils utils;
                }
                """,
                """
                import javax.servlet.Filter;
                /*~~(Servlet 6.0 removed SingleThreadModel, HttpSessionContext and HttpUtils; redesign concurrency/session/URL behavior because there is no type-level Jakarta replacement)~~>*/import javax.servlet.SingleThreadModel;
                /*~~(Servlet 6.0 removed SingleThreadModel, HttpSessionContext and HttpUtils; redesign concurrency/session/URL behavior because there is no type-level Jakarta replacement)~~>*/import javax.servlet.http.HttpSessionContext;
                /*~~(Servlet 6.0 removed SingleThreadModel, HttpSessionContext and HttpUtils; redesign concurrency/session/URL behavior because there is no type-level Jakarta replacement)~~>*/import javax.servlet.http.HttpUtils;

                abstract class LegacyServlet implements SingleThreadModel {
                    Filter filter;
                    /*~~(This Servlet type was removed in 6.0 and has no direct replacement; redesign the owning behavior)~~>*/HttpSessionContext sessions;
                    /*~~(This Servlet type was removed in 6.0 and has no direct replacement; redesign the owning behavior)~~>*/HttpUtils utils;
                }
                """));
    }

    @Test
    void recommendedSafeMigrationCompletesInOneCycle() {
        rewriteRun(
                spec -> spec.cycles(1).expectedCyclesThatMakeChanges(1),
                java(
                        """
                        import javax.servlet.http.HttpServletRequest;
                        class LegacyRequest {
                            boolean encoded(HttpServletRequest request) {
                                return request.isRequestedSessionIdFromUrl();
                            }
                        }
                        """,
                        """
                        import jakarta.servlet.http.HttpServletRequest;
                        class LegacyRequest {
                            boolean encoded(HttpServletRequest request) {
                                return request.isRequestedSessionIdFromURL();
                            }
                        }
                        """));
    }

    @Test
    void recommendedRecipeIsNoOpOnAnAlreadyMigratedProject() {
        rewriteRun(
                pomXml("""
                        <project><modelVersion>4.0.0</modelVersion><groupId>example</groupId><artifactId>ready</artifactId><version>1</version>
                          <properties><maven.compiler.release>17</maven.compiler.release></properties>
                          <dependencies><dependency><groupId>jakarta.servlet</groupId><artifactId>jakarta.servlet-api</artifactId><version>6.1.0</version><scope>provided</scope></dependency></dependencies>
                        </project>
                        """),
                java("""
                        import jakarta.servlet.Filter;
                        class ReadyFilter { Filter filter; }
                        """),
                xml(
                        """
                        <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_1.xsd"
                                 version="6.1"/>
                        """,
                        source -> source.path("src/main/webapp/WEB-INF/web.xml")));
    }
}
