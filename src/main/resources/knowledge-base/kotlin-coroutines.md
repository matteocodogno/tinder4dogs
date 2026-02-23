# Coroutines in Kotlin

Coroutines are Kotlin's solution for asynchronous programming.

## CoroutineScope
Every coroutine must be launched within a scope.
In Spring Boot use CoroutineScope(Dispatchers.IO) for I/O operations.

## suspend functions
Suspend functions can be paused without blocking a thread.
They can only be called from another suspend function or a coroutine.

## Flow
Kotlin Flow is the coroutine equivalent of RxJava Observable.
Use flow { } to create asynchronous data streams.
