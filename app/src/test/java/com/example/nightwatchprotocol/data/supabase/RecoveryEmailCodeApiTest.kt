package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.auth.AuthError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryEmailCodeApiTest {

    // ---- the answer of the Edge Function ----

    @Test
    fun a429_carriesTheSecondsTheFunctionAskedFor() {
        assertEquals(
            AuthError.RateLimited(42),
            mapSendCodeResponse(429, """{"error":"rate_limited","retry_after_seconds":42}"""),
        )
    }

    @Test
    fun a429_withoutSeconds_orWithNonsense_hasNoSeconds() {
        assertEquals(AuthError.RateLimited(null), mapSendCodeResponse(429, "Too Many Requests"))
        assertEquals(AuthError.RateLimited(null), mapSendCodeResponse(429, """{"error":"rate_limited","retry_after_seconds":0}"""))
        assertEquals(AuthError.RateLimited(3_600), mapSendCodeResponse(429, """{"retry_after_seconds":999999}"""))
    }

    @Test
    fun theSameAddressAsTheMainOne_isItsOwnError() {
        assertEquals(AuthError.SameAsPrimaryEmail, mapSendCodeResponse(400, """{"error":"same_as_primary"}"""))
    }

    @Test
    fun anInvalidAddress_isInvalidEmail() {
        assertEquals(AuthError.InvalidEmail, mapSendCodeResponse(400, """{"error":"invalid_email"}"""))
    }

    @Test
    fun aMailProviderFailure_isEmailSendFailed() {
        assertEquals(AuthError.EmailSendFailed, mapSendCodeResponse(502, """{"error":"email_failed"}"""))
    }

    @Test
    fun anythingElse_isUnknown() {
        assertEquals(AuthError.Unknown, mapSendCodeResponse(401, """{"error":"unauthorized"}"""))
        assertEquals(AuthError.Unknown, mapSendCodeResponse(500, "no es json"))
        assertEquals(AuthError.Unknown, mapSendCodeResponse(400, """{"error":"algo_nuevo"}"""))
    }

    @Test
    fun noConnection_isNetworkError_andOtherFailuresAreUnknown() {
        assertEquals(AuthError.NetworkError, mapSendCodeFailure(IOException("sin red")))
        assertEquals(AuthError.Unknown, mapSendCodeFailure(IllegalStateException("x")))
    }

    // ---- the answer of verify_recovery_email_code ----

    private fun failureOf(raw: String): AuthError? = try {
        checkVerifyAnswer(raw)
        null
    } catch (e: AuthError) {
        e
    }

    @Test
    fun ok_isSuccess_withOrWithoutTheJsonQuotes() {
        assertEquals(null, failureOf("ok"))
        assertEquals(null, failureOf("\"ok\""))
        assertEquals(null, failureOf(" \"ok\"\n"))
    }

    @Test
    fun eachRefusalHasItsOwnError() {
        assertEquals(AuthError.InvalidCode, failureOf("\"invalid\""))
        assertEquals(AuthError.CodeExpired, failureOf("\"expired\""))
        assertEquals(AuthError.TooManyAttempts, failureOf("\"too_many_attempts\""))
    }

    @Test
    fun noPendingCode_isTreatedAsExpired() {
        assertEquals(AuthError.CodeExpired, failureOf("\"not_found\""))
    }

    @Test
    fun anyOtherVerifyAnswer_isUnknown() {
        assertEquals(AuthError.Unknown, failureOf("\"not_authenticated\""))
        assertEquals(AuthError.Unknown, failureOf(""))
        assertEquals(AuthError.Unknown, failureOf("null"))
    }

    @Test
    fun theNamesMatchTheServerSide() {
        // Typos here would only show up on a device: the names must be the ones in the migration and the function.
        assertEquals("send-recovery-email-code", SEND_RECOVERY_CODE_FUNCTION)
        assertEquals("verify_recovery_email_code", VERIFY_RECOVERY_CODE_RPC)
    }
}
