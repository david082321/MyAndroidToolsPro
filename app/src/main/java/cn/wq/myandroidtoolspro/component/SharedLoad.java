package cn.wq.myandroidtoolspro.component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

final class SharedLoad<T> {
    interface Observer<T> { void onResult(T value, Exception error); }
    private final Executor worker;
    private final Executor delivery;
    private final Callable<T> operation;
    private final List<Observer<T>> observers = new ArrayList<>();
    private FutureTask<Void> task;
    private T value;
    private Exception error;
    private boolean complete;
    private boolean closed;
    private int generation;

    SharedLoad(Executor worker, Executor delivery, Callable<T> operation) {
        this.worker = worker;
        this.delivery = delivery;
        this.operation = operation;
    }

    synchronized Runnable observe(Observer<T> observer) {
        if (closed) return () -> { };
        observers.add(observer);
        if (complete) deliver(observer, generation, value, error);
        else if (task == null) start();
        return () -> { synchronized (SharedLoad.this) { observers.remove(observer); } };
    }

    synchronized void refresh() {
        if (closed) return;
        generation++;
        if (task != null) task.cancel(true);
        task = null;
        complete = false;
        value = null;
        error = null;
        if (!observers.isEmpty()) start();
    }

    synchronized void close() {
        closed = true;
        generation++;
        observers.clear();
        value = null;
        if (task != null) task.cancel(true);
    }

    private void start() {
        final int version = generation;
        task = new FutureTask<>(() -> {
            T result = null;
            Exception failure = null;
            try {
                result = operation.call();
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception exception) {
                failure = exception;
            }
            synchronized (SharedLoad.this) {
                if (closed || generation != version) return null;
                value = result;
                error = failure;
                complete = true;
                for (Observer<T> observer : new ArrayList<>(observers)) {
                    deliver(observer, version, result, failure);
                }
            }
            return null;
        });
        worker.execute(task);
    }

    private void deliver(Observer<T> observer, int version, T result, Exception failure) {
        delivery.execute(() -> {
            synchronized (SharedLoad.this) {
                if (closed || generation != version || !observers.contains(observer)) return;
            }
            observer.onResult(result, failure);
        });
    }
}
