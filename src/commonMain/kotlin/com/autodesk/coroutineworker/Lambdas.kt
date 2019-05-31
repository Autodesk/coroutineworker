package com.autodesk.coroutineworker

/** A lambda that is called when some work is complete, with the result */
internal typealias CompletionLambda<T> = (result: Result<T>) -> Unit

/** An empty lambda that is called to cancel an ongoing async work */
internal typealias CancellationLambda = () -> Unit
