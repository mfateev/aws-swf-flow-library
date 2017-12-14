package com.uber.cadence.internal.dispatcher;

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
