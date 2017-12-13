package com.uber.cadence.internal.dispatcher;

/**
 * Executes code passed to {@link #newRunner(Runnable)} as well as threads created from it using
 * {@link Workflow#newThread(Runnable)} deterministically. Requires use of provided wrappers for synchronization
 * and notification instead of native ones.
 */
public interface DeterministicRunner {

    static DeterministicRunner newRunner(Runnable root) {
        return new DeterministicRunnerImpl(root);
    }

    // ExecuteUntilAllBlocked executes threads one by one in deterministic order
    // until all of them are completed or blocked.
    // Throws exception if one of the threads didn't handle an exception.
    void runUntilAllBlocked() throws Throwable;

    // IsDone returns true when all of threads are completed
    boolean isDone();

    /**
     * * Destroys all threads by throwing {@link DestroyWorkflowThreadError} without waiting for their completion
      */
    void close();

    // Stack trace of all threads owned by the DeterministicRunner instance
    String stackTrace();
}
