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
    private boolean interrupted;
    private boolean inRunUntilBlocked;
    private boolean remainedBlocked;

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
            while (!inRunUntilBlocked || throwInterrupted() || !unblockFunction.get()) {
                status = Status.YIELDED;
                runCondition.signal();
                yieldCondition.await();
                mayBeEvaluate(reason);
            }
        } finally {
            setStatus(Status.RUNNING);
            remainedBlocked = false;
            lock.unlock();
        }
    }

    /**
     * Throws InterruptedException if interrupted is true resetting it to false.
     * Should be called under the lock.
     *
     * @return true just to be able to use in the while expression.
     * @throws InterruptedException if interrupted is true
     */
    private boolean throwInterrupted() throws InterruptedException {
        if (interrupted) {
            interrupted = false;
            throw new InterruptedException();
        }
        return false;
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
            return status == Status.DONE;
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

    /**
     * @return true if thread made some progress. Which is yield was unblocked and some code after it was executed.
     */
    public boolean runUntilBlocked() {
        lock.lock();
        try {
            if (status == Status.DONE) {
                return false;
            }
            if (evaluationFunction != null) {
                throw new IllegalStateException("Cannot runUntilBlocked while evaluating");
            }
            inRunUntilBlocked = true;
            if (status != status.CREATED) {
                status = Status.RUNNING;
            }
            remainedBlocked = true;
            yieldCondition.signal();
            while (status == status.RUNNING || status == Status.CREATED) {
                runCondition.await();
                if (evaluationFunction != null) {
                    throw new IllegalStateException("Cannot runUntilBlocked while evaluating");
                }
            }
            return !remainedBlocked;
        } catch (InterruptedException e) {
            throw new Error("Unexpected interrupt", e);
        } finally {
            inRunUntilBlocked = false;
            lock.unlock();
        }
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
        runUntilBlocked();
    }

    public void interrupt() {
        lock.lock();
        try {
            interrupted = true;
        } finally {
            lock.unlock();
        }
    }

    public boolean isInterrupted() {
        lock.lock();
        try {
            return interrupted;
        } finally {
            lock.unlock();
        }
    }

    public boolean resetInterrupted() {
        lock.lock();
        try {
            boolean result = interrupted;
            interrupted = false;
            return result;
        } finally {
            lock.unlock();
        }
    }
}
