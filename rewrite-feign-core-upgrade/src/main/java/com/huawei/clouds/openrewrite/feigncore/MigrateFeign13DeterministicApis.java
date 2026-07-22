package com.huawei.clouds.openrewrite.feigncore;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/** Source changes whose target behavior and type can be proved from the attributed call. */
public final class MigrateFeign13DeterministicApis extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic Feign 13 APIs";
    }

    @Override
    public String getDescription() {
        return "Correct the Feign 10 Contract validation method spelling, replace decode404 with its identical " +
               "dismiss404 name, and preserve epoch-millisecond RetryableException retry-after reads.";
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
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();

                if (methodOn(method, "feign.Contract", "parseAndValidatateMetadata")) {
                    return rename(visited, method, "parseAndValidateMetadata");
                }
                if (methodOn(method, "feign.BaseBuilder", "decode404") ||
                    methodOn(method, "feign.Feign$Builder", "decode404")) {
                    return rename(visited, method, "dismiss404");
                }

                if ("getTime".equals(visited.getSimpleName()) &&
                    (visited.getArguments().isEmpty() || visited.getArguments().stream().allMatch(J.Empty.class::isInstance)) &&
                    visited.getSelect() instanceof J.MethodInvocation retryAfter &&
                    retryAfter.getMethodType() != null && "retryAfter".equals(retryAfter.getSimpleName()) &&
                    "feign.RetryableException".equals(retryAfter.getMethodType().getDeclaringType()
                            .getFullyQualifiedName())) {
                    JavaType.Method retryType = retryAfter.getMethodType()
                            .withReturnType(JavaType.ShallowClass.build("java.lang.Long"));
                    return retryAfter.withPrefix(visited.getPrefix())
                            .withName(retryAfter.getName().withType(retryType))
                            .withMethodType(retryType);
                }
                return visited;
            }
        };
    }

    private static J.MethodInvocation rename(J.MethodInvocation invocation, JavaType.Method method, String name) {
        JavaType.Method replacement = method.withName(name);
        return invocation.withName(invocation.getName().withSimpleName(name).withType(replacement))
                .withMethodType(replacement);
    }

    static boolean methodOn(JavaType.Method method, String owner, String name) {
        return method != null && name.equals(method.getName()) && method.getDeclaringType() != null &&
               TypeUtils.isAssignableTo(owner, method.getDeclaringType());
    }
}
