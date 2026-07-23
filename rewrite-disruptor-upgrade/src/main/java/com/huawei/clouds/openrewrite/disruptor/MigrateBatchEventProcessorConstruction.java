package com.huawei.clouds.openrewrite.disruptor;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

/** Replace the removed public three-argument processor constructor with the 4.0 builder. */
public final class MigrateBatchEventProcessorConstruction extends Recipe {
    private static final String PROCESSOR = "com.lmax.disruptor.BatchEventProcessor";
    private static final String BUILDER = "com.lmax.disruptor.BatchEventProcessorBuilder";

    @Override
    public String getDisplayName() {
        return "Build Disruptor 4 BatchEventProcessor instances";
    }

    @Override
    public String getDescription() {
        return "Replace type-attributed three-argument BatchEventProcessor construction with " +
               "BatchEventProcessorBuilder.build while preserving provider, barrier, and handler expressions.";
    }

    @Override
    public JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private final JavaTemplate replacement = JavaTemplate.builder(
                    "new BatchEventProcessorBuilder().build(#{any()}, #{any()}, #{any()})")
                    .imports(BUILDER)
                    .contextSensitive()
                    .javaParser(targetParser())
                    .build();

            @Override
            public J visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedDisruptorDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J visitedTree = super.visitNewClass(newClass, ctx);
                if (!(visitedTree instanceof J.NewClass visited)) return visitedTree;
                List<Expression> arguments = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                if (!TypeUtils.isOfClassType(visited.getType(), PROCESSOR) || arguments.size() != 3 ||
                    visited.getBody() != null) {
                    return visited;
                }
                maybeAddImport(BUILDER);
                J.MethodInvocation migrated = replacement.apply(updateCursor(visited), visited.getCoordinates().replace(),
                        arguments.get(0), arguments.get(1), arguments.get(2));
                JavaType.Method methodType = migrated.getMethodType();
                return methodType == null ? migrated :
                        migrated.withMethodType(methodType.withReturnType(visited.getType()));
            }
        };
    }

    private static JavaParser.Builder<?, ?> targetParser() {
        return JavaParser.fromJavaVersion().dependsOn(
                "package com.lmax.disruptor; public interface DataProvider<T> { T get(long sequence); }",
                "package com.lmax.disruptor; public interface SequenceBarrier { }",
                "package com.lmax.disruptor; public interface EventHandler<T> { " +
                "void onEvent(T event, long sequence, boolean endOfBatch) throws Exception; }",
                "package com.lmax.disruptor; public final class BatchEventProcessor<T> { }",
                """
                package com.lmax.disruptor;
                public final class BatchEventProcessorBuilder {
                    public <T> BatchEventProcessor<T> build(DataProvider<T> provider, SequenceBarrier barrier,
                                                             EventHandler<? super T> handler) { return null; }
                }
                """
        );
    }
}
