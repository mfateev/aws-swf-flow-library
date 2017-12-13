package com.uber.cadence.internal.dispatcher;

import java.util.function.Supplier;

public interface WorkflowThread {

    void start();

    void join() throws InterruptedException;

    void join(long millis) throws InterruptedException;

    void interrupt();

    boolean isInterrupted();

    boolean isAlive();

    void setName(String name);

    String getName();

    long getId();

    Thread.State getState();

    static WorkflowThread currentThread() {
        return WorkflowThreadImpl.currentThread();
    }

    static void sleep(long millis) throws InterruptedException {
        throw new UnsupportedOperationException("not implemented yet");
    }

    static boolean interrupted() {
        return WorkflowThreadImpl.currentThread().resetInterrupted();
    }

    /**
     * Block current thread until unblockCondition is evaluated to true.
     * This method is intended for framework level libraries, never use directly in a workflow implementation.
     * @param reason reason for blocking
     * @param unblockCondition condition that should return true to indicate that thread should unblock.
     * @throws InterruptedException if thread was interrupted.
     * @throws DestroyWorkflowThreadError if thread was asked to be destroyed.
     */
    static void yield(String reason, Supplier<Boolean> unblockCondition) throws InterruptedException, DestroyWorkflowThreadError {
        WorkflowThreadImpl.currentThread().getContext().yield(reason, unblockCondition);
    }
}
