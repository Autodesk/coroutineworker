package com.autodesk.coroutineworker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Encapsulates performing background work. On JVM, this use coroutines outright.
 * In native, this uses Worker threads and manages its mutability/concurrency issues.
 */
expect class CoroutineWorker internal constructor() {

    /** Cancels the underlying Job */
    fun cancel()

    /**
     * Cancel the underlying job/worker and waits for it
     * to receive its cancellation message or complete
     * */
    suspend fun cancelAndJoin()

    companion object {

        /**
         * Enqueues the background work [block] to run and returns a reference to the worker, which can be cancelled
         */
        fun execute(block: suspend CoroutineScope.() -> Unit): CoroutineWorker

        /**
         * Performs [block] in another [CoroutineContext] ([jvmContext]) and waits for it to complete.
         * This is similar to [kotlinx.coroutines.withContext] with some caveats:
         *
         * - Native: we cannot use [kotlinx.coroutines.withContext] because
         *           the global Dispatchers do not work properly. Therefore,
         *           the context argument is ignored, and the block is always
         *           run on some other Worker in the Worker pool. This is the
         *           most similar to switching contexts on JVM.
         *
         * - JVM: This has the same behavior as calling [kotlinx.coroutines.withContext] in the JVM
         */
        suspend fun <T> withContext(jvmContext: CoroutineContext, block: suspend CoroutineScope.() -> T): T
    }
}

@Deprecated(
    "Use withContext instead, which allows JVM to take advantage of specifying a dispatcher, such as Dispatchers.IO",
    ReplaceWith("CoroutineWorker.withContext(Dispatchers.Default, block)", "kotlinx.coroutines.Dispatchers")
)
suspend fun <T> CoroutineWorker.Companion.performAndWait(block: suspend CoroutineScope.() -> T) = withContext(Dispatchers.Default, block)
