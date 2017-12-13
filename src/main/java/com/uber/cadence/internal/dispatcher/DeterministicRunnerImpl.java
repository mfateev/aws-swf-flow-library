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

    public DeterministicRunnerImpl(Runnable root) {
        WorkflowThreadImpl rootWorkflowThreadImpl = new WorkflowThreadImpl(this, root);
        threads.add(rootWorkflowThreadImpl);
        rootWorkflowThreadImpl.start();
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
                while (ci.hasNext()) {
                    WorkflowThreadImpl c = ci.next();
                    progress = c.runUntilBlocked() || progress;
                    if (c.isDone()) {
                        ci.remove();
                        if (c.getUnhandledException() != null) {
                            unhandledException = c.getUnhandledException();
                            break;
                        }
                    }
                }
                if (unhandledException != null) {
                    close();
                    throw unhandledException;
                }
                threads.addAll(threadsToAdd);
            } while (progress && !threads.isEmpty());
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

    public WorkflowThreadImpl newThread(Runnable r) {
        WorkflowThreadImpl result = new WorkflowThreadImpl(this, r);
        threadsToAdd.add(result); // This is synchronized collection.
        return result;
    }
}
