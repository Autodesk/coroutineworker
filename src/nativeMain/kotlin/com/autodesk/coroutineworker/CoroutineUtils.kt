package com.autodesk.coroutineworker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze

internal suspend fun waitAndDelayForCondition(condition: () -> Boolean) {
    do {
        delay(50)
    } while (!condition())
}

@OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
public actual suspend fun <T> threadSafeSuspendCallback(startAsync: (CompletionLambda<T>) -> CancellationLambda): T {
    check(coroutineContext[ContinuationInterceptor] is Delay) {
        """threadSafeSuspendCallback only works for CoroutineDispatchers that implement Delay.
            |Implement Delay for your dispatcher or use runBlocking.
        """.trimMargin()
    }

    // this will contain the future result of the async work
    val futureResult = AtomicReference<Result<T>?>(null).freeze()

    // keep track of cancelled state, so that we
    // can avoid updating the future when cancelled
    val isCancelled = AtomicInt(0)

    // create a frozen completion handler for the async work
    val completion = { result: Result<T> ->
        initRuntimeIfNeeded()
        if (isCancelled.value == 0) {
            // store the result in the AtomicReference, which
            // signals that the work is complete
            futureResult.value = result.freeze()
        }
    }.freeze()

    // start the async work and pass it a completion handler
    // it returns a closure to call if we get cancelled
    val cancellable = startAsync(completion)

    try {
        // wait for the result to appear, which signals that the
        // work on the other thread is done
        waitAndDelayForCondition { futureResult.value != null }

        val result = futureResult.value
        // Ensure we don't leak memory: https://github.com/JetBrains/kotlin-native/blob/master/runtime/src/main/kotlin/kotlin/native/concurrent/Atomics.kt#L206
        futureResult.value = null
        if (result == null) {
            throw IllegalStateException("Future should have a result; found null")
        }
        return result.getOrThrow()
    } catch (e: CancellationException) {
        // we were cancelled. cancel the work we
        // were waiting on too
        isCancelled.value = 1
        cancellable()
        throw e
    }
}

/**
 * Symbolic dispatcher used to trigger IO-bound-thread-like behavior on native
 */
public object IODispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        throw UnsupportedOperationException(
            "This dispatcher is symbolic and should not be used to run anything."
        )
    }
}
