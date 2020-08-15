package com.autodesk.coroutineworker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

public actual class CoroutineWorker internal actual constructor() : CoroutineScope {

    private val job = Job()

    /**
     * The context of this scope. See [CoroutineScope.coroutineContext]
     */
    override val coroutineContext: CoroutineContext = job

    public actual fun cancel() {
        job.cancel()
    }

    public actual suspend fun cancelAndJoin() {
        job.cancelAndJoin()
    }

    public actual companion object {

        public actual fun execute(block: suspend CoroutineScope.() -> Unit): CoroutineWorker {
            return CoroutineWorker().also {
                it.launch(block = block)
            }
        }

        public actual suspend fun <T> withContext(jvmContext: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
            return kotlinx.coroutines.withContext(jvmContext) {
                block()
            }
        }
    }
}
