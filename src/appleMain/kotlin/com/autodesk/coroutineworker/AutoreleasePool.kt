package com.autodesk.coroutineworker

internal actual inline fun <R> autoreleasepool(block: () -> R): R = kotlinx.cinterop.autoreleasepool(block)
