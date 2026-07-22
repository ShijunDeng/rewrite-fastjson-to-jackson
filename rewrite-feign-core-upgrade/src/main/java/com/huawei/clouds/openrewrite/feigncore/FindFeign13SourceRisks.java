package com.huawei.clouds.openrewrite.feigncore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/** Locate Feign 13 source boundaries that require application-specific design choices. */
public final class FindFeign13SourceRisks extends Recipe {
    private static final String RETRY_AFTER =
            "RetryableException.retryAfter() returns epoch milliseconds (Long) in Feign 13 instead of Date; " +
            "preserve null handling and choose Date/Instant/duration semantics at this exact use";
    private static final String RESPONSE_INTERCEPTOR =
            "Feign 13 stores a chain of response interceptors; repeated responseInterceptor calls append instead of " +
            "replacing the previous interceptor, so verify ordering, short-circuiting, decoding, and exception behavior";
    private static final String QUERY_MAP =
            "@QueryMap(encoded=true) no longer controls query-map encoding; choose a QueryMapEncoder and verify percent " +
            "encoding, nulls, collections, nested values, and already-encoded input";

    @Override
    public String getDisplayName() {
        return "Find Feign 13 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact retry timing, query encoding, interceptor, builder, contract, client, response-body, " +
               "exception, and retry extension points whose Feign 13 migration is not mechanically decidable.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedFeignCoreDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
                TypeTree base = visited.getExtends();
                if (base != null) {
                    String message = extensionMessage(base.getType());
                    if (message != null) visited = visited.withExtends(mark(base, message));
                }
                return visited.withImplements(ListUtils.map(visited.getImplements(), type -> {
                    String message = extensionMessage(type.getType());
                    return message == null ? type : mark(type, message);
                }));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (MigrateFeign13DeterministicApis.methodOn(method, "feign.RetryableException", "retryAfter") &&
                    !TypeUtils.isOfClassType(method.getReturnType(), "java.lang.Long") &&
                    !TypeUtils.isOfClassType(method.getReturnType(), "java.lang.long")) {
                    return mark(visited, RETRY_AFTER);
                }
                if (MigrateFeign13DeterministicApis.methodOn(method, "feign.BaseBuilder", "responseInterceptor") ||
                    MigrateFeign13DeterministicApis.methodOn(method, "feign.Feign$Builder", "responseInterceptor")) {
                    if (getCursor().getParentTreeCursor().getValue() instanceof J.MethodInvocation enclosing &&
                        enclosing.getSelect() instanceof J.MethodInvocation selected &&
                        selected.getId().equals(visited.getId()) &&
                        (MigrateFeign13DeterministicApis.methodOn(enclosing.getMethodType(), "feign.BaseBuilder", "responseInterceptor") ||
                         MigrateFeign13DeterministicApis.methodOn(enclosing.getMethodType(), "feign.Feign$Builder", "responseInterceptor"))) {
                        return visited;
                    }
                    return mark(visited, RESPONSE_INTERCEPTOR);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                JavaType.Method constructor = visited.getConstructorType();
                if (constructor != null && constructor.getDeclaringType() != null &&
                    TypeUtils.isAssignableTo("feign.RetryableException", constructor.getDeclaringType()) &&
                    constructor.getParameterTypes().stream()
                            .anyMatch(type -> TypeUtils.isOfClassType(type, "java.util.Date"))) {
                    return mark(visited, "This deprecated RetryableException constructor accepts Date only for " +
                                         "compatibility; migrate the retry-after argument to nullable epoch milliseconds " +
                                         "without changing no-retry/null or clock semantics");
                }
                return visited;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation visited = super.visitAnnotation(annotation, ctx);
                if (TypeUtils.isOfClassType(visited.getType(), "feign.QueryMap") &&
                    visited.getArguments() != null && visited.getArguments().stream().anyMatch(argument ->
                            argument instanceof J.Assignment assignment &&
                            assignment.getVariable() instanceof J.Identifier identifier &&
                            "encoded".equals(identifier.getSimpleName()) &&
                            assignment.getAssignment() instanceof J.Literal literal &&
                            Boolean.TRUE.equals(literal.getValue()))) {
                    return mark(visited, QUERY_MAP);
                }
                return visited;
            }
        };
    }

    private static String extensionMessage(JavaType type) {
        String name = typeName(type);
        if (name == null) return null;
        if (TypeUtils.isAssignableTo("feign.BaseBuilder", type) ||
            TypeUtils.isAssignableTo("feign.Feign$Builder", type)) {
            return "Feign 13 BaseBuilder has two type parameters and final build() delegates to internalBuild(); adapt this " +
                   "custom builder explicitly and verify capability enrichment, cloning, and interceptor ordering";
        }
        if (TypeUtils.isAssignableTo("feign.AsyncClient", type)) {
            return "Feign 13 changed AsyncClient execution/context contracts; recompile this implementation and verify " +
                   "request options, cancellation, executor ownership, context propagation, timeout, and completion errors";
        }
        if (TypeUtils.isAssignableTo("feign.Contract", type)) {
            return "Custom Feign Contract extension detected; recompile against 13.6 and verify inherited annotations, " +
                   "parameter processors, default/static methods, validation warnings, and QueryMap encoding";
        }
        if (TypeUtils.isAssignableTo("feign.Response$Body", type)) {
            return "Custom Response.Body detected; Feign 13 tightened body/close stream contracts, so verify one-shot " +
                   "consumption, charset, IOException handling, resource closure, and decoder ownership";
        }
        if (TypeUtils.isAssignableTo("feign.Retryer", type) ||
            TypeUtils.isAssignableTo("feign.Util$RetryAfterDecoder", type)) {
            return "Custom retry extension detected; Feign 13 uses nullable epoch milliseconds for retry-after, so verify " +
                   "clock arithmetic, backoff, interruption, clone state, maximum attempts, and propagation policy";
        }
        if (TypeUtils.isAssignableTo("feign.FeignException", type)) {
            return "Custom FeignException subtype detected; recompile against 13.6 constructors and verify response body, " +
                   "headers, request, cause, charset, serialization, and status-specific exception mapping";
        }
        return null;
    }

    private static String typeName(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq == null ? null : fq.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
