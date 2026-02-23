# Error Handling in Kotlin

Kotlin offers several strategies for handling errors:

## try-catch-finally
The classic approach for both checked and unchecked exceptions.
The finally block always executes, regardless of the exception.

## Result<T>
Result<T> encapsulates either a success value or a failure.
Use runCatching { } to convert exceptions into Result.
Supports map, fold, onSuccess, and onFailure for functional handling.

## Sealed Classes for domain errors
Defining a sealed class Error with specific subclasses enables
type-safe, exhaustive domain-error handling.
