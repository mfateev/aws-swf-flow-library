package com.uber.cadence.internal.dispatcher;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DispatcherImpl implements Dispatcher {

    private final Lock lock = new ReentrantLock();

    @Override
    public void executeUntilAllBlocked() {

    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public void close() {

    }

    @Override
    public String stackTrace() {
        return null;
    }

    public Lock getLock() {
        return lock;
    }
}
