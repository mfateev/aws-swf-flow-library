package com.uber.cadence.internal.dispatcher;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DispatcherTest {

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
    public void testRootCoroutine() throws Throwable {
        System.out.println("testRootCoroutine");
        Dispatcher d = new DispatcherImpl(() -> {
            status = "started";
            Coroutine.getContext().yield("reason1",
                    () -> unblock1
            );
            status = "after1";
            Coroutine.getContext().yield("reason2",
                    () -> unblock2
            );
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
    public void testRootCoroutineFailure() throws Throwable {
        System.out.println("testRootCoroutineFailure");

        Dispatcher d = new DispatcherImpl(() -> {
            status = "started";
            Coroutine.getContext().yield("reason1",
                    () -> unblock1
            );
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
//        Dispatcher d = new DispatcherImpl(() -> {
//            status = "started";
//            Coroutine.getContext().yield("reason1",
//                    () -> unblock1
//            );
//            status = "after1";
//            try {
//                Coroutine.getContext().yield("reason2",
//                        () -> unblock2
//                );
//            } catch (DestroyCoroutineError e) {
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