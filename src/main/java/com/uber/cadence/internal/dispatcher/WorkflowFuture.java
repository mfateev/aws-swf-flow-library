package com.uber.cadence.internal.dispatcher;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

public class WorkflowFuture<T> implements Future<T> {

    private final BiConsumer<WorkflowFuture<T>, Boolean> cancellationHandler;
    private T value;
    private Exception failure;
    private boolean completed;
    private boolean cancelled;

    public WorkflowFuture() {
        this.cancellationHandler = null;
    }

    public WorkflowFuture(BiConsumer<WorkflowFuture<T>, Boolean> cancellationHandler) {
        this.cancellationHandler = cancellationHandler;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        }
        if (cancellationHandler != null) {
            // Ideally cancellationHandler completes this future
            cancellationHandler.accept(this, mayInterruptIfRunning);
        }
        if (!isDone()) {
            completeExceptionally(new CancellationException());
        }
        cancelled = true;
        return true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isDone() {
        return completed;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        if (!completed) {
            WorkflowThreadImpl.yield("Feature.get", () -> completed);
        }
        if (failure != null) {
            throw new ExecutionException(failure);
        }
        return value;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (!completed) {
            WorkflowThreadImpl.yield(unit.toMillis(timeout), "Feature.get", () -> completed);
        }
        if (!completed) {
            throw new TimeoutException();
        }
        if (failure != null) {
            throw new ExecutionException(failure);
        }
        return value;
    }

    public boolean complete(T value) {
        if (completed) {
            return false;
        }
        this.completed = true;
        this.value = value;
        return true;
    }

    public boolean completeExceptionally(Exception value) {
        if (completed) {
            return false;
        }
        this.completed = true;
        this.failure = value;
        return true;
    }

}
