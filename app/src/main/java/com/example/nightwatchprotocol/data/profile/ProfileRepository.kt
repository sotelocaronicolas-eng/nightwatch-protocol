package com.example.nightwatchprotocol.data.profile

import kotlin.coroutines.cancellation.CancellationException

/** What the `profile` row of the user holds. A missing row or column is simply null. */
data class RemoteProfile(
    val recoveryEmail: String? = null,
    val avatarBase64: String? = null,
    /** One of the [Gender] keys as stored; unknown values are ignored by [Gender.fromKey]. */
    val gender: String? = null,
    /** ISO `yyyy-MM-dd` as stored; parsed by [BirthDate.parseIso]. */
    val birthDate: String? = null,
    /** Where the photo lives in the storage bucket; null when there is none. */
    val avatarPath: String? = null,
)

interface ProfileRepository {
    /** Throws when the read fails or takes longer than the repository's timeout. */
    suspend fun getProfile(uid: String): RemoteProfile

    /** Uploads the photo (JPEG bytes, Base64-encoded) and stores its path. Throws on failure or timeout. */
    suspend fun saveAvatar(uid: String, avatarBase64: String)

    /** Deletes the photo file and clears its path. Throws on failure or timeout. */
    suspend fun removeAvatar(uid: String)

    /** Merges gender (a [Gender] key) and birth date (ISO text); a null removes that field. Throws on failure or timeout. */
    suspend fun saveExtras(uid: String, gender: String?, birthDate: String?)

    /** The photo bytes of [RemoteProfile.avatarPath]; null when there is nothing to download. Throws on failure. */
    suspend fun downloadAvatar(uid: String, profile: RemoteProfile): ByteArray?

    /**
     * Sends a 6-digit code to [email], the recovery address. The address is only saved once [verifyRecoveryEmailCode]
     * accepts the code. Throws `AuthError` (RateLimited, SameAsPrimaryEmail, EmailSendFailed, NetworkError...).
     */
    suspend fun requestRecoveryEmailCode(email: String)

    /** Checks [code]; the server saves the pending email as the recovery email. Throws `AuthError` on failure. */
    suspend fun verifyRecoveryEmailCode(code: String)
}

/** Like runCatching, but a cancelled coroutine is never swallowed as an ordinary failure. */
inline fun <T> runCatchingKeepCancellation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
