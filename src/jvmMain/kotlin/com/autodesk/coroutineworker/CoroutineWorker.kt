package com.autodesk.coroutineworker

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

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
            return CoroutineWorker().also {
                it.launch(block = block)
            }
        }

        actual suspend fun <T> performAndWait(block: suspend CoroutineScope.() -> T): T {
            return CoroutineWorker().run {
                val channel = Channel<Result<T>>()
                launch {
                    val result = runCatching {
                        block()
                    }
                    channel.send(result)
                }
                val result = channel.receive()
                channel.close()
                result.getOrThrow()
            }
        }
    }
}
