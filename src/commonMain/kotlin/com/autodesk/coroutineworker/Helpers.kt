package com.autodesk.coroutineworker

/**
 * A convenience wrapper for threadSafeSuspendCallback, for when
 * you don't intend to resume the continuation with an exception
 * */
public suspend fun <T> threadSafeSuspendCallbackWithValue(startAsync: ((T) -> Unit) -> CancellationLambda): T {
    return threadSafeSuspendCallback { completion ->
        startAsync {
            completion(Result.success(it))
        }
    }
}
