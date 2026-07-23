package com.huawei.clouds.openrewrite.disruptor;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/** Fold extension interfaces removed in Disruptor 4 into EventHandler implementations. */
public final class FoldDisruptorEventHandlerExtensions extends Recipe {
    private static final String EVENT_HANDLER = "com.lmax.disruptor.EventHandler";
    private static final String LIFECYCLE = "com.lmax.disruptor.LifecycleAware";
    private static final String BATCH_START = "com.lmax.disruptor.BatchStartAware";
    private static final String TIMEOUT = "com.lmax.disruptor.TimeoutHandler";
    private static final String MIGRATE_BATCH = "disruptor-migrate-batch-start";

    @Override
    public String getDisplayName() {
        return "Fold removed Disruptor handler extension interfaces";
    }

    @Override
    public String getDescription() {
        return "Remove LifecycleAware, BatchStartAware, and TimeoutHandler from classes that are already EventHandler " +
               "implementations, and add the new queueDepth parameter to their one-argument onBatchStart override.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate queueDepthParameter = JavaTemplate.builder("long queueDepth").build();

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedDisruptorDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                boolean eventHandler = TypeUtils.isAssignableTo(EVENT_HANDLER, classDecl.getType());
                boolean batchStart = eventHandler && classDecl.getImplements().stream()
                        .anyMatch(type -> TypeUtils.isOfClassType(type.getType(), BATCH_START));
                getCursor().putMessage(MIGRATE_BATCH, batchStart);
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                if (!eventHandler) return visited;

                List<TypeTree> kept = visited.getImplements().stream()
                        .filter(type -> !removedExtension(type.getType())).toList();
                if (kept.size() == visited.getImplements().size()) return visited;
                maybeRemoveImport(LIFECYCLE);
                maybeRemoveImport(BATCH_START);
                maybeRemoveImport(TIMEOUT);
                return visited.withImplements(kept);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                if (!Boolean.TRUE.equals(getCursor().getNearestMessage(MIGRATE_BATCH)) ||
                    !"onBatchStart".equals(visited.getSimpleName()) || visited.getParameters().size() != 1 ||
                    !(visited.getParameters().get(0) instanceof J.VariableDeclarations parameter) ||
                    !TypeUtils.isOfClassType(parameter.getType(), "java.lang.Long") &&
                    parameter.getType() != JavaType.Primitive.Long) {
                    return visited;
                }
                J.MethodDeclaration parsed = queueDepthParameter.apply(updateCursor(visited),
                        visited.getCoordinates().replaceParameters());
                List<Statement> parameters = new ArrayList<>(visited.getParameters());
                parameters.add(parsed.getParameters().get(0));
                return autoFormat(visited.withParameters(parameters), ctx, getCursor().getParent());
            }
        };
    }

    private static boolean removedExtension(JavaType type) {
        return TypeUtils.isOfClassType(type, LIFECYCLE) || TypeUtils.isOfClassType(type, BATCH_START) ||
               TypeUtils.isOfClassType(type, TIMEOUT);
    }
}
