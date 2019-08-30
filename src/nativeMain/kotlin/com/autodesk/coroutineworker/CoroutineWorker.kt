package com.autodesk.coroutineworker

import co.touchlab.stately.collections.frozenHashSet
import co.touchlab.stately.concurrency.AtomicBoolean
import co.touchlab.stately.concurrency.Lock
import co.touchlab.stately.concurrency.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.SharedImmutable
import kotlin.native.concurrent.freeze
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Identity key used to identify CoroutineWorker information across threads */
private typealias CoroutineWorkerIdentifier = Any

/**
 * When there's an entry in this set, this means we are attempting to cancel
 * the worker associated with that CoroutineWorkerIdentifier
 * */
@SharedImmutable
private val CANCELLED_WORKERS = frozenHashSet<CoroutineWorkerIdentifier>()

actual class CoroutineWorker {

    /** Object whose identity is used to access associated data in the global thread-safe maps */
    private val identifier = CoroutineWorkerIdentifier()

    /**
     * True, if the job finished without being cancelled; false, otherwise.
     * The work could either still be in progress or cancelled.
     * */
    private val finishedWithoutCancelling = AtomicBoolean(false)

    /**
     * Ensures consistency when setting completion state
     * */
    private val completionLock = Lock()

    actual fun cancel() {
        cancelIfRunning()
    }

    private fun cancelIfRunning(): Boolean {
        return completionLock.withLock {
            if (finishedWithoutCancelling.value) {
                return@withLock false
            }
            // signal that this job should cancel
            CANCELLED_WORKERS.add(identifier)
            true
        }
    }

    actual suspend fun cancelAndJoin() {
        if (!cancelIfRunning()) {
            return
        }
        // repeated check and wait for cancellation to complete
        waitAndDelayForCondition { !CANCELLED_WORKERS.contains(identifier) }
    }

    private fun completionHandler(): (Boolean) -> Unit {
        val lock = completionLock
        val identifier = identifier
        val finishedWithoutCancelling = finishedWithoutCancelling
        return { cancelled ->
            lock.withLock {
                CANCELLED_WORKERS.remove(identifier)
                finishedWithoutCancelling.value = !cancelled
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
                    it.identifier,
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
            val identifier: CoroutineWorkerIdentifier,
            val notifyCompletion: (Boolean) -> Unit,
            val block: suspend CoroutineScope.() -> Unit
        ) : CoroutineWorkItem {
            override val work: suspend CoroutineScope.() -> Unit
            init {
                work = {
                    var completed = false
                    var cancelled = false
                    try {
                        repeatedlyCheckForCancellation(this.coroutineContext, identifier) { completed }
                        // inside of a new CoroutineScope, so that child jobs are cancelled
                        coroutineScope(block)
                    } catch (_: CancellationException) {
                        cancelled = true
                    } finally {
                        completed = true
                        notifyCompletion(cancelled)
                    }
                }
            }

            // repeatedly checks if the scope has been cancelled and cancels the scope if needed; bails out, when the job completes
            private fun CoroutineScope.repeatedlyCheckForCancellation(context: CoroutineContext, scope: CoroutineWorkerIdentifier, completedGetter: () -> Boolean) {
                launch {
                    waitAndDelayForCondition {
                        val cancelled = CANCELLED_WORKERS.contains(scope)
                        if (cancelled) {
                            context.cancel()
                        }
                        completedGetter() || cancelled
                    }
                }
            }
        }
    }

    init { freeze() }
}
