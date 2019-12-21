package com.autodesk.coroutineworker

import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.freeze
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

actual class CoroutineWorker {

    /**
     * True, if the job was cancelled; false, otherwise.
     */
    private val state = CoroutineWorkerState()

    actual fun cancel() {
        cancelIfRunning()
    }

    private fun cancelIfRunning(): Boolean {
        if (state.completed) {
            return false
        }
        // signal that this job should cancel
        state.cancelled = true
        return true
    }

    actual suspend fun cancelAndJoin() {
        if (!cancelIfRunning()) {
            return
        }
        // repeated check and wait for the job to complete
        waitAndDelayForCondition { state.completed }
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
                val state = it.state
                executor.enqueueWork(WorkItem(
                    { state.cancelled },
                    { state.completed = true },
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
            val cancelled: () -> Boolean,
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
            private fun CoroutineScope.repeatedlyCheckForCancellation(context: CoroutineContext, cancelled: () -> Boolean, completedGetter: () -> Boolean) {
                launch {
                    waitAndDelayForCondition {
                        val cancelledValue = cancelled()
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

private class CoroutineWorkerState {

    /**
     * The backing store for the state
     */
    private val value = AtomicInt(0)

    /**
     * True, if the job was cancelled; false, otherwise.
     */
    var cancelled: Boolean
        get() = isSet(cancelledBit)
        set(value) = updateValue(cancelledBit, value)

    /**
     * True, if the job finished; false, otherwise.
     */
    var completed: Boolean
        get() = isSet(completedBit)
        set(value) = updateValue(completedBit, value)

    /**
     * Updates the value with the bit, setting or un-setting it
     */
    private fun updateValue(bit: Int, set: Boolean) {
        value.value = if (set) {
            value.value or bit
        } else {
            value.value and bit.inv()
        }
    }

    /**
     * Returns whether or not the bit is set
     */
    private fun isSet(bit: Int) = (value.value and bit) == bit

    companion object {
        /**
         * Cancelled bit
         */
        private const val cancelledBit = 1

        /**
         * Completed bit
         */
        private const val completedBit = 2
    }

    init { freeze() }
}
