package com.autodesk.coroutineworker

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CoroutineWorkerTest {

    @Test
    fun `nested executes run with coroutines`() {
        val ran = atomic(false)
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
    fun `withContext across threads`() {
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
        val started = atomic(false)
        val cancelled = atomic(false)
        testRunBlocking {
            val job = CoroutineWorker.execute {
                started.value = true
                try {
                    busyWaitForCondition { false }
                } catch (_: CancellationException) {
                    cancelled.value = true
                }
            }
            busyWaitForCondition { started.value }
            job.cancel()
            busyWaitForCondition { cancelled.value }
        }
    }

    @Test
    fun `cancelAndJoin waits for jobs to cancel`() {
        testRunBlocking {
            val innerJobRunning = atomic(false)
            val innerCancelled = atomic(false)
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
    fun `can return null values from withContext`() {
        testRunBlocking {
            val value: Unit? = CoroutineWorker.withContext(Dispatchers.Default) {
                delay(20)
                null
            }
            assertNull(value)
        }
    }

    @Test
    fun `cancellation works across withContext boundary`() {
        testRunBlocking {
            val pwRunning = atomic(false)
            val pwCancelled = atomic(false)
            val job = CoroutineWorker.execute {
                CoroutineWorker.withContext(Dispatchers.Default) {
                    var jobNotifiedStarted = false
                    while (true) {
                        try {
                            delay(20)
                        } catch (e: CancellationException) {
                            pwCancelled.value = true
                            throw e
                        }
                        if (!jobNotifiedStarted) {
                            pwRunning.value = true
                            jobNotifiedStarted = true
                        }
                    }
                }
            }
            busyWaitForCondition { pwRunning.value }
            job.cancelAndJoin()
            busyWaitForCondition { pwCancelled.value }
        }
    }
}

private suspend fun busyWaitForCondition(cond: () -> Boolean) {
    while (!cond()) {
        delay(20)
    }
}
