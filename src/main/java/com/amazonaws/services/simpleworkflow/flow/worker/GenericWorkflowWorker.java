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

import java.lang.management.ManagementFactory;

import com.uber.cadence.WorkflowService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinitionFactoryFactory;

public class GenericWorkflowWorker extends GenericWorker {

    private static final Log log = LogFactory.getLog(GenericWorkflowWorker.class);

    private static final String THREAD_NAME_PREFIX = "SWF Decider ";

    private WorkflowDefinitionFactoryFactory workflowDefinitionFactoryFactory;

    public GenericWorkflowWorker() {
        setIdentity(ManagementFactory.getRuntimeMXBean().getName());
    }

    public GenericWorkflowWorker(WorkflowService.Iface service, String domain, String taskListToPoll) {
        this();
        setService(service);
        setDomain(domain);
        setTaskListToPoll(taskListToPoll);
    }

    public WorkflowDefinitionFactoryFactory getWorkflowDefinitionFactoryFactory() {
        return workflowDefinitionFactoryFactory;
    }

    public void setWorkflowDefinitionFactoryFactory(WorkflowDefinitionFactoryFactory workflowDefinitionFactoryFactory) {
        this.workflowDefinitionFactoryFactory = workflowDefinitionFactoryFactory;
    }

    protected DecisionTaskPoller createWorkflowPoller() {
        DecisionTaskPoller poller = new DecisionTaskPoller();
        return poller;
    }

    @Override
    protected void checkRequredProperties() {
        checkRequiredProperty(workflowDefinitionFactoryFactory, "workflowDefinitionFactoryFactory");
    }

    @Override
    protected String getPollThreadNamePrefix() {
        return THREAD_NAME_PREFIX + getTaskListToPoll() + " ";
    }

    @Override
    protected TaskPoller createPoller() {
        DecisionTaskPoller result = new DecisionTaskPoller();
        AsyncWorkflowFactory workflowFactory = new PromiseAsyncWorkflowFactory(workflowDefinitionFactoryFactory);
        result.setDecisionTaskHandler(new AsyncDecisionTaskHandler(workflowFactory));
        result.setDomain(getDomain());
        result.setIdentity(getIdentity());
        result.setService(getService());
        result.setTaskListToPoll(getTaskListToPoll());
        return result;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[super=" + super.toString() + ", workflowDefinitionFactoryFactory="
                + workflowDefinitionFactoryFactory + "]";
    }
}
