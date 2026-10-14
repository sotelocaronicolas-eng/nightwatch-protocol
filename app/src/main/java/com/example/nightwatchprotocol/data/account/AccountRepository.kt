package com.example.nightwatchprotocol.data.account

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
}
