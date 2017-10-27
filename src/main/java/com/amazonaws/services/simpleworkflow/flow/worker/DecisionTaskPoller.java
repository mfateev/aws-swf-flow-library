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
package com.amazonaws.services.simpleworkflow.flow.worker;

import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.uber.cadence.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.services.simpleworkflow.flow.common.WorkflowExecutionUtils;
import org.apache.thrift.TException;

public class DecisionTaskPoller implements TaskPoller {

    private static final Log log = LogFactory.getLog(DecisionTaskPoller.class);

    private static final Log decisionsLog = LogFactory.getLog(DecisionTaskPoller.class.getName() + ".decisions");

    private static final int MAXIMUM_PAGE_SIZE = 500;

    private WorkflowService.Iface service;

    private String domain;

    private String taskListToPoll;

    private String identity;

    private boolean validated;

    private DecisionTaskHandler decisionTaskHandler;

    public DecisionTaskPoller() {
        identity = ManagementFactory.getRuntimeMXBean().getName();
    }

    public DecisionTaskPoller(WorkflowService.Iface service, String domain, String taskListToPoll,
            DecisionTaskHandler decisionTaskHandler) {
        this.service = service;
        this.domain = domain;
        this.taskListToPoll = taskListToPoll;
        this.decisionTaskHandler = decisionTaskHandler;
        identity = ManagementFactory.getRuntimeMXBean().getName();
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        validated = false;
        this.identity = identity;
    }

    public WorkflowService.Iface getService() {
        return service;
    }

    public String getDomain() {
        return domain;
    }

    public DecisionTaskHandler getDecisionTaskHandler() {
        return decisionTaskHandler;
    }

    public void setDecisionTaskHandler(DecisionTaskHandler decisionTaskHandler) {
        validated = false;
        this.decisionTaskHandler = decisionTaskHandler;
    }

    public void setService(WorkflowService.Iface service) {
        validated = false;
        this.service = service;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getTaskListToPoll() {
        return taskListToPoll;
    }

    public void setTaskListToPoll(String pollTaskList) {
        this.taskListToPoll = pollTaskList;
    }

    /**
     * Poll for a task using {@link #getPollTimeoutInSeconds()}
     *
     * @return null if poll timed out
     * @throws TException
     */
    private PollForDecisionTaskResponse poll() throws TException {
        validate();
        PollForDecisionTaskRequest pollRequest = new PollForDecisionTaskRequest();

        pollRequest.setDomain(domain);
        pollRequest.setIdentity(identity);

        TaskList tl = new TaskList();
        tl.setName(taskListToPoll);
        pollRequest.setTaskList(tl);

        if (log.isDebugEnabled()) {
            log.debug("poll request begin: " + pollRequest);
        }
        PollForDecisionTaskResponse result = service.PollForDecisionTask(pollRequest);
        if (log.isDebugEnabled()) {
            log.debug("poll request returned decision task: workflowType=" + result.getWorkflowType() + ", workflowExecution="
                    + result.getWorkflowExecution() + ", startedEventId=" + result.getStartedEventId() + ", previousStartedEventId=" + result.getPreviousStartedEventId());
        }

        if (result == null || result.getTaskToken() == null) {
            return null;
        }
        return result;
    }

    /**
     * Poll for a workflow task and call appropriate decider. This method might
     * call the service multiple times to retrieve the whole history it it is
     * paginated.
     * 
     * @return true if task was polled and decided upon, false if poll timed out
     * @throws Exception
     */
    @Override
    public boolean pollAndProcessSingleTask() throws Exception {
        RespondDecisionTaskCompletedRequest taskCompletedRequest = null;
        PollForDecisionTaskResponse task = poll();
        if (task == null) {
            return false;
        }
        DecisionTaskWithHistoryIterator historyIterator = new DecisionTaskWithHistoryIteratorImpl(task);
        try {
            taskCompletedRequest = decisionTaskHandler.handleDecisionTask(historyIterator);
            if (decisionsLog.isTraceEnabled()) {
                decisionsLog.trace(WorkflowExecutionUtils.prettyPrintDecisions(taskCompletedRequest.getDecisions()));
            }
            service.RespondDecisionTaskCompleted(taskCompletedRequest);
        }
        catch (Exception e) {
            PollForDecisionTaskResponse firstTask = historyIterator.getDecisionTask();
            if (firstTask != null) {
                if (log.isWarnEnabled()) {
                    log.warn("DecisionTask failure: taskId= " + firstTask.getStartedEventId() + ", workflowExecution="
                            + firstTask.getWorkflowExecution(), e);
                }
            }
            if (taskCompletedRequest != null && decisionsLog.isWarnEnabled()) {
                decisionsLog.warn("Failed taskId=" + firstTask.getStartedEventId() + " decisions="
                        + WorkflowExecutionUtils.prettyPrintDecisions(taskCompletedRequest.getDecisions()));
            }
            throw e;
        }
        return true;
    }

    private void validate() throws IllegalStateException {
        if (validated) {
            return;
        }
        checkFieldSet("decisionTaskHandler", decisionTaskHandler);
        checkFieldSet("service", service);
        checkFieldSet("identity", identity);
        validated = true;
    }

    private void checkFieldSet(String fieldName, Object fieldValue) throws IllegalStateException {
        if (fieldValue == null) {
            throw new IllegalStateException("Required field " + fieldName + " is not set");
        }
    }

    protected void checkFieldNotNegative(String fieldName, long fieldValue) throws IllegalStateException {
        if (fieldValue < 0) {
            throw new IllegalStateException("Field " + fieldName + " is negative");
        }
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void shutdownNow() {
    }

    @Override
    public boolean awaitTermination(long left, TimeUnit milliseconds) throws InterruptedException {
        //TODO: Waiting for all currently running pollAndProcessSingleTask to complete 
        return false;
    }

    private class DecisionTaskWithHistoryIteratorImpl implements DecisionTaskWithHistoryIterator {
        private final PollForDecisionTaskResponse task;
        private Iterator<HistoryEvent> current;
        private byte[] nextPageToken;

        public DecisionTaskWithHistoryIteratorImpl(PollForDecisionTaskResponse task) {
            this.task = task;
            current = task.getHistory().getEventsIterator();
            nextPageToken = task.getNextPageToken();
        }

        @Override
        public PollForDecisionTaskResponse getDecisionTask() {
            return null;
        }

        @Override
        public Iterator<HistoryEvent> getHistory() {
            return new Iterator<HistoryEvent>() {
                @Override
                public boolean hasNext() {
                    return current.hasNext() || nextPageToken != null;
                }

                @Override
                public HistoryEvent next() {
                    if (current.hasNext()) {
                        return current.next();
                    }
                    GetWorkflowExecutionHistoryRequest request = new GetWorkflowExecutionHistoryRequest();
                    request.setDomain(domain);
                    request.setExecution(task.getWorkflowExecution());
                    request.setMaximumPageSize(MAXIMUM_PAGE_SIZE);
                    try {
                        GetWorkflowExecutionHistoryResponse r = service.GetWorkflowExecutionHistory(request);
                        current = r.getHistory().getEventsIterator();
                        nextPageToken = r.getNextPageToken();
                    } catch (TException e) {
                        throw new RuntimeException(e);
                    }
                    return current.next();
                }
            };
        }
    }
}
