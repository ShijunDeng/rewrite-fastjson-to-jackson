package com.huawei.clouds.openrewrite.disruptor;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class MigrateDisruptor4JavaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateDisruptor4Java())
                .parser(JavaParser.fromJavaVersion().dependsOn(DisruptorTestApi.sources()));
    }

    @Test
    void buildsBatchEventProcessor() {
        rewriteRun(java(
                """
                import com.lmax.disruptor.BatchEventProcessor;
                import com.lmax.disruptor.DataProvider;
                import com.lmax.disruptor.EventHandler;
                import com.lmax.disruptor.SequenceBarrier;

                class Pipeline<E> {
                    BatchEventProcessor<E> create(DataProvider<E> data, SequenceBarrier barrier, EventHandler<E> handler) {
                        return new BatchEventProcessor<>(data, barrier, handler);
                    }
                }
                """,
                """
                import com.lmax.disruptor.*;

                class Pipeline<E> {
                    BatchEventProcessor<E> create(DataProvider<E> data, SequenceBarrier barrier, EventHandler<E> handler) {
                        return new BatchEventProcessorBuilder().build(data, barrier, handler);
                    }
                }
                """));
    }

    @Test
    void foldsLifecycleAndTimeoutIntoEventHandler() {
        rewriteRun(java(
                """
                import com.lmax.disruptor.EventHandler;
                import com.lmax.disruptor.LifecycleAware;
                import com.lmax.disruptor.TimeoutHandler;

                class Handler implements EventHandler<String>, LifecycleAware, TimeoutHandler {
                    public void onEvent(String e, long s, boolean end) { }
                    public void onStart() { }
                    public void onShutdown() { }
                    public void onTimeout(long sequence) { }
                }
                """,
                """
                import com.lmax.disruptor.EventHandler;

                class Handler implements EventHandler<String> {
                    public void onEvent(String e, long s, boolean end) { }
                    public void onStart() { }
                    public void onShutdown() { }
                    public void onTimeout(long sequence) { }
                }
                """));
    }

    @Test
    void migratesBatchStartSignatureAndPreservesBody() {
        rewriteRun(java(
                """
                import com.lmax.disruptor.BatchStartAware;
                import com.lmax.disruptor.EventHandler;

                class Handler implements EventHandler<String>, BatchStartAware {
                    long observed;
                    public void onEvent(String e, long s, boolean end) { }
                    public void onBatchStart(long batchSize) { observed = batchSize; }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "implements EventHandler<String>");
                    assertContains(out, "onBatchStart(long batchSize, long queueDepth)");
                    assertContains(out, "observed = batchSize");
                    assertFalse(out.contains("BatchStartAware"), out);
                })));
    }

    @Test
    void replacesSequenceReportingInterfaceWithEventHandler() {
        rewriteRun(java(
                """
                import com.lmax.disruptor.Sequence;
                import com.lmax.disruptor.SequenceReportingEventHandler;
                class Handler implements SequenceReportingEventHandler<String> {
                    public void onEvent(String e, long s, boolean end) { }
                    public void setSequenceCallback(Sequence callback) { }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "import com.lmax.disruptor.EventHandler;");
                    assertContains(out, "implements EventHandler<String>");
                    assertFalse(out.contains("SequenceReportingEventHandler"), out);
                })));
    }

    @Test
    void migratesReducedAlibabaCanalPattern() {
        // Reduced from alibaba/canal@e20e4424468ef0ed60a97d921763b348eca27163.
        rewriteRun(java(
                """
                import com.lmax.disruptor.*;
                class CanalPipeline<E> {
                    BatchEventProcessor<E> create(RingBuffer<E> ring, SequenceBarrier barrier) {
                        EventHandler<E> handler = new Stage<>();
                        return new BatchEventProcessor<>(ring, barrier, handler);
                    }
                    static class Stage<E> implements EventHandler<E>, LifecycleAware {
                        public void onEvent(E e, long s, boolean end) { }
                        public void onStart() { }
                        public void onShutdown() { }
                    }
                }
                """, source -> source.after(actual -> actual).afterRecipe(after -> {
                    String out = after.printAll();
                    assertContains(out, "new BatchEventProcessorBuilder().build(ring, barrier, handler)");
                    assertFalse(out.contains("LifecycleAware"), out);
                })));
    }

    @Test
    void standaloneLifecycleAbstractionIsNoopForAuto() {
        rewriteRun(java("""
                import com.lmax.disruptor.LifecycleAware;
                interface ComponentLifecycle extends LifecycleAware { }
                """));
    }

    @Test
    void sameNamedApplicationApiIsNoop() {
        rewriteRun(java("class BatchEventProcessor { BatchEventProcessor(Object a,Object b,Object c){} } " +
                        "class Use { Object x(){ return new BatchEventProcessor(1,2,3); } }"));
    }

    @Test
    void generatedAndInstallationParentsAreNoop() {
        rewriteRun(
                java("import com.lmax.disruptor.*; class Generated<E> { Object x(DataProvider<E> d, SequenceBarrier b, EventHandler<E> h)" +
                     "{ return new BatchEventProcessor<>(d,b,h); } }", source -> source.path("generated-code/Generated.java")),
                java("import com.lmax.disruptor.*; class Installed<E> { Object x(DataProvider<E> d, SequenceBarrier b, EventHandler<E> h)" +
                     "{ return new BatchEventProcessor<>(d,b,h); } }", source -> source.path("installation/lib/Installed.java")));
    }

    @Test
    void installLeafIsProcessedAndAutoIsIdempotent() {
        rewriteRun(spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1), java(
                "import com.lmax.disruptor.*; class install<E> { Object x(DataProvider<E> d, SequenceBarrier b, EventHandler<E> h)" +
                "{ return new BatchEventProcessor<>(d,b,h); } }",
                source -> source.path("install.java").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "new BatchEventProcessorBuilder().build(d, b, h)");
                    assertCount(after.printAll(), "new BatchEventProcessorBuilder()", 1);
                })));
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
