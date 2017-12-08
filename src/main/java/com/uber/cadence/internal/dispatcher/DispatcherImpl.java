package com.uber.cadence.internal.dispatcher;

import com.google.common.base.Throwables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DispatcherImpl implements Dispatcher {

    private final Lock lock = new ReentrantLock();
    private List<Coroutine> coroutines = new LinkedList<>(); // protected by lock
    private List<Coroutine> coroutinesToAdd = Collections.synchronizedList(new ArrayList<>());

    public DispatcherImpl(Runnable root) {
        Coroutine rootCoroutine = new Coroutine(this, root);
        coroutines.add(rootCoroutine);
        rootCoroutine.start();
    }

    @Override
    public void runUntilAllBlocked() throws Throwable {
        lock.lock();
        try {
            Throwable unhandledException = null;
            // Keep repeating until at least one of the coroutines makes progress.
            boolean progress;
            do {
                coroutinesToAdd.clear();
                progress = false;
                ListIterator<Coroutine> ci = coroutines.listIterator();
                while (ci.hasNext()) {
                    Coroutine c = ci.next();
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
                coroutines.addAll(coroutinesToAdd);
            } while (progress && !coroutines.isEmpty());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isDone() {
        lock.lock();
        try {
            return coroutines.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            for (Coroutine c : coroutines) {
                c.stop();
            }
            coroutines.clear();
        } finally {
            lock.unlock();
        }

    }

    @Override
    public String stackTrace() {
        return null;
    }

    public Coroutine newCoroutine(Runnable r) {
        Coroutine result = new Coroutine(this, r);
        coroutinesToAdd.add(result); // This is synchronized collection.
        return result;
    }
}
