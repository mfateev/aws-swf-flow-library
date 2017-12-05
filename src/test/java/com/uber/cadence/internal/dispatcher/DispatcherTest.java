package com.uber.cadence.internal.dispatcher;

import org.junit.Test;

import static org.junit.Assert.*;

public class DispatcherTest {

    private String status;
    private boolean unblock1;
    private boolean unblock2;
    private Throwable failure;

    @Test
    public void testRootCoroutine() throws Throwable {
        status = "initial";
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
        status = "initial";
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
//    public void testCoroutineStop() {
//        status = "initial";
//        Coroutine c = new Coroutine(() -> {
//            status = "started";
//            Coroutine.getContext().yield("reason1",
//                    () -> unblock1
//            );
//            status = "after1";
//            try {
//                Coroutine.getContext().yield("reason2",
//                        () -> unblock2
//                );
//            } catch (InterruptedCoroutineError e) {
//                failure = e;
//                throw e;
//            }
//            status = "done";
//        });
//        assertEquals("initial", status);
//        c.runUntilBlocked();
//        assertEquals("started", status);
//        assertFalse(c.isDone());
//        c.evaluateInCoroutineContext(reason -> assertEquals("reason1", reason));
//        unblock1 = true;
//        c.runUntilBlocked();
//        assertEquals("after1", status);
//        c.evaluateInCoroutineContext(reason -> assertEquals("reason2", reason));
//        c.stop();
//        assertTrue(c.isDone());
//        assertNull(c.getUnhandledException());
//        assertNotNull(failure);
//    }
}