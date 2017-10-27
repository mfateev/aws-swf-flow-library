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

import com.uber.cadence.ActivityType;

/**
 * Exception used to communicate failure of remote activity.
 */
@SuppressWarnings("serial")
public class ActivityTaskFailedException extends ActivityTaskException {
    
    private byte[] details;
    
    public ActivityTaskFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ActivityTaskFailedException(String message) {
        super(message);
    }
    
    public ActivityTaskFailedException(long eventId, ActivityType activityType, String activityId, String reason, byte[] details) {
        super(reason, eventId, activityType, activityId);
        this.details = details;
    }
    
    public byte[] getDetails() {
        return details;
    }

    public void setDetails(byte[] details) {
        this.details = details;
    }
}
