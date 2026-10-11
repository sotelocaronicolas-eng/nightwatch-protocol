package com.example.nightwatchprotocol.data.profile

import kotlin.coroutines.cancellation.CancellationException

/** What `users/{uid}/profile/main` holds. A missing document or field is simply null. */
data class RemoteProfile(
    val recoveryEmail: String? = null,
    val avatarBase64: String? = null,
)

class ProfileUnavailableException : Exception("No se pudo leer el perfil a tiempo")

interface ProfileRepository {
    /** Throws when the read fails or takes longer than the repository's timeout. */
    suspend fun getProfile(uid: String): RemoteProfile
}

/** Like runCatching, but a cancelled coroutine is never swallowed as an ordinary failure. */
inline fun <T> runCatchingKeepCancellation(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
