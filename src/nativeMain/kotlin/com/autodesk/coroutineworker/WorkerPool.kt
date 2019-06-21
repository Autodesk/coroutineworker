package com.autodesk.coroutineworker

import co.touchlab.stately.concurrency.AtomicReference
import co.touchlab.stately.concurrency.Lock
import co.touchlab.stately.concurrency.value
import co.touchlab.stately.concurrency.withLock
import kotlin.native.concurrent.AtomicLong
import kotlin.native.concurrent.Future
import kotlin.native.concurrent.FutureState
import kotlin.native.concurrent.TransferMode
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

    /** Held while cleaning up futures */
    private val futureCleanupLock = Lock()

    /** Futures from previous work, which are periodically cleaned up */
    private val futures = AtomicReference(emptyList<Future<*>>().freeze())

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
        val workerCompleteBlock = {
            nextWorkerLock.withLock {
                worker.numBlocksQueue.decrement()
            }
        }.freeze()
        val future = worker.worker.execute(
            TransferMode.SAFE,
            { Pair(work.freeze(), workerCompleteBlock) }) { (work, complete) ->
            try {
                work()
            } finally {
                complete()
            }
        }
        cleanupFinishedFuturesAndAdd(future)
    }

    private fun cleanupFinishedFuturesAndAdd(futureToAdd: Future<*>) {
        futureCleanupLock.withLock {
            val futuresToKeep = mutableListOf(futureToAdd)
            futuresToKeep.add(futureToAdd)
            for (future in futures.value) {
                if (future.state == FutureState.SCHEDULED) {
                    // still waiting; check back on it later
                    futuresToKeep.add(future)
                    continue
                }
                // we're not keeping this future; consume it to free its internal resourcOes
                try {
                    // consume the future to clean it up
                    future.consume { }
                } catch (_: Throwable) { /* ignore thrown results */
                }
            }
            futures.value = futuresToKeep.freeze()
        }
    }

    init { freeze() }
}
