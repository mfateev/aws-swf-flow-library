package com.uber.cadence.internal.dispatcher;

class WorkflowThreadImpl implements WorkflowThread {

    private final Coroutine coroutine;
    private final Thread thread;

    public WorkflowThreadImpl(Runnable r) {
        coroutine = Coroutine.newCoroutine(r);
        thread = coroutine.getThread();
    }

    @Override
    public void start() {
        thread.start();
    }

    @Override
    public void join() {
        Coroutine.yield("WorkflowThread.join", () -> coroutine.isDone());
    }

    // TODO: Timeout support
    @Override
    public void join(long millis) {
        Coroutine.yield("WorkflowThread.join", () -> coroutine.isDone());
    }

    @Override
    public void sleep(long millis) throws InterruptedException {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void interrupt() {
        coroutine.interrupt();
    }

    @Override
    public boolean isInterrupted() {
        return coroutine.isInterrupted();
    }

    @Override
    public boolean isAlive() {
        return thread.isAlive() && !coroutine.isDestroyRequested();
    }

    @Override
    public void setName(String name) {
        thread.setName(name);
    }

    @Override
    public String getName() {
        return thread.getName();
    }

    @Override
    public long getId() {
        return thread.getId();
    }

    @Override
    public Thread.State getState() {
        return coroutine.getState();
    }
}
