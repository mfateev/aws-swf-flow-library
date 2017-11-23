/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.services.simpleworkflow.flow;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Clock that must be used inside workflow definition code to ensure replay
 * determinism.
 */
public interface AsyncWorkflowClock {

    public static class IdCancellationCallbackPair {
        private final String id;
        private final Consumer<Throwable> cancellationCallback;

        public IdCancellationCallbackPair(String id, Consumer<Throwable> cancellationCallback) {
            this.id = id;
            this.cancellationCallback = cancellationCallback;
        }

        public String getId() {
            return id;
        }

        public Consumer<Throwable> getCancellationCallback() {
            return cancellationCallback;
        }
    }

    /**
     * @return time of the {@link com.uber.cadence.PollForDecisionTaskResponse} start event of the decision
     * being processed or replayed.
     */
    public long currentTimeMillis();

    /**
     * <code>true</code> indicates if workflow is replaying already processed
     * events to reconstruct it state. <code>false</code> indicates that code is
     * making forward process for the first time. For example can be used to
     * avoid duplicating log records due to replay.
     */
    public boolean isReplaying();

    /**
     * Create a Value that becomes ready after the specified delay.
     *
     * @param delaySeconds time-interval after which the Value becomes ready in seconds.
     * @param callback     Callback that is called with null parameter after the specified delay.
     *                     CancellationException is passed as a parameter in case of a cancellation.
     * @return pair that contains timer id and cancellation callback. Invoke {@link Consumer#accept(Object)} to cancel timer.
     */
    public abstract IdCancellationCallbackPair createTimer(long delaySeconds, Consumer<Throwable> callback);

    /**
     * Create a Value that becomes ready after the specified delay.
     *
     * @param userContext context object that is passed to the callback when timer fires.
     * @param callback    Callback that is called  value passed as context parameter after the specified delay.
     * @return pair that contains timer id and cancellation callback. Invoke {@link Consumer#accept(Object)} to cancel timer.
     */
    public abstract <T> IdCancellationCallbackPair createTimer(long delaySeconds, final T userContext, BiConsumer<T, Throwable> callback);

}
