package cn.wq.myandroidtoolspro.component;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SharedLoadTest {
    @Test public void observersShareOneJobAndCompletedResult() {
        QueueExecutor work = new QueueExecutor();
        AtomicInteger calls = new AtomicInteger();
        SharedLoad<Integer> load = new SharedLoad<>(work, Runnable::run, calls::incrementAndGet);
        List<Integer> results = new ArrayList<>();
        load.observe((value, error) -> results.add(value));
        load.observe((value, error) -> results.add(value));
        work.runNext();
        load.observe((value, error) -> results.add(value));
        assertEquals(1, calls.get());
        assertEquals(java.util.Arrays.asList(1, 1, 1), results);
    }

    @Test public void refreshDiscardsAlreadyQueuedOldCallbacks() {
        QueueExecutor work = new QueueExecutor();
        QueueExecutor ui = new QueueExecutor();
        AtomicInteger calls = new AtomicInteger();
        SharedLoad<Integer> load = new SharedLoad<>(work, ui, calls::incrementAndGet);
        List<Integer> results = new ArrayList<>();
        load.observe((value, error) -> results.add(value));
        work.runNext();
        load.refresh();
        ui.runNext();
        assertTrue(results.isEmpty());
        work.runNext();
        ui.runNext();
        assertEquals(java.util.Collections.singletonList(2), results);
    }

    @Test public void unsubscribeAndClosePreventDelivery() {
        QueueExecutor work = new QueueExecutor();
        QueueExecutor ui = new QueueExecutor();
        SharedLoad<Integer> load = new SharedLoad<>(work, ui, () -> 1);
        List<Integer> results = new ArrayList<>();
        Runnable unsubscribe = load.observe((value, error) -> results.add(value));
        work.runNext();
        unsubscribe.run();
        ui.runNext();
        load.observe((value, error) -> results.add(value));
        load.close();
        ui.runNext();
        assertTrue(results.isEmpty());
    }

    @Test public void failedLoadCanBeRetriedWithoutCachingAnEmptySuccess() {
        QueueExecutor work = new QueueExecutor();
        AtomicInteger attempts = new AtomicInteger();
        SharedLoad<Integer> load = new SharedLoad<>(work, Runnable::run, () -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Unavailable");
            return 42;
        });
        List<Exception> errors = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        load.observe((value, error) -> { values.add(value); errors.add(error); });
        work.runNext();
        assertNull(values.get(0));
        assertTrue(errors.get(0) instanceof IllegalStateException);
        load.refresh();
        work.runNext();
        assertEquals(Integer.valueOf(42), values.get(1));
        assertNull(errors.get(1));
    }

    private static class QueueExecutor implements Executor {
        final Queue<Runnable> queue = new ArrayDeque<>();
        @Override public void execute(Runnable task) { queue.add(task); }
        void runNext() { queue.remove().run(); }
    }
}
