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

import com.amazonaws.services.simpleworkflow.flow.ActivityTaskFailedException;
import com.amazonaws.services.simpleworkflow.flow.ActivityTaskTimedOutException;
import com.amazonaws.services.simpleworkflow.flow.generic.ExecuteActivityParameters;
import com.amazonaws.services.simpleworkflow.flow.generic.GenericAsyncActivityClient;
import com.uber.cadence.ActivityTaskCanceledEventAttributes;
import com.uber.cadence.ActivityTaskCompletedEventAttributes;
import com.uber.cadence.ActivityTaskFailedEventAttributes;
import com.uber.cadence.ActivityTaskStartedEventAttributes;
import com.uber.cadence.ActivityTaskTimedOutEventAttributes;
import com.uber.cadence.ActivityType;
import com.uber.cadence.HistoryEvent;
import com.uber.cadence.ScheduleActivityTaskDecisionAttributes;
import com.uber.cadence.TaskList;
import com.uber.cadence.TimeoutType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class GenericAsyncActivityClientImpl implements GenericAsyncActivityClient {

    private final class ActivityCancellationHandler implements Consumer<Throwable> {

        private final String activityId;

        private final  BiConsumer<byte[], Throwable> callback;

        private ActivityCancellationHandler(String activityId, BiConsumer<byte[], Throwable> callaback) {
            this.activityId = activityId;
            this.callback = callaback;
        }

        @Override
        public void accept(Throwable cause) {
            decisions.requestCancelActivityTask(activityId, () -> {
                OpenRequestInfo<byte[], ActivityType> scheduled = scheduledActivities.remove(activityId);
                if (scheduled == null) {
                    throw new IllegalArgumentException("Activity \"" + activityId + "\" wasn't scheduled");
                }
                callback.accept(null, new CancellationException("Cancelled by request"));
            });
        }
    }

    @Override
    public Consumer<Throwable> scheduleActivityTask(ExecuteActivityParameters parameters, BiConsumer<byte[], Throwable> callback) {
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
        decisions.scheduleActivityTask(attributes);
        context.setCompletionHandle(callback);
        scheduledActivities.put(attributes.getActivityId(), context);
        return new ActivityCancellationHandler(attributes.getActivityId(), callback);
    }

    private final DecisionsHelper decisions;

    private final Map<String, OpenRequestInfo<byte[], ActivityType>> scheduledActivities = new HashMap<>();

    public GenericAsyncActivityClientImpl(DecisionsHelper decisions) {
        this.decisions = decisions;
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
                BiConsumer<byte[], Throwable> completionHandle = scheduled.getCompletionCallback();
                // It is OK to fail with subclass of CancellationException when cancellation requested.
                // It allows passing information about cancellation (details in this case) to the surrounding doCatch block
                completionHandle.accept(null, e);
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
                BiConsumer<byte[], Throwable> completionHandle = scheduled.getCompletionCallback();
                completionHandle.accept(result, null);
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
                BiConsumer<byte[], Throwable> completionHandle = scheduled.getCompletionCallback();
                completionHandle.accept(null, failure);
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
                BiConsumer<byte[], Throwable> completionHandle = scheduled.getCompletionCallback();
                completionHandle.accept(null, failure);
            }
        }
    }

}
