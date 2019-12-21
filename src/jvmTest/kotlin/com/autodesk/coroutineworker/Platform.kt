package com.autodesk.coroutineworker

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope

actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T) = kotlinx.coroutines.runBlocking(context, block)
actual fun Any.ensureNeverFrozen() = Unit
