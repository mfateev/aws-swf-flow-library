package com.uber.cadence.internal.dispatcher;

import java.util.function.Supplier;

public class Workflow {

    public static WorkflowThread newThread(Runnable r)  {
        return new WorkflowThreadImpl(r);
    }
}
