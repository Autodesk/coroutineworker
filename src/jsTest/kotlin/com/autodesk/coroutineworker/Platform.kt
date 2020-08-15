package com.autodesk.coroutineworker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.coroutines.CoroutineContext

actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T): dynamic = GlobalScope.promise { block() }

actual fun Any.ensureNeverFrozen() = Unit
