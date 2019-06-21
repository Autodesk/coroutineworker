package com.autodesk.coroutineworker

import co.touchlab.stately.collections.frozenLinkedList
import co.touchlab.stately.concurrency.AtomicReference
import co.touchlab.stately.concurrency.Lock
import co.touchlab.stately.concurrency.value
import co.touchlab.stately.concurrency.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.SharedImmutable
import kotlin.native.concurrent.freeze

/**
 * Holds the hook for handling background uncaught exceptions
 */
@SharedImmutable
private val UNHANDLE_EXCEPTION_HOOK = AtomicReference<((Throwable) -> Unit)?>(null)

/**
 * Interface for a work item that can be queued to run in
 * a BackgroundCoroutineWorkQueueExecutor
 */
internal interface CoroutineWorkItem {
    /** The block to execute via a Worker */
    val work: suspend CoroutineScope.() -> Unit
}

/**
 * An executor that runs blocks in a CoroutineScope on a background
 * Worker (via a WorkerPool)
 *
 * @param numWorkers The number of workers needed in the pool
 */
internal class BackgroundCoroutineWorkQueueExecutor<WorkItem : CoroutineWorkItem>(private val numWorkers: Int) {

    /**
     * Locks access during enqueue and dequeue operations
     * to ensure counts can be consistently read across threads
     * */
    private val queueLock = Lock()

    /** The queue of WorkItems to execute */
    private val queue = frozenLinkedList<WorkItem>()

    /** The pool, on which blocks are executed */
    private val pool = WorkerPool(numWorkers)

    /** The number of workers actively processing blocks */
    private val _numActiveWorkers = AtomicInt(0)

    /** Getter for _numActiveWorkers; useful for preventing leakage in tests */
    val numActiveWorkers: Int
        get() = _numActiveWorkers.value

    /** @return the next work item to process, if any */
    private fun dequeueWork(): WorkItem? = queueLock.withLock {
        if (queue.isEmpty()) {
            // worker is going to become inactive
            _numActiveWorkers.decrement()
            null
        } else {
            queue.removeAt(0)
        }
    }

    /** Queues an item to be executed */
    fun enqueueWork(item: WorkItem) = queueLock.withLock {
        queue.add(item)
        // start a worker if we have more workers to start
        val activeWorkerCount = _numActiveWorkers.value
        if (activeWorkerCount < numWorkers) {
            pool.performWork {
                runBlocking {
                    processWorkItems()
                }
            }
            _numActiveWorkers.increment()
        }
    }

    suspend fun CoroutineScope.processWorkItems() {
        val workItem = dequeueWork() ?: return

        // Execute the work in a job that can be cancelled
        try {
            async {
                workItem.work(this)
            }.await()
        } catch (_: CancellationException) {
            // ignore cancellation
        } catch (e: Throwable) {
            val handler = UNHANDLE_EXCEPTION_HOOK.value
            if (handler != null) {
                handler(e)
            } else {
                throw e
            }
        }

        // execute a coroutine to attempt to process the next work item, if possible
        launch { processWorkItems() }
    }

    init { freeze() }

    companion object {
        /**
         * Sets the handler for uncaught exceptions encountered in work items
         */
        internal fun setUnhandledExceptionHook(handler: (Throwable) -> Unit) {
            UNHANDLE_EXCEPTION_HOOK.value = handler.freeze()
        }
    }
}

/**
 * Set handler for exceptions that would
 * be bubbled up to the underlying Worker
 *
 * @param handler the lambda called with the thrown exception
 */
fun setUnhandledExceptionHook(handler: (Throwable) -> Unit) {
    BackgroundCoroutineWorkQueueExecutor.setUnhandledExceptionHook(handler)
}
