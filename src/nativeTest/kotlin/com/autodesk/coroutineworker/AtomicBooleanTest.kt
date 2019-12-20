package com.autodesk.coroutineworker

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtomicBooleanTest {

    @Test
    fun test() {
        val falseBool = AtomicBoolean(false)
        assertFalse(falseBool.value)

        val trueBool = AtomicBoolean(true)
        assertTrue(trueBool.value)

        trueBool.apply {
            value = false
            assertFalse(value)
        }

        falseBool.apply {
            value = true
            assertTrue(value)
        }
    }
}
