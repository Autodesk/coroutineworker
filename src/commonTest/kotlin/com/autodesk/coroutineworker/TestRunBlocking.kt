package com.autodesk.coroutineworker

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeout

fun <T> testRunBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return runBlocking(context) {
        ensureNeverFrozen()
        // fail tests, if they take too long; something is likely hung
        withTimeout(10_000, block)
    }
}
