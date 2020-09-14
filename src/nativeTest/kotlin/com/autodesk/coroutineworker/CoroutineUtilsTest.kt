package com.autodesk.coroutineworker

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlin.native.concurrent.isFrozen
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoroutineUtilsTest {

    @Test
    fun `threadSafeSuspendCallback completion is frozen`() {
        testRunBlocking {
            var called = false
            threadSafeSuspendCallback<Unit> { completion ->
                called = true
                assertTrue(completion.isFrozen)
                completion(Result.success(Unit))
                return@threadSafeSuspendCallback { Unit }
            }
            assertTrue(called)
        }
    }

    @Test
    fun `withContext with special dispatcher uses special thread`() {
        testRunBlocking {
            CoroutineWorker.withContext(IODispatcher) {
                // should return true because it's on the special thread
                assertTrue(BackgroundCoroutineWorkQueueExecutor.shouldPerformIoWorkInline())
            }
        }
    }

    @Test
    fun `withContext without special dispatcher does not use special thread`() {
        testRunBlocking {
            CoroutineWorker.withContext(Dispatchers.Default) {
                // should return false because it's not on the special thread
                assertFalse(BackgroundCoroutineWorkQueueExecutor.shouldPerformIoWorkInline())
            }
        }
    }

    @Test
    fun `multiple withContext on IODispatcher works without timeout`() {
        testRunBlocking {
            val ran = atomic(false)
            CoroutineWorker.withContext(IODispatcher) {
                // should return false because it's not on the special thread
                CoroutineWorker.withContext(IODispatcher) {
                    ran.value = true
                }
            }
            assertTrue(ran.value)
        }
    }
}
