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

import com.uber.cadence.EventType;
import com.uber.cadence.HistoryEvent;
import com.uber.cadence.PollForDecisionTaskResponse;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.services.simpleworkflow.flow.common.WorkflowExecutionUtils;

class HistoryHelper {

    private static final Log historyLog = LogFactory.getLog(HistoryHelper.class.getName() + ".history");

    class EventsIterator implements Iterator<HistoryEvent> {

        private final DecisionTaskWithHistoryIterator decisionTaskWithHistoryIterator;

        private Iterator<HistoryEvent> events;

        private Queue<HistoryEvent> bufferedEvents = new LinkedList<>();

        public EventsIterator(DecisionTaskWithHistoryIterator decisionTaskWithHistoryIterator) {
            this.decisionTaskWithHistoryIterator = decisionTaskWithHistoryIterator;
            this.events = decisionTaskWithHistoryIterator.getHistory();
        }

        @Override
        public boolean hasNext() {
            return !bufferedEvents.isEmpty() || events.hasNext();
        }

        @Override
        public HistoryEvent next() {
            if (bufferedEvents.isEmpty()) {
                return events.next();
            }
            return bufferedEvents.poll();
        }

        public PollForDecisionTaskResponse getDecisionTask() {
            return decisionTaskWithHistoryIterator.getDecisionTask();
        }

        public boolean isNextDecisionTimedOut() {
            while (events.hasNext()) {
                HistoryEvent event = events.next();
                bufferedEvents.add(event);
                EventType eventType = event.getEventType();
                if (eventType.equals(EventType.DecisionTaskTimedOut)) {
                    return true;
                }
                else if (eventType.equals(EventType.DecisionTaskCompleted)) {
                    return false;
                }
            }
            return false;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private final EventsIterator events;

    public HistoryHelper(DecisionTaskWithHistoryIterator decisionTasks) {
        this.events = new EventsIterator(decisionTasks);
    }

    public EventsIterator getEvents() {
        return events;
    }

    public String toString() {
        return WorkflowExecutionUtils.prettyPrintHistory(events.getDecisionTask().getHistory().getEvents().iterator(), true);
    }

    public PollForDecisionTaskResponse getDecisionTask() {
        return events.getDecisionTask();
    }

    public long getLastNonReplayEventId() {
        Long result = getDecisionTask().getPreviousStartedEventId();
        if (result == null) {
            return 0;
        }
        return result;
    }
}
