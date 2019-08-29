package com.autodesk.coroutineworker

import kotlin.native.concurrent.isFrozen
import kotlin.test.Test
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
}
