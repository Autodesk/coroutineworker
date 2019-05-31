package com.autodesk.coroutineworker
/**
 * Bridges a platform's callback-based async method to coroutines,
 * ensuring that the coroutine is resumed on a thread appropriate
 * for the platform
 *
 * @param startAsync The lambda that starts the work, calls the passed CompletionLambda lambda
 *                   when complete, and returns a lambda that can be called to cancel the async
 *                   work
 * */
expect suspend fun <T> threadSafeSuspendCallback(startAsync: (CompletionLambda<T>) -> CancellationLambda): T
