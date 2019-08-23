package com.autodesk.coroutineworker

import co.touchlab.stately.concurrency.Lock
import co.touchlab.stately.concurrency.withLock
import kotlin.native.concurrent.AtomicLong
import kotlin.native.concurrent.SharedImmutable
import kotlin.native.concurrent.Worker
import kotlin.native.concurrent.freeze

/**
 * Keeps track of last time (via a sequence number) the worker
 * was used, and the number of blocks queued for the worker
 */
private class WeightedWorker(
    val worker: Worker
) {
    /** Set to the pool's current sequence, each time it's used */
    val lastSequence = AtomicLong(0)

    /** Set to the pool's current sequence, each time it's used */
    val numBlocksQueue = AtomicLong(0)

    companion object {
        @SharedImmutable
        val comparator = compareBy<WeightedWorker>(
            { it.numBlocksQueue.value },
            { it.lastSequence.value }
        )
    }

    init { freeze() }
}

/**
 * A pool of Worker instances, which are used in order of least busy
 * and then least recently used.
 *
 * @param numWorkers the number of Worker instances to keep in the bool
 */
internal class WorkerPool(private val numWorkers: Int) {
    /** The available workers */
    private val workers = (0 until numWorkers).map { WeightedWorker(Worker.start()) }

    /** The current sequence, which is incremented each time performWork is called */
    private val currentSequence = AtomicLong(0)

    /** Ensures consistent state when updating WeightedWorker and WorkerPool state */
    private val nextWorkerLock = Lock()

    private fun nextWorker(): WeightedWorker = nextWorkerLock.withLock {
        workers.minWith(comparator = WeightedWorker.comparator)!!.apply {
            numBlocksQueue.increment()
            lastSequence.value = currentSequence.addAndGet(1)
        }
    }

    fun performWork(work: () -> Unit) {
        // get the next worker
        val worker = nextWorker()

        // prepare the block to update state, when the worker is finished
        val workerCompleteBlock: () -> Unit = {
            nextWorkerLock.withLock {
                worker.numBlocksQueue.decrement()
            }
        }
        val workerOperation: () -> Unit = {
            try {
                work()
            } finally {
                workerCompleteBlock()
            }
        }.freeze()
        worker.worker.executeAfter(operation = workerOperation)
    }

    init { freeze() }
}
