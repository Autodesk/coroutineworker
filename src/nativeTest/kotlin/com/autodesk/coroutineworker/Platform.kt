package com.autodesk.coroutineworker

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.ensureNeverFrozen

actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T) = kotlinx.coroutines.runBlocking(context, block)
actual fun Any.ensureNeverFrozen() = ensureNeverFrozen()
