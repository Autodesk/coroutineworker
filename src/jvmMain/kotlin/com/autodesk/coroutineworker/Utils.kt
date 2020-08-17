package com.autodesk.coroutineworker

import kotlinx.coroutines.suspendCancellableCoroutine

public actual suspend fun <T> threadSafeSuspendCallback(startAsync: (CompletionLambda<T>) -> CancellationLambda): T {
    return suspendCancellableCoroutine { cont ->
        val cancellable = startAsync {
            if (!cont.isCancelled) {
                cont.resumeWith(it)
            }
        }
        cont.invokeOnCancellation {
            cancellable()
        }
    }
}
