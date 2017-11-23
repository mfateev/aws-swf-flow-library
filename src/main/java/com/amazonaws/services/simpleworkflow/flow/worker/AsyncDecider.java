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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import com.uber.cadence.EventType;
import com.uber.cadence.HistoryEvent;
import com.uber.cadence.PollForDecisionTaskResponse;
import com.uber.cadence.TimerFiredEventAttributes;
import com.uber.cadence.TimerStartedEventAttributes;
import com.uber.cadence.WorkflowExecutionSignaledEventAttributes;
import com.uber.cadence.WorkflowExecutionStartedEventAttributes;
import com.uber.cadence.WorkflowType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.WorkflowException;
import com.amazonaws.services.simpleworkflow.flow.core.AsyncScope;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.generic.ContinueAsNewWorkflowExecutionParameters;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinition;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinitionFactory;
import com.amazonaws.services.simpleworkflow.flow.worker.HistoryHelper.EventsIterator;

class AsyncDecider {

    //TODO: remove from this class
    private final WorkflowDefinitionFactory workflowDefinitionFactory;

    public interface AsyncWorkflow {
        boolean eventLoop() throws Throwable;

        /**
         *
         * @return null means no output yet
         */
        byte[] getOutput();

        void cancel(CancellationException e);

        Throwable getFailure();

        boolean isCancelRequested();

        String getAsynchronousThreadDump();

        byte[] getWorkflowState() throws  WorkflowException;

        void close();
    }

    private static class PromiseAsyncWorkflow implements AsyncWorkflow {

        private final WorkflowAsyncScope scope;
        private final WorkflowDefinitionFactory workflowDefinitionFactory;


        private PromiseAsyncWorkflow(HistoryEvent event, WorkflowDefinitionFactory workflowDefinitionFactory, DecisionContext context) throws Exception {
            this.scope = new WorkflowAsyncScope(event, workflowDefinitionFactory.getWorkflowDefinition(context), event.getWorkflowExecutionStartedEventAttributes().getWorkflowType());
            this.workflowDefinitionFactory = workflowDefinitionFactory;
        }

        @Override
        public boolean eventLoop() throws Throwable {
            return scope.eventLoop();
        }

        @Override
        public byte[] getOutput() {
            Promise<byte[]> output = scope.getOutput();
            if (output.isReady()) {
                return output.get();
            } else {
                return null;
            }
        }

        @Override
        public void cancel(CancellationException e) {
            scope.cancel(e);
        }

        @Override
        public Throwable getFailure() {
            return scope.getFailure();
        }

        @Override
        public boolean isCancelRequested() {
            return scope.isCancelRequested();
        }

        @Override
        public String getAsynchronousThreadDump() {
            return scope.getAsynchronousThreadDumpAsString();
        }

        @Override
        public byte[] getWorkflowState() throws  WorkflowException {
            return scope.getWorkflowState();
        }

        @Override
        public void close() {
            workflowDefinitionFactory.deleteWorkflowDefinition(scope.getWorkflowDefinition());
        }
    }

    private static final class WorkflowAsyncScope extends AsyncScope {

        private final WorkflowExecutionStartedEventAttributes attributes;
        private WorkflowDefinition definition;

        private Promise<byte[]> output;

        public WorkflowAsyncScope(HistoryEvent event, WorkflowDefinition definition, WorkflowType workflowType) {
            super(false, true);
            if (definition == null) {
                throw new IllegalStateException("Unknown workflow type: " + workflowType);
            }

            this.definition = definition;
            assert event.getEventType().equals(EventType.WorkflowExecutionStarted.toString());
            this.attributes = event.getWorkflowExecutionStartedEventAttributes();
        }

        @Override
        protected void doAsync() throws Throwable {
            output = definition.execute(attributes.getInput());
        }

        public Promise<byte[]> getOutput() {
            return output;
        }

        public byte[] getWorkflowState() throws WorkflowException {
            return definition.getWorkflowState();
        }

        public void close() {
        }

        public WorkflowDefinition getWorkflowDefinition() {
            return definition;
        }
    }

    private static final Log log = LogFactory.getLog(AsyncDecider.class);

    private static final int MILLION = 1000000;

    private final HistoryHelper historyHelper;

    private final DecisionsHelper decisionsHelper;

    private final GenericActivityClientImpl activityClient;

    private final GenericWorkflowClientImpl workflowClient;

    private final WorkflowClockImpl workflowClock;

    private final DecisionContext context;

    private AsyncWorkflow workflowAsyncScope;

    private boolean cancelRequested;

    private WorkfowContextImpl workflowContext;

    private boolean unhandledDecision;

    private boolean completed;

    private Throwable failure;

    public AsyncDecider(WorkflowDefinitionFactory workflowDefinitionFactory, HistoryHelper historyHelper,
            DecisionsHelper decisionsHelper) throws Exception {
        this.workflowDefinitionFactory = workflowDefinitionFactory;
        this.historyHelper = historyHelper;
        this.decisionsHelper = decisionsHelper;
        this.activityClient = new GenericActivityClientImpl(decisionsHelper);
        PollForDecisionTaskResponse decisionTask = historyHelper.getDecisionTask();
        workflowContext = new WorkfowContextImpl(decisionTask);
        this.workflowClient = new GenericWorkflowClientImpl(decisionsHelper, workflowContext);
        this.workflowClock = new WorkflowClockImpl(decisionsHelper);
        context = new DecisionContextImpl(activityClient, workflowClient, workflowClock, workflowContext);
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    private void handleWorkflowExecutionStarted(HistoryEvent event) throws Exception {
        workflowAsyncScope = new PromiseAsyncWorkflow(event, workflowDefinitionFactory, context);
    }

    private void processEvent(HistoryEvent event, EventType eventType) throws Throwable {
        switch (eventType) {
        case ActivityTaskCanceled:
            activityClient.handleActivityTaskCanceled(event);
            break;
        case ActivityTaskCompleted:
            activityClient.handleActivityTaskCompleted(event);
            break;
        case ActivityTaskFailed:
            activityClient.handleActivityTaskFailed(event);
            break;
        case ActivityTaskStarted:
            activityClient.handleActivityTaskStarted(event.getActivityTaskStartedEventAttributes());
            break;
        case ActivityTaskTimedOut:
            activityClient.handleActivityTaskTimedOut(event);
            break;
        case ExternalWorkflowExecutionCancelRequested:
            workflowClient.handleChildWorkflowExecutionCancelRequested(event);
            break;
        case ChildWorkflowExecutionCanceled:
            workflowClient.handleChildWorkflowExecutionCanceled(event);
            break;
        case ChildWorkflowExecutionCompleted:
            workflowClient.handleChildWorkflowExecutionCompleted(event);
            break;
        case ChildWorkflowExecutionFailed:
            workflowClient.handleChildWorkflowExecutionFailed(event);
            break;
        case ChildWorkflowExecutionStarted:
            workflowClient.handleChildWorkflowExecutionStarted(event);
            break;
        case ChildWorkflowExecutionTerminated:
            workflowClient.handleChildWorkflowExecutionTerminated(event);
            break;
        case ChildWorkflowExecutionTimedOut:
            workflowClient.handleChildWorkflowExecutionTimedOut(event);
            break;
        case DecisionTaskCompleted:
            handleDecisionTaskCompleted(event);
            break;
        case DecisionTaskScheduled:
            // NOOP
            break;
        case DecisionTaskStarted:
            handleDecisionTaskStarted(event);
            break;
        case DecisionTaskTimedOut:
            // Handled in the processEvent(event)
            break;
//        case ExternalWorkflowExecutionSignaled:
//            workflowClient.handleExternalWorkflowExecutionSignaled(event);
//            break;
        case StartChildWorkflowExecutionFailed:
            workflowClient.handleStartChildWorkflowExecutionFailed(event);
            break;
        case TimerFired:
            handleTimerFired(event);
            break;
        case WorkflowExecutionCancelRequested:
            handleWorkflowExecutionCancelRequested(event);
            break;
        case WorkflowExecutionSignaled:
            handleWorkflowExecutionSignaled(event);
            break;
        case WorkflowExecutionStarted:
            handleWorkflowExecutionStarted(event);
            break;
        case WorkflowExecutionTerminated:
            // NOOP
            break;
        case WorkflowExecutionTimedOut:
            // NOOP
            break;
        case ActivityTaskScheduled:
            decisionsHelper.handleActivityTaskScheduled(event);
            break;
        case ActivityTaskCancelRequested:
            decisionsHelper.handleActivityTaskCancelRequested(event);
            break;
        case RequestCancelActivityTaskFailed:
            decisionsHelper.handleRequestCancelActivityTaskFailed(event);
            break;
        case MarkerRecorded:
            break;
        case WorkflowExecutionCompleted:
            break;
        case WorkflowExecutionFailed:
            break;
        case WorkflowExecutionCanceled:
            break;
        case WorkflowExecutionContinuedAsNew:
            break;
        case TimerStarted:
            handleTimerStarted(event);
            break;
        case TimerCanceled:
            workflowClock.handleTimerCanceled(event);
            break;
//        case SignalExternalWorkflowExecutionInitiated:
//            decisionsHelper.handleSignalExternalWorkflowExecutionInitiated(event);
//            break;
        case RequestCancelExternalWorkflowExecutionInitiated:
            decisionsHelper.handleRequestCancelExternalWorkflowExecutionInitiated(event);
            break;
        case RequestCancelExternalWorkflowExecutionFailed:
            decisionsHelper.handleRequestCancelExternalWorkflowExecutionFailed(event);
            break;
        case StartChildWorkflowExecutionInitiated:
            decisionsHelper.handleStartChildWorkflowExecutionInitiated(event);
            break;
        case CancelTimerFailed:
            decisionsHelper.handleCancelTimerFailed(event);
        }
    }

    private void eventLoop() throws Throwable {
        if (completed) {
            return;
        }
        try {
            completed = workflowAsyncScope.eventLoop();
        }
        catch (CancellationException e) {
            if (!cancelRequested) {
                failure = e;
            }
            completed = true;
        }
        catch (Throwable e) {
            failure = e;
            completed = true;
        }
    }

    private void completeWorkflow() {
        if (completed && !unhandledDecision) {
            if (failure != null) {
                decisionsHelper.failWorkflowExecution(failure);
            }
            else if (cancelRequested) {
                decisionsHelper.cancelWorkflowExecution();
            }
            else {
                ContinueAsNewWorkflowExecutionParameters continueAsNewOnCompletion = workflowContext.getContinueAsNewOnCompletion();
                if (continueAsNewOnCompletion != null) {
                    decisionsHelper.continueAsNewWorkflowExecution(continueAsNewOnCompletion);
                }
                else {
                    byte[] workflowOutput = workflowAsyncScope.getOutput();
                    decisionsHelper.completeWorkflowExecution(workflowOutput);
                }
            }
        }
    }

    private void handleDecisionTaskStarted(HistoryEvent event) throws Throwable {
    }

    private void handleWorkflowExecutionCancelRequested(HistoryEvent event) throws Throwable {
        workflowContext.setCancelRequested(true);
        workflowAsyncScope.cancel(new CancellationException());
        cancelRequested = true;
    }

    private void handleTimerFired(HistoryEvent event) throws Throwable {
        TimerFiredEventAttributes attributes = event.getTimerFiredEventAttributes();
        String timerId = attributes.getTimerId();
        if (timerId.equals(DecisionsHelper.FORCE_IMMEDIATE_DECISION_TIMER)) {
            return;
        }
        workflowClock.handleTimerFired(event.getEventId(), attributes);
    }

    private void handleTimerStarted(HistoryEvent event) {
        TimerStartedEventAttributes attributes = event.getTimerStartedEventAttributes();
        String timerId = attributes.getTimerId();
        if (timerId.equals(DecisionsHelper.FORCE_IMMEDIATE_DECISION_TIMER)) {
            return;
        }
        decisionsHelper.handleTimerStarted(event);
    }

    private void handleWorkflowExecutionSignaled(HistoryEvent event) throws Throwable {
        assert (event.getEventType().equals(EventType.WorkflowExecutionSignaled.toString()));
        final WorkflowExecutionSignaledEventAttributes signalAttributes = event.getWorkflowExecutionSignaledEventAttributes();
        if (completed) {
           throw new IllegalStateException("Signal received after workflow is closed. TODO: Change signal handling from callback to a queue to fix the issue.");
        }
    }

    private void handleDecisionTaskCompleted(HistoryEvent event) {
        decisionsHelper.handleDecisionCompletion(event.getDecisionTaskCompletedEventAttributes());
    }

    public void decide() throws Exception {
        try {
            long lastNonReplayedEventId = historyHelper.getLastNonReplayEventId();
            // Buffer events until the next DecisionTaskStarted and then process them
            // setting current time to the time of DecisionTaskStarted event
            EventsIterator eventsIterator = historyHelper.getEvents();
            List<HistoryEvent> reordered = null;
            do {
                List<HistoryEvent> decisionStartToCompletionEvents = new ArrayList<HistoryEvent>();
                List<HistoryEvent> decisionCompletionToStartEvents = new ArrayList<HistoryEvent>();
                boolean concurrentToDecision = true;
                int lastDecisionIndex = -1;
                while (eventsIterator.hasNext()) {
                    HistoryEvent event = eventsIterator.next();
                    EventType eventType = event.getEventType();
                    if (eventType == EventType.DecisionTaskCompleted) {
                        decisionsHelper.setWorkflowContextData(event.getDecisionTaskCompletedEventAttributes().getExecutionContext());
                        concurrentToDecision = false;
                    }
                    else if (eventType == EventType.DecisionTaskStarted) {
                        decisionsHelper.handleDecisionTaskStartedEvent();

                        if (!eventsIterator.isNextDecisionTimedOut()) {
                            // Cadence timestamp is in nanoseconds
                            long replayCurrentTimeMilliseconds = event.getTimestamp() / MILLION;
                            workflowClock.setReplayCurrentTimeMilliseconds(replayCurrentTimeMilliseconds);
                            break;
                        }
                    }
                    else if (eventType == EventType.DecisionTaskScheduled || eventType == EventType.DecisionTaskTimedOut) {
                        // skip
                    }
                    else {
                        if (concurrentToDecision) {
                            decisionStartToCompletionEvents.add(event);
                        }
                        else {
                            if (isDecisionEvent(eventType)) {
                                lastDecisionIndex = decisionCompletionToStartEvents.size();
                            }
                            decisionCompletionToStartEvents.add(event);
                        }
                    }
                }
                int size = decisionStartToCompletionEvents.size() + decisionStartToCompletionEvents.size();
                // Reorder events to correspond to the order that decider sees them. 
                // The main difference is that events that were added during decision task execution 
                // should be processed after events that correspond to the decisions. 
                // Otherwise the replay is going to break.
                reordered = new ArrayList<HistoryEvent>(size);
                // First are events that correspond to the previous task decisions
                if (lastDecisionIndex >= 0) {
                    reordered.addAll(decisionCompletionToStartEvents.subList(0, lastDecisionIndex + 1));
                }
                // Second are events that were added during previous task execution
                reordered.addAll(decisionStartToCompletionEvents);
                // The last are events that were added after previous task completion
                if (decisionCompletionToStartEvents.size() > lastDecisionIndex + 1) {
                    reordered.addAll(decisionCompletionToStartEvents.subList(lastDecisionIndex + 1,
                            decisionCompletionToStartEvents.size()));
                }
                for (HistoryEvent event : reordered) {
                    if (event.getEventId() >= lastNonReplayedEventId) {
                        workflowClock.setReplaying(false);
                    }
                    EventType eventType = event.getEventType();
                    processEvent(event, eventType);
                    eventLoop();
                }
                completeWorkflow();

            }
            while (eventsIterator.hasNext());
            if (unhandledDecision) {
                unhandledDecision = false;
                completeWorkflow();
            }
        }
        //TODO (Cadence): Handle Cadence exception gracefully.
//        catch (AmazonServiceException e) {
//            // We don't want to fail workflow on service exceptions like 500 or throttling
//            // Throwing from here drops decision task which is OK as it is rescheduled after its StartToClose timeout.
//            if (e.getErrorType() == ErrorType.Client && !"ThrottlingException".equals(e.getErrorCode())) {
//                if (log.isErrorEnabled()) {
//                    log.error("Failing workflow " + workflowContext.getWorkflowExecution(), e);
//                }
//                decisionsHelper.failWorkflowDueToUnexpectedError(e);
//            }
//            else {
//                throw e;
//            }
//        }
        catch (Throwable e) {
            if (log.isErrorEnabled()) {
                log.error("Failing workflow " + workflowContext.getWorkflowExecution(), e);
            }
            decisionsHelper.failWorkflowDueToUnexpectedError(e);
        }
        finally {
            try {
                decisionsHelper.setWorkflowContextData(workflowAsyncScope.getWorkflowState());
            }
            catch (WorkflowException e) {
                decisionsHelper.setWorkflowContextData(e.getDetails());
            }
            catch (Throwable e) {
                decisionsHelper.setWorkflowContextData(String.valueOf(e.getMessage()).getBytes(TaskPoller.UTF8_CHARSET));
            }
            workflowAsyncScope.close();
        }
    }

    private boolean isDecisionEvent(EventType eventType) {
        switch (eventType) {
        case ActivityTaskScheduled:
        case ActivityTaskCancelRequested:
        case RequestCancelActivityTaskFailed:
        case MarkerRecorded:
        case WorkflowExecutionCompleted:
        case WorkflowExecutionFailed:
        case WorkflowExecutionCanceled:
        case WorkflowExecutionContinuedAsNew:
        case TimerStarted:
        case TimerCanceled:
        case CancelTimerFailed:
//        case SignalExternalWorkflowExecutionInitiated:
//        case SignalExternalWorkflowExecutionFailed:
        case RequestCancelExternalWorkflowExecutionInitiated:
        case RequestCancelExternalWorkflowExecutionFailed:
        case StartChildWorkflowExecutionInitiated:
        case StartChildWorkflowExecutionFailed:
            return true;
        default:
            return false;
        }
    }

    public String getAsynchronousThreadDumpAsString() {
        checkAsynchronousThreadDumpState();
        return workflowAsyncScope.getAsynchronousThreadDump();
    }

    private void checkAsynchronousThreadDumpState() {
        if (workflowAsyncScope == null) {
            throw new IllegalStateException("workflow hasn't started yet");
        }
        if (decisionsHelper.isWorkflowFailed()) {
            throw new IllegalStateException("Cannot get AsynchronousThreadDump of a failed workflow",
                    decisionsHelper.getWorkflowFailureCause());
        }
    }

    public DecisionsHelper getDecisionsHelper() {
        return decisionsHelper;
    }
    
}
