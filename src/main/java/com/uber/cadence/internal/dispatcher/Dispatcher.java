package com.uber.cadence.internal.dispatcher;

public interface Dispatcher {

    // ExecuteUntilAllBlocked executes threads one by one in deterministic order
    // until all of them are completed or blocked on Channel or Selector
    // Throws exception if one of the coroutines didn't handle an exception.
    void runUntilAllBlocked() throws Throwable;

    // IsDone returns true when all of threads are completed
    boolean isDone();

    // Destroys all threads without waiting for their completion
    void close();

    // Stack trace of all threads owned by the Dispatcher instance
    String stackTrace();
}
