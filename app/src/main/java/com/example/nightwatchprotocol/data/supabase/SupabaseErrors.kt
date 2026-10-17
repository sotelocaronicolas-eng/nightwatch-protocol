package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.firestore.FIRESTORE_TIMEOUT_MILLIS
import com.example.nightwatchprotocol.data.firestore.FailureKind
import com.example.nightwatchprotocol.data.firestore.FirestoreAccessException
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.io.IOException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/*
 * Mapping of supabase-kt failures to the error categories the app already has:
 *   - AuthError             for the auth flows (login, register, password).
 *   - FailureKind           for data and storage calls (reported through FirestoreAccessException).
 *
 * Network failures need no special case: supabase-kt's HttpRequestException, Ktor's HttpRequestTimeoutException /
 * ConnectTimeoutException / SocketTimeoutException and java.net's UnknownHostException / ConnectException are all
 * IOExceptions. A RestException is a server answer, so it is never an IOException.
 *
 * Never put a RestException (or its message) into a log, a UI message or the `cause` of our exceptions: its message
 * includes the request headers, which carry the Authorization token and the apikey.
 */

/**
 * Auth errors. What is lost compared with Firebase:
 *  - `invalid_credentials` is answered the same for an unknown email and for a wrong password (on purpose, so the API
 *    cannot be used to find out which emails have an account). It maps to [AuthError.InvalidCredentials]; Firebase's
 *    "El correo no está registrado" ([AuthError.UserNotFound]) and "La contraseña es incorrecta"
 *    ([AuthError.WrongPassword]) can no longer be produced.
 *  - [AuthError.GoogleAccountOnly] cannot be produced either (Supabase has no "which providers does this email use").
 */
fun mapSupabaseAuthError(error: Throwable): AuthError = when (error) {
    // Checked before AuthRestException: it is a subclass whose code is also "weak_password".
    is AuthWeakPasswordException -> AuthError.WeakPassword
    // Only the seconds are read from the description; the exception itself is never kept (see the note above).
    is AuthRestException ->
        if (error.errorCode == AuthErrorCode.OverEmailSendRateLimit) {
            AuthError.RateLimited(retryAfterSeconds(error.errorDescription))
        } else {
            mapAuthErrorCode(error.errorCode)
        }
    is IOException -> AuthError.NetworkError
    else -> AuthError.Unknown
}

/** Codes without a category of their own (rate limits, disabled signup, expired sessions...) are [AuthError.Unknown]. */
fun mapAuthErrorCode(code: AuthErrorCode?): AuthError = when (code) {
    AuthErrorCode.InvalidCredentials -> AuthError.InvalidCredentials
    AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> AuthError.EmailAlreadyInUse
    // same_password: the server refuses to "change" a password to the one it already has.
    AuthErrorCode.WeakPassword, AuthErrorCode.SamePassword -> AuthError.WeakPassword
    AuthErrorCode.EmailAddressInvalid -> AuthError.InvalidEmail
    AuthErrorCode.ReauthenticationNeeded, AuthErrorCode.ReauthenticationNotValid -> AuthError.RecentLoginRequired
    AuthErrorCode.EmailNotConfirmed -> AuthError.EmailNotConfirmed
    // GoTrue says otp_expired ("Token has expired or is invalid") for a wrong code as well as for an expired one.
    AuthErrorCode.OtpExpired -> AuthError.InvalidCode
    AuthErrorCode.OverRequestRateLimit -> AuthError.TooManyAttempts
    AuthErrorCode.OverEmailSendRateLimit -> AuthError.RateLimited(null)
    else -> AuthError.Unknown
}

private val RETRY_SECONDS = Regex("""(\d+)\s*(?:seconds?|segundos?|s)\b""", RegexOption.IGNORE_CASE)
private const val MAX_RETRY_SECONDS = 3_600

/**
 * The wait Supabase asks for in `over_email_send_rate_limit`: "For security purposes, you can only request this after
 * 45 seconds." Null when the text has no number of seconds (the caller then uses its own default).
 */
fun retryAfterSeconds(description: String?): Int? =
    RETRY_SECONDS.find(description.orEmpty())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(MAX_RETRY_SECONDS)

/** Postgres "insufficient_privilege": a missing GRANT or a row-level-security violation. */
const val POSTGRES_INSUFFICIENT_PRIVILEGE = "42501"

/** Data and storage failures. A cancelled coroutine is not a failure: callers rethrow [CancellationException] first. */
fun classifySupabaseFailure(error: Throwable): FailureKind = when (error) {
    is IOException -> FailureKind.Offline
    is PostgrestRestException ->
        if (isPermissionDenied(error.statusCode, error.code, error.error, error.description)) {
            FailureKind.PermissionDenied
        } else {
            FailureKind.Unknown
        }
    is RestException ->
        if (isPermissionDenied(error.statusCode, null, error.error, error.description)) {
            FailureKind.PermissionDenied
        } else {
            FailureKind.Unknown
        }
    else -> FailureKind.Unknown
}

/**
 * 401/403, Postgres 42501 and PostgREST's own JWT codes (PGRST300 to PGRST399, e.g. PGRST301 "JWT expired").
 * Storage answers a row-level-security rejection with HTTP 400 and `statusCode: 403` inside the body, which
 * supabase-kt exposes as the exception's `error` text, so the wording is checked too.
 */
internal fun isPermissionDenied(statusCode: Int, postgresCode: String?, error: String?, description: String?): Boolean {
    if (statusCode == 401 || statusCode == 403) return true
    if (postgresCode == POSTGRES_INSUFFICIENT_PRIVILEGE) return true
    if (postgresCode != null && postgresCode.startsWith("PGRST3")) return true
    val text = "${error.orEmpty()} ${description.orEmpty()}".lowercase()
    return "row-level security" in text || "unauthorized" in text
}

/** Carries only what is safe to log: the class and the status, never the message of a [RestException]. */
private class SupabaseFailureCause(description: String) : Exception(description)

private fun safeCause(error: Throwable): Throwable {
    if (error !is RestException) return error
    return SupabaseFailureCause("${error::class.simpleName} status=${error.statusCode}")
}

/**
 * Runs a Supabase call with a time limit and reports every failure as a [FirestoreAccessException] (the data-access
 * exception the view models already understand). Cancellation of the caller is never swallowed.
 *
 * There is no offline write queue: without a connection the call fails with [FailureKind.Offline].
 */
suspend fun <T> supabaseCall(timeoutMillis: Long = FIRESTORE_TIMEOUT_MILLIS, block: suspend () -> T): T {
    try {
        return withTimeout(timeoutMillis) { block() }
    } catch (e: TimeoutCancellationException) {
        throw FirestoreAccessException(FailureKind.Offline, e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirestoreAccessException) {
        throw e
    } catch (e: Exception) {
        throw FirestoreAccessException(classifySupabaseFailure(e), safeCause(e))
    }
}
