package com.example.nightwatchprotocol.data.auth

import com.example.nightwatchprotocol.data.account.toAccountProfile
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.Identity
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resumeWithException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.nightwatchprotocol.data.supabase.fakeHttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class SupabaseAuthRepositoryTest {

    private val ana = user("ana-id", "ana@example.com")

    private fun user(id: String, email: String?, metadata: JsonObject? = null, identities: List<Identity>? = null) =
        UserInfo(
            aud = "authenticated",
            id = id,
            email = email,
            userMetadata = metadata,
            identities = identities,
            createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
        )

    private fun session(user: UserInfo?) =
        UserSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer", user = user)

    private fun authenticated(user: UserInfo?) = SessionStatus.Authenticated(session(user))

    /** A stand-in for supabase-kt's [Auth] that answers only what the repository uses. */
    private class AuthStub(initial: SessionStatus, stored: UserSession? = null) {
        val status = MutableStateFlow(initial)
        var signOutError: Throwable? = null

        /** What signInWith / signUpWith fail with; null = they succeed (and open a session if [opensSession]). */
        var signInError: Throwable? = null
        var signUpError: Throwable? = null
        var opensSession = true
        var signInCalls = 0
        val signInProviders = mutableListOf<Any?>()
        val signInConfigs = mutableListOf<Any?>()
        var signUpCalls = 0

        /** verifyEmailOtp (type, email, token) and resendEmail (type, email); a null error = success. */
        var verifyError: Throwable? = null
        var resendError: Throwable? = null
        val verifyCalls = mutableListOf<List<Any?>>()
        val resendCalls = mutableListOf<List<Any?>>()
        var resetError: Throwable? = null
        val resetCalls = mutableListOf<String>()
        val signOutScopes = mutableListOf<SignOutScope>()
        var clearSessionCalls = 0
        var storedLoads = 0

        private val sessionManager = object : SessionManager {
            override suspend fun saveSession(session: UserSession) = Unit
            override suspend fun loadSession(): UserSession? {
                storedLoads++
                return stored
            }
            override suspend fun deleteSession() = Unit
        }

        val auth: Auth = Proxy.newProxyInstance(Auth::class.java.classLoader, arrayOf(Auth::class.java)) { _, method, args ->
            when (method.name) {
                "getSessionStatus" -> status
                "getSessionManager" -> sessionManager
                "signInWith" -> {
                    signInCalls++
                    signInProviders += args[0]
                    signInConfigs += args[2]
                    outcome(args, signInError)
                }
                "signUpWith" -> {
                    signUpCalls++
                    outcome(args, signUpError)
                }
                "verifyEmailOtp" -> {
                    verifyCalls += listOf(args[0], args[1], args[2])
                    outcome(args, verifyError)
                }
                "resetPasswordForEmail" -> {
                    resetCalls += args[0] as String
                    outcome(args, resetError, opens = false)
                }
                "resendEmail" -> {
                    resendCalls += listOf(args[0], args[1])
                    outcome(args, resendError, opens = false)
                }
                "signOut" -> {
                    signOutScopes += args[0] as SignOutScope
                    signOutError?.let { throw it }
                    Unit
                }
                "clearSession" -> {
                    clearSessionCalls++
                    status.value = SessionStatus.NotAuthenticated(isSignOut = true)
                    Unit
                }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Auth

        // A Proxy would wrap a checked exception (supabase-kt's RestException) in UndeclaredThrowableException, so the
        // failure is delivered through the suspend function's continuation, which is what a real failure does anyway.
        @Suppress("UNCHECKED_CAST")
        private fun outcome(args: Array<Any?>, error: Throwable?, opens: Boolean = true): Any? {
            val continuation = args.last() as Continuation<Any?>
            if (error != null) {
                continuation.resumeWithException(error)
                return COROUTINE_SUSPENDED
            }
            if (opens && opensSession) {
                val newUser = UserInfo(aud = "authenticated", id = "new-id", email = "nuevo@example.com")
                status.value = SessionStatus.Authenticated(
                    UserSession(accessToken = "a", refreshToken = "r", expiresIn = 3600, tokenType = "bearer", user = newUser),
                )
            }
            return Unit
        }
    }

    private class FakeAccountStatusLookup {
        var result: Result<AccountStatus> = Result.success(AccountStatus.NotFound)
        val asked = mutableListOf<String>()
        val lookup = AccountStatusLookup { email ->
            asked += email
            result.getOrThrow()
        }
    }

    private fun TestScope.repository(
        stub: AuthStub,
        statusLookup: AccountStatusLookup = FakeAccountStatusLookup().lookup,
        readyTimeout: Long = 3_000,
        signOutTimeout: Long = 5_000,
    ) = SupabaseAuthRepository(stub.auth, statusLookup, backgroundScope, readyTimeout, signOutTimeout).also { runCurrent() }

    private fun restError(code: String, status: Int = 400) = AuthRestException(code, "x", fakeHttpResponse(status))

    private suspend fun SupabaseAuthRepository.signInFailure(): AuthError =
        try {
            signIn("ana@example.com", "Secreta1!")
            fail("debía lanzar")
            AuthError.Unknown
        } catch (e: AuthError) {
            e
        }

    // ---- reading the user ----

    @Test
    fun authenticated_exposesIdAndEmail() = runTest {
        val repo = repository(AuthStub(authenticated(ana)))
        assertEquals("ana@example.com", repo.currentUserEmail)
        assertEquals("ana-id", repo.currentUserUid)
    }

    @Test
    fun notAuthenticated_hasNoUser() = runTest {
        val repo = repository(AuthStub(SessionStatus.NotAuthenticated()))
        assertNull(repo.currentUserEmail)
        assertNull(repo.currentUserUid)
    }

    @Test
    fun resolveCurrentUser_coversEveryStatus() {
        val failure = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException()))
        assertEquals(ana, resolveCurrentUser(authenticated(ana), null))
        assertNull(resolveCurrentUser(SessionStatus.NotAuthenticated(true), ana))
        assertEquals(ana, resolveCurrentUser(SessionStatus.Initializing, ana))
        assertEquals(ana, resolveCurrentUser(failure, ana))
        assertNull(resolveCurrentUser(failure, null))
    }

    @Test
    fun nextLastKnownUser_followsTheSession() {
        val failure = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException()))
        assertEquals(ana, nextLastKnownUser(authenticated(ana), null))
        assertEquals(ana, nextLastKnownUser(authenticated(null), ana))
        assertEquals(ana, nextLastKnownUser(failure, ana))
        assertEquals(ana, nextLastKnownUser(SessionStatus.Initializing, ana))
        assertNull(nextLastKnownUser(SessionStatus.NotAuthenticated(), ana))
    }

    // ---- awaitSessionReady ----

    @Test
    fun awaitSessionReady_waitsWhileInitializing_thenSeesTheUser() = runTest {
        val stub = AuthStub(SessionStatus.Initializing)
        val repo = repository(stub)
        var done = false
        backgroundScope.launch {
            repo.awaitSessionReady()
            done = true
        }
        runCurrent()
        assertFalse(done)
        assertNull(repo.currentUserEmail)

        stub.status.value = authenticated(ana)
        runCurrent()
        assertTrue(done)
        assertEquals("ana@example.com", repo.currentUserEmail)
    }

    @Test
    fun awaitSessionReady_offlineWithExpiredToken_keepsTheStoredUser() = runTest {
        val failure = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException()))
        val stub = AuthStub(failure, stored = session(ana))
        val repo = repository(stub)

        repo.awaitSessionReady()

        assertEquals("ana@example.com", repo.currentUserEmail)
        assertEquals(1, stub.storedLoads)
    }

    @Test
    fun awaitSessionReady_stuckInitializing_givesUpAfterTheTimeoutAndUsesTheStoredUser() = runTest {
        val stub = AuthStub(SessionStatus.Initializing, stored = session(ana))
        val repo = repository(stub, readyTimeout = 3_000)
        var done = false
        backgroundScope.launch {
            repo.awaitSessionReady()
            done = true
        }

        advanceTimeBy(2_999)
        runCurrent()
        assertFalse(done)
        advanceTimeBy(2)
        runCurrent()
        assertTrue(done)
        assertEquals("ana-id", repo.currentUserUid)
    }

    @Test
    fun awaitSessionReady_withoutStoredSession_staysSignedOut() = runTest {
        val repo = repository(AuthStub(SessionStatus.NotAuthenticated()))
        repo.awaitSessionReady()
        assertNull(repo.currentUserEmail)
    }

    // ---- signOut ----

    @Test
    fun signOut_readsAsSignedOutAtOnce_andClearsTheSession() = runTest {
        val stub = AuthStub(authenticated(ana))
        val repo = repository(stub)

        repo.signOut()
        assertNull(repo.currentUserEmail)
        runCurrent()

        assertEquals(listOf(SignOutScope.LOCAL), stub.signOutScopes)
        assertTrue(stub.status.value is SessionStatus.NotAuthenticated)
    }

    @Test
    fun signOut_whenTheServerCannotBeReached_stillClearsTheLocalSession() = runTest {
        val stub = AuthStub(authenticated(ana)).apply { signOutError = RuntimeException("sin red") }
        val repo = repository(stub)

        repo.signOut()
        runCurrent()

        assertEquals(1, stub.clearSessionCalls)
        assertTrue(stub.status.value is SessionStatus.NotAuthenticated)
        assertNull(repo.currentUserUid)
    }

    @Test
    fun awaitSessionReady_waitsForAPendingSignOut() = runTest {
        val stub = AuthStub(authenticated(ana)).apply { signOutError = RuntimeException("sin red") }
        val repo = repository(stub)

        repo.signOut()
        repo.awaitSessionReady()

        assertEquals(1, stub.clearSessionCalls)
        assertNull(repo.currentUserEmail)
    }

    // ---- login: individual errors ----

    @Test
    fun login_unknownEmail_isUserNotFound() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.success(AccountStatus.NotFound) }
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { signInError = restError("invalid_credentials") }

        assertEquals(AuthError.UserNotFound, repository(stub, status.lookup).signInFailure())
        assertEquals(listOf("ana@example.com"), status.asked)
    }

    @Test
    fun login_emailWithPassword_isWrongPassword() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.success(AccountStatus.Password) }
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { signInError = restError("invalid_credentials") }

        assertEquals(AuthError.WrongPassword, repository(stub, status.lookup).signInFailure())
    }

    @Test
    fun login_googleOnlyAccount_isGoogleAccountOnly() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.success(AccountStatus.OtherProvider) }
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { signInError = restError("invalid_credentials") }

        assertEquals(AuthError.GoogleAccountOnly, repository(stub, status.lookup).signInFailure())
    }

    @Test
    fun login_whenTheLookupFails_keepsTheGenericInvalidCredentials() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.failure(RuntimeException("rpc caída")) }
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { signInError = restError("invalid_credentials") }

        assertEquals(AuthError.InvalidCredentials, repository(stub, status.lookup).signInFailure())
        assertEquals(1, status.asked.size)
    }

    @Test
    fun login_otherFailures_neverTriggerTheLookup() = runTest {
        val status = FakeAccountStatusLookup()
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { signInError = java.io.IOException("sin red") }

        assertEquals(AuthError.NetworkError, repository(stub, status.lookup).signInFailure())
        assertTrue(status.asked.isEmpty())
    }

    @Test
    fun login_success_neverTriggersTheLookup() = runTest {
        val status = FakeAccountStatusLookup()
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        repository(stub, status.lookup).signIn("ana@example.com", "Secreta1!")

        assertEquals(1, stub.signInCalls)
        assertTrue(status.asked.isEmpty())
    }

    // ---- sign-up: individual errors ----

    private suspend fun SupabaseAuthRepository.registerFailure(): AuthError =
        try {
            register("Ana", "ana@example.com", "Secreta1!")
            fail("debía lanzar")
            AuthError.Unknown
        } catch (e: AuthError) {
            e
        }

    @Test
    fun register_emailWithPassword_isEmailAlreadyInUse_withoutCallingSignUp() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.success(AccountStatus.Password) }
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        assertEquals(AuthError.EmailAlreadyInUse, repository(stub, status.lookup).registerFailure())
        assertEquals(0, stub.signUpCalls)
    }

    @Test
    fun register_googleOnlyEmail_isGoogleAccountOnly_withoutCallingSignUp() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.success(AccountStatus.OtherProvider) }
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        assertEquals(AuthError.GoogleAccountOnly, repository(stub, status.lookup).registerFailure())
        assertEquals(0, stub.signUpCalls)
    }

    @Test
    fun register_newEmail_goesOnToSignUp() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.success(AccountStatus.NotFound) }
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        repository(stub, status.lookup).register("Ana", "ana@example.com", "Secreta1!")

        assertEquals(1, stub.signUpCalls)
        assertEquals(listOf("ana@example.com"), status.asked)
    }

    @Test
    fun register_whenTheLookupFails_goesOnToSignUp_andSupabasesOwnErrorIsMappedAsBefore() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.failure(RuntimeException("rpc caída")) }
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { signUpError = restError("user_already_exists", 422) }

        assertEquals(AuthError.EmailAlreadyInUse, repository(stub, status.lookup).registerFailure())
        assertEquals(1, stub.signUpCalls)
    }

    @Test
    fun register_lookupFailsAndSignUpSucceeds_opensTheSession() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.failure(RuntimeException("rpc caída")) }
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        val repo = repository(stub, status.lookup)
        repo.register("Ana", "ana@example.com", "Secreta1!")

        assertEquals("nuevo@example.com", repo.currentUserEmail)
    }

    @Test
    fun register_withoutASession_afterSignUp_isEmailConfirmationRequired() = runTest {
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { opensSession = false }

        assertEquals(AuthError.EmailConfirmationRequired, repository(stub).registerFailure())
    }

    @Test
    fun registerErrorFor_coversEveryStatus() {
        assertEquals(AuthError.EmailAlreadyInUse, registerErrorFor(AccountStatus.Password))
        assertEquals(AuthError.GoogleAccountOnly, registerErrorFor(AccountStatus.OtherProvider))
        assertNull(registerErrorFor(AccountStatus.NotFound))
        assertNull(registerErrorFor(null))
    }

    @Test
    fun loginErrorFor_coversEveryStatus() {
        assertEquals(AuthError.UserNotFound, loginErrorFor(AccountStatus.NotFound))
        assertEquals(AuthError.WrongPassword, loginErrorFor(AccountStatus.Password))
        assertEquals(AuthError.GoogleAccountOnly, loginErrorFor(AccountStatus.OtherProvider))
        assertEquals(AuthError.InvalidCredentials, loginErrorFor(null))
    }

    @Test
    fun parseAccountStatus_acceptsTheWireValues_withOrWithoutJsonQuotes() {
        assertEquals(AccountStatus.NotFound, parseAccountStatus("not_found"))
        assertEquals(AccountStatus.Password, parseAccountStatus("\"password\""))
        assertEquals(AccountStatus.OtherProvider, parseAccountStatus(" \"other_provider\"\n"))
        assertNull(parseAccountStatus("algo raro"))
        assertNull(parseAccountStatus(""))
        assertNull(parseAccountStatus(null))
    }

    // ---- sign-up with a code ----

    @Test
    fun signUpCode_isVerifiedWithTheSignUpOtpType_andOpensTheSession() = runTest {
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        val repo = repository(stub)
        repo.verifySignUpCode("ana@example.com", "123456")

        assertEquals(listOf<Any?>(OtpType.Email.SIGNUP, "ana@example.com", "123456"), stub.verifyCalls.single())
        assertEquals("nuevo@example.com", repo.currentUserEmail)
    }

    @Test
    fun signUpCode_aWrongOrExpiredCode_isInvalidCode() = runTest {
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { verifyError = restError("otp_expired", 403) }
        try {
            repository(stub).verifySignUpCode("ana@example.com", "000000")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.InvalidCode, e)
        }
    }

    @Test
    fun signUpCode_verifyingAfterASignOut_isNotHiddenByTheLocalSignOutFlag() = runTest {
        val stub = AuthStub(authenticated(ana))
        val repo = repository(stub)
        repo.signOut()
        runCurrent()

        repo.verifySignUpCode("ana@example.com", "123456")

        assertEquals("nuevo@example.com", repo.currentUserEmail)
    }

    @Test
    fun signUpCode_resendUsesTheSignUpOtpType() = runTest {
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        repository(stub).resendSignUpCode("ana@example.com")

        assertEquals(listOf<Any?>(OtpType.Email.SIGNUP, "ana@example.com"), stub.resendCalls.single())
    }

    @Test
    fun signUpCode_resendFailuresAreMapped() = runTest {
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { resendError = java.io.IOException("sin red") }
        try {
            repository(stub).resendSignUpCode("ana@example.com")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.NetworkError, e)
        }
    }

    @Test
    fun login_withAnUnconfirmedEmail_isEmailNotConfirmed_withoutTheLookup() = runTest {
        val status = FakeAccountStatusLookup()
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { signInError = restError("email_not_confirmed") }

        assertEquals(AuthError.EmailNotConfirmed, repository(stub, status.lookup).signInFailure())
        assertTrue(status.asked.isEmpty())
    }

    // ---- Google (ID token + nonce) ----

    @Test
    fun google_sendsTheIdTokenWithTheGoogleProviderAndTheRawNonce() = runTest {
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        repository(stub).signInWithGoogle("el-id-token", "nonce-sin-hash")

        assertEquals(IDToken, stub.signInProviders.single())
        @Suppress("UNCHECKED_CAST")
        val config = IDToken.Config().also { (stub.signInConfigs.single() as IDToken.Config.() -> Unit)(it) }
        assertEquals("el-id-token", config.idToken)
        assertEquals(Google, config.provider)
        assertEquals("nonce-sin-hash", config.nonce)
    }

    @Test
    fun google_usesNonces() = runTest {
        assertTrue(repository(AuthStub(SessionStatus.NotAuthenticated())).usesGoogleNonce)
    }

    @Test
    fun google_noConnection_isNetworkError() = runTest {
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { signInError = java.io.IOException("sin red") }
        try {
            repository(stub).signInWithGoogle("token", "nonce")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.NetworkError, e)
        }
    }

    @Test
    fun google_aRejectedToken_isMappedLikeAnyOtherAuthError() = runTest {
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { signInError = restError("bad_jwt") }
        try {
            repository(stub).signInWithGoogle("token", "nonce")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.Unknown, e)
        }
    }

    // ---- password recovery with a code ----

    private suspend fun SupabaseAuthRepository.resetFailure(): AuthError =
        try {
            sendPasswordResetEmail("ana@example.com")
            fail("debía lanzar")
            AuthError.Unknown
        } catch (e: AuthError) {
            e
        }

    @Test
    fun recovery_isDoneWithACode() = runTest {
        assertTrue(repository(AuthStub(SessionStatus.NotAuthenticated())).recoversPasswordWithCode)
    }

    @Test
    fun recovery_unknownEmail_isUserNotFound_andNothingIsSent() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.success(AccountStatus.NotFound) }
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        assertEquals(AuthError.UserNotFound, repository(stub, status.lookup).resetFailure())
        assertTrue(stub.resetCalls.isEmpty())
    }

    @Test
    fun recovery_googleOnlyEmail_isGoogleAccountOnly_andNothingIsSent() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.success(AccountStatus.OtherProvider) }
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        assertEquals(AuthError.GoogleAccountOnly, repository(stub, status.lookup).resetFailure())
        assertTrue(stub.resetCalls.isEmpty())
    }

    @Test
    fun recovery_emailWithPassword_sendsTheRecoveryEmail() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.success(AccountStatus.Password) }
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        repository(stub, status.lookup).sendPasswordResetEmail("ana@example.com")

        assertEquals(listOf("ana@example.com"), status.asked)
        assertEquals(listOf("ana@example.com"), stub.resetCalls)
    }

    @Test
    fun recovery_whenTheLookupFails_theResetGoesOn() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.failure(RuntimeException("rpc caída")) }
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        repository(stub, status.lookup).sendPasswordResetEmail("ana@example.com")

        assertEquals(listOf("ana@example.com"), stub.resetCalls)
    }

    @Test
    fun recovery_serverFailuresAreMapped() = runTest {
        val status = FakeAccountStatusLookup().apply { result = Result.success(AccountStatus.Password) }
        val offline = AuthStub(SessionStatus.NotAuthenticated()).apply { resetError = java.io.IOException("sin red") }
        assertEquals(AuthError.NetworkError, repository(offline, status.lookup).resetFailure())

        val invalid = AuthStub(SessionStatus.NotAuthenticated()).apply { resetError = restError("email_address_invalid") }
        assertEquals(AuthError.InvalidEmail, repository(invalid, status.lookup).resetFailure())
    }

    @Test
    fun recoveryCode_isVerifiedWithTheRecoveryOtpType_andOpensTheSession() = runTest {
        val stub = AuthStub(SessionStatus.NotAuthenticated())

        val repo = repository(stub)
        repo.verifyRecoveryCode("ana@example.com", "123456")

        assertEquals(listOf<Any?>(OtpType.Email.RECOVERY, "ana@example.com", "123456"), stub.verifyCalls.single())
        assertEquals("nuevo@example.com", repo.currentUserEmail)
    }

    @Test
    fun recoveryCode_aWrongCode_isInvalidCode() = runTest {
        val stub = AuthStub(SessionStatus.NotAuthenticated()).apply { verifyError = restError("otp_expired", 403) }
        try {
            repository(stub).verifyRecoveryCode("ana@example.com", "000000")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.InvalidCode, e)
        }
    }

    // ---- registration outcome ----

    @Test
    fun hasOpenSession_onlyWhenAuthenticated() {
        assertTrue(hasOpenSession(authenticated(ana)))
        assertFalse(hasOpenSession(SessionStatus.NotAuthenticated()))
        assertFalse(hasOpenSession(SessionStatus.Initializing))
    }

    // ---- account profile ----

    @Test
    fun toAccountProfile_readsNameEmailDateAndPasswordIdentity() {
        val info = user(
            "id",
            "ana@example.com",
            metadata = buildJsonObject { put("display_name", "Ana") },
            identities = listOf(Identity(id = "1", identityData = JsonObject(emptyMap()), provider = "email", userId = "id")),
        )
        val profile = info.toAccountProfile()
        assertEquals("Ana", profile.displayName)
        assertEquals("ana@example.com", profile.email)
        assertEquals(1_700_000_000_000, profile.creationTimeMillis)
        assertTrue(profile.hasPassword)
    }

    @Test
    fun toAccountProfile_googleOnlyAccountHasNoPassword() {
        val info = user(
            "id",
            "ana@example.com",
            identities = listOf(Identity(id = "1", identityData = JsonObject(emptyMap()), provider = "google", userId = "id")),
        )
        assertFalse(info.toAccountProfile().hasPassword)
    }

    @Test
    fun toAccountProfile_tolerantOfMissingMetadata() {
        assertNull(user("id", "a@b.c").toAccountProfile().displayName)
        assertNull(user("id", "a@b.c", metadata = buildJsonObject { put("display_name", JsonNull) }).toAccountProfile().displayName)
        assertNull(user("id", "a@b.c", metadata = buildJsonObject { put("display_name", JsonPrimitive(null as String?)) }).toAccountProfile().displayName)
        assertFalse(user("id", "a@b.c", identities = null).toAccountProfile().hasPassword)
    }
}
