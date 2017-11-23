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

import com.amazonaws.services.simpleworkflow.flow.AsyncWorkflowClock;
import com.uber.cadence.HistoryEvent;
import com.uber.cadence.StartTimerDecisionAttributes;
import com.uber.cadence.TimerCanceledEventAttributes;
import com.uber.cadence.TimerFiredEventAttributes;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class AsyncWorkflowClockImpl implements AsyncWorkflowClock {

    private static final Log log = LogFactory.getLog(AsyncWorkflowClockImpl.class);

    private final class TimerCancellationHandler implements Consumer<Throwable> {

        private final String timerId;

        public <T> TimerCancellationHandler(String timerId) {
            this.timerId = timerId;
        }

        @Override
        public void accept(Throwable reason) {
            decisions.cancelTimer(timerId, new Runnable() {

                @Override
                public void run() {
                    OpenRequestInfo<?, ?> scheduled = scheduledTimers.remove(timerId);
                    BiConsumer<?, Throwable> context = scheduled.getCompletionCallback();
                    CancellationException exception = new CancellationException("Cancelled by request");
                    exception.initCause(reason);
                    context.accept(null, exception);
                }
            });
        }
    }

    private final DecisionsHelper decisions;

    private final Map<String, OpenRequestInfo<?, ?>> scheduledTimers = new HashMap<String, OpenRequestInfo<?, ?>>();

    private long replayCurrentTimeMilliseconds;

    private boolean replaying = true;

    AsyncWorkflowClockImpl(DecisionsHelper decisions) {
        this.decisions = decisions;
    }

    @Override
    public long currentTimeMillis() {
        return replayCurrentTimeMilliseconds;
    }

    void setReplayCurrentTimeMilliseconds(long replayCurrentTimeMilliseconds) {
        this.replayCurrentTimeMilliseconds = replayCurrentTimeMilliseconds;
    }

    @Override
    public boolean isReplaying() {
        return replaying;
    }

    @Override
    public IdCancellationCallbackPair createTimer(long delaySeconds, Consumer<Throwable> callback) {
        return createTimer(delaySeconds, null, (userContext, failure) -> {
            callback.accept(failure);
        });
    }

    @Override
    public <T> IdCancellationCallbackPair createTimer(long delaySeconds, T userContext, BiConsumer<T, Throwable> callback) {
        if (delaySeconds < 0) {
            throw new IllegalArgumentException("Negative delaySeconds: " + delaySeconds);
        }
        if (delaySeconds == 0) {
            callback.accept(userContext, null);
            return new IdCancellationCallbackPair("immediate", throwable -> {
            });
        }
        final OpenRequestInfo<T, Object> context = new OpenRequestInfo<>(userContext);
        final StartTimerDecisionAttributes timer = new StartTimerDecisionAttributes();
        timer.setStartToFireTimeoutSeconds(delaySeconds);
        final String timerId = decisions.getNextId();
        timer.setTimerId(timerId);
        decisions.startTimer(timer, userContext);
        context.setCompletionHandle((ctx, throwable) -> {
            callback.accept(ctx, null);
        });
        scheduledTimers.put(timerId, context);
        return new IdCancellationCallbackPair(timerId, new TimerCancellationHandler(timerId));
    }

    void setReplaying(boolean replaying) {
        this.replaying = replaying;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void handleTimerFired(Long eventId, TimerFiredEventAttributes attributes) {
        String timerId = attributes.getTimerId();
        if (decisions.handleTimerClosed(timerId)) {
            OpenRequestInfo scheduled = scheduledTimers.remove(timerId);
            if (scheduled != null) {
                BiConsumer completionCallback = scheduled.getCompletionCallback();
                completionCallback.accept(scheduled.getUserContext(), null);
            }
        } else {
            log.debug("handleTimerFired not complete");
        }
    }

    void handleTimerCanceled(HistoryEvent event) {
        TimerCanceledEventAttributes attributes = event.getTimerCanceledEventAttributes();
        String timerId = attributes.getTimerId();
        if (decisions.handleTimerCanceled(event)) {
            OpenRequestInfo<?, ?> scheduled = scheduledTimers.remove(timerId);
            if (scheduled != null) {
                BiConsumer<?, Throwable> completionCallback = scheduled.getCompletionCallback();
                CancellationException exception = new CancellationException("Cancelled by request");
                completionCallback.accept(null, exception);
            }
        } else {
            log.debug("handleTimerCanceled not complete");
        }
    }

}
