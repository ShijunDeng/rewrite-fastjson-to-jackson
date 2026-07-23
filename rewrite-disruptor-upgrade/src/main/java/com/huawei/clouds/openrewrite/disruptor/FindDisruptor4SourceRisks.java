package com.huawei.clouds.openrewrite.disruptor;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.Set;

/** Locate removed APIs and runtime decisions that cannot be safely inferred from syntax alone. */
public final class FindDisruptor4SourceRisks extends Recipe {
    static final String WORKER_POOL =
            "Disruptor 4 removed WorkHandler, WorkerPool, WorkProcessor, and worker-pool DSL methods; choose an " +
            "explicit competing-consumer topology and verify exactly-once ownership, ordering, gating, backpressure, " +
            "exception handling, halt/drain, and shutdown semantics";
    static final String EXECUTOR =
            "Disruptor 4 removed constructors that accept Executor; provide a ThreadFactory and explicitly review " +
            "thread naming, daemon status, affinity/priority, uncaught exceptions, lifecycle, and executor ownership";
    static final String EXTENSION =
            "This removed Disruptor handler extension is not in a deterministic EventHandler implementation shape; " +
            "fold its callback into EventHandler or redesign the standalone abstraction before compiling with 4.0.0";
    static final String BATCH =
            "Disruptor 4 onBatchStart receives (batchSize, queueDepth), and the old single value represented queue " +
            "depth despite its name; verify batching thresholds, flush decisions, latency, and backlog metrics";
    static final String RESET =
            "RingBuffer.resetTo was removed in Disruptor 4; do not emulate cursor rewinding without proving producer, " +
            "consumer gating, unpublished-slot, replay, and concurrent publication safety";
    static final String LOGGING =
            "Disruptor 4 FatalExceptionHandler/IgnoreExceptionHandler use System.Logger; verify JUL/System.Logger " +
            "routing, level filters, observability, and fatal-event alerting in the deployed runtime";
    static final String LOG2 =
            "Disruptor 4 Util.log2 throws for zero or negative values; validate configuration before this call and " +
            "test invalid ring/batch sizes rather than depending on the legacy result";

    private static final String DISRUPTOR = "com.lmax.disruptor.dsl.Disruptor";
    private static final String EVENT_HANDLER = "com.lmax.disruptor.EventHandler";
    private static final String RING_BUFFER = "com.lmax.disruptor.RingBuffer";
    private static final String UTIL = "com.lmax.disruptor.util.Util";
    private static final String THREAD_FACTORY = "java.util.concurrent.ThreadFactory";
    private static final String EXECUTOR_TYPE = "java.util.concurrent.Executor";
    private static final Set<String> WORK_TYPES = Set.of(
            "com.lmax.disruptor.WorkHandler", "com.lmax.disruptor.WorkerPool",
            "com.lmax.disruptor.WorkProcessor", "com.lmax.disruptor.EventReleaseAware",
            "com.lmax.disruptor.EventReleaser", "com.lmax.disruptor.dsl.BasicExecutor");
    private static final Set<String> REMOVED_EXTENSIONS = Set.of(
            "com.lmax.disruptor.LifecycleAware", "com.lmax.disruptor.BatchStartAware",
            "com.lmax.disruptor.TimeoutHandler", "com.lmax.disruptor.SequenceReportingEventHandler");
    private static final Set<String> WORK_METHODS = Set.of(
            "handleEventsWithWorkerPool", "thenHandleEventsWithWorkerPool");
    private static final Set<String> LOG_HANDLERS = Set.of(
            "com.lmax.disruptor.FatalExceptionHandler", "com.lmax.disruptor.IgnoreExceptionHandler");

    @Override
    public String getDisplayName() {
        return "Find Disruptor 4 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark type-attributed removed worker pools, Executor construction, unmerged handler extensions, " +
               "batch semantics, cursor reset, logging, and log2 input boundaries.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedDisruptorDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier visited = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null) return visited;
                JavaType type = visited.getType();
                if (WORK_TYPES.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name))) {
                    return mark(visited, WORKER_POOL);
                }
                if (REMOVED_EXTENSIONS.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name))) {
                    return mark(visited, EXTENSION);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                if (LOG_HANDLERS.stream().anyMatch(name -> TypeUtils.isOfClassType(visited.getType(), name))) {
                    return mark(visited, LOGGING);
                }
                if (!TypeUtils.isOfClassType(visited.getType(), DISRUPTOR)) return visited;
                List<Expression> args = visited.getArguments().stream()
                        .filter(argument -> !(argument instanceof J.Empty)).toList();
                if (args.size() >= 3 && TypeUtils.isAssignableTo(EXECUTOR_TYPE, args.get(2).getType()) &&
                    !TypeUtils.isAssignableTo(THREAD_FACTORY, args.get(2).getType())) {
                    return mark(visited, EXECUTOR);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null || method.getDeclaringType() == null) return visited;
                String owner = method.getDeclaringType().getFullyQualifiedName();
                if (WORK_METHODS.contains(method.getName()) &&
                    (owner.startsWith("com.lmax.disruptor.dsl.") || DISRUPTOR.equals(owner))) {
                    return mark(visited, WORKER_POOL);
                }
                if ("resetTo".equals(method.getName()) && TypeUtils.isAssignableTo(RING_BUFFER, method.getDeclaringType())) {
                    return mark(visited, RESET);
                }
                if ("log2".equals(method.getName()) && TypeUtils.isAssignableTo(UTIL, method.getDeclaringType())) {
                    return mark(visited, LOG2);
                }
                return visited;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                if (!"onBatchStart".equals(visited.getSimpleName()) || visited.getParameters().size() != 2) {
                    return visited;
                }
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                return owner != null && TypeUtils.isAssignableTo(EVENT_HANDLER, owner.getType())
                        ? mark(visited, BATCH) : visited;
            }
        };
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
