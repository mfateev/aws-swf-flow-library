package com.uber.cadence.internal.dispatcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

class WorkflowThreadImpl implements WorkflowThread {

    /**
     * Runnable passed to the thread that wraps a runnable passed to the WorkflowThreadImpl constructor.
     */
    class RunnableWrapper implements Runnable {

        private final WorkflowThreadContext context;
        private final Runnable r;

        RunnableWrapper(WorkflowThreadContext context, Runnable r) {
            this.context = context;
            this.r = r;
            if (context.getStatus() != Status.CREATED) {
                throw new IllegalStateException("context not in CREATED state");
            }
        }

        @Override
        public void run() {
            currentThreadThreadLocal.set(WorkflowThreadImpl.this);
            try {
                // initialYield blocks thread until the first runUntilBlocked is called.
                // Otherwise r starts executing without control of the dispatcher.
                context.initialYield();
                r.run();
                context.setStatus(Status.DONE);
            } catch (DestroyWorkflowThreadError e) {
                if (!context.destroyRequested()) {
                    context.setStatus(Status.FAILED);
                    context.setUnhandledException(e);
                } else {
                    context.setStatus(Status.DONE);
                }
            } catch (Throwable e) {
                context.setStatus(Status.FAILED);
                context.setUnhandledException(e);
            }
        }
    }

    static final ThreadLocal<WorkflowThreadImpl> currentThreadThreadLocal = new ThreadLocal<>();

    private final Thread thread;
    private final WorkflowThreadContext context;
    private final DeterministicRunner runner;

    static WorkflowThreadImpl currentThread() {
        WorkflowThreadImpl result = currentThreadThreadLocal.get();
        if (result == null) {
            throw new IllegalStateException("Called from non coroutine thread");
        }
        if (result.getContext().getStatus() != Status.RUNNING) {
            throw new IllegalStateException("Called from non running coroutine thread");
        }
        return result;
    }

    public static WorkflowThread newThread(Runnable runnable) {
        return new WorkflowThreadImpl(currentThread().getRunner(), runnable);
    }

    public void interrupt() {
       context.interrupt();
    }

    public boolean isInterrupted() {
        return context.getStatus() == Status.INTERRUPTED;
    }

    public boolean isDestroyRequested() {
        return context.destroyRequested();
    }

    public void start() {
        if (context.getStatus() != Status.CREATED) {
            throw new IllegalThreadStateException("already started");
        }
        thread.start();
    }

    public WorkflowThreadContext getContext() {
        return context;
    }

    public DeterministicRunner getRunner() {
        return runner;
    }

    @Override
    public void join() throws InterruptedException {
        WorkflowThread.yield("WorkflowThread.join", () -> isDone());
    }

    // TODO: Timeout support
    @Override
    public void join(long millis) throws InterruptedException {
        WorkflowThread.yield("WorkflowThread.join", () -> isDone());
    }

    public boolean isAlive() {
        return thread.isAlive()  && !isDestroyRequested();
    }

    public void setName(String name) {
        thread.setName(name);
    }

    public String getName() {
        return thread.getName();
    }

    public long getId() {
        return thread.getId();
    }

    public WorkflowThreadImpl(DeterministicRunner runner, Runnable runnable) {
        this.runner = runner;
        this.context = new WorkflowThreadContext();
        RunnableWrapper cr = new RunnableWrapper(context, runnable);
        // TODO: Different name for each workflow thread.
        // TODO: Use thread pool instead of creating new threads.
        this.thread = new Thread(cr, "workflow-thread");
    }

    public Thread getThread() {
        return thread;
    }

    /**
     * @return true if coroutine made some progress.
     */
    public boolean runUntilBlocked() {
        if (thread.getState() == Thread.State.NEW) {
            // Thread is not yet started
            return false;
        }
        return context.runUntilBlocked();
    }

    public boolean isDone() {
        return context.isDone();
    }

    public Thread.State getState() {
        if (context.getStatus() == Status.YIELDED) {
            return Thread.State.BLOCKED;
        } else {
            return Thread.State.RUNNABLE;
        }
    }

    public Throwable getUnhandledException() {
        return context.getUnhandledException();
    }

    /**
     * Evaluates function in the context of the coroutine without unblocking it.
     * Used to get current coroutine status, like stack trace.
     *
     * @param function Parameter is reason for current goroutine blockage.
     */
    public void evaluateInCoroutineContext(Consumer<String> function) {
        context.evaluateInCoroutineContext(function);
    }

    /**
     * Interrupt coroutine by throwing DestroyWorkflowThreadError from a yield method
     * it is blocked on and wait for coroutine thread to finish execution.
     */
    public void stop() {
        context.destroy();
        if (!context.isDone()) {
            throw new RuntimeException("Couldn't destroy the thread. " +
                    "The blocked thread stack trace\n: " + getStackTrace());
        }
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new Error("Unexpected interrupt", e);
        }
    }

    /**
     * @return stack trace of the coroutine thread
     */
    public String getStackTrace() {
        StackTraceElement[] st = thread.getStackTrace();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        for (StackTraceElement se : st) {
            pw.println("\tat " + se);
        }
        return pw.toString();
    }

}
