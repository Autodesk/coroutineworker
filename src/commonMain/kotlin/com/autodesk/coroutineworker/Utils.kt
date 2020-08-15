package com.autodesk.coroutineworker
/**
 * Bridges a platform's callback-based async method to coroutines,
 * ensuring that the coroutine is resumed on a thread appropriate
 * for the platform.
 *
 * [startAsync] is called to start the work. It calls the passed [CompletionLambda]
 * when complete, and returns a [CancellationLambda] that can be called to cancel the
 * async work
 * */
public expect suspend fun <T> threadSafeSuspendCallback(startAsync: (CompletionLambda<T>) -> CancellationLambda): T
