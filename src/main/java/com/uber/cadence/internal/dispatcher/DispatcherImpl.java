package com.uber.cadence.internal.dispatcher;

import com.google.common.base.Throwables;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DispatcherImpl implements Dispatcher {

    private final Lock lock = new ReentrantLock();
    private List<Coroutine> coroutines = new LinkedList<>();

    public DispatcherImpl(Runnable root) {
        Coroutine rootCoroutine = new Coroutine(root);
        coroutines.add(rootCoroutine);
    }

    @Override
    public void runUntilAllBlocked() throws Throwable {
        Throwable unhandledException = null;
        // Keep repeating until at least one of the coroutines makes progress.
        boolean progress;
        do {
            progress = false;
            ListIterator<Coroutine> ci = coroutines.listIterator();
            while(ci.hasNext()){
                Coroutine c = ci.next();
                progress = c.runUntilBlocked() || progress;
                if(c.isDone()){
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
        } while (progress && !coroutines.isEmpty());
    }

    @Override
    public boolean isDone() {
        return coroutines.isEmpty();
    }

    @Override
    public void close() {
        for(Coroutine c: coroutines) {
            c.stop();
        }
        coroutines.clear();
    }

    @Override
    public String stackTrace() {
        return null;
    }

    Lock getLock() {
        return lock;
    }
}
