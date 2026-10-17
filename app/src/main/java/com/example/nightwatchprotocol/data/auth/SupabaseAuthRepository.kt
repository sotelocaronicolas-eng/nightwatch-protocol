package com.example.nightwatchprotocol.data.auth

import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.data.supabase.mapSupabaseAuthError
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
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
 * Also Google sign-in (ID token + nonce), the codes of the sign-up and of the password recovery. Email change lives in
 * [SupabaseAccountRepository].
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
     *
     * An email that already has an account fails first, without calling signUp: [AuthError.EmailAlreadyInUse] if it
     * has a password, [AuthError.GoogleAccountOnly] if it was created with another provider (Google).
     */
    override suspend fun register(name: String, email: String, password: String) = authCall {
        beginNewSession()
        // Tell "already registered" apart before creating anything. A failed lookup just goes on to signUp, where
        // Supabase's own user_already_exists is mapped as before.
        registerErrorFor(lookUpStatus(email))?.let { throw it }
        auth.signUpWith(Email, redirectUrl = null) {
            this.email = email
            this.password = password
            data = buildJsonObject { put(DISPLAY_NAME_KEY, name) }
        }
        if (!hasOpenSession(auth.sessionStatus.value)) throw AuthError.EmailConfirmationRequired
    }

    /**
     * "Confirm email" is on, so [register] left no session. The code is the one the "Confirm sign up" template
     * prints; with a valid one Supabase opens the session, and the sign-up goes on like a normal login.
     */
    override suspend fun verifySignUpCode(email: String, code: String) = authCall {
        beginNewSession()
        auth.verifyEmailOtp(OtpType.Email.SIGNUP, email, code)
    }

    override suspend fun resendSignUpCode(email: String) = authCall {
        auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    override val recoversPasswordWithCode: Boolean get() = true

    /**
     * Supabase would answer the same whether or not the email exists, so [AccountStatusLookup] is asked first, to give
     * the same individual errors as login: [AuthError.UserNotFound] and [AuthError.GoogleAccountOnly]. If the lookup
     * itself fails, the reset goes on (Supabase then sends nothing for an unknown address). The "Reset password"
     * template prints the code (`{{ .Token }}`); there is no link, so no redirect URL is needed.
     *
     * Supabase has no "resend" for recovery, so asking again is simply calling this again.
     */
    override suspend fun sendPasswordResetEmail(email: String) = authCall {
        when (lookUpStatus(email)) {
            AccountStatus.NotFound -> throw AuthError.UserNotFound
            AccountStatus.OtherProvider -> throw AuthError.GoogleAccountOnly
            AccountStatus.Password, null -> Unit
        }
        auth.resetPasswordForEmail(email, redirectUrl = null)
    }

    /** The recovery OTP type; the session it opens is the one that sets the new password. */
    override suspend fun verifyRecoveryCode(email: String, code: String) = authCall {
        beginNewSession()
        auth.verifyEmailOtp(OtpType.Email.RECOVERY, email, code)
    }

    override val usesGoogleNonce: Boolean get() = true

    /**
     * Native Google sign-in: Credential Manager's ID token goes straight to Supabase (no browser, no client secret).
     * [nonce] is the RAW nonce; Google got its SHA-256. The provider has "Skip nonce checks" off, so a missing or
     * wrong nonce is rejected by the server.
     *
     * If an account with the same email and a password already exists, Supabase links the Google identity to that
     * same user (same uid, same data) instead of creating a second one.
     */
    override suspend fun signInWithGoogle(idToken: String, nonce: String?) = authCall {
        beginNewSession()
        auth.signInWith(IDToken, redirectUrl = null) {
            this.idToken = idToken
            this.provider = Google
            this.nonce = nonce
        }
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

    /**
     * `signOut(OTHERS)` asks the server to invalidate the refresh token of every session except this one, which stays
     * signed in (supabase-kt does not clear the local session for this scope).
     */
    override suspend fun signOutOtherSessions() = authCall {
        auth.signOut(SignOutScope.OTHERS)
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
