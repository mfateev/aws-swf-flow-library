package com.uber.cadence.internal.dispatcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;
import java.util.function.Supplier;

class Coroutine {

    class CoroutineRunnable implements Runnable {

        private final CoroutineContextImpl context;
        private final Runnable r;

        CoroutineRunnable(CoroutineContextImpl context, Runnable r) {
            this.context = context;
            this.r = r;
            if (context.getStatus() != Status.CREATED) {
                throw new IllegalStateException("context not in CREATED state");
            }
        }

        @Override
        public void run() {
            contextThreadLocal.set(context);
            try {
                // initialYield blocks thread until the first runUntilBlocked is called.
                // Otherwise r starts executing without control of the dispatcher.
                context.initialYield();
                r.run();
                context.setStatus(Status.DONE);
            } catch (DestroyCoroutineError e) {
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

    static final ThreadLocal<CoroutineContext> contextThreadLocal = new ThreadLocal<>();

    private final Thread thread;
    private final CoroutineContextImpl context;

    public static Coroutine newCoroutine(Runnable r) {
        return getContext().getDispatcher().newCoroutine(r);
    }

    static CoroutineContext getContext() {
        CoroutineContext result = contextThreadLocal.get();
        if (result == null) {
            throw new IllegalStateException("Called from non coroutine thread");
        }
        if (((CoroutineContextImpl) result).getStatus() != Status.RUNNING) {
            throw new IllegalStateException("Called from non running coroutine thread");
        }
        return result;
    }


    public void interrupt() {
       thread.interrupt();
    }

    public boolean isInterrupted() {
        return thread.isInterrupted();
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

    static void yield(String reason, Supplier<Boolean> unblockCondition) throws DestroyCoroutineError {
        Coroutine.getContext().yield(reason, unblockCondition);
    }

    public Coroutine(DispatcherImpl dispatcher, Runnable r) {
        context = new CoroutineContextImpl(dispatcher);
        CoroutineRunnable cr = new CoroutineRunnable(context, r);
        thread = new Thread(cr, "corountine");
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
     * Interrupt coroutine by throwing DestroyCoroutineError from a yield method
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
