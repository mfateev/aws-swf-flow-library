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
import java.util.function.Consumer;

import com.amazonaws.services.simpleworkflow.flow.AsyncWorkflowClock;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.uber.cadence.HistoryEvent;
import com.uber.cadence.StartTimerDecisionAttributes;
import com.uber.cadence.TimerCanceledEventAttributes;
import com.uber.cadence.TimerFiredEventAttributes;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.services.simpleworkflow.flow.WorkflowClock;
import com.amazonaws.services.simpleworkflow.flow.core.ExternalTask;
import com.amazonaws.services.simpleworkflow.flow.core.ExternalTaskCancellationHandler;
import com.amazonaws.services.simpleworkflow.flow.core.ExternalTaskCompletionHandle;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;

class WorkflowClockImpl implements WorkflowClock {

    private final AsyncWorkflowClock clock;

    WorkflowClockImpl(AsyncWorkflowClock clock) {
        this.clock = clock;
    }

    @Override
    public long currentTimeMillis() {
        return clock.currentTimeMillis();
    }

    @Override
    public boolean isReplaying() {
        return clock.isReplaying();
    }

    @Override
    public Promise<Void> createTimer(long delaySeconds) {
        return createTimer(delaySeconds, null);
    }

    @Override
    public <T> Promise<T> createTimer(final long delaySeconds, final T userContext) {
        final Settable<T> result = new Settable<>();

        new ExternalTask() {
            @Override
            protected ExternalTaskCancellationHandler doExecute(ExternalTaskCompletionHandle handle) throws Throwable {
                AsyncWorkflowClock.IdCancellationCallbackPair pair = clock.createTimer(delaySeconds, userContext,
                        (uc, failure) -> {
                            if (failure == null) {
                                result.set(uc);
                                handle.complete();
                            } else {
                                handle.fail(failure);
                            }
                        });
                String taskName = "timerId=" + pair.getId() + " delaySeconds=" + delaySeconds;
                setName(taskName);
                result.setDescription("createTimer " + taskName);
                return cause -> {
                    pair.getCancellationCallback().accept(cause);
                };
            }
        };
        return result;
    }
}
