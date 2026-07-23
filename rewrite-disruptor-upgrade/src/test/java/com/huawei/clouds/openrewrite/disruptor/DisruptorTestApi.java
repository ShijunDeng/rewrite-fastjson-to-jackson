package com.huawei.clouds.openrewrite.disruptor;

final class DisruptorTestApi {
    private DisruptorTestApi() {
    }

    static String[] sources() {
        return new String[]{
                "package com.lmax.disruptor; public interface EventFactory<T> { T newInstance(); }",
                "package com.lmax.disruptor; public interface DataProvider<T> { T get(long sequence); }",
                "package com.lmax.disruptor; public interface SequenceBarrier { }",
                "package com.lmax.disruptor; public class Sequence { public void set(long value) { } }",
                """
                package com.lmax.disruptor;
                public interface EventHandler<T> {
                    void onEvent(T event, long sequence, boolean endOfBatch) throws Exception;
                    default void onBatchStart(long batchSize, long queueDepth) { }
                    default void onStart() { }
                    default void onShutdown() { }
                    default void onTimeout(long sequence) throws Exception { }
                    default void setSequenceCallback(Sequence sequence) { }
                }
                """,
                "package com.lmax.disruptor; public interface LifecycleAware { void onStart(); void onShutdown(); }",
                "package com.lmax.disruptor; public interface BatchStartAware { void onBatchStart(long batchSize); }",
                "package com.lmax.disruptor; public interface TimeoutHandler { void onTimeout(long sequence) throws Exception; }",
                """
                package com.lmax.disruptor;
                public interface SequenceReportingEventHandler<T> extends EventHandler<T> {
                    void setSequenceCallback(Sequence sequence);
                }
                """,
                "package com.lmax.disruptor; public interface WorkHandler<T> { void onEvent(T event) throws Exception; }",
                "package com.lmax.disruptor; public interface EventReleaseAware { void setEventReleaser(EventReleaser r); }",
                "package com.lmax.disruptor; public interface EventReleaser { void release(); }",
                """
                package com.lmax.disruptor;
                public class RingBuffer<T> implements DataProvider<T> {
                    public T get(long sequence) { return null; }
                    public void resetTo(long sequence) { }
                }
                """,
                """
                package com.lmax.disruptor;
                public final class BatchEventProcessor<T> {
                    public BatchEventProcessor(DataProvider<T> provider, SequenceBarrier barrier,
                                               EventHandler<? super T> handler) { }
                    public Sequence getSequence() { return null; }
                }
                """,
                """
                package com.lmax.disruptor;
                public final class BatchEventProcessorBuilder {
                    public <T> BatchEventProcessor<T> build(DataProvider<T> provider, SequenceBarrier barrier,
                                                             EventHandler<? super T> handler) { return null; }
                }
                """,
                "package com.lmax.disruptor; public final class WorkerPool<T> { public Sequence[] getWorkerSequences(){return null;} }",
                "package com.lmax.disruptor; public final class WorkProcessor<T> { }",
                "package com.lmax.disruptor; public final class FatalExceptionHandler { public FatalExceptionHandler() { } }",
                "package com.lmax.disruptor; public final class IgnoreExceptionHandler { public IgnoreExceptionHandler() { } }",
                """
                package com.lmax.disruptor.util;
                public final class Util { public static int log2(int value) { return 0; } }
                """,
                "package com.lmax.disruptor.dsl; public enum ProducerType { SINGLE, MULTI }",
                """
                package com.lmax.disruptor.dsl;
                public class EventHandlerGroup<T> {
                    public final EventHandlerGroup<T> thenHandleEventsWithWorkerPool(
                            com.lmax.disruptor.WorkHandler<T>... handlers) { return this; }
                }
                """,
                """
                package com.lmax.disruptor.dsl;
                public class Disruptor<T> {
                    public Disruptor(com.lmax.disruptor.EventFactory<T> factory, int size,
                                     java.util.concurrent.Executor executor) { }
                    public Disruptor(com.lmax.disruptor.EventFactory<T> factory, int size,
                                     java.util.concurrent.ThreadFactory factoryThreads) { }
                    public final EventHandlerGroup<T> handleEventsWithWorkerPool(
                            com.lmax.disruptor.WorkHandler<T>... handlers) { return null; }
                }
                """,
                """
                package com.lmax.disruptor.dsl;
                public final class BasicExecutor implements java.util.concurrent.Executor {
                    public BasicExecutor(java.util.concurrent.ThreadFactory factory) { }
                    public void execute(Runnable task) { }
                }
                """
        };
    }
}
