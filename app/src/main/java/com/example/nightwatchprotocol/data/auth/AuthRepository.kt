package com.example.nightwatchprotocol.data.auth

interface AuthRepository {
    val currentUserEmail: String?
    val currentUserUid: String?

    /**
     * Returns once [currentUserEmail] and [currentUserUid] can be trusted. Some backends load the stored session
     * asynchronously, so a read before this returns can say "nobody" for a user who is signed in. Call it once at
     * startup (and at the start of any background work that reads the session) before reading them.
     */
    suspend fun awaitSessionReady()

    /**
     * True when the backend checks a nonce on Google sign-in (Supabase): the screen must then hash a fresh nonce into
     * Credential Manager's request and pass the raw one to [signInWithGoogle]. Firebase does not use it. This also
     * marks the backend whose Google failures are told apart (cancelled / no connection) instead of one generic dialog.
     */
    val usesGoogleNonce: Boolean get() = false

    suspend fun signIn(email: String, password: String)
    suspend fun register(name: String, email: String, password: String)
    /**
     * Starts a password recovery for [email]: a link on Firebase, a 6-digit code on the backend that
     * [recoversPasswordWithCode]. Throws [AuthError.UserNotFound] for an email without an account (and
     * [AuthError.GoogleAccountOnly] when the account has no password), so the screens can say so.
     */
    suspend fun sendPasswordResetEmail(email: String)

    /**
     * True when [sendPasswordResetEmail] sends a code to be checked with [verifyRecoveryCode] (Supabase); false when it
     * sends a link that is opened outside the app (Firebase). The login screen picks the recovery flow from this.
     */
    val recoversPasswordWithCode: Boolean get() = false

    /**
     * Checks the recovery code and opens a session, whose only purpose is to set the new password (through
     * [com.example.nightwatchprotocol.data.account.AccountRepository.updatePassword]) before signing out again.
     */
    suspend fun verifyRecoveryCode(email: String, code: String): Unit = throw AuthError.Unknown

    /** [nonce] is the raw nonce whose SHA-256 was sent to Google; backends that do not use one ignore it. */
    suspend fun signInWithGoogle(idToken: String, nonce: String? = null)
    fun signOut()

    /**
     * Confirms an account created by [register] with the 6-digit code from its email, and opens the session.
     * Only the backend that asks for a code (Supabase, with "Confirm email" on) implements it; Firebase never calls it.
     */
    suspend fun verifySignUpCode(email: String, code: String): Unit = throw AuthError.Unknown

    /** Sends a new sign-up code to [email]. Same availability as [verifySignUpCode]. */
    suspend fun resendSignUpCode(email: String): Unit = throw AuthError.Unknown
}
