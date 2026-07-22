package com.huawei.clouds.openrewrite.jakartaservlet;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JakartaServletJavaRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindJakartaServlet61JavaRisks())
                .parser(JakartaServletSourceMigrationTest.servletParser());
    }

    @Test
    void marksGetValueNamesCallAndMethodReferenceWithoutUnsafeRename() {
        rewriteRun(java(
                """
                import jakarta.servlet.http.HttpSession;
                import java.util.function.Supplier;

                class SessionNames {
                    void read(HttpSession session) {
                        String[] names = session.getValueNames();
                        Supplier<String[]> supplier = session::getValueNames;
                    }
                }
                """,
                """
                import jakarta.servlet.http.HttpSession;
                import java.util.function.Supplier;

                class SessionNames {
                    void read(HttpSession session) {
                        String[] names = /*~~(HttpSession.getValueNames returned String[] but getAttributeNames returns Enumeration<String>; rewrite loops, arrays and method references manually)~~>*/session.getValueNames();
                        Supplier<String[]> supplier = /*~~(getValueNames returns String[] while getAttributeNames returns Enumeration<String>; adapt this functional type and consumer manually)~~>*/session::getValueNames;
                    }
                }
                """));
    }

    @Test
    void marksRemovedTypesWithoutInventingJakartaReplacements() {
        rewriteRun(java(
                """
                import javax.servlet.SingleThreadModel;
                import javax.servlet.http.HttpSessionContext;
                import javax.servlet.http.HttpUtils;

                class RemovedTypes implements SingleThreadModel {
                    HttpSessionContext sessions;
                    HttpUtils utils;
                }
                """,
                """
                /*~~(Servlet 6.0 removed SingleThreadModel, HttpSessionContext and HttpUtils; redesign concurrency/session/URL behavior because there is no type-level Jakarta replacement)~~>*/import javax.servlet.SingleThreadModel;
                /*~~(Servlet 6.0 removed SingleThreadModel, HttpSessionContext and HttpUtils; redesign concurrency/session/URL behavior because there is no type-level Jakarta replacement)~~>*/import javax.servlet.http.HttpSessionContext;
                /*~~(Servlet 6.0 removed SingleThreadModel, HttpSessionContext and HttpUtils; redesign concurrency/session/URL behavior because there is no type-level Jakarta replacement)~~>*/import javax.servlet.http.HttpUtils;

                class RemovedTypes implements SingleThreadModel {
                    /*~~(This Servlet type was removed in 6.0 and has no direct replacement; redesign the owning behavior)~~>*/HttpSessionContext sessions;
                    /*~~(This Servlet type was removed in 6.0 and has no direct replacement; redesign the owning behavior)~~>*/HttpUtils utils;
                }
                """));
    }

    @Test
    void marksRemovedMethodsAndUnavailableExceptionContracts() {
        rewriteRun(java(
                """
                import javax.servlet.Servlet;
                import javax.servlet.ServletContext;
                import javax.servlet.ServletRequest;
                import javax.servlet.UnavailableException;
                import javax.servlet.http.HttpServletResponse;
                import javax.servlet.http.HttpSession;

                class RemovedCalls {
                    void use(Servlet servlet, ServletContext context, ServletRequest request,
                             HttpServletResponse response, HttpSession session, Exception failure) {
                        session.getSessionContext();
                        request.getRealPath("/WEB-INF/data");
                        response.setStatus(503, "unavailable");
                        context.log(failure, "failed");
                        context.getServletNames();
                        UnavailableException unavailable = new UnavailableException(servlet, "down");
                        unavailable.getServlet();
                    }
                }
                """,
                """
                import javax.servlet.Servlet;
                import javax.servlet.ServletContext;
                import javax.servlet.ServletRequest;
                import javax.servlet.UnavailableException;
                import javax.servlet.http.HttpServletResponse;
                import javax.servlet.http.HttpSession;

                class RemovedCalls {
                    void use(Servlet servlet, ServletContext context, ServletRequest request,
                             HttpServletResponse response, HttpSession session, Exception failure) {
                        /*~~(HttpSessionContext/getSessionContext was removed without replacement; use an application-owned, cluster-aware and privacy-reviewed session registry if truly required)~~>*/session.getSessionContext();
                        /*~~(ServletRequest.getRealPath was removed; prefer stream/classpath access or call ServletContext.getRealPath while handling null for non-expanded deployments)~~>*/request.getRealPath("/WEB-INF/data");
                        /*~~(setStatus(int,String) was removed and its message was ambiguous; choose setStatus(int) or sendError(int,String) after deciding commit/error-page semantics)~~>*/response.setStatus(503, "unavailable");
                        /*~~(ServletContext.log(Exception,String) was removed; use log(String,Throwable) and preserve message/exception ordering deliberately)~~>*/context.log(failure, "failed");
                        /*~~(ServletContext servlet enumeration/lookup APIs were removed without replacement; use application-owned registration metadata rather than container instance discovery)~~>*/context.getServletNames();
                        UnavailableException unavailable = /*~~(UnavailableException constructors that accepted a Servlet were removed; preserve permanent/temporary intent with (message) or (message, seconds) explicitly)~~>*/new UnavailableException(servlet, "down");
                        /*~~(UnavailableException.getServlet was removed without replacement; keep component identity in application diagnostics if needed)~~>*/unavailable.getServlet();
                    }
                }
                """));
    }

    @Test
    void marksCustomInitializerAndWrapperBoundaries() {
        rewriteRun(
                java(
                        """
                        import jakarta.servlet.ServletContainerInitializer;
                        import jakarta.servlet.ServletContext;
                        import jakarta.servlet.ServletException;
                        import java.util.Set;

                        class Startup implements ServletContainerInitializer {
                            public void onStartup(Set<Class<?>> classes, ServletContext context) throws ServletException {}
                        }
                        """,
                        """
                        import jakarta.servlet.ServletContainerInitializer;
                        import jakarta.servlet.ServletContext;
                        import jakarta.servlet.ServletException;
                        import java.util.Set;

                        /*~~(Custom ServletContainerInitializer detected; migrate its service-file path, HandlesTypes ecosystem and startup ordering together)~~>*/class Startup implements ServletContainerInitializer {
                            public void onStartup(Set<Class<?>> classes, ServletContext context) throws ServletException {}
                        }
                        """),
                java(
                        """
                        import jakarta.servlet.http.HttpServletRequest;
                        import jakarta.servlet.http.HttpServletRequestWrapper;

                        class RequestWrapper extends HttpServletRequestWrapper {
                            RequestWrapper(HttpServletRequest request) { super(request); }
                        }
                        """,
                        """
                        import jakarta.servlet.http.HttpServletRequest;
                        import jakarta.servlet.http.HttpServletRequestWrapper;

                        /*~~(Custom Servlet wrapper detected; remove obsolete overrides and verify delegation for request IDs, mapping, trailers, push, async and error dispatch)~~>*/class RequestWrapper extends HttpServletRequestWrapper {
                            RequestWrapper(HttpServletRequest request) { super(request); }
                        }
                        """,
                        spec -> spec.path("RequestWrapper.java")));
    }

    @Test
    void marksGitblitStyleDirectRequestImplementation() {
        // Reduced from pdinc-oss/gitblit ee443d9da3939395243e2f81436ad4059c7b72bb.
        rewriteRun(java(
                """
                import javax.servlet.http.HttpServletRequest;
                abstract class ServletRequestWrapper implements HttpServletRequest {}
                """,
                """
                import javax.servlet.http.HttpServletRequest;
                /*~~(Direct Servlet request/response/session implementation detected; recompile every method against 6.1 and review added defaults, removed aliases, async and wrapper identity behavior)~~>*/abstract class ServletRequestWrapper implements HttpServletRequest {}
                """));
    }

    @Test
    void marksParameterParsingFailureBoundary() {
        rewriteRun(java(
                """
                import jakarta.servlet.ServletRequest;
                class Parameters {
                    String read(ServletRequest request) { return request.getParameter("q"); }
                }
                """,
                """
                import jakarta.servlet.ServletRequest;
                class Parameters {
                    String read(ServletRequest request) { return /*~~(Servlet 6.1 parameter parsing may throw IllegalStateException for malformed encoding, I/O and configured limits; test and map failures instead of treating them as missing parameters)~~>*/request.getParameter("q"); }
                }
                """));
    }

    @Test
    void marksErrorDispatchOriginalMethodAndQueryAssumptions() {
        rewriteRun(java(
                """
                import jakarta.servlet.DispatcherType;
                import jakarta.servlet.http.HttpServletRequest;

                class ErrorFilter {
                    void audit(HttpServletRequest request) {
                        if (request.getDispatcherType() == DispatcherType.ERROR) {
                            request.getMethod();
                            request.getQueryString();
                        }
                    }
                }
                """,
                """
                import jakarta.servlet.DispatcherType;
                import jakarta.servlet.http.HttpServletRequest;

                class ErrorFilter {
                    void audit(HttpServletRequest request) {
                        if (request.getDispatcherType() == DispatcherType.ERROR) {
                            /*~~(Servlet 6.1 performs error dispatch as GET; use RequestDispatcher.ERROR_METHOD/ERROR_QUERY_STRING for the original request and retest auth, CSRF, audit and cache logic)~~>*/request.getMethod();
                            /*~~(Servlet 6.1 performs error dispatch as GET; use RequestDispatcher.ERROR_METHOD/ERROR_QUERY_STRING for the original request and retest auth, CSRF, audit and cache logic)~~>*/request.getQueryString();
                        }
                    }
                }
                """));
    }

    @Test
    void marksCookieAndOptionalPushBehavior() {
        rewriteRun(java(
                """
                import jakarta.servlet.http.Cookie;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.PushBuilder;

                class LegacyHttpFeatures {
                    void use(Cookie cookie, HttpServletRequest request, PushBuilder push) {
                        cookie.setVersion(1);
                        request.newPushBuilder();
                        push.path("/asset.css");
                    }
                }
                """,
                """
                import jakarta.servlet.http.Cookie;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.PushBuilder;

                class LegacyHttpFeatures {
                    void use(Cookie cookie, HttpServletRequest request, PushBuilder push) {
                        /*~~(RFC 2109 Cookie comment/version APIs are deprecated for removal; migrate to RFC 6265 attributes and test SameSite, Partitioned, domain/path and header serialization)~~>*/cookie.setVersion(1);
                        /*~~(HTTP/2 server push is deprecated and optional in Servlet 6.1; handle null and migrate toward 103 Early Hints/preload/cache behavior)~~>*/request.newPushBuilder();
                        /*~~(HTTP/2 server push is deprecated and optional in Servlet 6.1; handle null and migrate toward 103 Early Hints/preload/cache behavior)~~>*/push.path("/asset.css");
                    }
                }
                """));
    }

    @Test
    void marksUriAndAsyncBoundaries() {
        rewriteRun(java(
                """
                import jakarta.servlet.AsyncContext;
                import jakarta.servlet.http.HttpServletRequest;

                class AsyncPaths {
                    void run(HttpServletRequest request) {
                        request.getRequestURI();
                        AsyncContext async = request.startAsync();
                        async.complete();
                    }
                }
                """,
                """
                import jakarta.servlet.AsyncContext;
                import jakarta.servlet.http.HttpServletRequest;

                class AsyncPaths {
                    void run(HttpServletRequest request) {
                        /*~~(URI/path canonicalization and getRealPath behavior were clarified; test encoded separators, dot segments, matrix parameters, proxies and non-expanded WARs before security decisions)~~>*/request.getRequestURI();
                        AsyncContext async = /*~~(Async/non-blocking scheduling and isReady semantics require container integration tests for dispatch serialization, close/complete ordering, back-pressure and failures)~~>*/request.startAsync();
                        /*~~(Async/non-blocking scheduling and isReady semantics require container integration tests for dispatch serialization, close/complete ordering, back-pressure and failures)~~>*/async.complete();
                    }
                }
                """));
    }

    @Test
    void marksSecurityManagerAndReflectionStringsOnlyInServletSource() {
        rewriteRun(java(
                """
                import jakarta.servlet.ServletRequest;
                class SecurityBoundary {
                    void use(ServletRequest request) {
                        System.getSecurityManager();
                        String reflected = "javax.servlet.http.HttpServletRequest";
                    }
                }
                """,
                """
                import jakarta.servlet.ServletRequest;
                class SecurityBoundary {
                    void use(ServletRequest request) {
                        /*~~(Servlet 6.1 removed SecurityManager requirements; replace this permission boundary with process/container/platform isolation and explicit application authorization)~~>*/System.getSecurityManager();
                        String reflected = /*~~(String-based javax.servlet reference is not type-safe; identify its reflection, scanner, serialization or framework owner before changing it)~~>*/"javax.servlet.http.HttpServletRequest";
                    }
                }
                """));
    }

    @Test
    void leavesStableServletApisUntouched() {
        rewriteRun(java("""
                import jakarta.servlet.ServletContext;
                import jakarta.servlet.http.HttpServletResponse;
                import jakarta.servlet.http.HttpSession;

                class StableApis {
                    void use(ServletContext context, HttpServletResponse response, HttpSession session, Exception failure) {
                        session.getAttribute("user");
                        session.setAttribute("user", "alice");
                        response.setStatus(204);
                        context.log("failed", failure);
                    }
                }
                """));
    }
}
