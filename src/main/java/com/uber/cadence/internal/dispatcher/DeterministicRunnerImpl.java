package com.uber.cadence.internal.dispatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class DeterministicRunnerImpl implements DeterministicRunner {

    private final Lock lock = new ReentrantLock();
    private final SyncDecisionContext decisionContext;
    private List<WorkflowThreadImpl> threads = new LinkedList<>(); // protected by lock
    private List<WorkflowThreadImpl> threadsToAdd = Collections.synchronizedList(new ArrayList<>());
    private final WorkflowClock clock;
    /**
     * Time at which any thread that runs under dispatcher can make progress.
     * For example when {@link WorkflowThread#sleep(long)} expires.
     * 0 means no blocked threads.
     */
    private long nextWakeUpTime;

    public DeterministicRunnerImpl(Runnable root) {
        this(null, System::currentTimeMillis, root);
    }

    public DeterministicRunnerImpl(SyncDecisionContext decisionContext, WorkflowClock clock, Runnable root) {
        this.decisionContext = decisionContext;
        this.clock = clock;
        // TODO: thread name
        WorkflowThreadImpl rootWorkflowThreadImpl = new WorkflowThreadImpl(this, "workflow-root", root);
        threads.add(rootWorkflowThreadImpl);
        rootWorkflowThreadImpl.start();
    }

    public SyncDecisionContext getDecisionContext() {
        return decisionContext;
    }

    @Override
    public void runUntilAllBlocked() throws Throwable {
        lock.lock();
        try {
            Throwable unhandledException = null;
            // Keep repeating until at least one of the threads makes progress.
            boolean progress;
            do {
                threadsToAdd.clear();
                progress = false;
                ListIterator<WorkflowThreadImpl> ci = threads.listIterator();
                nextWakeUpTime = 0;
                while (ci.hasNext()) {
                    WorkflowThreadImpl c = ci.next();
                    progress = c.runUntilBlocked() || progress;
                    if (c.isDone()) {
                        ci.remove();
                        if (c.getUnhandledException() != null) {
                            unhandledException = c.getUnhandledException();
                            break;
                        }
                    } else {
                        long t = c.getBlockedUntil();
                        if (t > nextWakeUpTime) {
                            nextWakeUpTime = t;
                        }
                    }
                }
                if (unhandledException != null) {
                    close();
                    throw unhandledException;
                }
                threads.addAll(threadsToAdd);
            } while (progress && !threads.isEmpty());
            if (nextWakeUpTime < currentTimeMillis()) {
                nextWakeUpTime = 0;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isDone() {
        lock.lock();
        try {
            return threads.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            for (WorkflowThreadImpl c : threads) {
                c.stop();
            }
            threads.clear();
        } finally {
            lock.unlock();
        }

    }

    @Override
    public String stackTrace() {
        return null;
    }

    @Override
    public long currentTimeMillis() {
        return clock.currentTimeMillis();
    }

    @Override
    public long getNextWakeUpTime() {
        return nextWakeUpTime;
    }

    public WorkflowThreadImpl newThread(Runnable r) {
        return newThread(r, null);
    }

    public WorkflowThreadImpl newThread(Runnable r, String name) {
        WorkflowThreadImpl result = new WorkflowThreadImpl(this, name, r);
        threadsToAdd.add(result); // This is synchronized collection.
        return result;
    }
}
