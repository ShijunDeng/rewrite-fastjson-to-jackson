package com.huawei.clouds.openrewrite.springretry;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Marks behavior-sensitive and binary-incompatible Spring Retry 2.0 boundaries. */
public final class FindSpringRetry20SourceRisks extends Recipe {
    static final String PROXY_RECOVERY =
            "Spring Retry 2.0 使用 Spring 6 AOP；验证 @EnableRetry/@Retryable/@Recover 的代理模式、self-invocation、" +
            "final/private 方法、advisor/transaction 顺序、恢复方法选择和异常传播";
    static final String EXPRESSION =
            "Spring Retry 2.0 的注解表达式支持初始化时 #{...} 与运行时无分隔符求值；验证 bean 引用、#root、" +
            "参数名、返回类型、异常表达式及每次重试的求值时机";
    static final String LISTENER =
            "RetryListener 在 2.0 中方法变为 default 并新增 onSuccess；验证 open 顺序、onError/close 逆序、" +
            "onSuccess 抛错后的重试分类、动态注册和线程安全";
    static final String LISTENER_SUPPORT =
            "RetryListenerSupport 自 2.0.1 起弃用并计划移除；可人工改为 implements RetryListener，" +
            "但需检查继承层次、构造、instanceof/反射和二进制兼容，配方不盲目 ChangeType";
    static final String REMOVED_RETHROW =
            "RetryTemplate 的 protected rethrow(RetryContext,String) 在 2.0 被替换为三参数方法；" +
            "旧 override/调用会编译失败，第三参数语义依赖私有 throwLastExceptionOnExhausted 状态，必须人工迁移";
    static final String ADVICE_RETURN =
            "RetryConfiguration.buildAdvice() 的 protected 返回类型由 Advice 收窄为 " +
            "AnnotationAwareRetryOperationsInterceptor；旧子类的宽返回类型 override 会编译失败";
    static final String STATEFUL_CACHE =
            "stateful retry、RetryState 或 RetryContextCache 的 key/equals、容量、并发、缓存淘汰、" +
            "事务边界与不可恢复异常语义必须用失败重放测试确认";
    static final String BACKOFF_POLICY =
            "RetryTemplate/RetryPolicy/BackOffPolicy 的 attempts、timeout、Sleeper、random/exponential backoff、" +
            "异常分类与恢复行为需用确定性时钟和故障注入测试确认";
    static final String STATISTICS =
            "StatisticsListener 在 2.0 不再继承 RetryListenerSupport；检查继承/反射/类型判断以及 label、" +
            "计数器生命周期和并发统计假设";
    static final String SERIALIZATION =
            "Spring Retry 策略/上下文类的序列化标识与继承结构跨 1.3.4/2.0.13 有变化；不要复用持久化、" +
            "分布式缓存或会话中的旧 Java 序列化对象";
    static final String METRICS =
            "MetricsRetryListener 依赖可选 Micrometer；验证 registry 生命周期、meter 命名、tag 基数、" +
            "重复注册及与自定义 RetryListener 的回调顺序";

    private static final Set<String> EXPRESSION_ATTRIBUTES = Set.of(
            "maxAttemptsExpression", "exceptionExpression", "delayExpression", "maxDelayExpression",
            "multiplierExpression", "resetTimeoutExpression", "openTimeoutExpression");
    private static final Set<String> LISTENER_METHODS = Set.of(
            "setListeners", "registerListener", "hasListeners", "open", "onError", "onSuccess", "close");
    private static final Set<String> CACHE_METHODS = Set.of(
            "setRetryContextCache", "setCircuitBreakerRetryContextCache", "get", "put", "remove", "containsKey");
    private static final Set<String> RETRY_METHODS = Set.of(
            "execute", "setRetryPolicy", "setBackOffPolicy", "withTimeout", "withinMillis", "maxAttempts",
            "infiniteRetry", "fixedBackoff", "exponentialBackoff", "uniformRandomBackoff", "noBackoff");

    @Override
    public String getDisplayName() {
        return "Find Spring Retry 2.0 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark proxy/recovery, expression, listener, removed protected API, advice return, stateful cache, " +
               "policy/backoff, statistics, serialization and Micrometer decisions.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return SpringRetrySupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import import_, ExecutionContext ctx) {
                J.Import visited = super.visitImport(import_, ctx);
                String type = visited.getTypeName();
                String message = importMessage(type);
                return message == null ? visited : mark(visited, message);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                String type = typeName(visited.getType());
                if ("org.springframework.retry.annotation.EnableRetry".equals(type) ||
                    "org.springframework.retry.annotation.Retryable".equals(type) ||
                    "org.springframework.retry.annotation.CircuitBreaker".equals(type) ||
                    "org.springframework.retry.annotation.Recover".equals(type)) {
                    visited = mark(visited, PROXY_RECOVERY);
                }
                if (retryAnnotation(type) && hasExpressionAttribute(visited)) {
                    visited = mark(visited, EXPRESSION);
                }
                if ("org.springframework.retry.annotation.Retryable".equals(type) &&
                    booleanAttribute(visited, "stateful")) {
                    visited = mark(visited, STATEFUL_CACHE);
                }
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                String parent = visited.getExtends() == null ? null : typeName(visited.getExtends().getType());
                if ("org.springframework.retry.support.RetryTemplate".equals(parent)) {
                    return mark(visited, REMOVED_RETHROW);
                }
                if ("org.springframework.retry.annotation.RetryConfiguration".equals(parent)) {
                    return mark(visited, ADVICE_RETURN);
                }
                if ("org.springframework.retry.listener.RetryListenerSupport".equals(parent)) {
                    return mark(mark(visited, LISTENER_SUPPORT), LISTENER);
                }
                if ("org.springframework.retry.stats.StatisticsListener".equals(parent)) {
                    return mark(visited, STATISTICS);
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                if ("rethrow".equals(visited.getSimpleName()) && parameterCount(visited) == 2 &&
                    enclosingExtends(getCursor(), "org.springframework.retry.support.RetryTemplate")) {
                    return mark(visited, REMOVED_RETHROW);
                }
                if ("buildAdvice".equals(visited.getSimpleName()) &&
                    enclosingExtends(getCursor(), "org.springframework.retry.annotation.RetryConfiguration")) {
                    return mark(visited, ADVICE_RETURN);
                }
                if (Set.of("open", "onError", "onSuccess", "close").contains(visited.getSimpleName()) &&
                    enclosingRetryListener(getCursor())) {
                    return mark(visited, LISTENER);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = visited.getMethodType();
                if (methodType == null) return visited;
                String owner = typeName(methodType.getDeclaringType());
                String name = visited.getSimpleName();
                if (retryListenerType(owner) && LISTENER_METHODS.contains(name)) return mark(visited, LISTENER);
                if (cacheType(owner) && CACHE_METHODS.contains(name)) return mark(visited, STATEFUL_CACHE);
                if (metricsType(owner)) return mark(visited, METRICS);
                if (statisticsType(owner)) return mark(visited, STATISTICS);
                if (policyType(owner) || backoffType(owner) ||
                    "org.springframework.retry.support.RetryTemplate".equals(owner) && RETRY_METHODS.contains(name) ||
                    "org.springframework.retry.support.RetryTemplateBuilder".equals(owner) &&
                    RETRY_METHODS.contains(name)) {
                    return mark(visited, BACKOFF_POLICY);
                }
                if (proxyType(owner)) return mark(visited, PROXY_RECOVERY);
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = typeName(visited.getType());
                if (cacheType(type)) return mark(visited, STATEFUL_CACHE);
                if (metricsType(type)) return mark(visited, METRICS);
                if (statisticsType(type)) return mark(visited, STATISTICS);
                if (policyType(type) || backoffType(type)) return mark(visited, BACKOFF_POLICY);
                return visited;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable,
                                                                     ExecutionContext ctx) {
                J.VariableDeclarations visited = super.visitVariableDeclarations(multiVariable, ctx);
                if (visited.getVariables().stream().anyMatch(variable ->
                        "serialVersionUID".equals(variable.getSimpleName())) && enclosingRetryType(getCursor())) {
                    return mark(visited, SERIALIZATION);
                }
                return visited;
            }
        };
    }

    private static String importMessage(String type) {
        if (type == null) return null;
        if ("org.springframework.retry.listener.RetryListenerSupport".equals(type)) return LISTENER_SUPPORT;
        if (retryListenerType(type)) return LISTENER;
        if (cacheType(type) || "org.springframework.retry.RetryState".equals(type)) return STATEFUL_CACHE;
        if (metricsType(type)) return METRICS;
        if (statisticsType(type)) return STATISTICS;
        if (policyType(type) || backoffType(type)) return BACKOFF_POLICY;
        return null;
    }

    private static boolean hasExpressionAttribute(J.Annotation annotation) {
        return annotation.getArguments() != null && annotation.getArguments().stream().anyMatch(argument ->
                argument instanceof J.Assignment assignment &&
                assignment.getVariable() instanceof J.Identifier identifier &&
                EXPRESSION_ATTRIBUTES.contains(identifier.getSimpleName()));
    }

    private static boolean booleanAttribute(J.Annotation annotation, String attribute) {
        return annotation.getArguments() != null && annotation.getArguments().stream().anyMatch(argument ->
                argument instanceof J.Assignment assignment &&
                assignment.getVariable() instanceof J.Identifier identifier &&
                attribute.equals(identifier.getSimpleName()) &&
                assignment.getAssignment() instanceof J.Literal literal &&
                Boolean.TRUE.equals(literal.getValue()));
    }

    private static boolean retryAnnotation(String type) {
        return "org.springframework.retry.annotation.Retryable".equals(type) ||
               "org.springframework.retry.annotation.CircuitBreaker".equals(type) ||
               "org.springframework.retry.annotation.Backoff".equals(type);
    }

    private static int parameterCount(J.MethodDeclaration method) {
        return method.getParameters().size();
    }

    private static boolean enclosingExtends(Cursor cursor, String parent) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof J.ClassDeclaration declaration) {
                return declaration.getExtends() != null &&
                       parent.equals(typeName(declaration.getExtends().getType()));
            }
            if (current.getValue() instanceof J.CompilationUnit) return false;
        }
        return false;
    }

    private static boolean enclosingRetryListener(Cursor cursor) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof J.ClassDeclaration declaration) {
                JavaType.FullyQualified type = declaration.getType();
                return type != null && TypeUtils.isAssignableTo("org.springframework.retry.RetryListener", type);
            }
            if (current.getValue() instanceof J.CompilationUnit) return false;
        }
        return false;
    }

    private static boolean enclosingRetryType(Cursor cursor) {
        for (Cursor current = cursor.getParentTreeCursor(); current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof J.ClassDeclaration declaration) {
                JavaType.FullyQualified type = declaration.getType();
                if (type == null) return false;
                String name = type.getFullyQualifiedName();
                return name.startsWith("org.springframework.retry.") ||
                       TypeUtils.isAssignableTo("org.springframework.retry.RetryPolicy", type) ||
                       TypeUtils.isAssignableTo("org.springframework.retry.RetryContext", type);
            }
            if (current.getValue() instanceof J.CompilationUnit) return false;
        }
        return false;
    }

    private static boolean retryListenerType(String type) {
        return type != null && (type.equals("org.springframework.retry.RetryListener") ||
               type.startsWith("org.springframework.retry.listener."));
    }

    private static boolean cacheType(String type) {
        return type != null && (type.startsWith("org.springframework.retry.policy.") &&
               (type.contains("RetryContextCache") || type.contains("MapRetryContextCache")));
    }

    private static boolean policyType(String type) {
        return type != null && (type.startsWith("org.springframework.retry.policy.") ||
               type.equals("org.springframework.retry.RetryPolicy"));
    }

    private static boolean backoffType(String type) {
        return type != null && type.startsWith("org.springframework.retry.backoff.");
    }

    private static boolean statisticsType(String type) {
        return type != null && type.startsWith("org.springframework.retry.stats.");
    }

    private static boolean metricsType(String type) {
        return type != null && (type.contains("MetricsRetryListener") ||
               type.startsWith("io.micrometer."));
    }

    private static boolean proxyType(String type) {
        return type != null && (type.startsWith("org.springframework.retry.interceptor.") ||
               type.startsWith("org.springframework.retry.annotation.AnnotationAware"));
    }

    private static String typeName(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? null : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
