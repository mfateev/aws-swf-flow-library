package com.uber.cadence.internal.dispatcher;

public interface WorkflowThread {

    void start();

    void join();

    void join(long millis);

    void sleep(long millis) throws InterruptedException;

    void interrupt();

    public static boolean interrupted() {
        return false;
    }

    boolean isInterrupted();

    boolean isAlive();

    void setName(String name);

    String getName();

    long getId();

    Thread.State getState();

}
