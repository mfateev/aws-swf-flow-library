package com.uber.cadence.internal.dispatcher;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

class CoroutineContextImpl implements CoroutineContext {

    private Status status = Status.CREATED;
    private Throwable unhandledException;
    private Lock lock = new ReentrantLock();
    // Used to block yield call
    private Condition yieldCondition = lock.newCondition();
    // Used to block runUntilBlocked call
    private Condition runCondition = lock.newCondition();
    private Condition evaluationCondition = lock.newCondition();
    private Consumer<String> evaluationFunction;
    private boolean interrupted;

    @Override
    public void yield(String reason, Supplier<Boolean> unblockFunction) {
        if (unblockFunction == null) {
            throw new IllegalArgumentException("null unblockFunction");
        }
        while (!unblockFunction.get()) {
            lock.lock();
            try {
                status = Status.YIELDED;
                runCondition.signal();
                yieldCondition.await();
                if (status == Status.EVALUATING) {
                    try {
                        evaluationFunction.accept(reason);
                    } catch (Exception e) {
                        evaluationFunction.accept(e.toString());
                    } finally {
                        status = Status.YIELDED;
                        evaluationCondition.signal();
                    }
                }
            } catch (InterruptedException e) {
                throw new Error("Unexpected interrupt", e);
            } finally {
                lock.unlock();
            }
        }
        status = Status.RUNNING;
    }

    public void evaluateInCoroutineContext(Consumer<String> function) {
        lock.lock();
        try {
            if (function == null) {
                throw new IllegalArgumentException("null function");
            }
            if (status != Status.YIELDED) {
                throw new IllegalStateException("Not in yielded status");
            }
            ;
            if (evaluationFunction != null) {
                throw new IllegalStateException("Already evaluating");
            }
            evaluationFunction = function;
            status = Status.EVALUATING;
            yieldCondition.signal();
            while (status == Status.EVALUATING) {
                evaluationCondition.await();
            }
        } catch (InterruptedException e) {
            throw new Error("Unexpected interrupt", e);
        } finally {
            evaluationFunction = null;
            lock.unlock();
        }
    }

    @Override
    public boolean interrupted() {
        return interrupted;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        // Unblock runUntilBlocked if thread exited instead of yielding.
        if (isDone()) {
            lock.lock();
            try {
                runCondition.signal();
            } finally {
                lock.unlock();
            }
        }
    }

    public boolean isDone() {
        return status == Status.DONE || status == Status.FAILED;
    }

    public Throwable getUnhandledException() {
        return unhandledException;
    }

    public void setUnhandledException(Throwable unhandledException) {
        this.unhandledException = unhandledException;
    }

    public boolean runUntilBlocked() {
        lock.lock();
        try {
            if (status == Status.FAILED || status == Status.DONE) {
                return false;
            }
            status = Status.RUNNING;
            yieldCondition.signal();
            while (status == status.RUNNING) {
                runCondition.await();
            }
        } catch (InterruptedException e) {
            throw new Error("Unexpected interrupt", e);
        } finally {
            lock.unlock();
        }
        return false; // TODO PROGRESS
    }

    public void interrupt() {
        lock.lock();
        try {
            interrupted = true;
        } finally {
            lock.unlock();
        }
        evaluateInCoroutineContext((r) -> {
            throw new InterruptedCoroutineError();
        });
    }
}
