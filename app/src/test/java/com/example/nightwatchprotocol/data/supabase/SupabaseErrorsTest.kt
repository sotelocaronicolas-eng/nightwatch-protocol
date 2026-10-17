package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.firestore.FailureKind
import com.example.nightwatchprotocol.data.firestore.FirestoreAccessException
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.NotFoundRestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.exceptions.UnknownRestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseErrorsTest {

    private fun authError(code: String, status: Int = 400) = AuthRestException(code, "descripción", fakeHttpResponse(status))

    private fun postgrest(code: String?, status: Int = 400) =
        PostgrestRestException("falló", null, null, code, fakeHttpResponse(status))

    // ---- auth ----

    @Test
    fun auth_invalidCredentials_isInvalidCredentials() {
        assertEquals(AuthError.InvalidCredentials, mapSupabaseAuthError(authError("invalid_credentials")))
    }

    @Test
    fun auth_userAlreadyExists_andEmailExists_areEmailAlreadyInUse() {
        assertEquals(AuthError.EmailAlreadyInUse, mapSupabaseAuthError(authError("user_already_exists")))
        assertEquals(AuthError.EmailAlreadyInUse, mapSupabaseAuthError(authError("email_exists")))
    }

    @Test
    fun auth_weakPasswordCode_isWeakPassword() {
        assertEquals(AuthError.WeakPassword, mapSupabaseAuthError(authError("weak_password")))
    }

    @Test
    fun auth_weakPasswordException_isWeakPassword() {
        val error = AuthWeakPasswordException("muy corta", fakeHttpResponse(422), listOf("length"))
        assertEquals(AuthError.WeakPassword, mapSupabaseAuthError(error))
    }

    @Test
    fun auth_samePassword_isWeakPassword() {
        assertEquals(AuthError.WeakPassword, mapSupabaseAuthError(authError("same_password", 422)))
    }

    @Test
    fun auth_invalidEmail_isInvalidEmail() {
        assertEquals(AuthError.InvalidEmail, mapSupabaseAuthError(authError("email_address_invalid")))
    }

    @Test
    fun auth_reauthentication_isRecentLoginRequired() {
        assertEquals(AuthError.RecentLoginRequired, mapSupabaseAuthError(authError("reauthentication_needed")))
        assertEquals(AuthError.RecentLoginRequired, mapSupabaseAuthError(authError("reauthentication_not_valid")))
    }

    @Test
    fun auth_codesWithoutCategory_areUnknown() {
        listOf("over_request_rate_limit", "over_email_send_rate_limit", "signup_disabled", "email_not_confirmed", "session_expired")
            .forEach { assertEquals(it, AuthError.Unknown, mapSupabaseAuthError(authError(it))) }
    }

    @Test
    fun auth_unrecognisedCode_isUnknown() {
        assertEquals(AuthError.Unknown, mapSupabaseAuthError(authError("un_codigo_que_no_existe")))
    }

    @Test
    fun auth_networkFailures_areNetworkError() {
        assertEquals(AuthError.NetworkError, mapSupabaseAuthError(UnknownHostException()))
        assertEquals(AuthError.NetworkError, mapSupabaseAuthError(ConnectException()))
        assertEquals(AuthError.NetworkError, mapSupabaseAuthError(IOException("sin red")))
        assertEquals(AuthError.NetworkError, mapSupabaseAuthError(HttpRequestTimeoutException("https://x.invalid", 10_000)))
        assertEquals(AuthError.NetworkError, mapSupabaseAuthError(HttpRequestException("falló", HttpRequestBuilder())))
    }

    @Test
    fun auth_anythingElse_isUnknown() {
        assertEquals(AuthError.Unknown, mapSupabaseAuthError(IllegalStateException("x")))
        assertEquals(AuthError.Unknown, mapSupabaseAuthError(UnknownRestException("raro", fakeHttpResponse(500))))
    }

    // ---- data and storage ----

    @Test
    fun data_networkFailures_areOffline() {
        assertEquals(FailureKind.Offline, classifySupabaseFailure(UnknownHostException()))
        assertEquals(FailureKind.Offline, classifySupabaseFailure(HttpRequestTimeoutException("https://x.invalid", 10_000)))
        assertEquals(FailureKind.Offline, classifySupabaseFailure(HttpRequestException("falló", HttpRequestBuilder())))
    }

    @Test
    fun data_postgres42501_isPermissionDenied_whateverTheHttpStatus() {
        assertEquals(FailureKind.PermissionDenied, classifySupabaseFailure(postgrest("42501", 403)))
        assertEquals(FailureKind.PermissionDenied, classifySupabaseFailure(postgrest("42501", 400)))
    }

    @Test
    fun data_expiredJwt_isPermissionDenied() {
        assertEquals(FailureKind.PermissionDenied, classifySupabaseFailure(postgrest("PGRST301", 401)))
        assertEquals(FailureKind.PermissionDenied, classifySupabaseFailure(postgrest(null, 401)))
    }

    @Test
    fun data_otherPostgrestErrors_areUnknown() {
        assertEquals(FailureKind.Unknown, classifySupabaseFailure(postgrest("23514", 400)))
        assertEquals(FailureKind.Unknown, classifySupabaseFailure(postgrest("PGRST116", 406)))
        assertEquals(FailureKind.Unknown, classifySupabaseFailure(postgrest(null, 500)))
    }

    @Test
    fun data_storageRlsRejection_isPermissionDenied() {
        // Storage answers HTTP 400 with the real 403 inside the body; supabase-kt surfaces the wording as `error`.
        val rls = UnknownRestException("new row violates row-level security policy", fakeHttpResponse(400))
        assertEquals(FailureKind.PermissionDenied, classifySupabaseFailure(rls))
        assertEquals(FailureKind.PermissionDenied, classifySupabaseFailure(UnauthorizedRestException("Unauthorized", fakeHttpResponse(400), "no")))
        assertEquals(FailureKind.PermissionDenied, classifySupabaseFailure(UnknownRestException("x", fakeHttpResponse(403))))
    }

    @Test
    fun data_storageOtherErrors_areUnknown() {
        assertEquals(FailureKind.Unknown, classifySupabaseFailure(NotFoundRestException("Not found", fakeHttpResponse(400), "Object not found")))
        assertEquals(FailureKind.Unknown, classifySupabaseFailure(BadRequestRestException("Invalid", fakeHttpResponse(400), "mime type not supported")))
        assertEquals(FailureKind.Unknown, classifySupabaseFailure(UnknownRestException("boom", fakeHttpResponse(500))))
    }

    @Test
    fun data_authRejectionOn401_isPermissionDenied() {
        assertEquals(FailureKind.PermissionDenied, classifySupabaseFailure(authError("bad_jwt", 401)))
    }

    @Test
    fun data_anythingElse_isUnknown() {
        assertEquals(FailureKind.Unknown, classifySupabaseFailure(IllegalArgumentException("x")))
    }

    // ---- supabaseCall ----

    @Test
    fun call_returnsTheResult() = runTest {
        assertEquals(7, supabaseCall { 7 })
    }

    @Test
    fun call_wrapsFailuresWithTheirKind() = runTest {
        try {
            supabaseCall<Unit> { throw postgrest("42501", 403) }
            fail("debía lanzar")
        } catch (e: FirestoreAccessException) {
            assertEquals(FailureKind.PermissionDenied, e.kind)
        }
        try {
            supabaseCall<Unit> { throw UnknownHostException() }
            fail("debía lanzar")
        } catch (e: FirestoreAccessException) {
            assertEquals(FailureKind.Offline, e.kind)
        }
    }

    @Test
    fun call_neverCarriesTheTokenOfARestException() = runTest {
        try {
            supabaseCall<Unit> { throw postgrest("42501", 403) }
            fail("debía lanzar")
        } catch (e: FirestoreAccessException) {
            val everything = generateSequence<Throwable>(e) { it.cause }.joinToString { it.message.orEmpty() }
            assertFalse(everything.contains("secret-token"))
            assertTrue(e.cause != null)
        }
    }

    @Test
    fun call_timeoutIsOffline() = runTest {
        try {
            supabaseCall(timeoutMillis = 100) { delay(10_000) }
            fail("debía lanzar")
        } catch (e: FirestoreAccessException) {
            assertEquals(FailureKind.Offline, e.kind)
        }
    }

    @Test
    fun call_cancellationIsNotSwallowed() = runTest {
        try {
            supabaseCall<Unit> { throw CancellationException("cancelada") }
            fail("debía lanzar")
        } catch (e: CancellationException) {
            assertEquals("cancelada", e.message)
        }
    }
}
