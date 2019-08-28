package com.autodesk.coroutineworker

import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Test

class CoroutineWorkerJVMTest {

    @Test
    fun `test withContext changes contexts`() {
        testRunBlocking {
            var called = false
            val job = launch {
                val context = coroutineContext
                CoroutineWorker.withContext(Dispatchers.IO) {
                    assertNotSame(context, coroutineContext)
                    called = true
                }
            }
            job.join()
            assertTrue(called)
        }
    }
}
