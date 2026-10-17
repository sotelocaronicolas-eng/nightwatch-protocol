package com.example.nightwatchprotocol.data.auth

import com.example.nightwatchprotocol.data.account.toAccountProfile
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.Identity
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import java.lang.reflect.Proxy
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
    }

    private fun TestScope.repository(stub: AuthStub, readyTimeout: Long = 3_000, signOutTimeout: Long = 5_000) =
        SupabaseAuthRepository(stub.auth, backgroundScope, readyTimeout, signOutTimeout).also { runCurrent() }

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

    // ---- not available in this block ----

    @Test
    fun googleAndPasswordReset_failWithAClassifiedError_notACrash() = runTest {
        val repo = repository(AuthStub(SessionStatus.NotAuthenticated()))
        try {
            repo.signInWithGoogle("token")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.Unknown, e)
        }
        try {
            repo.sendPasswordResetEmail("ana@example.com")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.Unknown, e)
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
