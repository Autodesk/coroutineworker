package com.autodesk.coroutineworker

import co.touchlab.stately.concurrency.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CoroutineWorkerTest {

    @Test
    fun `nested executes run with coroutines`() {
        val ran = AtomicBoolean(false)
        testRunBlocking {
            CoroutineWorker.execute {
                CoroutineWorker.execute {
                    launch {
                        ran.value = true
                    }
                }
            }
            busyWaitForCondition { ran.value }
        }
    }

    @Test
    fun `performAndWait across threads`() {
        testRunBlocking {
            val ran = CoroutineWorker.withContext(Dispatchers.Default) {
                CoroutineWorker.withContext(Dispatchers.Default) {
                    async { true }.await()
                }
            }
            assertTrue(ran)
        }
    }

    @Test
    fun `job cancellation across threads`() {
        val started = AtomicBoolean(false)
        val `continue` = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        testRunBlocking {
            val job = CoroutineWorker.execute {
                started.value = true
                busyWaitForCondition { `continue`.value }
                // one extra delay to give coroutine chance to resume
                // with cancellation exception
                var once = false
                busyWaitForCondition { once.also { once = true } }
                finished.value = true
            }
            busyWaitForCondition { started.value }
            job.cancel()
            `continue`.value = true
            delay(20)
            assertFalse(finished.value)
        }
    }

    @Test
    fun `cancelAndJoin waits for jobs to cancel`() {
        testRunBlocking {
            val innerJobRunning = AtomicBoolean(false)
            val innerCancelled = AtomicBoolean(false)
            val job = CoroutineWorker.execute {
                launch {
                    var jobNotifiedStarted = false
                    while (true) {
                        try {
                            delay(20)
                        } catch (e: CancellationException) {
                            innerCancelled.value = true
                            throw e
                        }
                        if (!jobNotifiedStarted) {
                            innerJobRunning.value = true
                            jobNotifiedStarted = true
                        }
                    }
                }
            }
            busyWaitForCondition { innerJobRunning.value }
            job.cancelAndJoin()
            assertTrue(innerCancelled.value)
        }
    }

    @Test
    fun `can return null values from performAndWait`() {
        testRunBlocking {
            val value: Unit? = CoroutineWorker.withContext(Dispatchers.Default) {
                delay(20)
                null
            }
            assertNull(value)
        }
    }
}

private suspend fun busyWaitForCondition(cond: () -> Boolean) {
    while (!cond()) {
        delay(20)
    }
}
