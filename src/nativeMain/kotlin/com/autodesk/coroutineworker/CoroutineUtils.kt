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

    val futureResult = AtomicReference<Result<T>?>(null).freeze()

    // call the block completion handler; get the cancellation handler and freeze it
    val cancellable = startAsync { result: Result<T> ->
        initRuntimeIfNeeded()
        // freeze the ref and result and go back to the worker thread
        futureResult.value = result.freeze()
    }

    try {
        waitAndDelayForCondition { futureResult.value != null }

        val result = futureResult.value
        // Ensure we don't leak memory: https://github.com/JetBrains/kotlin-native/blob/master/runtime/src/main/kotlin/kotlin/native/concurrent/Atomics.kt#L206
        futureResult.value = null
        if (result == null) {
            throw IllegalStateException("Future should have a result; found null")
        }
        return result.getOrThrow()
    } catch (e: CancellationException) {
        cancellable()
        throw e
    }
}
