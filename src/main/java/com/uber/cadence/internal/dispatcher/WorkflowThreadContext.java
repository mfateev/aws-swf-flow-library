package com.uber.cadence.internal.dispatcher;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

class WorkflowThreadContext {

    private Status status = Status.CREATED;
    private Throwable unhandledException;
    private Lock lock = new ReentrantLock();
    // Used to block yield call
    private Condition yieldCondition = lock.newCondition();
    // Used to block runUntilBlocked call
    private Condition runCondition = lock.newCondition();
    private Condition evaluationCondition = lock.newCondition();
    private Consumer<String> evaluationFunction;
    private boolean destroyRequested;
    private boolean inRunUntilBlocked;

    public void initialYield() throws InterruptedException {
        if (getStatus() != Status.CREATED) {
            throw new IllegalStateException("not in CREATED but in " + getStatus() + " state");
        }
        yield("created", () -> true);
    }

    public void yield(String reason, Supplier<Boolean> unblockFunction) throws InterruptedException {
        if (unblockFunction == null) {
            throw new IllegalArgumentException("null unblockFunction");
        }
        // Evaluates unblockFunction out of the lock to avoid deadlocks.
        lock.lock();
        try {
            // TODO: Verify that calling unblockFunction under the lock is a sane thing to do.
            while (!inRunUntilBlocked || !unblockFunction.get()) {
                if (status == Status.INTERRUPTED) {
                    throw new InterruptedException();
                }
                status = Status.YIELDED;
                runCondition.signal();
                yieldCondition.await();
                if (status == Status.INTERRUPTED) {
                    throw new InterruptedException();
                }
                mayBeEvaluate(reason);
            }
        } finally {
            setStatus(Status.RUNNING);
            lock.unlock();
        }
    }

    /**
     * Execute evaluation function by the thread that owns this context if
     * {@link #evaluateInCoroutineContext(Consumer)} was called.
     *
     * @param reason human readable reason for current thread blockage passed to yield call.
     */
    private void mayBeEvaluate(String reason) {
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
    }

    /**
     * Call function by the thread that owns this context and is currently blocked in a yield.
     * Used to get information about current state of the thread like current stack trace.
     *
     * @param function to evaluate. Consumes reason for yielding parameter.
     */
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
            if (inRunUntilBlocked) {
                throw new IllegalStateException("Running runUntilBlocked");
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

    public boolean destroyRequested() {
        lock.lock();
        try {
            return destroyRequested;
        } finally {
            lock.unlock();
        }
    }

    public Status getStatus() {
        lock.lock();
        try {
            return status;
        } finally {
            lock.unlock();
        }
    }

    public void setStatus(Status status) {
        // Unblock runUntilBlocked if thread exited instead of yielding.
        lock.lock();
        try {
            this.status = status;
            if (isDone()) {
                runCondition.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isDone() {
        lock.lock();
        try {
            return status == Status.DONE || status == Status.FAILED;
        } finally {
            lock.unlock();
        }
    }

    public Throwable getUnhandledException() {
        lock.lock();
        try {
            return unhandledException;
        } finally {
            lock.unlock();
        }
    }

    public void setUnhandledException(Throwable unhandledException) {
        lock.lock();
        try {
            this.unhandledException = unhandledException;
        } finally {
            lock.unlock();
        }
    }

    public boolean runUntilBlocked() {
        lock.lock();
        try {
            if (status == Status.FAILED || status == Status.DONE) {
                return false;
            }
            if (evaluationFunction != null) {
                throw new IllegalStateException("Cannot runUntilBlocked while evaluating");
            }
            inRunUntilBlocked = true;
            if (status != status.CREATED) {
                status = Status.RUNNING;
            }
            yieldCondition.signal();
            while (status == status.RUNNING || status == Status.CREATED) {
                runCondition.await();
                if (evaluationFunction != null) {
                    throw new IllegalStateException("Cannot runUntilBlocked while evaluating");
                }
            }
        } catch (InterruptedException e) {
            throw new Error("Unexpected interrupt", e);
        } finally {
            inRunUntilBlocked = false;
            lock.unlock();
        }
        return false; // TODO PROGRESS
    }

    public void destroy() {
        lock.lock();
        try {
            destroyRequested = true;
        } finally {
            lock.unlock();
        }
        evaluateInCoroutineContext((r) -> {
            throw new DestroyWorkflowThreadError();
        });
    }

    public void interrupt() {
        lock.lock();
        try {
            status = Status.INTERRUPTED;
        } finally {
            lock.unlock();
        }
    }
}
