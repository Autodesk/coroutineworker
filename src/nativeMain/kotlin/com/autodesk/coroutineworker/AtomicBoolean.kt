package com.autodesk.coroutineworker

import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.freeze

internal class AtomicBoolean(value: Boolean) {

    private val _value: AtomicInt

    init {
        _value = AtomicInt(if (value) 1 else 0)
        freeze()
    }

    var value: Boolean
        get() = _value.value == 1
        set(value) {
            _value.value = if (value) 1 else 0
        }
}
