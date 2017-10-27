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
package com.amazonaws.services.simpleworkflow.flow.generic;

import java.util.Map;
import java.util.concurrent.CancellationException;

import com.amazonaws.services.simpleworkflow.flow.ActivityExecutionContext;
import com.amazonaws.services.simpleworkflow.flow.ActivityFailureException;
import com.amazonaws.services.simpleworkflow.flow.ActivityWorker;
import com.amazonaws.services.simpleworkflow.flow.worker.ActivityTypeExecutionOptions;

/**
 * Base class for activity implementation. Extending
 * {@link ActivityImplementationBase} instead of {@link ActivityImplementation}
 * is recommended.
 * 
 * @see ActivityWorker
 * @see ActivityImplementationBase
 * 
 * @author fateev, suskin
 */
public abstract class ActivityImplementation {

    public abstract ActivityTypeExecutionOptions getExecutionOptions();

    /**
     * Execute external activity or initiate its execution if
     * {@link ActivityTypeExecutionOptions#isManualActivityCompletion()} is <code>true</code>.
     * 
     * @param context
     *            information about activity to be executed. Use
     *            {@link com.uber.cadence.PollForActivityTaskResponse#getInput()} to get activity input
     *            arguments.
     * @return result of activity execution if {@link ActivityTypeExecutionOptions#isManualActivityCompletion()} is set
     *         to false. Use
     *         {@link ActivityWorker#respondActivityTaskCompleted(String, Map)}
     *         to return result in asynchronous case.
     */
    public abstract byte[] execute(ActivityExecutionContext context) throws ActivityFailureException, CancellationException;

}
