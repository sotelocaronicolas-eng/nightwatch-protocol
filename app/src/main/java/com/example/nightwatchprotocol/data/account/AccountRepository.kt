package com.example.nightwatchprotocol.data.account

data class AccountProfile(
    val displayName: String?,
    val email: String?,
    val creationTimeMillis: Long?,
    val hasPassword: Boolean,
)

/**
 * Account operations for the signed-in user. Failures are thrown as
 * [com.example.nightwatchprotocol.data.auth.AuthError]; an old session raises
 * `AuthError.RecentLoginRequired` from the operations that need a recent sign-in.
 */
interface AccountRepository {
    /** The signed-in user's data, or null when nobody is signed in. */
    fun currentProfile(): AccountProfile?

    suspend fun updateDisplayName(name: String)

    /** Proves the user knows [currentPassword]; refreshes the "recent login" window. */
    suspend fun reauthenticate(currentPassword: String)

    /** Sets a new password. Throws `AuthError.RecentLoginRequired` when the session is too old. */
    suspend fun updatePassword(newPassword: String)

    /** Reauthenticates with [currentPassword] and then sets [newPassword]. */
    suspend fun changePassword(currentPassword: String, newPassword: String)

    /**
     * Sends a 6-digit code to [newEmail]; the account email only changes once [verifyEmailChangeCode] accepts it.
     */
    suspend fun requestEmailChange(newEmail: String)

    /** Confirms the email change with the code sent to [newEmail]; the account email is [newEmail] afterwards. */
    suspend fun verifyEmailChangeCode(newEmail: String, code: String)

    /** Sends a new code to [newEmail], without changing the pending request. */
    suspend fun resendEmailChangeCode(newEmail: String)

    /**
     * Run after an email change was confirmed: unlinks the Google accounts of this user whose email is not
     * [newEmail] (they would still open the account with the old address). It never throws; false means something
     * could not be unlinked.
     */
    suspend fun unlinkOutdatedGoogleAccounts(newEmail: String): Boolean
}
