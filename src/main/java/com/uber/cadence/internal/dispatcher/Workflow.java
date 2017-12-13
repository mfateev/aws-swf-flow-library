package com.uber.cadence.internal.dispatcher;

public class Workflow {

    public static WorkflowThread newThread(Runnable runnable)  {
        return WorkflowThreadImpl.newThread(runnable);
    }
}
