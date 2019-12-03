package sample

import com.autodesk.coroutineworker.CoroutineWorker

expect class Sample() {
    fun checkMe(): Int
}

expect object Platform {
    fun name(): String
}

fun hello(): String = "Hello from ${Platform.name()}"

fun performWork(completion: (Int) -> Unit) {
    CoroutineWorker.execute {
        val result = 4 + 4
        completion(result)
    }
}
