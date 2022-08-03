package com.autodesk.coroutineworker

import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [startAsync] is called to start the work. It calls the passed [CompletionLambda]
 * when complete, and returns a [CancellationLambda] that can be called to cancel the
 * async work
 * */
public suspend fun <T> threadSafeSuspendCallback(startAsync: (CompletionLambda<T>) -> CancellationLambda): T {
    return suspendCancellableCoroutine { cont ->
        val cancellable = startAsync {
            if (cont.isActive) {
                cont.resumeWith(it)
            }
        }
        cont.invokeOnCancellation {
            cancellable()
        }
    }
}