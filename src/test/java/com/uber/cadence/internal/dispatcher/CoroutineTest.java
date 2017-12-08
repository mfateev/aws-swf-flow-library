package com.uber.cadence.internal.dispatcher;

import org.junit.Test;

import static org.junit.Assert.*;

public class CoroutineTest {

    private String status;
    private boolean unblock1;
    private boolean unblock2;
    private Throwable failure;

    @Test
    public void testCoroutine() {
        status = "initial";
        Coroutine c = new Coroutine(null, () -> {
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
        c.runUntilBlocked();
        assertEquals("started", status);
        assertFalse(c.isDone());
        c.evaluateInCoroutineContext(reason -> assertEquals("reason1", reason));
        unblock1 = true;
        c.runUntilBlocked();
        assertEquals("after1", status);
        c.evaluateInCoroutineContext(reason -> assertEquals("reason2", reason));
        // Just check that running again doesn't make any progress.
        c.runUntilBlocked();
        assertEquals("after1", status);
        c.evaluateInCoroutineContext(reason -> assertEquals("reason2", reason));
        unblock2 = true;
        c.runUntilBlocked();
        assertEquals("done", status);
        assertTrue(c.isDone());
        assertNull(c.getUnhandledException());
    }

    @Test
    public void testCoroutineFailure() {
        status = "initial";
        Coroutine c = new Coroutine(null, () -> {
            status = "started";
            Coroutine.getContext().yield("reason1",
                    () -> unblock1
            );
            throw new RuntimeException("simulated");
        });
        assertEquals("initial", status);
        c.runUntilBlocked();
        assertEquals("started", status);
        assertFalse(c.isDone());
        c.evaluateInCoroutineContext(reason -> assertEquals("reason1", reason));
        unblock1 = true;
        c.runUntilBlocked();
        assertTrue(c.isDone());
        assertNotNull(c.getUnhandledException());
    }

    @Test
    public void testCoroutineStop() {
        status = "initial";
        Coroutine c = new Coroutine(null, () -> {
            status = "started";
            Coroutine.getContext().yield("reason1",
                    () -> unblock1
            );
            status = "after1";
            try {
                Coroutine.getContext().yield("reason2",
                        () -> unblock2
                );
            } catch (DestroyCoroutineError e) {
                failure = e;
                throw e;
            }
            status = "done";
        });
        assertEquals("initial", status);
        c.runUntilBlocked();
        assertEquals("started", status);
        assertFalse(c.isDone());
        c.evaluateInCoroutineContext(reason -> assertEquals("reason1", reason));
        unblock1 = true;
        c.runUntilBlocked();
        assertEquals("after1", status);
        c.evaluateInCoroutineContext(reason -> assertEquals("reason2", reason));
        c.stop();
        assertTrue(c.isDone());
        assertNull(c.getUnhandledException());
        assertNotNull(failure);
    }
}