# CoroutineWorker

[![Build Status](https://dev.azure.com/autodeskoss/coroutineworker/_apis/build/status/Autodesk.coroutineworker?branchName=master)](https://dev.azure.com/autodeskoss/coroutineworker/_build/latest?definitionId=1&branchName=master)

![Maven Central](https://img.shields.io/maven-central/v/com.autodesk/coroutineworker.svg)

## Specs

- Supported on Native and JVM (feel free to contribute adding more targets)
- Kotlin 1.3.60

## Gradle

To use in your multiplatform project, update your common dependencies in your gradle configuration:

```groovy
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation "com.autodesk:coroutineworker:0.4.0"
            }
        }
    }
}
```

CoroutineWorker uses gradle module metadata. We recommend adding the following to your settings.gradle to take advantage of that:

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
      completion(networkRestul)
    }
  }

  // result is now available here
}
```

## Sample Project

In the sample directory, there is a sample project that demonstrates adding CoroutineWorker to an iOS + JVM library. We just used the sample library from IntelliJ's template for a "Mobile Shared Library." In the sample is a function called `performWork` (common code) that takes a completion lambda and demonstrates `CoroutineWorker.execute`. In tests, we use K/N concurrency helpers from `co.touchlab.stately` to demonstrate capturing a result across threads in K/N and executing this function.

## CoroutineWorker Prefers Frozen State

Object detachment (i.e. [transferring object ownership](https://github.com/JetBrains/kotlin-native/blob/master/CONCURRENCY.md#object-transfer-and-freezing) from one thread to another) is relatively difficult to achieve (outside of simple scenarios) compared to working with objects that are frozen and immutable. Because of this, CoroutineWorker prefers taking the frozen, immutable route:

- Lambdas passed to CoroutineWorker are automatically frozen when they are going to be passed across threads.
- The result value from `withContext` is also frozen.

### Tips for Working with Frozen State

- Be careful about what your frozen lambdas capture; those objects will be frozen too. Especially, watch for implicit references to `this`.
- Call [`ensureNeverFrozen()`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.native.concurrent/ensure-never-frozen.html) on objects that you don't expect to ever be frozen.
