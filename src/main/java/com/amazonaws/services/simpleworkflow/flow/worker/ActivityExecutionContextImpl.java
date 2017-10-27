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

import com.uber.cadence.PollForActivityTaskResponse;
import com.uber.cadence.RecordActivityTaskHeartbeatRequest;
import com.uber.cadence.RecordActivityTaskHeartbeatResponse;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowService.Iface;
import java.util.concurrent.CancellationException;

import com.uber.cadence.WorkflowService;
import com.amazonaws.services.simpleworkflow.flow.ActivityExecutionContext;
import org.apache.thrift.TException;

/**
 * Base implementation of an {@link ActivityExecutionContext}.
 * 
 * @see ActivityExecutionContext
 * 
 * @author fateev, suskin
 * 
 */
class ActivityExecutionContextImpl extends ActivityExecutionContext {

    private final Iface service;

    private final String domain;
    
    private final PollForActivityTaskResponse task;

    /**
     * Create an ActivityExecutionContextImpl with the given attributes.
     * 
     * @param service
     *            The {@link WorkflowService.Iface} this
     *            ActivityExecutionContextImpl will send service calls to.
     * @param task
     *            The {@link PollForActivityTaskResponse} this ActivityExecutionContextImpl
     *            will be used for.
     *
     * @see ActivityExecutionContext
     */
    public ActivityExecutionContextImpl(Iface service, String domain, PollForActivityTaskResponse task) {
        this.domain = domain;
        this.service = service;
        this.task = task;
    }

    /**
     * @throws CancellationException
     * @see ActivityExecutionContext#recordActivityHeartbeat(byte[])
     */
    @Override
    public void recordActivityHeartbeat(byte[] details) throws CancellationException {
        //TODO: call service with the specified minimal interval (through @ActivityExecutionOptions)
        // allowing more frequent calls of this method.
        RecordActivityTaskHeartbeatRequest r = new RecordActivityTaskHeartbeatRequest();
        r.setTaskToken(task.getTaskToken());
        r.setDetails(details);
        RecordActivityTaskHeartbeatResponse status;
        try {
            status = service.RecordActivityTaskHeartbeat(r);
        } catch (TException e) {
            throw new RuntimeException(e);
        }
        if (status.isCancelRequested()) {
            throw new CancellationException();
        }
    }

    /**
     * @see ActivityExecutionContext#getTask()
     */
    @Override
    public PollForActivityTaskResponse getTask() {
        return task;
    }

    /**
     * @see ActivityExecutionContext#getService()
     */
    @Override
    public Iface getService() {
        return service;
    }

    @Override
    public byte[] getTaskToken() {
        return task.getTaskToken();
    }

    @Override
    public WorkflowExecution getWorkflowExecution() {
        return task.getWorkflowExecution();
    }

    @Override
    public String getDomain() {
        return domain;
    }

}
