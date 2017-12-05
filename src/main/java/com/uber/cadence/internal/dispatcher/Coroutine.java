package com.uber.cadence.internal.dispatcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

class Coroutine {

    static final ThreadLocal<CoroutineContext> contextThreadLocal = new ThreadLocal<>();
    private Thread thread;

    class CoroutineRunnable implements Runnable {

        private final CoroutineContextImpl context;
        private final Runnable r;

        CoroutineRunnable(CoroutineContextImpl context, Runnable r) {
            this.context = context;
            this.r = r;
        }

        @Override
        public void run() {
            contextThreadLocal.set(context);
            try {
                context.yield("created", () -> context.getStatus() != Status.CREATED);
                context.setStatus(Status.RUNNING);
                r.run();
                context.setStatus(Status.DONE);
            } catch (InterruptedCoroutineError e) {
                if (!context.interrupted()) {
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

    private final CoroutineContextImpl context;

    public Coroutine(Runnable r) {
        context = new CoroutineContextImpl();
        CoroutineRunnable cr = new CoroutineRunnable(context, r);
        thread = new Thread(cr, "corountine");
        thread.start();
    }

    /**
     * @return true if coroutine made some progress.
     */
    public boolean runUntilBlocked() {
        return context.runUntilBlocked();
    }

    public boolean isDone() {
        return context.isDone();
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
     * Interrupt coroutine by throwing InterruptedCoroutineError from a yield method
     * it is blocked on and wait for coroutine thread to finish execution.
     */
    public void stop() {
        context.interrupt();
        if (!context.isDone()) {
            throw new RuntimeException("Couldn't stop the thread. " +
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
