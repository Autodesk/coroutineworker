package com.autodesk.coroutineworker

import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.freeze

/**
 * Boolean wrapper around AtomicInt
 */
internal class AtomicBoolean(value: Boolean) {

    /**
     * Backing store for the Boolean value
     */
    private val _value: AtomicInt

    init {
        _value = AtomicInt(if (value) 1 else 0)
        freeze()
    }

    /**
     * Public API for the wrapped Boolean, which represents the Boolean value
     */
    var value: Boolean
        get() = _value.value == 1
        set(value) {
            _value.value = if (value) 1 else 0
        }
}
