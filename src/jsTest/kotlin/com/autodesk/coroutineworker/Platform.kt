package com.autodesk.coroutineworker

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T): dynamic = GlobalScope.promise { block() }

actual fun Any.ensureNeverFrozen() = asDynamic()
