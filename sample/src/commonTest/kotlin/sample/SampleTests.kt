package sample

import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.test.assertTrue

class SampleTests {
    @Test
    fun testMe() {
        assertTrue(Sample().checkMe() > 0)
    }

    @Test
    fun testCoroutineWorkerCommonWork() {
        // setup an atomic ref to capture the result
        val result = atomic<Int?>(null)
        // perform the background work
        performWork {
            // grab the result
            result.value = it
        }
        // busy wait for the result
        while (result.value == null) {}
        println("4 + 4 = ${result.value!!}")
    }
}
