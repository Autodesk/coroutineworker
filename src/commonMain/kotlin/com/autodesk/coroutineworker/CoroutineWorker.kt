package com.autodesk.coroutineworker

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Encapsulates performing background work. On JVM, this use coroutines outright.
 * In native, this uses Worker threads and manages its mutability/concurrency issues.
 */
expect class CoroutineWorker {

    /** Cancels the underlying Job */
    fun cancel()

    /**
     * Cancel the underlying job/worker and waits for it
     * to receive its cancellation message or complete
     * */
    suspend fun cancelAndJoin()

    companion object {

        /**
         * Enqueues the background work to run and returns a reference to the worker, which can be cancelled
         *
         * @param block The work block to execute in the background
         */
        fun execute(block: suspend CoroutineScope.() -> Unit): CoroutineWorker

        /**
         * Performs block in another CoroutineContext and waits for it to complete.
         * This is similar to withContext in kotlinx.coroutines, with some caveats:
         *
         * - Native: we cannot use kotlinx.coroutines.withContext because
         *           the global Dispatchers do not work properly. Therefore,
         *           the context argument is ignored, and the block is always
         *           run on some other Worker in the Worker pool. This is the
         *           most similar to switching contexts on JVM.
         *
         * - JVM: This has the same behavior as calling withContext in the JVM
         *
         * In the future when kotlinx.coroutines has support for native multi-threaded
         * coroutines, this will make it easy to transition to kotlinx.coroutines.withContext
         *
         * @param jvmContext The context to switch to for JVM targets. For example,
         *                   you might want to use Dispatchers.IO, as you would normally
         *                   when doing blocking IO (for example) with coroutines.
         * @param block The work block to execute
         */
        suspend fun <T> withContext(jvmContext: CoroutineContext, block: suspend CoroutineScope.() -> T): T
    }
}

@Deprecated(
    "Use withContext instead, which allows JVM to take advantage of specifying a dispatcher, such as Dispatchers.IO",
    ReplaceWith("CoroutineWorker.withContext(Dispatchers.Default, block)", "kotlinx.coroutines.Dispatchers")
)
suspend fun <T> CoroutineWorker.Companion.performAndWait(block: suspend CoroutineScope.() -> T) = withContext(Dispatchers.Default, block)
