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
    private List<WorkflowThreadImpl> threads = new LinkedList<>(); // protected by lock
    private List<WorkflowThreadImpl> threadsToAdd = Collections.synchronizedList(new ArrayList<>());
    private final WorkflowClock clock;

    public DeterministicRunnerImpl(Runnable root) {
        this(() -> System.currentTimeMillis(), root);
    }

    public DeterministicRunnerImpl(WorkflowClock clock, Runnable root) {
        this.clock = clock;
        // TODO: thread name
        WorkflowThreadImpl rootWorkflowThreadImpl = new WorkflowThreadImpl(this, "workflow-root", root);
        threads.add(rootWorkflowThreadImpl);
        rootWorkflowThreadImpl.start();
    }

    @Override
    public long runUntilAllBlocked() throws Throwable {
        lock.lock();
        try {
            Throwable unhandledException = null;
            // Keep repeating until at least one of the threads makes progress.
            boolean progress;
            long blockedUntil;
            do {
                threadsToAdd.clear();
                progress = false;
                ListIterator<WorkflowThreadImpl> ci = threads.listIterator();
                blockedUntil = 0;
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
                        if (t > blockedUntil) {
                            blockedUntil = t;
                        }
                    }
                }
                if (unhandledException != null) {
                    close();
                    throw unhandledException;
                }
                threads.addAll(threadsToAdd);
            } while (progress && !threads.isEmpty());
            if (blockedUntil < currentTimeMillis()) {
                blockedUntil = 0;
            }
            return blockedUntil;
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

    public WorkflowThreadImpl newThread(Runnable r) {
        return newThread(r, null);
    }

    public WorkflowThreadImpl newThread(Runnable r, String name) {
        WorkflowThreadImpl result = new WorkflowThreadImpl(this, name, r);
        threadsToAdd.add(result); // This is synchronized collection.
        return result;
    }
}
