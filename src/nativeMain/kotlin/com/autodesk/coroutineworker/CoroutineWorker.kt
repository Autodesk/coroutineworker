package com.autodesk.coroutineworker

import co.touchlab.stately.concurrency.Lock
import co.touchlab.stately.concurrency.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.freeze
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

actual class CoroutineWorker {

    /**
     * True, if the job was cancelled; false, otherwise.
     * */
    private val cancelled = AtomicBoolean(false)

    /**
     * True, if the job finished; false, otherwise.
     * */
    private val completed = AtomicBoolean(false)

    /**
     * Ensures consistency when setting completion state
     * */
    private val completionLock = Lock()

    actual fun cancel() {
        cancelIfRunning()
    }

    private fun cancelIfRunning(): Boolean {
        return completionLock.withLock {
            if (completed.value) {
                return@withLock false
            }
            // signal that this job should cancel
            cancelled.value = true
            true
        }
    }

    actual suspend fun cancelAndJoin() {
        if (!cancelIfRunning()) {
            return
        }
        // repeated check and wait for the job to complete
        waitAndDelayForCondition { completed.value }
    }

    private fun completionHandler(): () -> Unit {
        val lock = completionLock
        val completed = completed
        return {
            lock.withLock {
                completed.value = true
            }
        }
    }

    actual companion object {

        /**
         * Gets the number of active workers running in the underlying WorkerPool.
         * This is useful when testing, to ensure you don't leave workers running
         * across tests.
         */
        val numActiveWorkers: Int
            get() = executor.numActiveWorkers

        /** The executor used for all BackgroundJobs */
        private val executor = BackgroundCoroutineWorkQueueExecutor<WorkItem>(4)

        actual fun execute(block: suspend CoroutineScope.() -> Unit): CoroutineWorker {
            return CoroutineWorker().also {
                executor.enqueueWork(WorkItem(
                    it.cancelled,
                    it.completionHandler(),
                    block
                ))
            }
        }

        actual suspend fun <T> withContext(jvmContext: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
            return threadSafeSuspendCallback<T> { completion ->
                val job = execute {
                    val result = runCatching {
                        block()
                    }
                    completion(result)
                }
                return@threadSafeSuspendCallback { job.cancel() }
            }
        }

        /** CoroutineWorker's CoroutineWorkItem class that listens for cancellation */
        private class WorkItem(
            val cancelled: AtomicBoolean,
            val notifyCompletion: () -> Unit,
            val block: suspend CoroutineScope.() -> Unit
        ) : CoroutineWorkItem {
            override val work: suspend CoroutineScope.() -> Unit
            init {
                work = {
                    var completed = false
                    try {
                        repeatedlyCheckForCancellation(this.coroutineContext, cancelled) { completed }
                        // inside of a new CoroutineScope, so that child jobs are cancelled
                        coroutineScope(block)
                    } finally {
                        completed = true
                        notifyCompletion()
                    }
                }
            }

            // repeatedly checks if the scope has been cancelled and cancels the scope if needed; bails out, when the job completes
            private fun CoroutineScope.repeatedlyCheckForCancellation(context: CoroutineContext, cancelled: AtomicBoolean, completedGetter: () -> Boolean) {
                launch {
                    waitAndDelayForCondition {
                        val cancelledValue = cancelled.value
                        if (cancelledValue) {
                            context.cancel()
                        }
                        completedGetter() || cancelledValue
                    }
                }
            }
        }
    }

    init { freeze() }
}
