package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.data.auth.AuthError
import kotlin.coroutines.cancellation.CancellationException

/** What `users/{uid}/profile/main` holds. A missing document or field is simply null. */
data class RemoteProfile(
    val recoveryEmail: String? = null,
    val avatarBase64: String? = null,
    /** One of the [Gender] keys as stored; unknown values are ignored by [Gender.fromKey]. */
    val gender: String? = null,
    /** ISO `yyyy-MM-dd` as stored; parsed by [BirthDate.parseIso]. */
    val birthDate: String? = null,
    /** Where the photo lives when it is stored as a file (Supabase Storage); null for Base64 backends. */
    val avatarPath: String? = null,
)

interface ProfileRepository {
    /** Throws when the read fails or takes longer than the repository's timeout. */
    suspend fun getProfile(uid: String): RemoteProfile

    /** Merges the avatar into the profile document. Throws on failure or timeout; the write may still sync later. */
    suspend fun saveAvatar(uid: String, avatarBase64: String)

    /** Deletes only the avatar field. Throws on failure or timeout. */
    suspend fun removeAvatar(uid: String)

    /** Merges the recovery email into the profile document. Throws on failure or timeout. */
    suspend fun saveRecoveryEmail(uid: String, recoveryEmail: String)

    /** Merges gender (a [Gender] key) and birth date (ISO text); a null removes that field. Throws on failure or timeout. */
    suspend fun saveExtras(uid: String, gender: String?, birthDate: String?)

    /**
     * The photo bytes when the backend stores the photo as a file ([RemoteProfile.avatarPath]) instead of inside
     * the profile ([RemoteProfile.avatarBase64]); null when there is nothing to download. Throws on failure.
     */
    suspend fun downloadAvatar(uid: String, profile: RemoteProfile): ByteArray? = null

    /**
     * True when the recovery email is saved only after a 6-digit code sent to it was confirmed (Supabase): the screen
     * then uses [requestRecoveryEmailCode] and [verifyRecoveryEmailCode] instead of [saveRecoveryEmail]. False when
     * [saveRecoveryEmail] stores it directly (Firebase).
     */
    val verifiesRecoveryEmailWithCode: Boolean get() = false

    /** Sends a code to [email]. Throws `AuthError` (RateLimited, SameAsPrimaryEmail, EmailSendFailed, NetworkError...). */
    suspend fun requestRecoveryEmailCode(email: String): Unit = throw AuthError.Unknown

    /** Checks [code]; the server saves the pending email as the recovery email. Throws `AuthError` on failure. */
    suspend fun verifyRecoveryEmailCode(code: String): Unit = throw AuthError.Unknown
}

/** Like runCatching, but a cancelled coroutine is never swallowed as an ordinary failure. */
inline fun <T> runCatchingKeepCancellation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
