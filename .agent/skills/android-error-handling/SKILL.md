---
name: android-error-handling
description: |
  Generic Result wrapper, error types, and extension helpers for Android/KMP - Result<T, E>, DataError, EmptyResult, map, onSuccess, onFailure. Use this skill whenever defining error types, creating a Result wrapper, handling success/failure flows, mapping errors, or working with typed errors anywhere in the app (not just data layer — also validation, auth, domain logic). Trigger on phrases like "Result wrapper", "error handling", "DataError", "onSuccess", "onFailure", "EmptyResult", "map result", "error type", "validation error", or "typed errors".
---

# Android / KMP Error Handling

## Result Wrapper (`core:domain`)

A generic, typed Result that works across all layers — data, domain, presentation, validation, anywhere a function can succeed or fail with a typed error.

```kotlin
interface Error

sealed interface Result<out D, out E : Error> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Error<out E : com.example.Error>(val error: E) : Result<Nothing, E>
}

typealias EmptyResult<E> = Result<Unit, E>
```

---

## Extension Helpers (`core:domain`)

These live alongside the `Result` definition:

```kotlin
inline fun <T, E : Error, R> Result<T, E>.map(
    map: (T) -> R
): Result<R, E> {
    return when (this) {
        is Result.Error -> Result.Error(error)
        is Result.Success -> Result.Success(map(this.data))
    }
}

inline fun <T, E : Error> Result<T, E>.onSuccess(
    action: (T) -> Unit
): Result<T, E> {
    return when (this) {
        is Result.Error -> this
        is Result.Success -> {
            action(this.data)
            this
        }
    }
}

inline fun <T, E : Error> Result<T, E>.onFailure(
    action: (E) -> Unit
): Result<T, E> {
    return when (this) {
        is Result.Error -> {
            action(error)
            this
        }
        is Result.Success -> this
    }
}

fun <T, E : Error> Result<T, E>.asEmptyResult(): EmptyResult<E> {
    return map { }
}
```

All helpers return `Result` so they can be chained:
```kotlin
repository.saveNote(note)
    .onSuccess { /* update UI */ }
    .onFailure { /* show error */ }
    .asEmptyResult()
```

---

## Shared Error Types (`core:domain`)

### DataError

```kotlin
sealed interface DataError : Error {
    enum class Network : DataError {
        BAD_REQUEST,
        REQUEST_TIMEOUT,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        TOO_MANY_REQUESTS,
        NO_INTERNET,
        PAYLOAD_TOO_LARGE,
        SERVER_ERROR,
        SERVICE_UNAVAILABLE,
        SERIALIZATION,
        UNKNOWN
    }

    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        UNKNOWN
    }
}
```

### Feature-Specific Errors

Features define their own error types by implementing `Error`:

```kotlin
enum class PasswordValidationError : Error {
    TOO_SHORT,
    NO_UPPERCASE,
    NO_DIGIT
}

// Used with Result — always a single error, not a list:
fun validatePassword(pw: String): EmptyResult<PasswordValidationError>
```

Multiple validation errors are not supported — always return a single error type per Result.

---

## Exception Handling Philosophy

Never throw exceptions for expected failures — always return `Result.Error`. Catch exceptions at the layer that is responsible for the exception:

| Exception origin | Catch in | Example |
|---|---|---|
| HTTP / network | Data layer | `UnresolvedAddressException` → `DataError.Network.NO_INTERNET` |
| Database / disk | Data layer | `SQLiteFullException` → `DataError.Local.DISK_FULL` |
| Business logic | Domain layer | Invalid input → `Result.Error(ValidationError.TOO_SHORT)` |
| Presentation | Presentation layer | Catch and map to `Result.Error` at that layer |

The layer that owns the exception catches it and converts it to a typed `Result.Error`. Upper layers never see raw exceptions for expected failures.

---

## Mapping Errors to UiText

Every error type that is displayed to the user should have a `.toUiText()` extension function. Place it in:

- **Feature's `presentation` module** — if the error is feature-specific (e.g., `AuthError.toUiText()`)
- **`core:presentation`** — if the error is shared across features (e.g., `DataError.toUiText()`)

If an error is purely internal and never shown to the user (e.g., a retry signal, an internal state marker), it does not need a `.toUiText()` mapping.

```kotlin
// core:presentation
fun DataError.toUiText(): UiText {
    return when (this) {
        DataError.Network.NO_INTERNET -> UiText.StringResource(R.string.error_no_internet)
        DataError.Network.SERVER_ERROR -> UiText.StringResource(R.string.error_server)
        DataError.Network.UNAUTHORIZED -> UiText.StringResource(R.string.error_unauthorized)
        DataError.Local.DISK_FULL -> UiText.StringResource(R.string.error_disk_full)
        // ... map all user-facing cases
        else -> UiText.StringResource(R.string.error_unknown)
    }
}
```

---

## Safe Call Helpers (`core:data`)

Typed extension functions on `HttpClient` that wrap Ktor calls and map HTTP responses to `Result<T, DataError.Network>`:

```kotlin
suspend inline fun <reified Response : Any> HttpClient.get(
    route: String,
    queryParameters: Map<String, Any?> = mapOf()
): Result<Response, DataError.Network> {
    return safeCall {
        get {
            url(constructRoute(route))
            queryParameters.forEach { (key, value) ->
                parameter(key, value)
            }
        }
    }
}

suspend inline fun <reified Request, reified Response : Any> HttpClient.post(
    route: String,
    body: Request
): Result<Response, DataError.Network> {
    return safeCall {
        post {
            url(constructRoute(route))
            setBody(body)
        }
    }
}

suspend inline fun <reified Response : Any> HttpClient.delete(
    route: String,
    queryParameters: Map<String, Any?> = mapOf()
): Result<Response, DataError.Network> {
    return safeCall {
        delete {
            url(constructRoute(route))
            queryParameters.forEach { (key, value) ->
                parameter(key, value)
            }
        }
    }
}

suspend inline fun <reified T> safeCall(
    execute: () -> HttpResponse
): Result<T, DataError.Network> {
    val response = try {
        execute()
    } catch (e: UnresolvedAddressException) {
        e.printStackTrace()
        return Result.Error(DataError.Network.NO_INTERNET)
    } catch (e: SerializationException) {
        e.printStackTrace()
        return Result.Error(DataError.Network.SERIALIZATION)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        e.printStackTrace()
        return Result.Error(DataError.Network.UNKNOWN)
    }

    return responseToResult(response)
}

suspend inline fun <reified T> responseToResult(
    response: HttpResponse
): Result<T, DataError.Network> {
    return when (response.status.value) {
        in 200..299 -> Result.Success(response.body<T>())
        401 -> Result.Error(DataError.Network.UNAUTHORIZED)
        408 -> Result.Error(DataError.Network.REQUEST_TIMEOUT)
        409 -> Result.Error(DataError.Network.CONFLICT)
        413 -> Result.Error(DataError.Network.PAYLOAD_TOO_LARGE)
        429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
        else -> Result.Error(DataError.Network.UNKNOWN)
    }
}

fun constructRoute(route: String): String {
    return when {
        route.contains(BuildConfig.BASE_URL) -> route
        route.startsWith("/") -> BuildConfig.BASE_URL + route
        else -> BuildConfig.BASE_URL + "/$route"
    }
}
```

Usage in a data source is clean and uniform:
```kotlin
suspend fun getNotes(): Result<List<NoteDto>, DataError.Network> {
    return httpClient.get(route = "/notes")
}
```

---

## When to Use What

| Scenario | Error type | Example return |
|---|---|---|
| Network call | `DataError.Network` | `Result<List<NoteDto>, DataError.Network>` |
| Local DB access | `DataError.Local` | `Result<Note, DataError.Local>` |
| Repository (multi-source) | `DataError` (supertype) | `Result<List<Note>, DataError>` |
| Domain validation | Custom `Error` enum | `EmptyResult<PasswordValidationError>` |
| Auth logic | Custom `Error` enum | `Result<User, AuthError>` |

The `Result` wrapper is not limited to the data layer — use it anywhere a function has typed success and failure outcomes.
