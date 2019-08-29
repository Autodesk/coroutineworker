package com.autodesk.coroutineworker

import co.touchlab.stately.concurrency.value
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.freeze
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal suspend fun waitAndDelayForCondition(condition: () -> Boolean) {
    do {
        delay(50)
    } while (!condition())
}

actual suspend fun <T> threadSafeSuspendCallback(startAsync: (CompletionLambda<T>) -> CancellationLambda): T {

    // this will contain the future result of the async work
    val futureResult = AtomicReference<Result<T>?>(null).freeze()

    // create a frozen completion handler for the async work
    val completion = { result: Result<T> ->
        initRuntimeIfNeeded()
        // store the result in the AtomicReference, which
        // signals that the work is complete
        futureResult.value = result.freeze()
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
        cancellable()
        throw e
    }
}
