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
package com.amazonaws.services.simpleworkflow.flow.generic;

import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.uber.cadence.WorkflowExecution;

public interface GenericWorkflowClient {

    /**
     * Start child workflow.
     * 
     * @return becomes ready when child successfully started.
     *         {@link StartChildWorkflowReply#getResult()} becomes ready upon
     *         child completion.
     */
    Promise<StartChildWorkflowReply> startChildWorkflow(StartChildWorkflowExecutionParameters parameters);

    Promise<String> startChildWorkflow(String workflow, byte[] input);

    Promise<String> startChildWorkflow(String workflow, Promise<byte[]> input);

    Promise<Void> signalWorkflowExecution(SignalExternalWorkflowParameters signalParameters);

    void requestCancelWorkflowExecution(WorkflowExecution execution);

    void continueAsNewOnCompletion(ContinueAsNewWorkflowExecutionParameters parameters);

    /**
     * Deterministic unique Id generator
     */
    String generateUniqueId();

}
