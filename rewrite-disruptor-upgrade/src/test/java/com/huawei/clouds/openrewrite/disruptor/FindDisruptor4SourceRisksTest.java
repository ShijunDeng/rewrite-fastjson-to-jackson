package com.huawei.clouds.openrewrite.disruptor;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class FindDisruptor4SourceRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindDisruptor4SourceRisks())
                .parser(JavaParser.fromJavaVersion().dependsOn(DisruptorTestApi.sources()));
    }

    @Test
    void marksRealMeituanStyleWorkerPoolDslAndWorkHandler() {
        // Reduced from Meituan-Dianping/ptubes@3573788403bb7fd32b6ec24ad847a5ad94545ccc.
        rewriteRun(java(
                """
                import com.lmax.disruptor.WorkHandler;
                import com.lmax.disruptor.dsl.Disruptor;
                class BinlogPipeline<E> {
                    void attach(Disruptor<E> disruptor, WorkHandler<E> handler) {
                        disruptor.handleEventsWithWorkerPool(handler);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindDisruptor4SourceRisks.WORKER_POOL))));
    }

    @Test
    void marksRealCanalStyleWorkerTopologyTypes() {
        // Reduced from alibaba/canal@e20e4424468ef0ed60a97d921763b348eca27163.
        rewriteRun(java(
                """
                import com.lmax.disruptor.WorkHandler;
                import com.lmax.disruptor.WorkerPool;
                class MysqlMultiStageCoprocessor<E> {
                    WorkerPool<E> pool;
                    WorkHandler<E> handler;
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindDisruptor4SourceRisks.WORKER_POOL))));
    }

    @Test
    void marksRealMyriadStyleExecutorConstructorButAcceptsThreadFactory() {
        // Reduced from apache/incubator-myriad@9bd85f6d3c80cb7424c5886b872e2fe67d870bfa.
        rewriteRun(java(
                """
                import com.lmax.disruptor.EventFactory;
                import com.lmax.disruptor.dsl.Disruptor;
                import java.util.concurrent.Executor;
                import java.util.concurrent.ThreadFactory;
                class DisruptorManager<E> {
                    Disruptor<E> legacy(EventFactory<E> f, Executor executor) {
                        return new Disruptor<>(f, 1024, executor);
                    }
                    Disruptor<E> current(EventFactory<E> f, ThreadFactory factory) {
                        return new Disruptor<>(f, 1024, factory);
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertCount(after.printAll(), FindDisruptor4SourceRisks.EXECUTOR, 1);
                    assertContains(after.printAll(), "return new Disruptor<>(f, 1024, factory)");
                })));
    }

    @Test
    void marksStandaloneHandlerExtensionsThatAutoRecipeCannotFold() {
        // Reduced from wso2/andes@596e6012abf67459b0095317af840218c9a9df8e.
        rewriteRun(java(
                """
                import com.lmax.disruptor.LifecycleAware;
                import com.lmax.disruptor.SequenceReportingEventHandler;
                abstract class Hooks implements LifecycleAware { }
                interface Processor<E> extends SequenceReportingEventHandler<E> { }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindDisruptor4SourceRisks.EXTENSION, 2))));
    }

    @Test
    void marksMigratedBatchCallbackSemanticBoundary() {
        rewriteRun(java(
                """
                import com.lmax.disruptor.EventHandler;
                class Handler implements EventHandler<String> {
                    public void onEvent(String e, long s, boolean end) { }
                    public void onBatchStart(long batchSize, long queueDepth) { }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), FindDisruptor4SourceRisks.BATCH))));
    }

    @Test
    void marksResetToLoggingAndLog2AtExactApis() {
        rewriteRun(java(
                """
                import com.lmax.disruptor.FatalExceptionHandler;
                import com.lmax.disruptor.IgnoreExceptionHandler;
                import com.lmax.disruptor.RingBuffer;
                import com.lmax.disruptor.util.Util;
                class RuntimeBoundaries {
                    void reset(RingBuffer<String> ring) { ring.resetTo(7); }
                    Object fatal() { return new FatalExceptionHandler(); }
                    Object ignored() { return new IgnoreExceptionHandler(); }
                    int size(int value) { return Util.log2(value); }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    assertCount(after.printAll(), FindDisruptor4SourceRisks.RESET, 1);
                    assertCount(after.printAll(), FindDisruptor4SourceRisks.LOGGING, 2);
                    assertCount(after.printAll(), FindDisruptor4SourceRisks.LOG2, 1);
                })));
    }

    @Test
    void sameNamedApplicationApisAreNoop() {
        rewriteRun(java(
                """
                class RingBuffer { void resetTo(long sequence) { } }
                class Util { static int log2(int value) { return 0; } }
                class Dsl { void handleEventsWithWorkerPool(Object value) { } }
                class Use { void use(RingBuffer r, Dsl d) { r.resetTo(1); Util.log2(1); d.handleEventsWithWorkerPool(this); } }
                """, source -> source.afterRecipe(after -> assertNoMarker(after.printAll()))));
    }

    @Test
    void generatedInstallAndCachesAreNoop() {
        rewriteRun(
                java("import com.lmax.disruptor.RingBuffer; class Generated { void x(RingBuffer<String> r){ r.resetTo(1); } }",
                        source -> source.path("generated-code/Generated.java")),
                java("import com.lmax.disruptor.RingBuffer; class Installed { void x(RingBuffer<String> r){ r.resetTo(1); } }",
                        source -> source.path("installation/lib/Installed.java")),
                java("import com.lmax.disruptor.RingBuffer; class Cached { void x(RingBuffer<String> r){ r.resetTo(1); } }",
                        source -> source.path(".m2/cache/Cached.java")));
    }

    @Test
    void installLeafIsMarkedAndMarkersAreIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import com.lmax.disruptor.RingBuffer; class install { void x(RingBuffer<String> r){ r.resetTo(1); } }",
                source -> source.path("install.java").after(actual -> actual).afterRecipe(after ->
                        assertCount(after.printAll(), FindDisruptor4SourceRisks.RESET, 1))));
    }

    private static void assertNoMarker(String actual) {
        assertFalse(actual.contains("/*~~("), actual);
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected <" + expected + "> in:\n" + actual);
    }

    private static void assertCount(String actual, String expected, int count) {
        int found = 0;
        for (int at = 0; (at = actual.indexOf(expected, at)) >= 0; at += expected.length()) found++;
        int result = found;
        assertTrue(result == count, () -> "Expected " + count + " occurrences of <" + expected +
                "> but found " + result + " in:\n" + actual);
    }
}
