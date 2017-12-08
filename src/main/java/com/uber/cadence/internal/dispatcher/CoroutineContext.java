package com.uber.cadence.internal.dispatcher;

import java.util.function.Supplier;

/**
 * Exposes operations to code that runs under coroutine thread
 */
interface CoroutineContext {

    /**
     *
     * @param reason human readable reason for blockage (like waiting on a lock)
     * @param unblockCondition yield call returns only when passed function returns true.
     *                         Condition can be evaluated multiple times and should be thread safe.
     * @throws DestroyCoroutineError when coroutine was destroyRequested. User code must not interfere
     * with this error.
     */
    void yield(String reason, Supplier<Boolean> unblockCondition) throws DestroyCoroutineError;

    boolean destroyRequested();

    DispatcherImpl getDispatcher();
}
