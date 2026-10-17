package com.example.nightwatchprotocol.data.auth

import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.data.supabase.mapSupabaseAuthError
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

/** How long [SupabaseAuthRepository.awaitSessionReady] waits for the stored session before using it as it is. */
const val SESSION_READY_TIMEOUT_MILLIS = 3_000L

/** How long the server-side half of a sign-out may take before the session is cleared locally anyway. */
const val SIGN_OUT_TIMEOUT_MILLIS = 5_000L

/** Name of the sign-up metadata key that holds the display name (see [SupabaseAccountRepository]). */
const val DISPLAY_NAME_KEY = "display_name"

/**
 * Email and password sign-in on Supabase Auth.
 *
 * Not available in this backend yet (they fail with [AuthError.Unknown], never with a crash): Google sign-in and
 * password recovery. Email change lives in [SupabaseAccountRepository].
 *
 * The user is read from `sessionStatus`, which is what `currentUserOrNull()` reads, plus one fallback: with a
 * token that already expired and no network, supabase-kt reports `RefreshFailure` (or keeps `Initializing` while it
 * retries) and `currentUserOrNull()` is null. The stored user is kept instead, so opening the app offline does not
 * throw a signed-in user out to Login; the data calls will simply fail as "sin conexión".
 */
class SupabaseAuthRepository(
    private val auth: Auth,
    private val accountStatusLookup: AccountStatusLookup,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val sessionReadyTimeoutMillis: Long = SESSION_READY_TIMEOUT_MILLIS,
    private val signOutTimeoutMillis: Long = SIGN_OUT_TIMEOUT_MILLIS,
) : AuthRepository {

    @Volatile
    private var lastKnownUser: UserInfo? = null

    // Set by signOut() so the user reads as signed out at once, like Firebase; the real clean-up runs in pendingSignOut.
    @Volatile
    private var signedOutLocally = false

    @Volatile
    private var pendingSignOut: Job? = null

    init {
        scope.launch {
            auth.sessionStatus.collect { status -> lastKnownUser = nextLastKnownUser(status, lastKnownUser) }
        }
    }

    /** The signed-in user, or null. Also used by [SupabaseAccountRepository]. */
    val currentUser: UserInfo?
        get() = if (signedOutLocally) null else resolveCurrentUser(auth.sessionStatus.value, lastKnownUser)

    override val currentUserEmail: String?
        get() = currentUser?.email

    override val currentUserUid: String?
        get() = currentUser?.id

    /**
     * Waits for the session to leave `Initializing` (the equivalent of `Auth.awaitInitialization()`), but not forever:
     * with a token about to expire and no network supabase-kt retries the refresh inside the load, which can last as
     * long as the outage. After [sessionReadyTimeoutMillis], or on a `RefreshFailure`, the user of the stored session
     * is used. It also waits for a sign-out requested earlier (e.g. "remember session" off at launch).
     */
    override suspend fun awaitSessionReady() {
        pendingSignOut?.join()
        val status = withTimeoutOrNull(sessionReadyTimeoutMillis) {
            auth.sessionStatus.first { it !is SessionStatus.Initializing }
        }
        if (status == null || status is SessionStatus.RefreshFailure) {
            if (lastKnownUser == null) {
                lastKnownUser = runCatchingKeepCancellation { auth.sessionManager.loadSession()?.user }.getOrNull()
            }
        }
    }

    /**
     * Supabase answers `invalid_credentials` for an unknown email and for a wrong password alike. Only then, the
     * email is looked up (see [AccountStatusLookup]) to tell them apart with the same errors Firebase used. If the
     * lookup fails for any reason, the generic [AuthError.InvalidCredentials] stands: it never throws by itself.
     */
    override suspend fun signIn(email: String, password: String) {
        try {
            authCall {
                beginNewSession()
                // redirectUrl = null is what the default resolves to (no deep link is configured); it is explicit so
                // the call does not depend on the Android application context.
                auth.signInWith(Email, redirectUrl = null) {
                    this.email = email
                    this.password = password
                }
            }
        } catch (e: AuthError.InvalidCredentials) {
            throw loginErrorFor(lookUpStatus(email))
        }
    }

    private suspend fun lookUpStatus(email: String): AccountStatus? =
        runCatchingKeepCancellation { accountStatusLookup.statusOf(email) }.getOrNull()

    /**
     * Creates the account and signs in. If the project has "Confirm email" turned ON, Supabase creates the user but
     * opens no session: that is NOT reported as a success, it fails with [AuthError.EmailConfirmationRequired].
     */
    override suspend fun register(name: String, email: String, password: String) = authCall {
        beginNewSession()
        auth.signUpWith(Email, redirectUrl = null) {
            this.email = email
            this.password = password
            data = buildJsonObject { put(DISPLAY_NAME_KEY, name) }
        }
        if (!hasOpenSession(auth.sessionStatus.value)) throw AuthError.EmailConfirmationRequired
    }

    // Not built in this block: the reset email needs a redirect/OTP flow that Supabase does not host.
    override suspend fun sendPasswordResetEmail(email: String) {
        throw AuthError.Unknown
    }

    // Not built in this block: Credential Manager's ID token still has to be configured as a Supabase provider.
    override suspend fun signInWithGoogle(idToken: String) {
        throw AuthError.Unknown
    }

    /**
     * Never leaves the user "half in": the local session is cleared even when the server cannot be reached.
     * `Auth.signOut()` throws without network and then does NOT clear the local session (it only ignores 401/403/404),
     * so [Auth.clearSession] runs in any case.
     */
    override fun signOut() {
        signedOutLocally = true
        lastKnownUser = null
        pendingSignOut = scope.launch {
            // A session still being loaded would otherwise be restored right after the clean-up.
            withTimeoutOrNull(sessionReadyTimeoutMillis) {
                auth.sessionStatus.first { it !is SessionStatus.Initializing }
            }
            try {
                withTimeout(signOutTimeoutMillis) { auth.signOut(SignOutScope.LOCAL) }
            } catch (e: TimeoutCancellationException) {
                // Server unreachable: the local clean-up below is what matters.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Same: any failure of the remote half still ends with the local session cleared.
            }
            withContext(NonCancellable) {
                if (auth.sessionStatus.value !is SessionStatus.NotAuthenticated) auth.clearSession()
            }
        }
    }

    // A sign-out still running must not wipe the session that is about to be opened.
    private suspend fun beginNewSession() {
        pendingSignOut?.join()
        signedOutLocally = false
    }

    private inline fun <T> authCall(block: () -> T): T {
        try {
            return block()
        } catch (e: AuthError) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw mapSupabaseAuthError(e)
        }
    }
}

internal fun hasOpenSession(status: SessionStatus): Boolean = status is SessionStatus.Authenticated

/**
 * The user for a session status. While the session is `Initializing` or failing to refresh (token expired, no
 * network) the last known user stands in for it; an explicit `NotAuthenticated` always means nobody.
 */
internal fun resolveCurrentUser(status: SessionStatus, lastKnown: UserInfo?): UserInfo? = when (status) {
    is SessionStatus.Authenticated -> status.session.user
    is SessionStatus.NotAuthenticated -> null
    is SessionStatus.Initializing, is SessionStatus.RefreshFailure -> lastKnown
}

/** What to remember as "last known user" after [status]. */
internal fun nextLastKnownUser(status: SessionStatus, previous: UserInfo?): UserInfo? = when (status) {
    is SessionStatus.Authenticated -> status.session.user ?: previous
    is SessionStatus.NotAuthenticated -> null
    is SessionStatus.Initializing, is SessionStatus.RefreshFailure -> previous
}
