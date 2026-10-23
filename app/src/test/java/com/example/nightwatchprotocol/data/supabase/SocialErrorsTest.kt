package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.request.HttpRequestBuilder
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SocialErrorsTest {

    /** What PostgREST answers when a SQL function RAISES: SQLSTATE P0001, and the code as the message. */
    private fun raised(code: String) = PostgrestRestException(code, null, null, POSTGRES_RAISE_EXCEPTION, fakeHttpResponse(400))

    private fun postgres(sqlState: String?, status: Int = 400) = PostgrestRestException("falló", null, null, sqlState, fakeHttpResponse(status))

    // ---- exceptions the server raises on purpose ----

    @Test
    fun theFiveRaisedServerCodes_areClassified() {
        assertEquals(SocialError.NoSession, classifySocialFailure(raised("no_session")))
        assertEquals(SocialError.NotEnabled, classifySocialFailure(raised("social_not_enabled")))
        assertEquals(SocialError.RateLimited, classifySocialFailure(raised("rate_limited")))
        assertEquals(SocialError.InvalidInput, classifySocialFailure(raised("bad_query")))
        // bad_action: the app asked for an action the server does not have. Not something to explain to the person.
        assertEquals(SocialError.Unknown, classifySocialFailure(raised("bad_action")))
    }

    @Test
    fun theBattleFunctionsRaisedCodes_areRejections() {
        assertEquals(SocialError.Rejected(SocialRejection.BattleOver), classifySocialFailure(raised("battle_over")))
        assertEquals(SocialError.Rejected(SocialRejection.StaleTurn), classifySocialFailure(raised("stale_turn")))
        assertEquals(SocialError.Rejected(SocialRejection.NotYourTurn), classifySocialFailure(raised("not_your_turn")))
        assertEquals(SocialError.Rejected(SocialRejection.BankEmpty), classifySocialFailure(raised("bank_empty")))
        assertEquals(SocialError.Rejected(SocialRejection.NotFound), classifySocialFailure(raised("not_found")))
        assertEquals(SocialError.Rejected(SocialRejection.AlreadyInBattle), classifySocialFailure(raised("already_in_battle")))
    }

    @Test
    fun aRaisedCodeThisVersionDoesNotKnow_isUnknown() {
        assertEquals(SocialError.Unknown, classifySocialFailure(raised("algo_nuevo")))
        assertEquals(SocialError.Unknown, classifySocialFailure(raised("")))
    }

    // ---- what Postgres itself refuses (insert of the profile) ----

    @Test
    fun uniqueViolation_isTheUsernameTaken() {
        assertEquals(SocialError.Rejected(SocialRejection.UsernameTaken), classifySocialFailure(postgres(POSTGRES_UNIQUE_VIOLATION, 409)))
    }

    @Test
    fun checkViolation_isAnInvalidUsername() {
        assertEquals(SocialError.Rejected(SocialRejection.InvalidUsername), classifySocialFailure(postgres(POSTGRES_CHECK_VIOLATION)))
    }

    // ---- the central classification is reused for everything else ----

    @Test
    fun noNetwork_isOffline() {
        assertEquals(SocialError.Offline, classifySocialFailure(UnknownHostException("sin red")))
        assertEquals(SocialError.Offline, classifySocialFailure(IOException("se cayó")))
        assertEquals(SocialError.Offline, classifySocialFailure(HttpRequestException("falló", HttpRequestBuilder())))
    }

    @Test
    fun permissionDenied_isReported_forInsufficientPrivilege_andForAnExpiredToken() {
        assertEquals(SocialError.PermissionDenied, classifySocialFailure(postgres(POSTGRES_INSUFFICIENT_PRIVILEGE, 403)))
        assertEquals(SocialError.PermissionDenied, classifySocialFailure(postgres("PGRST301", 401)))
        assertEquals(SocialError.PermissionDenied, classifySocialFailure(UnauthorizedRestException("Unauthorized", fakeHttpResponse(400), "no")))
    }

    @Test
    fun anythingElse_isUnknown() {
        assertEquals(SocialError.Unknown, classifySocialFailure(postgres("XX000", 500)))
        assertEquals(SocialError.Unknown, classifySocialFailure(IllegalStateException("raro")))
    }

    // ---- the error never carries the library's exception (its message has the token) ----

    @Test
    fun theClassifiedError_hasNoCause_soTheTokenCannotLeak() {
        val error: Any = classifySocialFailure(postgres("XX000", 500))

        assertFalse("el error no debe ser una excepción", error is Throwable)
        assertFalse(error.toString().contains(FAKE_BEARER_TOKEN))
        assertFalse(error.toString().contains("secret-token"))
        SocialError::class.java.declaredFields.forEach { assertFalse(Throwable::class.java.isAssignableFrom(it.type)) }
    }

    // ---- socialCall ----

    @Test
    fun socialCall_returnsTheValue() = runTest {
        assertEquals(SocialResult.Success(42), socialCall { 42 })
    }

    @Test
    fun socialCall_turnsAnyFailureIntoAClassifiedResult_andNeverThrows() = runTest {
        assertEquals(SocialResult.Failure(SocialError.RateLimited), socialCall<Int> { throw raised("rate_limited") })
        assertEquals(SocialResult.Failure(SocialError.Offline), socialCall<Int> { throw UnknownHostException() })
        assertEquals(SocialResult.Failure(SocialError.Unknown), socialCall<Int> { throw IllegalStateException() })
    }

    @Test
    fun socialCall_aSlowCall_isOffline() = runTest {
        val result = socialCall(timeoutMillis = 1_000) {
            delay(5_000)
            1
        }

        assertEquals(SocialResult.Failure(SocialError.Offline), result)
    }

    @Test
    fun socialCall_neverSwallowsTheCancellationOfTheCaller() = runTest {
        try {
            socialCall<Int> { throw CancellationException("cancelada") }
            fail("debía relanzar la cancelación")
        } catch (e: CancellationException) {
            assertTrue(e.message == "cancelada")
        }
    }
}
