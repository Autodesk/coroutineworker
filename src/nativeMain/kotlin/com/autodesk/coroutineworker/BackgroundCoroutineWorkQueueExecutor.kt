package com.autodesk.coroutineworker

import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.AtomicReference
import kotlin.native.concurrent.SharedImmutable
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.native.concurrent.ensureNeverFrozen
import kotlin.native.concurrent.freeze
import kotlinx.cinterop.StableRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Holds the hook for handling background uncaught exceptions
 */
@SharedImmutable
private val UNHANDLED_EXCEPTION_HOOK = AtomicReference<((Throwable) -> Unit)?>(null)

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
 * @property numWorkers The number of workers needed in the pool
 */
internal class BackgroundCoroutineWorkQueueExecutor<WorkItem : CoroutineWorkItem>(private val numWorkers: Int) {

    /**
     * The pool, on which blocks are executed
     */
    private val pool = WorkerPool(numWorkers)

    /**
     * Protects access to the queue on a single thread
     */
    private val queueThread = Worker.start()

    /**
     * The wrapped (allow freezing and mutable access on single thread) queue of WorkItems
     */
    private val wrappedQueue: StableRef<WorkQueue<WorkItem>> by lazy {
        StableRef.create(WorkQueue<WorkItem>()).freeze()
    }

    /**
     * The wrapped queue of WorkItems
     */
    private val queue: WorkQueue<WorkItem>
        get() = wrappedQueue.get()

    /**
     * The number of workers actively processing blocks
     */
    private val _numActiveWorkers = AtomicInt(0)

    /**
     * Getter for _numActiveWorkers; useful for preventing leakage in tests
     */
    val numActiveWorkers: Int
        get() = _numActiveWorkers.value

    /**
     * Returns the next work item to process, if any
     */
    private fun dequeueWork(): WorkItem? = queueThread.execute(TransferMode.SAFE, { this }) {
        with(it) {
            queue.dequeue().also {
                if (it == null) {
                    // worker is going to become inactive
                    _numActiveWorkers.decrement()
                }
            }
        }
    }.result

    /**
     * Queues an item to be executed
     */
    fun enqueueWork(item: WorkItem) = queueThread.executeAfter(operation = {
        queue.enqueue(item)
        // start a worker if we have more workers to start
        val activeWorkerCount = _numActiveWorkers.value
        if (activeWorkerCount < numWorkers) {
            pool.performWork {
                runBlocking {
                    // error if we accidentally freeze coroutine internals
                    this.ensureNeverFrozen()
                    processWorkItems()
                }
            }
            _numActiveWorkers.increment()
        }
    }.freeze())

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
            val handler = UNHANDLED_EXCEPTION_HOOK.value
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
            UNHANDLED_EXCEPTION_HOOK.value = handler.freeze()
        }
    }
}

/**
 * Set [handler] for exceptions that would
 * be bubbled up to the underlying Worker
 */
fun setUnhandledExceptionHook(handler: (Throwable) -> Unit) {
    BackgroundCoroutineWorkQueueExecutor.setUnhandledExceptionHook(handler)
}

/**
 * Queue of workItems
 */
private class WorkQueue<WorkItem : Any> {

    /**
     * Node in the queue of work items
     */
    private class Node<WorkItem>(val workItem: WorkItem) {
        /**
         * Pointer to the next node
         */
        var next: Node<WorkItem>? = null
    }

    /**
     * The head of the queue of WorkItems to execute
     * */
    private var queueHead: Node<WorkItem>? = null

    /**
     * The tail of the queue of WorkItems to execute
     */
    private var queueTail: Node<WorkItem>? = null

    /**
     * Enqueues a WorkItem
     */
    fun enqueue(item: WorkItem) {
        val curTail = queueTail
        queueTail = Node(item)
        curTail?.next = queueTail
        if (queueHead == null) {
            queueHead = queueTail
        }
    }

    /**
     * De-queues a WorkItem
     */
    fun dequeue(): WorkItem? {
        val curHead = queueHead ?: return null
        queueHead = curHead.next
        if (queueHead == null) {
            queueTail = null
        }
        return curHead.workItem
    }
}
