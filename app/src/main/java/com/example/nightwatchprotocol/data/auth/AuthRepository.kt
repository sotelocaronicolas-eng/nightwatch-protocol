package com.example.nightwatchprotocol.data.auth

/** Everything about who the user is: sign-in, sign-up, password recovery and the 6-digit codes they use. */
interface AuthRepository {
    val currentUserEmail: String?
    val currentUserUid: String?

    /**
     * Returns once [currentUserEmail] and [currentUserUid] can be trusted. The stored session is loaded
     * asynchronously, so a read before this returns can say "nobody" for a user who is signed in. Call it once at
     * startup (and at the start of any background work that reads the session) before reading them.
     */
    suspend fun awaitSessionReady()

    suspend fun signIn(email: String, password: String)

    /**
     * Creates the account. The project asks for the email to be confirmed, so no session is opened: it fails with
     * [AuthError.EmailConfirmationRequired] and the account waits for [verifySignUpCode].
     */
    suspend fun register(name: String, email: String, password: String)

    /**
     * Confirms an account created by [register] with the 6-digit code from its email, and opens the session.
     */
    suspend fun verifySignUpCode(email: String, code: String)

    /** Sends a new sign-up code to [email]. */
    suspend fun resendSignUpCode(email: String)

    /**
     * Starts a password recovery for [email]: a 6-digit code is emailed. Throws [AuthError.UserNotFound] for an email
     * without an account (and [AuthError.GoogleAccountOnly] when the account has no password), so the screens can say so.
     */
    suspend fun sendPasswordResetEmail(email: String)

    /**
     * Checks the recovery code and opens a session, whose only purpose is to set the new password (through
     * [com.example.nightwatchprotocol.data.account.AccountRepository.updatePassword]) before signing out again.
     */
    suspend fun verifyRecoveryCode(email: String, code: String)

    /**
     * Native Google sign-in. [nonce] is the raw nonce whose SHA-256 was sent to Google when the ID token was asked for
     * (see [GoogleSignInAttempt]); the server checks that the two match.
     */
    suspend fun signInWithGoogle(idToken: String, nonce: String)

    fun signOut()

    /**
     * Really revokes the sessions of the user's other devices, on the server, by invalidating their refresh tokens
     * (their already-issued access tokens keep working until they expire). Throws `AuthError` on failure.
     */
    suspend fun signOutOtherSessions()
}
