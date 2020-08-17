package com.autodesk.coroutineworker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

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
