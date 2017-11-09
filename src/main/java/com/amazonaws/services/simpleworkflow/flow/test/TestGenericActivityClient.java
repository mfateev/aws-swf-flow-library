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
package com.amazonaws.services.simpleworkflow.flow.test;

import com.amazonaws.services.simpleworkflow.flow.ActivityTask;
import com.uber.cadence.ActivityType;
import com.uber.cadence.PollForActivityTaskResponse;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowService.Iface;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.simpleworkflow.flow.ActivityExecutionContext;
import com.amazonaws.services.simpleworkflow.flow.ActivityFailureException;
import com.amazonaws.services.simpleworkflow.flow.ActivityTaskFailedException;
import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.core.*;
import com.amazonaws.services.simpleworkflow.flow.generic.*;

public class TestGenericActivityClient implements GenericActivityClient {

    private final class TestActivityExecutionContext extends ActivityExecutionContext {

        private final ActivityTask activityTask;

        private final WorkflowExecution workflowExecution;

        private TestActivityExecutionContext(ActivityTask activityTask, WorkflowExecution workflowExecution) {
            this.activityTask = activityTask;
            this.workflowExecution = workflowExecution;
        }

        @Override
        public void recordActivityHeartbeat(byte[] details) {
            //TODO: timeouts
        }

        @Override
        public ActivityTask getTask() {
            return activityTask;
        }

        @Override
        public Iface getService() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public byte[] getTaskToken() {
            return activityTask.getTaskToken();
        }

        @Override
        public com.uber.cadence.WorkflowExecution getWorkflowExecution() {
            return workflowExecution;
        }

        @Override
        public String getDomain() {
            return "dummyTestDomain";
        }
        
    }

    /**
     * Key is TaskList
     */
    protected final Map<String, ActivityImplementationFactory> factories = new HashMap<String, ActivityImplementationFactory>();

    protected final DecisionContextProvider decisionContextProvider;

    public TestGenericActivityClient(DecisionContextProvider decisionContextProvider) {
        this.decisionContextProvider = decisionContextProvider;
    }

    public TestGenericActivityClient() {
        this(new DecisionContextProviderImpl());
    }

    public void addFactory(String taskListToListen, ActivityImplementationFactory factory) {
        factories.put(taskListToListen, factory);
    }

    @Override
    public Promise<byte[]> scheduleActivityTask(final ExecuteActivityParameters parameters) {
        final ActivityType activityType = parameters.getActivityType();
        final Settable<byte[]> result = new Settable<>();
        final PollForActivityTaskResponse pollResponse = new PollForActivityTaskResponse();
        String activityId = parameters.getActivityId();
        if (activityId == null) {
            activityId = decisionContextProvider.getDecisionContext().getWorkflowClient().generateUniqueId();
        }
        pollResponse.setActivityId(activityId);
        pollResponse.setActivityType(activityType);
        pollResponse.setInput(parameters.getInput());
        pollResponse.setStartedEventId(0L);
        pollResponse.setTaskToken("dummyTaskToken".getBytes());
        DecisionContext decisionContext = decisionContextProvider.getDecisionContext();
        final WorkflowExecution workflowExecution = decisionContext.getWorkflowContext().getWorkflowExecution();
        pollResponse.setWorkflowExecution(workflowExecution);
        String taskList = parameters.getTaskList();
        if (taskList == null || taskList.isEmpty()) {
                throw new IllegalArgumentException("empty or null task list");
        }
        ActivityImplementationFactory factory = factories.get(taskList);
        // Nobody listens on the specified task list. So in case of a real service it causes 
        // ScheduleToStart timeout.
        //TODO: Activity heartbeats and passing details to the exception.
        if (factory == null) {
            throw new IllegalStateException("No listener registered for " + taskList + " task list");
        }
        final ActivityImplementation impl = factory.getActivityImplementation(activityType);
        if (impl == null) {
            throw new IllegalStateException("Unknown activity type: " + activityType);
        }
        ActivityTask task = new ActivityTask(pollResponse);
        ActivityExecutionContext executionContext = new TestActivityExecutionContext(task, workflowExecution);
        try {
            byte[] activityResult = impl.execute(executionContext);
            result.set(activityResult);
        }
        catch (Throwable e) {
            if (e instanceof ActivityFailureException) {
                ActivityFailureException falure = (ActivityFailureException) e;
                throw new ActivityTaskFailedException(0, activityType, parameters.getActivityId(), falure.getReason(),
                        falure.getDetails());
            }
            // Unless there is problem in the framework or generic activity implementation this shouldn't be executed
            ActivityTaskFailedException failure = new ActivityTaskFailedException(0, activityType, parameters.getActivityId(),
                    e.getMessage(), null);
            failure.initCause(e);
            throw failure;
        }
        return result;
    }

    @Override
    public Promise<byte[]> scheduleActivityTask(String activity, byte[] input) {
        ExecuteActivityParameters parameters = new ExecuteActivityParameters();
        ActivityType activityType = new ActivityType();
        activityType.setName(activity);
        parameters.setActivityType(activityType);
        parameters.setInput(input);
        return scheduleActivityTask(parameters);
    }

    @Override
    public Promise<byte[]> scheduleActivityTask(final String activity, final Promise<byte[]> input) {
        final Settable<byte[]> result = new Settable<>();
        new Task(input) {

            @Override
            protected void doExecute() throws Throwable {
                result.chain(scheduleActivityTask(activity, input.get()));
            }
        };
        return result;
    }

}
