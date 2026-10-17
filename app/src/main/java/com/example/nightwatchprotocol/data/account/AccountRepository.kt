package com.example.nightwatchprotocol.data.account

import com.example.nightwatchprotocol.data.auth.AuthError

data class AccountProfile(
    val displayName: String?,
    val email: String?,
    val creationTimeMillis: Long?,
    val hasPassword: Boolean,
)

/**
 * Firebase Auth operations for the signed-in user. Failures are thrown as
 * [com.example.nightwatchprotocol.data.auth.AuthError]; an old session raises
 * `AuthError.RecentLoginRequired` from the operations that need a recent sign-in.
 */
interface AccountRepository {
    /** The signed-in user's data from Firebase Auth, or null when nobody is signed in. */
    fun currentProfile(): AccountProfile?

    suspend fun updateDisplayName(name: String)

    /** Proves the user knows [currentPassword]; refreshes the "recent login" window. */
    suspend fun reauthenticate(currentPassword: String)

    /** Sets a new password. Throws `AuthError.RecentLoginRequired` when the session is too old. */
    suspend fun updatePassword(newPassword: String)

    /** Reauthenticates with [currentPassword] and then sets [newPassword]. */
    suspend fun changePassword(currentPassword: String, newPassword: String)

    /** Sends Firebase's verification link to [newEmail]; the address only changes once the link is opened. */
    suspend fun requestEmailChange(newEmail: String)

    /** Reloads the user and says whether the account email is now [newEmail]. */
    suspend fun isEmailChanged(newEmail: String): Boolean

    /**
     * True when [requestEmailChange] sends a 6-digit code to the new address (Supabase), to be checked with
     * [verifyEmailChangeCode]; false when it sends a link that is opened outside the app (Firebase). The screens pick
     * the flow from this, not from the backend.
     */
    val changesEmailWithCode: Boolean get() = false

    /** Confirms the email change with the code sent to [newEmail]; the account email is [newEmail] afterwards. */
    suspend fun verifyEmailChangeCode(newEmail: String, code: String): Unit = throw AuthError.Unknown

    /** Sends a new code to [newEmail], without changing the pending request. */
    suspend fun resendEmailChangeCode(newEmail: String): Unit = throw AuthError.Unknown

    /**
     * Run after an email change was confirmed: unlinks the Google accounts of this user whose email is not
     * [newEmail] (they would still open the account with the old address). It never throws; false means something
     * could not be unlinked. Firebase has nothing to unlink.
     */
    suspend fun unlinkOutdatedGoogleAccounts(newEmail: String): Boolean = true
}
