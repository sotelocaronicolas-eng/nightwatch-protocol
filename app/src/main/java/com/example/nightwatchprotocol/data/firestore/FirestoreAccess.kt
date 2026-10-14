package com.example.nightwatchprotocol.data.firestore

import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/** Why a Firestore call failed, in terms the UI can explain without technical details. */
enum class FailureKind { Offline, PermissionDenied, Unknown }

class FirestoreAccessException(
    val kind: FailureKind,
    cause: Throwable? = null,
) : Exception("Firestore no respondió: $kind", cause)

const val FIRESTORE_TIMEOUT_MILLIS = 5_000L

/** Maps a Firestore status code (by name, e.g. "PERMISSION_DENIED") to a [FailureKind]. */
fun classifyCodeName(codeName: String): FailureKind = when (codeName) {
    "UNAVAILABLE", "DEADLINE_EXCEEDED" -> FailureKind.Offline
    "PERMISSION_DENIED", "UNAUTHENTICATED" -> FailureKind.PermissionDenied
    else -> FailureKind.Unknown
}

fun classifyFailure(error: Throwable): FailureKind = when (error) {
    is FirebaseFirestoreException -> classifyCodeName(error.code.name)
    is IOException -> FailureKind.Offline
    else -> FailureKind.Unknown
}

/**
 * Runs a Firestore call with a time limit and reports every failure as a [FirestoreAccessException].
 *
 * It uses `withTimeout`, not `withTimeoutOrNull`: `Task<Void>.await()` (every `set`, `update` and `delete`)
 * returns `null` when it SUCCEEDS, so a `null` result cannot be taken as "timed out". A call that stays pending
 * (no usable connection) is reported as [FailureKind.Offline]; the SDK may still deliver the write later.
 * Cancellation of the caller is never swallowed.
 */
suspend fun <T> firestoreCall(timeoutMillis: Long = FIRESTORE_TIMEOUT_MILLIS, block: suspend () -> T): T {
    try {
        return withTimeout(timeoutMillis) { block() }
    } catch (e: TimeoutCancellationException) {
        throw FirestoreAccessException(FailureKind.Offline, e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirestoreAccessException) {
        throw e
    } catch (e: Exception) {
        throw FirestoreAccessException(classifyFailure(e), e)
    }
}
