/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not
 * use this file except in compliance with the License. A copy of the License is
 * located at
 * 
 * http://aws.amazon.com/apache2.0
 * 
 * or in the "license" file accompanying this file. This file is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.services.simpleworkflow.flow.worker;

import com.amazonaws.services.simpleworkflow.flow.core.ExternalTask;
import com.amazonaws.services.simpleworkflow.flow.core.ExternalTaskCancellationHandler;
import com.amazonaws.services.simpleworkflow.flow.core.ExternalTaskCompletionHandle;
import com.amazonaws.services.simpleworkflow.flow.core.Functor;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.amazonaws.services.simpleworkflow.flow.core.Task;
import com.amazonaws.services.simpleworkflow.flow.generic.ContinueAsNewWorkflowExecutionParameters;
import com.amazonaws.services.simpleworkflow.flow.generic.GenericAsyncWorkflowClient;
import com.amazonaws.services.simpleworkflow.flow.generic.GenericWorkflowClient;
import com.amazonaws.services.simpleworkflow.flow.generic.StartChildWorkflowExecutionParameters;
import com.amazonaws.services.simpleworkflow.flow.generic.StartChildWorkflowReply;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowType;

import java.util.function.Consumer;

class GenericWorkflowClientImpl implements GenericWorkflowClient {

    private static class StartChildWorkflowReplyImpl implements StartChildWorkflowReply {

        private String runId;

        private final Settable<byte[]> result = new Settable<>();

        public StartChildWorkflowReplyImpl(String runId, String description) {
            this.runId = runId;
            result.setDescription(description);
        }

        @Override
        public String getRunId() {
            return runId;
        }

        @Override
        public Promise<byte[]> getResult() {
            return result;
        }

        public void setResult(byte[] value) {
            result.set(value);
        }

    }

    private final GenericAsyncWorkflowClient client;

    GenericWorkflowClientImpl(GenericAsyncWorkflowClient client) {
        this.client = client;
    }

    @Override
    public Promise<StartChildWorkflowReply> startChildWorkflow(final StartChildWorkflowExecutionParameters parameters) {
        final String workflowId = parameters.getWorkflowId();
        WorkflowType workflowType = parameters.getWorkflowType();
        String taskName = "workflowId=" + workflowId + ", workflowType=" + workflowType;
        final Settable<StartChildWorkflowReply> result = new Settable<>();
        result.setDescription("startChildWorkflow " + taskName);

        new ExternalTask() {

            @Override
            protected ExternalTaskCancellationHandler doExecute(final ExternalTaskCompletionHandle handle) throws Throwable {
                Consumer<Throwable> cancellation = client.startChildWorkflow(parameters, runId -> {
                            String description = "startChildWorkflow workflowId=" + workflowId + ", runId=" + runId;
                            result.set(new StartChildWorkflowReplyImpl(runId, description));
                        }, (value, failure) -> {
                            if (failure != null) {
                                handle.fail(failure);
                            } else {
                                ((StartChildWorkflowReplyImpl) result.get()).setResult(value);
                                handle.complete();
                            }
                        }
                );
                return cause -> {
                    cancellation.accept(cause);
                };
            }
        };
        return result;
    }

    @Override
    public Promise<byte[]> startChildWorkflow(String workflow, byte[] input) {
        StartChildWorkflowExecutionParameters parameters = new StartChildWorkflowExecutionParameters();
        WorkflowType workflowType = new WorkflowType();
        workflowType.setName(workflow);
        parameters.setWorkflowType(workflowType);
        parameters.setInput(input);
        final Promise<StartChildWorkflowReply> started = startChildWorkflow(parameters);
        return new Functor<byte[]>(started) {

            @Override
            protected Promise<byte[]> doExecute() throws Throwable {
                return started.get().getResult();
            }
        };
    }

    @Override
    public Promise<byte[]> startChildWorkflow(final String workflow, final Promise<byte[]> input) {
        final Settable<byte[]> result = new Settable<>();

        new Task(input) {

            @Override
            protected void doExecute() throws Throwable {
                result.chain(startChildWorkflow(workflow, input.get()));
            }
        };
        return result;
    }

//    @Override
//    public Promise<Void> signalWorkflowExecution(final SignalExternalWorkflowParameters parameters) {
//        final OpenRequestInfo<Void, Void> context = new OpenRequestInfo<Void, Void>();
//        final SignalExternalWorkflowExecutionDecisionAttributes attributes = new SignalExternalWorkflowExecutionDecisionAttributes();
//        String signalId = decisions.getNextId();
//        attributes.setControl(signalId);
//        attributes.setSignalName(parameters.getSignalName());
//        attributes.setInput(parameters.getInput());
//        attributes.setRunId(parameters.getRunId());
//        attributes.setWorkflowId(parameters.getWorkflowId());
//        String taskName = "signalId=" + signalId + ", workflowId=" + parameters.getWorkflowId() + ", workflowRunId="
//                + parameters.getRunId();
//        new ExternalTask() {
//
//            @Override
//            protected ExternalTaskCancellationHandler doExecute(final ExternalTaskCompletionHandle handle) throws Throwable {
//
//                decisions.signalExternalWorkflowExecution(attributes);
//                context.setCompletionHandle(handle);
//                final String finalSignalId = attributes.getControl();
//                scheduledSignals.put(finalSignalId, context);
//                return new ExternalTaskCancellationHandler() {
//
//                    @Override
//                    public void handleCancellation(Throwable cause) {
//                        decisions.cancelSignalExternalWorkflowExecution(finalSignalId, null);
//                        OpenRequestInfo<Void, Void> scheduled = scheduledSignals.remove(finalSignalId);
//                        if (scheduled == null) {
//                            throw new IllegalArgumentException("Signal \"" + finalSignalId + "\" wasn't scheduled");
//                        }
//                        handle.complete();
//                    }
//                };
//            }
//        }.setName(taskName);
//        context.setResultDescription("signalWorkflowExecution " + taskName);
//        return context.getResult();
//    }

    @Override
    public void requestCancelWorkflowExecution(WorkflowExecution execution) {
        client.requestCancelWorkflowExecution(execution);
    }

    @Override
    public void continueAsNewOnCompletion(ContinueAsNewWorkflowExecutionParameters continueParameters) {
        client.continueAsNewOnCompletion(continueParameters);
    }

    @Override
    public String generateUniqueId() {
        return client.generateUniqueId();
    }
}
