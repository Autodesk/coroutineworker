# CoroutineWorker

[![Build Status](https://dev.azure.com/autodeskoss/coroutineworker/_apis/build/status/Autodesk.coroutineworker?branchName=master)](https://dev.azure.com/autodeskoss/coroutineworker/_build/latest?definitionId=1&branchName=master)

![Maven Central](https://img.shields.io/maven-central/v/com.autodesk/coroutineworker.svg)

## Specs

- Supported on Native and JVM
- Kotlin 1.3.41

## About

This library helps unify and support coroutine background thread usage in common code for Kotlin/Native, until [kotlinx.coroutines has support for native, multi-threaded coroutines](https://github.com/Kotlin/kotlinx.coroutines/issues/462). This library doesn't support every use case (but could support more with your help!), but it does support some useful ones:

### Spawning Asynchronous Work
```kotlin
val worker = CoroutineWorker.execute {
  // - In here, `this` is a `CoroutineScope`
  // - Run suspend functions, call launch, etc.
  // - This code runs in a thread pool
}

// Tells the worker to cancel (uses standard coroutine cancellation)
worker.cancel()

// Tells the worker to cancel; it suspends until cancellation is finished
worker.cancelAndJoin()
```

### Waiting on Asynchronous Work to Complete

```kotlin
val result = CoroutineWorker.performAndWait {
  // This is similar to execute, but it returns
  // the result of the work at the end of this lambda
  1
}
print(result) // prints 1
```

### Lower-Level Helpers

- Use `threadSafeSuspendCallback` to bridge callback-style async work out to your platform back into your library as a `suspend fun` (see example usages in the library).

### Important Notes

- Closures passed to `execute` and `performAndWait` are automatically frozen, so be careful about what your closure captures (e.g. implicit references to `this`)!
- The result value from `performAndWait` is also frozen.
