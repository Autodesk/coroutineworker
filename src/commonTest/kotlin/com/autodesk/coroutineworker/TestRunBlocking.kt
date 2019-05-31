package com.autodesk.coroutineworker

import co.touchlab.stately.ensureNeverFrozen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun <T> testRunBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return runBlocking(context) {
        ensureNeverFrozen()
        // fail tests, if they take too long; something is likely hung
        withTimeout(10_000, block)
    }
}
