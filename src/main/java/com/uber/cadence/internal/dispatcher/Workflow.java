package com.uber.cadence.internal.dispatcher;

import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;

public class Workflow {

    public static WorkflowThread newThread(Runnable runnable)  {
        return WorkflowThreadImpl.newThread(runnable);
    }

    public static WorkflowThread newThread(Runnable runnable, String name)  {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        return WorkflowThreadImpl.newThread(runnable, name);
    }


    /**
     * Note that workflow executes all threads one at a time, ensures that they are interrupted
     * only when blocked on something like Lock or {@link Future#get()} and uses memory barrier to ensure
     * that all variables are accessible from any thread. So Lock is needed only in rare cases when critical
     * section invokes blocking operations.
     * @return Lock implementation that can be used inside a workflow code.
     */
    public static Lock newReentrantLock() {
        return new LockImpl();
    }

    /**
     * Should be used to get current time instead of {@link System#currentTimeMillis()}
     */
    public static long currentTimeMillis() {
        return WorkflowThreadImpl.currentThread().getRunner().currentTimeMillis();
    }
}
