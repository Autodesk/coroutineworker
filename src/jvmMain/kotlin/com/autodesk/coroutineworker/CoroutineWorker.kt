package com.autodesk.coroutineworker

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual class CoroutineWorker : CoroutineScope {

    private val job = Job()

    override val coroutineContext: CoroutineContext = job

    actual fun cancel() {
        job.cancel()
    }

    actual suspend fun cancelAndJoin() {
        job.cancelAndJoin()
    }

    actual companion object {

        actual fun execute(block: suspend CoroutineScope.() -> Unit): CoroutineWorker {
            return CoroutineWorker().apply {
                launch(block = block)
            }
        }

        actual suspend fun <T> performAndWait(newCoroutineContext: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
            return CoroutineWorker().run {
                withContext(newCoroutineContext, block)
            }
        }
    }
}
