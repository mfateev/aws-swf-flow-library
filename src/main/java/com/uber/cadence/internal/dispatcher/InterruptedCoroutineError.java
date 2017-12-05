package com.uber.cadence.internal.dispatcher;

/**
 * Used to interrupt coroutine execution. Assumption is that none of the code
 * that coroutine executes catches {@link Error}.
 */
public class InterruptedCoroutineError extends Error {
}
