package com.huawei.clouds.openrewrite.jakartaservlet;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class JakartaServletSourceMigrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(JakartaServletDependencyUpgradeTest.environment().activateRecipes(
                        JakartaServletDependencyUpgradeTest.MIGRATION_RECIPE))
                .parser(servletParser());
    }

    @Test
    void migratesServlet4NamespaceAcrossAllStandardPackages() {
        rewriteRun(java(
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
                """));
    }

    @Test
    void migratesProvenEquivalentJavaxAliasesFromRealRepositoryShapes() {
        // YaCy 1f181065... uses isRequestedSessionIdFromUrl; Yona 60a5ac4... implements session aliases.
        rewriteRun(java(
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
                        session.setAttribute("user", new Object());
                        session.removeAttribute("user");
                    }
                }
                """));
    }

    @Test
    void migratesProvenEquivalentJakarta5Aliases() {
        rewriteRun(java(
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
                """));
    }

    @Test
    void migratesSafeSessionMethodReferences() {
        rewriteRun(java(
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
                """));
    }

    @Test
    void migratesFullyQualifiedServlet4Types() {
        rewriteRun(java(
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
                """));
    }

    @Test
    void migratesAliasOverridesInYacyStyleCustomRequest() {
        // Reduced from YaCy RequestHeader at 1f181065cebaabff33f961ecdf81fb2a57748053.
        rewriteRun(java(
                """
                import javax.servlet.http.HttpServletRequest;

                abstract class RequestHeader implements HttpServletRequest {
                    private final HttpServletRequest delegate;
                    RequestHeader(HttpServletRequest delegate) { this.delegate = delegate; }

                    @Override
                    public boolean isRequestedSessionIdFromUrl() {
                        return delegate.isRequestedSessionIdFromUrl();
                    }
                }
                """,
                """
                import jakarta.servlet.http.HttpServletRequest;

                /*~~(Direct Servlet request/response/session implementation detected; recompile every method against 6.1 and review added defaults, removed aliases, async and wrapper identity behavior)~~>*/abstract class RequestHeader implements HttpServletRequest {
                    private final HttpServletRequest delegate;
                    RequestHeader(HttpServletRequest delegate) { this.delegate = delegate; }

                    @Override
                    public boolean isRequestedSessionIdFromURL() {
                        return delegate.isRequestedSessionIdFromURL();
                    }
                }
                """));
    }

    @Test
    void recommendedRecipeMarksRatherThanRenamesGetValueNames() {
        rewriteRun(java(
                """
                import javax.servlet.http.HttpSession;
                class SessionArrayConsumer {
                    String[] names(HttpSession session) { return session.getValueNames(); }
                }
                """,
                """
                import jakarta.servlet.http.HttpSession;
                class SessionArrayConsumer {
                    String[] names(HttpSession session) { return /*~~(HttpSession.getValueNames returned String[] but getAttributeNames returns Enumeration<String>; rewrite loops, arrays and method references manually)~~>*/session.getValueNames(); }
                }
                """));
    }

    @Test
    void migratesExactServletPackageNamesInXmlButPreservesPomMetadata() {
        rewriteRun(
                spec -> spec.recipe(new MigrateJakartaServletNamespaceXml()),
                xml(
                        """
                        <beans>
                          <bean class="javax.servlet.http.HttpServlet"/>
                          <property name="contract" value="javax.servlet.ServletContainerInitializer"/>
                        </beans>
                        """,
                        """
                        <beans>
                          <bean class="jakarta.servlet.http.HttpServlet"/>
                          <property name="contract" value="jakarta.servlet.ServletContainerInitializer"/>
                        </beans>
                        """,
                        source -> source.path("src/main/resources/servlet-context.xml")),
                xml(
                        "<project><properties><osgi.import>javax.servlet;version=4</osgi.import></properties></project>",
                        source -> source.path("pom.xml")));
    }

    @Test
    void leavesApplicationMethodsUntouched() {
        rewriteRun(java("""
                class ApplicationSession {
                    Object getValue(String name) { return name; }
                    void putValue(String name, Object value) {}
                    void use() { getValue("value"); putValue("key", "value"); }
                }
                """));
    }

    static JavaParser.Builder<?, ?> servletParser() {
        return JavaParser.fromJavaVersion().classpath("javax.servlet-api", "jakarta.servlet-api");
    }
}
