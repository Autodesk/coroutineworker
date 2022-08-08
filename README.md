# This project is DEPRECATED

With the release of Kotlin 1.7.20-Beta (https://blog.jetbrains.com/kotlin/2022/08/kotlin-1-7-20-beta/) and the new 
Memory Model for Kotlin Native becoming the default, there's no longer a need for this library. Version 0.9.0 helps 
transition over to the new memory model without having to make any changes related to CoroutineWorker code. The latest 
version of CoroutineWorker uses the same implementation for all platforms.

# CoroutineWorker

[![Build Status](https://github.com/autodesk/coroutineworker/workflows/build/badge.svg)](https://github.com/autodesk/coroutineworker/actions?query=workflow%3Abuild)

![Maven Central](https://img.shields.io/maven-central/v/com.autodesk/coroutineworker.svg)

## Specs

- Supported on Native, JVM, and JS (legacy and IR) (feel free to contribute adding more targets)
- Kotlin 1.7.10

## Gradle

To use in your multiplatform project, update your common dependencies in your gradle configuration:

```groovy
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation "com.autodesk:coroutineworker:0.9.0"
            }
        }
    }
}
```

CoroutineWorker uses gradle module metadata. We recommend adding the following to your settings.gradle to take advantage of that (not necessary for Gradle 6+):

```groovy
enableFeaturePreview('GRADLE_METADATA')
```

## About

CoroutineWorker helps support multi-threaded coroutine usage in common code that works in Kotlin/Native and on JVM until [kotlinx.coroutines has full support for native, multi-threaded coroutines](https://github.com/Kotlin/kotlinx.coroutines/issues/462).

## Projects Using this on your Devices

- [PlanGrid iOS & Android](https://plangrid.com)

## Sample Usage

### Spawning Asynchronous Work

Use `execute` to start background work from common code:

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

From a coroutine context (i.e. somewhere you can call a `suspend fun`), use `withContext` to kick off work to another thread. It will non-blocking/suspend wait for the cross-thread work to complete:

```kotlin
suspend fun doWork() {
  val result = CoroutineWorker.withContext {
    // This is similar to execute, but it returns
    // the result of the work at the end of this lambda
    1
  }
  print(result) // prints 1
}
```

This is like using `withContext` on JVM to switch coroutine contexts. You can also properly pass a dispatcher, which will be used on JVM: `withContext(Dispatchers.IO) { … }`. The idea here is that this will be easy to migrate when we do get multi-threaded coroutine support in Kotlin/Native.

### Waiting on Asynchronous Callback-based Work

Use `threadSafeSuspendCallback` to bridge callback-style async work into your code as a `suspend fun`:

```kotlin

suspend fun performNetworkFetch() {
  val result = threadSafeSuspendCallback { completion ->
    // example: fetch network data that isn't coroutine-compatible
    fetchNetworkData { networkResult ->
      // notify that async work is complete
      completion(networkResult)
    }
  }

  // result is now available here
}
```

## Sample Project

In the sample directory, there is a sample project that demonstrates adding CoroutineWorker to an iOS + JVM library. We just used the sample library from IntelliJ's template for a "Mobile Shared Library." In the sample is a function called `performWork` (common code) that takes a completion lambda and demonstrates `CoroutineWorker.execute`. In tests, we use K/N concurrency helpers from `kotlinx.atomicfu` to demonstrate capturing a result across threads in K/N and executing this function.

## CoroutineWorker Prefers Frozen State

Object detachment (i.e. [transferring object ownership](https://kotlinlang.org/docs/native-concurrency.html#object-transfer-and-freezing) from one thread to another) is relatively difficult to achieve (outside of simple scenarios) compared to working with objects that are frozen and immutable. Because of this, CoroutineWorker prefers taking the frozen, immutable route:

- Lambdas passed to CoroutineWorker are automatically frozen when they are going to be passed across threads.
- The result value from `withContext` is also frozen.

### Tips for Working with Frozen State

- Be careful about what your frozen lambdas capture; those objects will be frozen too. Especially, watch for implicit references to `this`.
- Call [`ensureNeverFrozen()`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.native.concurrent/ensure-never-frozen.html) on objects that you don't expect to ever be frozen.

## IO-Bound Work

In the JVM world, you typically write code like this for managing IO-bound work with coroutines:

```
withContext(Dispatchers.IO) {
    // IO writes
}
```
Similar behavior is supported in CoroutineWorker for Kotlin/Native via the `IODispatcher`. To use it in common code, make an `expect val Dispatchers.IO: CoroutineDispatcher` that returns `IODispatcher` for Kotlin/Native and `Dispatchers.IO` for JVM, and pass that to `CoroutineWorker.withContext` when performing IO-bound worker.
