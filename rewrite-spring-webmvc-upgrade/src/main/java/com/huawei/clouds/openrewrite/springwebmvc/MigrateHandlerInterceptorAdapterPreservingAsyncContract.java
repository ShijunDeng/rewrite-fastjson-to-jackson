package com.huawei.clouds.openrewrite.springwebmvc;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Replace the removed interceptor adapter without narrowing its async contract
 * or discarding interfaces already implemented by the application class.
 */
public final class MigrateHandlerInterceptorAdapterPreservingAsyncContract extends Recipe {
    private static final String ADAPTER =
            "org.springframework.web.servlet.handler.HandlerInterceptorAdapter";
    private static final String ASYNC_INTERCEPTOR =
            "org.springframework.web.servlet.AsyncHandlerInterceptor";

    @Override
    public String getDisplayName() {
        return "Replace HandlerInterceptorAdapter while preserving its async contract";
    }

    @Override
    public String getDescription() {
        return "Replace a direct HandlerInterceptorAdapter superclass with AsyncHandlerInterceptor only when no " +
               "superclass behavior is invoked, preserving every interface already implemented by the class.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration,
                                                             ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ctx);
                if (cd.getExtends() == null ||
                    !TypeUtils.isOfClassType(cd.getExtends().getType(), ADAPTER) ||
                    containsSuperReference(cd)) {
                    return cd;
                }

                JavaType.FullyQualified replacementType = JavaType.ShallowClass.build(ASYNC_INTERCEPTOR);
                TypeTree replacement = TypeTree.build("AsyncHandlerInterceptor").withType(replacementType);
                List<TypeTree> interfaces = cd.getImplements() == null ? new ArrayList<>() :
                        new ArrayList<>(cd.getImplements());
                if (interfaces.stream().noneMatch(type ->
                        TypeUtils.isOfClassType(type.getType(), ASYNC_INTERCEPTOR))) {
                    interfaces.add(replacement);
                }
                cd = cd.withExtends(null).withImplements(interfaces);
                if (cd.getType() instanceof JavaType.Class classType) {
                    List<JavaType.FullyQualified> attributed = new ArrayList<>(classType.getInterfaces());
                    if (attributed.stream().noneMatch(type ->
                            TypeUtils.isOfClassType(type, ASYNC_INTERCEPTOR))) {
                        attributed.add(replacementType);
                    }
                    cd = cd.withType(classType
                            .withSupertype(JavaType.ShallowClass.build("java.lang.Object"))
                            .withInterfaces(attributed));
                }
                maybeRemoveImport(ADAPTER);
                maybeAddImport(ASYNC_INTERCEPTOR);
                return autoFormat(cd, replacement, ctx, getCursor().getParentOrThrow());
            }
        };
    }

    private static boolean containsSuperReference(J.ClassDeclaration classDeclaration) {
        boolean[] found = {false};
        new JavaIsoVisitor<Integer>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Integer depth) {
                J.Identifier visited = super.visitIdentifier(identifier, depth);
                if ("super".equals(visited.getSimpleName())) found[0] = true;
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration nested, Integer depth) {
                if (depth > 0) return nested;
                return super.visitClassDeclaration(nested, depth + 1);
            }
        }.visit(classDeclaration, 0);
        return found[0];
    }
}
