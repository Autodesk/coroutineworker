package com.autodesk.coroutineworker

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

    /** Number of blocks queued on this worker */
    val numBlocksQueued = AtomicLong(0)

    companion object {
        @SharedImmutable
        val comparator = compareBy<WeightedWorker>(
            { it.numBlocksQueued.value },
            { it.lastSequence.value }
        )
    }

    init { freeze() }
}

/**
 * A pool of Worker instances, which are used in order of least busy
 * and then least recently used. The pool will have [numWorkers] workers.
 */
internal class WorkerPool(private val numWorkers: Int) {
    /** The available workers */
    private val workers = (0 until numWorkers).map { WeightedWorker(Worker.start()) }

    /** The current sequence, which is incremented each time performWork is called */
    private val currentSequence = AtomicLong(0)

    private fun nextWorker(): WeightedWorker {
        var next: WeightedWorker? = null
        while (next == null) {
            next = workers.minWith(comparator = WeightedWorker.comparator)!!.takeIf {
                val currentValue = it.numBlocksQueued.value
                // try again, if numBlocksQueue was modified
                it.numBlocksQueued.compareAndSet(currentValue, currentValue + 1)
            }
        }
        return next.apply {
            lastSequence.value = currentSequence.addAndGet(1)
        }
    }

    fun performWork(work: () -> Unit) {
        // get the next worker
        val worker = nextWorker()

        // prepare the block to update state, when the worker is finished
        val workerCompleteBlock: () -> Unit = {
            worker.numBlocksQueued.decrement()
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
