package com.amazonaws.services.simpleworkflow.flow.worker;

import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinitionFactoryFactory;
import com.uber.cadence.WorkflowType;

public class PromiseAsyncWorkflowFactory implements AsyncWorkflowFactory {

    private final WorkflowDefinitionFactoryFactory workflowDefinitionFactoryFactory;

    public PromiseAsyncWorkflowFactory(WorkflowDefinitionFactoryFactory workflowDefinitionFactoryFactory) {
        this.workflowDefinitionFactoryFactory = workflowDefinitionFactoryFactory;
    }

    @Override
    public AsyncWorkflow getWorkflow(WorkflowType workflowType) throws Exception {
        return new PromiseAsyncWorkflow(workflowDefinitionFactoryFactory.getWorkflowDefinitionFactory(workflowType));
    }
}
