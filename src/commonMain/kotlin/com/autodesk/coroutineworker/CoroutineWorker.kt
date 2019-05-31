package com.autodesk.coroutineworker

import kotlinx.coroutines.CoroutineScope

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
         * Enqueues the background work to run, suspends while the work is in progress,
         * and returns the result of that background work back to the calling thread
         *
         * @param block The work block to execute in the background
         */
        suspend fun <T> performAndWait(block: suspend CoroutineScope.() -> T): T
    }
}
