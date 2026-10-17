package com.example.nightwatchprotocol.data.account

import com.example.nightwatchprotocol.data.auth.AccountStatus
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.SupabaseAuthRepository
import com.example.nightwatchprotocol.data.supabase.fakeHttpResponse
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.Identity
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.auth.user.UserUpdateBuilder
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resumeWithException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class SupabaseAccountRepositoryTest {

    private val ana = UserInfo(
        aud = "authenticated",
        id = "ana-id",
        email = "ana@example.com",
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
    )

    /** Answers only what the email-change calls use. A null error means success. */
    private class AuthStub(user: UserInfo?) {
        val status = MutableStateFlow<SessionStatus>(
            if (user == null) {
                SessionStatus.NotAuthenticated()
            } else {
                SessionStatus.Authenticated(UserSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer", user = user))
            },
        )
        var error: Throwable? = null
        val updates = mutableListOf<UserUpdateBuilder>()
        val verifyCalls = mutableListOf<List<Any?>>()
        val resendCalls = mutableListOf<List<Any?>>()

        /** What the server reports for the user's identities, and the unlinking that was asked for. */
        var identities: List<Identity> = emptyList()
        var retrieveError: Throwable? = null
        val unlinked = mutableListOf<String>()
        val unlinkFailsFor = mutableSetOf<String>()

        val auth: Auth = Proxy.newProxyInstance(Auth::class.java.classLoader, arrayOf(Auth::class.java)) { _, method, args ->
            when (method.name) {
                "getSessionStatus" -> status
                "updateUser" -> {
                    @Suppress("UNCHECKED_CAST")
                    updates += UserUpdateBuilder().also { (args[2] as UserUpdateBuilder.() -> Unit)(it) }
                    answer(args, UserInfo(aud = "authenticated", id = "ana-id", email = "ana@example.com"))
                }
                "verifyEmailOtp" -> {
                    verifyCalls += listOf(args[0], args[1], args[2])
                    answer(args, Unit)
                }
                "resendEmail" -> {
                    resendCalls += listOf(args[0], args[1])
                    answer(args, Unit)
                }
                "retrieveUserForCurrentSession" ->
                    answer(args, UserInfo(aud = "authenticated", id = "ana-id", email = "nuevo@example.com", identities = identities), retrieveError)
                "unlinkIdentity" -> {
                    val identityId = args[0] as String
                    unlinked += identityId
                    answer(args, Unit, if (identityId in unlinkFailsFor) AuthRestException("manual_linking_disabled", "x", fakeHttpResponse(422)) else null)
                }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Auth

        // A failure travels through the continuation, like a real one (a Proxy would wrap a checked exception).
        @Suppress("UNCHECKED_CAST")
        private fun answer(args: Array<Any?>, value: Any, failure: Throwable? = error): Any {
            failure ?: return value
            (args.last() as Continuation<Any?>).resumeWithException(failure)
            return COROUTINE_SUSPENDED
        }
    }

    private fun TestScope.repository(stub: AuthStub): SupabaseAccountRepository {
        val authRepository = SupabaseAuthRepository(
            stub.auth,
            { AccountStatus.Password },
            backgroundScope,
        ).also { runCurrent() }
        return SupabaseAccountRepository(stub.auth, authRepository)
    }

    private fun restError(code: String, status: Int = 422) = AuthRestException(code, "x", fakeHttpResponse(status))

    @Test
    fun theEmailChangeIsDoneWithACode() = runTest {
        assertTrue(repository(AuthStub(ana)).changesEmailWithCode)
    }

    @Test
    fun requestEmailChange_asksTheServerToChangeTheEmail() = runTest {
        val stub = AuthStub(ana)

        repository(stub).requestEmailChange("nuevo@example.com")

        assertEquals("nuevo@example.com", stub.updates.single().email)
        assertEquals(null, stub.updates.single().password)
    }

    @Test
    fun requestEmailChange_toAnAddressInUse_isEmailAlreadyInUse() = runTest {
        val stub = AuthStub(ana).apply { error = restError("email_exists") }
        try {
            repository(stub).requestEmailChange("otro@example.com")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.EmailAlreadyInUse, e)
        }
    }

    @Test
    fun requestEmailChange_withoutASession_failsBeforeAnyRequest() = runTest {
        val stub = AuthStub(null)
        try {
            repository(stub).requestEmailChange("nuevo@example.com")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.Unknown, e)
        }
        assertTrue(stub.updates.isEmpty())
    }

    @Test
    fun verifyEmailChangeCode_usesTheEmailChangeOtpType_andTheNewEmail() = runTest {
        val stub = AuthStub(ana)

        repository(stub).verifyEmailChangeCode("nuevo@example.com", "123456")

        assertEquals(listOf<Any?>(OtpType.Email.EMAIL_CHANGE, "nuevo@example.com", "123456"), stub.verifyCalls.single())
    }

    @Test
    fun verifyEmailChangeCode_aWrongCode_isInvalidCode() = runTest {
        val stub = AuthStub(ana).apply { error = restError("otp_expired", 403) }
        try {
            repository(stub).verifyEmailChangeCode("nuevo@example.com", "000000")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.InvalidCode, e)
        }
    }

    @Test
    fun resendEmailChangeCode_usesTheEmailChangeOtpType() = runTest {
        val stub = AuthStub(ana)

        repository(stub).resendEmailChangeCode("nuevo@example.com")

        assertEquals(listOf<Any?>(OtpType.Email.EMAIL_CHANGE, "nuevo@example.com"), stub.resendCalls.single())
    }

    // ---- unlinking the old Google account after the email change ----

    private fun identity(id: String, provider: String, email: String?) = Identity(
        id = "$provider-user",
        identityData = buildJsonObject { if (email != null) put("email", email) },
        identityId = id,
        provider = provider,
        userId = "ana-id",
    )

    @Test
    fun unlink_removesOnlyTheGoogleIdentitiesWithAnotherEmail() = runTest {
        val stub = AuthStub(ana).apply {
            identities = listOf(
                identity("id-email", "email", "nuevo@example.com"),
                identity("id-google-old", "google", "ana@example.com"),
            )
        }

        val ok = repository(stub).unlinkOutdatedGoogleAccounts("nuevo@example.com")

        assertTrue(ok)
        assertEquals(listOf("id-google-old"), stub.unlinked)
    }

    @Test
    fun unlink_leavesAGoogleIdentityWithTheNewEmail_ignoringCase() = runTest {
        val stub = AuthStub(ana).apply {
            identities = listOf(
                identity("id-email", "email", "nuevo@example.com"),
                identity("id-google", "google", "Nuevo@Example.com"),
            )
        }

        assertTrue(repository(stub).unlinkOutdatedGoogleAccounts("nuevo@example.com"))
        assertTrue(stub.unlinked.isEmpty())
    }

    @Test
    fun unlink_neverTouchesTheEmailIdentity_evenIfItsEmailDiffers() = runTest {
        val stub = AuthStub(ana).apply { identities = listOf(identity("id-email", "email", "ana@example.com")) }

        assertTrue(repository(stub).unlinkOutdatedGoogleAccounts("nuevo@example.com"))
        assertTrue(stub.unlinked.isEmpty())
    }

    @Test
    fun unlink_aFailureIsReportedAsFalse_andTheOtherIdentitiesAreStillTried() = runTest {
        val stub = AuthStub(ana).apply {
            identities = listOf(
                identity("id-google-1", "google", "uno@example.com"),
                identity("id-google-2", "google", "dos@example.com"),
            )
            unlinkFailsFor += "id-google-1"
        }

        assertFalse(repository(stub).unlinkOutdatedGoogleAccounts("nuevo@example.com"))
        assertEquals(listOf("id-google-1", "id-google-2"), stub.unlinked)
    }

    @Test
    fun unlink_ifTheIdentitiesCannotBeRead_returnsFalseWithoutThrowing() = runTest {
        val stub = AuthStub(ana).apply { retrieveError = java.io.IOException("sin red") }

        assertFalse(repository(stub).unlinkOutdatedGoogleAccounts("nuevo@example.com"))
        assertTrue(stub.unlinked.isEmpty())
    }

    @Test
    fun noConnection_isNetworkError() = runTest {
        val stub = AuthStub(ana).apply { error = java.io.IOException("sin red") }
        try {
            repository(stub).requestEmailChange("nuevo@example.com")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.NetworkError, e)
        }
    }
}
