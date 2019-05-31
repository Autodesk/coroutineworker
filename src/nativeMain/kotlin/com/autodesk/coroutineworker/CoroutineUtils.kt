package com.autodesk.coroutineworker

import co.touchlab.stately.concurrency.AtomicBoolean
import co.touchlab.stately.concurrency.AtomicReference
import co.touchlab.stately.concurrency.Lock
import co.touchlab.stately.concurrency.value
import co.touchlab.stately.concurrency.withLock
import kotlinx.cinterop.StableRef
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.native.concurrent.freeze

internal suspend fun waitAndDelayForCondition(condition: () -> Boolean) {
    do {
        delay(50)
    } while (!condition())
}

actual suspend fun <T> threadSafeSuspendCallback(startAsync: (CompletionLambda<T>) -> CancellationLambda): T {
    return coroutineScope {
        suspendCancellableCoroutine<T> { cont ->

            // create the random continuation key for retrieving thread-specific state
            val contRef = ThreadSafeContinuationRef(cont)

            // create the frozen completion handler
            val completion = getCompletion(contRef).freeze()

            // call the block completion handler; get the cancellation handler and freeze it
            val cancellable = startAsync(completion)

            cont.invokeOnCancellation {
                cancellable()
                contRef.dispose()
            }
        }
    }
}

private fun <T> CoroutineScope.getCompletion(contRef: ThreadSafeContinuationRef<T>): CompletionLambda<T> {
    val futureResult = AtomicReference<Result<T>?>(null).freeze()

    // Assume we were cancelled, if the continuation has already been consumed
    val isCancelled: () -> Boolean = {
        contRef.get() == null
    }
    // execute a coroutine on this thread that repeatedly checks for the result
    launch {
        waitAndDelayForCondition {
            (futureResult.value != null || isCancelled())
        }
        // cancelled; bail
        if (isCancelled()) {
            return@launch
        }
        val result = futureResult.value
        // Ensure we don't leak memory: https://github.com/JetBrains/kotlin-native/blob/master/runtime/src/main/kotlin/kotlin/native/concurrent/Atomics.kt#L206
        futureResult.value = null
        if (result == null) {
            throw IllegalStateException("Future should have a result; found null")
        }
        resumeContinuation(contRef, result)
    }
    return { result: Result<T> ->
        initRuntimeIfNeeded()
        // freeze the ref and result and go back to the worker thread
        futureResult.value = result.freeze()
    }
}

private fun <T> resumeContinuation(contRef: ThreadSafeContinuationRef<T>, result: Result<T>) {
    try {
        contRef.get()?.takeUnless { it.isCancelled }?.resumeWith(result)
    } finally {
        contRef.dispose()
    }
}

/**
 * Holds onto a continuation while it's passed back and forth across threads
 */
private class ThreadSafeContinuationRef<T> private constructor(private val ref: StableRef<CancellableContinuation<T>>) {

    constructor(continuation: CancellableContinuation<T>) : this(StableRef.create(continuation))

    private val lock = Lock()
    private val disposed = AtomicBoolean(false)

    fun get(): CancellableContinuation<T>? {
        return lock.withLock {
            ref.takeUnless { disposed.value }?.get()
        }
    }

    fun dispose() {
        if (disposed.value) return
        lock.withLock {
            ref.takeUnless { disposed.value }?.dispose()
            disposed.value = true
        }
    }

    init { freeze() }
}
