package com.autodesk.coroutineworker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.freeze

public actual class CoroutineWorker internal actual constructor() {

    /**
     * True, if the job was cancelled; false, otherwise.
     */
    private val state = CoroutineWorkerState()

    public actual fun cancel() {
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

    public actual suspend fun cancelAndJoin() {
        if (!cancelIfRunning()) {
            return
        }
        // repeated check and wait for the job to complete
        waitAndDelayForCondition { state.completed }
    }

    public actual companion object {

        /**
         * Gets the number of active workers running in the underlying WorkerPool.
         * This is useful when testing, to ensure you don't leave workers running
         * across tests.
         */
        public val numActiveWorkers: Int
            get() = executor.numActiveWorkers

        /** The executor used for all BackgroundJobs */
        private val executor = BackgroundCoroutineWorkQueueExecutor<WorkItem>(4)

        public actual fun execute(block: suspend CoroutineScope.() -> Unit): CoroutineWorker {
            return executeInternal(false, block)
        }

        public actual suspend fun <T> withContext(jvmContext: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
            val isIoWork = jvmContext == IODispatcher
            if (isIoWork && BackgroundCoroutineWorkQueueExecutor.shouldPerformIoWorkInline()) {
                return coroutineScope(block)
            }
            return threadSafeSuspendCallback<T> { completion ->
                val job = executeInternal(isIoWork) {
                    val result = runCatching {
                        block()
                    }
                    completion(result)
                }
                return@threadSafeSuspendCallback { job.cancel() }
            }
        }

        private fun executeInternal(isIoWork: Boolean, block: suspend CoroutineScope.() -> Unit): CoroutineWorker {
            return CoroutineWorker().also {
                val state = it.state
                executor.enqueueWork(
                    WorkItem(
                        { state.cancelled },
                        { state.completed = true },
                        block
                    ),
                    isIoWork
                )
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
                        // inside a new CoroutineScope, so that child jobs are cancelled
                        coroutineScope {
                            block()
                        }
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
        do {
            val old = value.value
            val new = if (set) {
                old or bit
            } else {
                old and bit.inv()
            }
        } while (!value.compareAndSet(old, new))
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
