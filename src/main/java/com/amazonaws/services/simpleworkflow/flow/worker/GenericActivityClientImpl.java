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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

import com.amazonaws.services.simpleworkflow.flow.ActivityTaskFailedException;
import com.amazonaws.services.simpleworkflow.flow.ActivityTaskTimedOutException;
import com.amazonaws.services.simpleworkflow.flow.core.ExternalTask;
import com.amazonaws.services.simpleworkflow.flow.core.ExternalTaskCancellationHandler;
import com.amazonaws.services.simpleworkflow.flow.core.ExternalTaskCompletionHandle;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.amazonaws.services.simpleworkflow.flow.core.Task;
import com.amazonaws.services.simpleworkflow.flow.generic.ExecuteActivityParameters;
import com.amazonaws.services.simpleworkflow.flow.generic.GenericActivityClient;
import com.uber.cadence.*;

class GenericActivityClientImpl implements GenericActivityClient {

    private final class ActivityCancellationHandler implements ExternalTaskCancellationHandler {

        private final String activityId;

        private final ExternalTaskCompletionHandle handle;

        private ActivityCancellationHandler(String activityId, ExternalTaskCompletionHandle handle) {
            this.activityId = activityId;
            this.handle = handle;
        }

        @Override
        public void handleCancellation(Throwable cause) {
            decisions.requestCancelActivityTask(activityId, new Runnable() {

                @Override
                public void run() {
                    OpenRequestInfo<byte[], ActivityType> scheduled = scheduledActivities.remove(activityId);
                    if (scheduled == null) {
                        throw new IllegalArgumentException("Activity \"" + activityId + "\" wasn't scheduled");
                    }
                    handle.complete();
                }
            });
        }
    }

    private final DecisionsHelper decisions;

    private final Map<String, OpenRequestInfo<byte[], ActivityType>> scheduledActivities = new HashMap<>();

    public GenericActivityClientImpl(DecisionsHelper decisions) {
        this.decisions = decisions;
    }

    @Override
    public Promise<byte[]> scheduleActivityTask(final ExecuteActivityParameters parameters) {
        final OpenRequestInfo<byte[], ActivityType> context = new OpenRequestInfo<>(parameters.getActivityType());
        final ScheduleActivityTaskDecisionAttributes attributes = new ScheduleActivityTaskDecisionAttributes();
        attributes.setActivityType(parameters.getActivityType());
        attributes.setInput(parameters.getInput());
        attributes.setHeartbeatTimeoutSeconds(parameters.getHeartbeatTimeoutSeconds());
        attributes.setScheduleToCloseTimeoutSeconds(parameters.getScheduleToCloseTimeoutSeconds());
        attributes.setScheduleToStartTimeoutSeconds(parameters.getScheduleToStartTimeoutSeconds());
        attributes.setStartToCloseTimeoutSeconds(parameters.getStartToCloseTimeoutSeconds());
//        attributes.setTaskPriority(FlowHelpers.taskPriorityToString(parameters.getTaskPriority()));
        String activityId = parameters.getActivityId();
        if (activityId == null) {
            activityId = String.valueOf(decisions.getNextId());
        }
        attributes.setActivityId(activityId);

        String taskList = parameters.getTaskList();
        if (taskList != null && !taskList.isEmpty()) {
            TaskList tl = new TaskList();
            tl.setName(taskList);
            attributes.setTaskList(tl);
        }
        String taskName = "activityId=" + activityId + ", activityType=" + attributes.getActivityType();
        new ExternalTask() {

            @Override
            protected ExternalTaskCancellationHandler doExecute(final ExternalTaskCompletionHandle handle) throws Throwable {

                decisions.scheduleActivityTask(attributes);
                context.setCompletionHandle(handle);
                scheduledActivities.put(attributes.getActivityId(), context);
                return new ActivityCancellationHandler(attributes.getActivityId(), handle);
            }
        }.setName(taskName);
        context.setResultDescription("scheduleActivityTask " + taskName);
        return context.getResult();
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

    @Override
    public Promise<byte[]> scheduleActivityTask(String activity, byte[] input) {
        ExecuteActivityParameters parameters = new ExecuteActivityParameters();
        ActivityType activityType = new ActivityType();
        activityType.setName(activity);
        parameters.setActivityType(activityType);
        parameters.setInput(input);
        return scheduleActivityTask(parameters);
    }

    void handleActivityTaskStarted(ActivityTaskStartedEventAttributes attributes) {
    }

    void handleActivityTaskCanceled(HistoryEvent event) {
        ActivityTaskCanceledEventAttributes attributes = event.getActivityTaskCanceledEventAttributes();
        String activityId = decisions.getActivityId(attributes);
        if (decisions.handleActivityTaskCanceled(event)) {
            CancellationException e = new CancellationException();
            OpenRequestInfo<byte[], ActivityType> scheduled = scheduledActivities.remove(activityId);
            if (scheduled != null) {
                ExternalTaskCompletionHandle completionHandle = scheduled.getCompletionHandle();
                // It is OK to fail with subclass of CancellationException when cancellation requested.
                // It allows passing information about cancellation (details in this case) to the surrounding doCatch block
                completionHandle.fail(e);
            }
        }
    }

    void handleActivityTaskCompleted(HistoryEvent event) {
        ActivityTaskCompletedEventAttributes attributes = event.getActivityTaskCompletedEventAttributes();
        String activityId = decisions.getActivityId(attributes);
        if (decisions.handleActivityTaskClosed(activityId)) {
            OpenRequestInfo<byte[], ActivityType> scheduled = scheduledActivities.remove(activityId);
            if (scheduled != null) {
                byte[] result = attributes.getResult();
                scheduled.getResult().set(result);
                ExternalTaskCompletionHandle completionHandle = scheduled.getCompletionHandle();
                completionHandle.complete();
            }
        }
    }

    void handleActivityTaskFailed(HistoryEvent event) {
        ActivityTaskFailedEventAttributes attributes = event.getActivityTaskFailedEventAttributes();
        String activityId = decisions.getActivityId(attributes);
        if (decisions.handleActivityTaskClosed(activityId)) {
            OpenRequestInfo<byte[], ActivityType> scheduled = scheduledActivities.remove(activityId);
            if (scheduled != null) {
                String reason = attributes.getReason();
                byte[] details = attributes.getDetails();
                ActivityTaskFailedException failure = new ActivityTaskFailedException(event.getEventId(),
                        scheduled.getUserContext(), activityId, reason, details);
                ExternalTaskCompletionHandle completionHandle = scheduled.getCompletionHandle();
                completionHandle.fail(failure);
            }
        }
    }

    void handleActivityTaskTimedOut(HistoryEvent event) {
        ActivityTaskTimedOutEventAttributes attributes = event.getActivityTaskTimedOutEventAttributes();
        String activityId = decisions.getActivityId(attributes);
        if (decisions.handleActivityTaskClosed(activityId)) {
            OpenRequestInfo<byte[], ActivityType> scheduled = scheduledActivities.remove(activityId);
            if (scheduled != null) {
                TimeoutType timeoutType = attributes.getTimeoutType();
                byte[] details = attributes.getDetails();
                ActivityTaskTimedOutException failure = new ActivityTaskTimedOutException(event.getEventId(),
                        scheduled.getUserContext(), activityId, timeoutType, details);
                ExternalTaskCompletionHandle completionHandle = scheduled.getCompletionHandle();
                completionHandle.fail(failure);
            }
        }
    }

}
