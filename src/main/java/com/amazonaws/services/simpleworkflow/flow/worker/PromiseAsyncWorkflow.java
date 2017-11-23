package com.amazonaws.services.simpleworkflow.flow.worker;

import com.amazonaws.services.simpleworkflow.flow.AsyncDecisionContext;
import com.amazonaws.services.simpleworkflow.flow.WorkflowException;
import com.amazonaws.services.simpleworkflow.flow.core.AsyncScope;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinition;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinitionFactory;
import com.uber.cadence.EventType;
import com.uber.cadence.HistoryEvent;
import com.uber.cadence.WorkflowExecutionStartedEventAttributes;
import com.uber.cadence.WorkflowType;

import java.util.concurrent.CancellationException;

class PromiseAsyncWorkflow implements AsyncWorkflow {

    private static final class WorkflowAsyncScope extends AsyncScope {

        private final WorkflowExecutionStartedEventAttributes attributes;
        private WorkflowDefinition definition;

        private Promise<byte[]> output;

        public WorkflowAsyncScope(HistoryEvent event, WorkflowDefinition definition, WorkflowType workflowType) {
            super(false, true);
            if (definition == null) {
                throw new IllegalStateException("Unknown workflow type: " + workflowType);
            }

            this.definition = definition;
            assert event.getEventType().equals(EventType.WorkflowExecutionStarted.toString());
            this.attributes = event.getWorkflowExecutionStartedEventAttributes();
        }

        @Override
        protected void doAsync() throws Throwable {
            output = definition.execute(attributes.getInput());
        }

        public Promise<byte[]> getOutput() {
            return output;
        }

        public byte[] getWorkflowState() throws WorkflowException {
            return definition.getWorkflowState();
        }

        public void close() {
        }

        public WorkflowDefinition getWorkflowDefinition() {
            return definition;
        }
    }


    private WorkflowAsyncScope scope;

    private final WorkflowDefinitionFactory workflowDefinitionFactory;

    public PromiseAsyncWorkflow(WorkflowDefinitionFactory workflowDefinitionFactory) throws Exception {
        this.workflowDefinitionFactory = workflowDefinitionFactory;
    }

    @Override
    public void start(HistoryEvent event, AsyncDecisionContext context) throws Exception {
        WorkflowType workflowType = event.getWorkflowExecutionStartedEventAttributes().getWorkflowType();
        this.scope = new WorkflowAsyncScope(event,
                workflowDefinitionFactory.getWorkflowDefinition(new DecisionContextImpl(context)),
                workflowType);
    }

    @Override
    public boolean eventLoop() throws Throwable {
        return scope.eventLoop();
    }

    @Override
    public byte[] getOutput() {
        Promise<byte[]> output = scope.getOutput();
        if (output.isReady()) {
            return output.get();
        } else {
            return null;
        }
    }

    @Override
    public void cancel(CancellationException e) {
        scope.cancel(e);
    }

    @Override
    public Throwable getFailure() {
        return scope.getFailure();
    }

    @Override
    public boolean isCancelRequested() {
        return scope.isCancelRequested();
    }

    @Override
    public String getAsynchronousThreadDump() {
        return scope.getAsynchronousThreadDumpAsString();
    }

    @Override
    public byte[] getWorkflowState() throws WorkflowException {
        return scope.getWorkflowState();
    }

    @Override
    public void close() {
        workflowDefinitionFactory.deleteWorkflowDefinition(scope.getWorkflowDefinition());
    }
}
