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
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.amazonaws.services.simpleworkflow.flow.core.Task;
import com.amazonaws.services.simpleworkflow.flow.generic.ExecuteActivityParameters;
import com.amazonaws.services.simpleworkflow.flow.generic.GenericActivityClient;
import com.amazonaws.services.simpleworkflow.flow.generic.GenericAsyncActivityClient;
import com.uber.cadence.ActivityType;

import java.util.function.Consumer;

class GenericActivityClientImpl implements GenericActivityClient {

    private final GenericAsyncActivityClient client;

    GenericActivityClientImpl(GenericAsyncActivityClient client) {
        this.client = client;
    }

    @Override
    public Promise<byte[]> scheduleActivityTask(ExecuteActivityParameters parameters) {
        String taskName = "activityId=" + parameters.getActivityId() +
                ", activityType=" + parameters.getActivityType();
        final Settable<byte[]> result = new Settable<>();
        result.setDescription("scheduleActivityTask " + taskName);
        new ExternalTask() {
            @Override
            protected ExternalTaskCancellationHandler doExecute(final ExternalTaskCompletionHandle handle) throws Throwable {

                Consumer<Throwable> cancellation = client.scheduleActivityTask(parameters,
                        (value, failure) -> {
                            if (value != null) {
                                result.set(value);
                                handle.complete();
                            } else {
                                handle.fail(failure);
                            }
                        });
                return cause -> {
                    cancellation.accept(cause);
                };
            }
        }.setName(taskName);
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
    public Promise<byte[]> scheduleActivityTask(String activity, Promise<byte[]> input) {
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
