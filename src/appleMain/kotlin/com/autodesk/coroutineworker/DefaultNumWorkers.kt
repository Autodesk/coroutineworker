package com.autodesk.coroutineworker

import kotlinx.cinterop.UnsafeNumber
import platform.Foundation.NSProcessInfo

@OptIn(UnsafeNumber::class)
public actual fun getDefaultNumWorkers(): Int =
    NSProcessInfo.processInfo.processorCount.toInt()
