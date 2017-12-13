package com.uber.cadence.internal.dispatcher;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DeterministicRunnerTest {

    private String status;
    private boolean unblock1;
    private boolean unblock2;
    private Throwable failure;

    @Before
    public void setUp() {
        unblock1 = false;
        unblock2 = false;
        failure = null;
        status = "initial";
    }

    @Test
    public void testRootThread() throws Throwable {
        DeterministicRunner d = new DeterministicRunnerImpl(() -> {
            status = "started";
            try {
                WorkflowThread.yield("reason1",
                        () -> unblock1
                );
                status = "after1";
                WorkflowThread.yield("reason2",
                        () -> unblock2
                );
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            status = "done";
        });
        assertEquals("initial", status);
        d.runUntilAllBlocked();
        assertEquals("started", status);
        assertFalse(d.isDone());
        unblock1 = true;
        d.runUntilAllBlocked();
        assertEquals("after1", status);
        // Just check that running again doesn't make any progress.
        d.runUntilAllBlocked();
        assertEquals("after1", status);
        unblock2 = true;
        d.runUntilAllBlocked();
        assertEquals("done", status);
        assertTrue(d.isDone());
    }

    @Test
    public void testRootThreadFailure() throws Throwable {
        DeterministicRunner d = new DeterministicRunnerImpl(() -> {
            status = "started";
            try {
                WorkflowThread.yield("reason1",
                        () -> unblock1
                );
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            throw new RuntimeException("simulated");
        });
        assertEquals("initial", status);
        d.runUntilAllBlocked();
        assertEquals("started", status);
        assertFalse(d.isDone());
        unblock1 = true;
        try {
            d.runUntilAllBlocked();
            fail("exception expected");
        } catch (Throwable throwable) {
        }
        assertTrue(d.isDone());
    }

//    @Test
//    public void testDispatcherStop() throws Throwable {
//        DeterministicRunner d = new DeterministicRunnerImpl(() -> {
//            status = "started";
//            WorkflowThreadImpl.getContext().yield("reason1",
//                    () -> unblock1
//            );
//            status = "after1";
//            try {
//                WorkflowThreadImpl.getContext().yield("reason2",
//                        () -> unblock2
//                );
//            } catch (DestroyWorkflowThreadError e) {
//                failure = e;
//                throw e;
//            }
//            status = "done";
//        });
//        assertEquals("initial", status);
//        d.runUntilAllBlocked();
//        assertEquals("started", status);
//        assertFalse(d.isDone());
//        unblock1 = true;
//        d.runUntilAllBlocked();
//        assertEquals("after1", status);
//        d.close();
//        assertTrue(d.isDone());
//        assertNotNull(failure);
//    }
}