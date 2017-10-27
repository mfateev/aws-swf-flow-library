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

import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.generic.ExecuteActivityParameters;
import com.amazonaws.services.simpleworkflow.flow.generic.GenericActivityClient;


public class TestPOJOActivityImplementationGenericActivityClient implements GenericActivityClient {

    private final TestGenericActivityClient genericClient;
    
    public TestPOJOActivityImplementationGenericActivityClient() {
        genericClient = new TestGenericActivityClient();
    }

    public void addWorker(TestPOJOActivityImplementationWorker worker) {
        genericClient.addFactory(worker.getTaskList(), worker.getFactory());
    }

    public Promise<byte[]> scheduleActivityTask(ExecuteActivityParameters parameters) {
        return genericClient.scheduleActivityTask(parameters);
    }

    public Promise<byte[]> scheduleActivityTask(String activity, byte[] input) {
        return genericClient.scheduleActivityTask(activity, input);
    }

    public Promise<byte[]> scheduleActivityTask(String activity, Promise<byte[]> input) {
        return genericClient.scheduleActivityTask(activity, input);
    }

    
}
