package com.autodesk.coroutineworker

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

public class CoroutineWorker : CoroutineScope {
    private val job = Job()

    /**
     * The context of this scope. See [CoroutineScope.coroutineContext]
     */
    override val coroutineContext: CoroutineContext = job

    public fun cancel() {
        job.cancel()
    }

    public suspend fun cancelAndJoin() {
        job.cancelAndJoin()
    }

    public companion object {

        public fun execute(block: suspend CoroutineScope.() -> Unit): CoroutineWorker {
            return CoroutineWorker().also {
                it.launch(block = block)
            }
        }

        public suspend fun <T> withContext(jvmContext: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
            return kotlinx.coroutines.withContext(jvmContext) {
                block()
            }
        }
    }
}
