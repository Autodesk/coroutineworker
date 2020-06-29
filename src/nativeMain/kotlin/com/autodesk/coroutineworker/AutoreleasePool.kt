package com.autodesk.coroutineworker

internal expect inline fun <R> autoreleasepool(block: () -> R): R
