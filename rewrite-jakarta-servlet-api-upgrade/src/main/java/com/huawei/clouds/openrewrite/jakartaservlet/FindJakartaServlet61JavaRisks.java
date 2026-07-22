package com.huawei.clouds.openrewrite.jakartaservlet;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Type-aware source review markers for removed and behavior-sensitive Servlet APIs. */
public final class FindJakartaServlet61JavaRisks extends Recipe {
    private static final Set<String> REMOVED_TYPES = Set.of(
            "javax.servlet.SingleThreadModel", "jakarta.servlet.SingleThreadModel",
            "javax.servlet.http.HttpSessionContext", "jakarta.servlet.http.HttpSessionContext",
            "javax.servlet.http.HttpUtils", "jakarta.servlet.http.HttpUtils");
    private static final Set<String> PARAMETER_METHODS = Set.of(
            "getParameter", "getParameterNames", "getParameterValues", "getParameterMap");
    private static final Set<String> REQUEST_PATH_METHODS = Set.of(
            "getRequestURI", "getRequestURL", "getContextPath", "getServletPath", "getPathInfo", "getPathTranslated");
    private static final Set<String> CONTEXT_PATH_METHODS = Set.of(
            "getRealPath", "getResource", "getResourceAsStream", "getResourcePaths", "getRequestDispatcher");
    private static final Set<String> ASYNC_METHODS = Set.of(
            "startAsync", "dispatch", "complete", "isReady", "setReadListener", "setWriteListener");
    private static final Set<String> COOKIE_COMPATIBILITY_METHODS = Set.of(
            "getComment", "setComment", "getVersion", "setVersion");
    private static final Set<String> REMOVED_CONTEXT_ENUMERATION_METHODS = Set.of(
            "getServlet", "getServlets", "getServletNames");

    @Override
    public String getDisplayName() {
        return "Find Jakarta Servlet 6.1 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark removed APIs without equivalent replacements, changed return types, custom wrappers and " +
               "initializers, parameter/error/URI/async behavior, Cookie and Push APIs, and SecurityManager usage.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private boolean servletSource;
            private boolean errorDispatchSource;

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                boolean previousServlet = servletSource;
                boolean previousError = errorDispatchSource;
                String source = compilationUnit.printAll();
                servletSource = source.contains("javax.servlet") || source.contains("jakarta.servlet");
                errorDispatchSource = source.contains("DispatcherType.ERROR") ||
                                      source.contains("RequestDispatcher.ERROR_") ||
                                      source.contains("servlet.error.");
                J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, ctx);
                if ("module-info.java".equals(cu.getSourcePath().getFileName().toString()) &&
                    cu.printAll().contains("requires java.servlet;")) {
                    cu = SearchResult.found(cu,
                            "Servlet 6.1 module name is jakarta.servlet; update the requires directive and verify every module-path dependency uses Jakarta types");
                }
                servletSource = previousServlet;
                errorDispatchSource = previousError;
                return cu;
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import i = super.visitImport(anImport, ctx);
                String imported = i.getQualid().printTrimmed(getCursor());
                if (REMOVED_TYPES.stream().anyMatch(type -> imported.equals(type) || imported.startsWith(type + "."))) {
                    return SearchResult.found(i,
                            "Servlet 6.0 removed SingleThreadModel, HttpSessionContext and HttpUtils; redesign concurrency/session/URL behavior because there is no type-level Jakarta replacement");
                }
                return i;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (directlyImplements(c, "javax.servlet.ServletContainerInitializer") ||
                    directlyImplements(c, "jakarta.servlet.ServletContainerInitializer")) {
                    return SearchResult.found(c,
                            "Custom ServletContainerInitializer detected; migrate its service-file path, HandlesTypes ecosystem and startup ordering together");
                }
                if (directlyImplementsServletContract(c)) {
                    return SearchResult.found(c,
                            "Direct Servlet request/response/session implementation detected; recompile every method against 6.1 and review added defaults, removed aliases, async and wrapper identity behavior");
                }
                if (extendsServletWrapper(c)) {
                    return SearchResult.found(c,
                            "Custom Servlet wrapper detected; remove obsolete overrides and verify delegation for request IDs, mapping, trailers, push, async and error dispatch");
                }
                return c;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                      ExecutionContext ctx) {
                J.VariableDeclarations v = super.visitVariableDeclarations(multiVariable, ctx);
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(v.getType());
                if (type != null && REMOVED_TYPES.contains(type.getFullyQualifiedName())) {
                    return SearchResult.found(v,
                            "This Servlet type was removed in 6.0 and has no direct replacement; redesign the owning behavior");
                }
                return v;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                if ("getValueNames".equals(m.getSimpleName()) && onHttpSession(m.getMethodType())) {
                    return SearchResult.found(m,
                            "HttpSession.getValueNames returned String[] but getAttributeNames returns Enumeration<String>; rewrite the declaration and every consumer explicitly");
                }
                if (("getSessionContext".equals(m.getSimpleName()) || "getRealPath".equals(m.getSimpleName())) &&
                    onServletContract(m.getMethodType())) {
                    return SearchResult.found(m,
                            "This overridden Servlet method was removed; choose a replacement contract and remove the override only after downstream callers are migrated");
                }
                return m;
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                J.MemberReference reference = super.visitMemberReference(memberRef, ctx);
                if ("getValueNames".equals(reference.getReference().getSimpleName()) &&
                    onHttpSession(reference.getMethodType())) {
                    return SearchResult.found(reference,
                            "getValueNames returns String[] while getAttributeNames returns Enumeration<String>; adapt this functional type and consumer manually");
                }
                return reference;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (isUnavailableException(n.getType()) && legacyUnavailableConstructor(n)) {
                    return SearchResult.found(n,
                            "UnavailableException constructors that accepted a Servlet were removed; preserve permanent/temporary intent with (message) or (message, seconds) explicitly");
                }
                return n;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (l.getValue() instanceof String value && value.contains("javax.servlet.")) {
                    return SearchResult.found(l,
                            "String-based javax.servlet reference is not type-safe; identify its reflection, scanner, serialization or framework owner before changing it");
                }
                return l;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                String name = m.getSimpleName();
                if ("getValueNames".equals(name) && onHttpSession(methodType)) {
                    return SearchResult.found(m,
                            "HttpSession.getValueNames returned String[] but getAttributeNames returns Enumeration<String>; rewrite loops, arrays and method references manually");
                }
                if ("getSessionContext".equals(name) && onHttpSession(methodType)) {
                    return SearchResult.found(m,
                            "HttpSessionContext/getSessionContext was removed without replacement; use an application-owned, cluster-aware and privacy-reviewed session registry if truly required");
                }
                if ("getRealPath".equals(name) && onRequest(methodType)) {
                    return SearchResult.found(m,
                            "ServletRequest.getRealPath was removed; prefer stream/classpath access or call ServletContext.getRealPath while handling null for non-expanded deployments");
                }
                if ("setStatus".equals(name) && m.getArguments().size() == 2 && onResponse(methodType)) {
                    return SearchResult.found(m,
                            "setStatus(int,String) was removed and its message was ambiguous; choose setStatus(int) or sendError(int,String) after deciding commit/error-page semantics");
                }
                if ("log".equals(name) && legacyServletContextLog(m) && onServletContext(methodType)) {
                    return SearchResult.found(m,
                            "ServletContext.log(Exception,String) was removed; use log(String,Throwable) and preserve message/exception ordering deliberately");
                }
                if ("getServlet".equals(name) && onUnavailableException(methodType)) {
                    return SearchResult.found(m,
                            "UnavailableException.getServlet was removed without replacement; keep component identity in application diagnostics if needed");
                }
                if (REMOVED_CONTEXT_ENUMERATION_METHODS.contains(name) && onServletContext(methodType)) {
                    return SearchResult.found(m,
                            "ServletContext servlet enumeration/lookup APIs were removed without replacement; use application-owned registration metadata rather than container instance discovery");
                }
                if (PARAMETER_METHODS.contains(name) && onRequest(methodType)) {
                    return SearchResult.found(m,
                            "Servlet 6.1 parameter parsing may throw IllegalStateException for malformed encoding, I/O and configured limits; test and map failures instead of treating them as missing parameters");
                }
                if ("newPushBuilder".equals(name) && onHttpRequest(methodType) || onPushBuilder(methodType)) {
                    return SearchResult.found(m,
                            "HTTP/2 server push is deprecated and optional in Servlet 6.1; handle null and migrate toward 103 Early Hints/preload/cache behavior");
                }
                if (COOKIE_COMPATIBILITY_METHODS.contains(name) && onCookieOrSessionCookieConfig(methodType)) {
                    return SearchResult.found(m,
                            "RFC 2109 Cookie comment/version APIs are deprecated for removal; migrate to RFC 6265 attributes and test SameSite, Partitioned, domain/path and header serialization");
                }
                if (errorDispatchSource && ("getMethod".equals(name) || "getQueryString".equals(name)) &&
                    onHttpRequest(methodType)) {
                    return SearchResult.found(m,
                            "Servlet 6.1 performs error dispatch as GET; use RequestDispatcher.ERROR_METHOD/ERROR_QUERY_STRING for the original request and retest auth, CSRF, audit and cache logic");
                }
                if (REQUEST_PATH_METHODS.contains(name) && onHttpRequest(methodType) ||
                    CONTEXT_PATH_METHODS.contains(name) && onServletContext(methodType)) {
                    return SearchResult.found(m,
                            "URI/path canonicalization and getRealPath behavior were clarified; test encoded separators, dot segments, matrix parameters, proxies and non-expanded WARs before security decisions");
                }
                if (ASYNC_METHODS.contains(name) && onAsyncOrNonBlockingType(methodType)) {
                    return SearchResult.found(m,
                            "Async/non-blocking scheduling and isReady semantics require container integration tests for dispatch serialization, close/complete ordering, back-pressure and failures");
                }
                if (servletSource && securityManagerCall(m)) {
                    return SearchResult.found(m,
                            "Servlet 6.1 removed SecurityManager requirements; replace this permission boundary with process/container/platform isolation and explicit application authorization");
                }
                return m;
            }
        };
    }

    private static boolean directlyImplementsServletContract(J.ClassDeclaration declaration) {
        return directlyImplements(declaration, "javax.servlet.ServletRequest") ||
               directlyImplements(declaration, "jakarta.servlet.ServletRequest") ||
               directlyImplements(declaration, "javax.servlet.ServletResponse") ||
               directlyImplements(declaration, "jakarta.servlet.ServletResponse") ||
               directlyImplements(declaration, "javax.servlet.http.HttpSession") ||
               directlyImplements(declaration, "jakarta.servlet.http.HttpSession");
    }

    private static boolean directlyImplements(J.ClassDeclaration declaration, String type) {
        return declaration.getImplements() != null && declaration.getImplements().stream()
                .anyMatch(implemented -> TypeUtils.isAssignableTo(type, implemented.getType()));
    }

    private static boolean extendsServletWrapper(J.ClassDeclaration declaration) {
        return declaration.getExtends() != null && (
                TypeUtils.isAssignableTo("javax.servlet.ServletRequestWrapper", declaration.getExtends().getType()) ||
                TypeUtils.isAssignableTo("jakarta.servlet.ServletRequestWrapper", declaration.getExtends().getType()) ||
                TypeUtils.isAssignableTo("javax.servlet.ServletResponseWrapper", declaration.getExtends().getType()) ||
                TypeUtils.isAssignableTo("jakarta.servlet.ServletResponseWrapper", declaration.getExtends().getType()));
    }

    private static boolean legacyUnavailableConstructor(J.NewClass newClass) {
        if (newClass.getArguments().size() == 3) {
            return true;
        }
        if (newClass.getArguments().size() != 2) {
            return false;
        }
        Expression first = newClass.getArguments().get(0);
        return TypeUtils.isAssignableTo("javax.servlet.Servlet", first.getType()) ||
               TypeUtils.isAssignableTo("jakarta.servlet.Servlet", first.getType());
    }

    private static boolean onHttpSession(JavaType.Method method) {
        return on(method, "javax.servlet.http.HttpSession", "jakarta.servlet.http.HttpSession");
    }

    private static boolean onRequest(JavaType.Method method) {
        return on(method, "javax.servlet.ServletRequest", "jakarta.servlet.ServletRequest");
    }

    private static boolean onHttpRequest(JavaType.Method method) {
        return on(method, "javax.servlet.http.HttpServletRequest", "jakarta.servlet.http.HttpServletRequest");
    }

    private static boolean onResponse(JavaType.Method method) {
        return on(method, "javax.servlet.http.HttpServletResponse", "jakarta.servlet.http.HttpServletResponse");
    }

    private static boolean onServletContext(JavaType.Method method) {
        return on(method, "javax.servlet.ServletContext", "jakarta.servlet.ServletContext");
    }

    private static boolean onUnavailableException(JavaType.Method method) {
        return on(method, "javax.servlet.UnavailableException", "jakarta.servlet.UnavailableException");
    }

    private static boolean onPushBuilder(JavaType.Method method) {
        return on(method, "javax.servlet.http.PushBuilder", "jakarta.servlet.http.PushBuilder");
    }

    private static boolean onCookieOrSessionCookieConfig(JavaType.Method method) {
        return on(method, "javax.servlet.http.Cookie", "jakarta.servlet.http.Cookie",
                "javax.servlet.SessionCookieConfig", "jakarta.servlet.SessionCookieConfig");
    }

    private static boolean onAsyncOrNonBlockingType(JavaType.Method method) {
        return on(method, "javax.servlet.ServletRequest", "jakarta.servlet.ServletRequest",
                "javax.servlet.AsyncContext", "jakarta.servlet.AsyncContext",
                "javax.servlet.ServletInputStream", "jakarta.servlet.ServletInputStream",
                "javax.servlet.ServletOutputStream", "jakarta.servlet.ServletOutputStream");
    }

    private static boolean onServletContract(JavaType.Method method) {
        return onRequest(method) || onHttpSession(method);
    }

    private static boolean isUnavailableException(JavaType type) {
        return TypeUtils.isAssignableTo("javax.servlet.UnavailableException", type) ||
               TypeUtils.isAssignableTo("jakarta.servlet.UnavailableException", type);
    }

    private static boolean on(JavaType.Method method, String... owners) {
        if (method == null) {
            return false;
        }
        for (String owner : owners) {
            if (TypeUtils.isAssignableTo(owner, method.getDeclaringType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean securityManagerCall(J.MethodInvocation method) {
        JavaType.Method type = method.getMethodType();
        if (type == null) {
            return false;
        }
        JavaType.FullyQualified owner = TypeUtils.asFullyQualified(type.getDeclaringType());
        String fqn = owner == null ? "" : owner.getFullyQualifiedName();
        return ("java.lang.System".equals(fqn) &&
                ("getSecurityManager".equals(method.getSimpleName()) || "setSecurityManager".equals(method.getSimpleName()))) ||
               ("java.security.AccessController".equals(fqn) && "doPrivileged".equals(method.getSimpleName()));
    }

    private static boolean legacyServletContextLog(J.MethodInvocation method) {
        if (method.getArguments().size() != 2) {
            return false;
        }
        JavaType.Method methodType = method.getMethodType();
        JavaType first = methodType != null && methodType.getParameterTypes().size() == 2
                ? methodType.getParameterTypes().get(0) : method.getArguments().get(0).getType();
        JavaType second = methodType != null && methodType.getParameterTypes().size() == 2
                ? methodType.getParameterTypes().get(1) : method.getArguments().get(1).getType();
        return TypeUtils.isAssignableTo("java.lang.Exception", first) &&
               TypeUtils.isOfClassType(second, "java.lang.String");
    }
}
