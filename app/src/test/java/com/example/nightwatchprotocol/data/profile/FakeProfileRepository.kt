package com.example.nightwatchprotocol.data.profile

import kotlinx.coroutines.awaitCancellation

class FakeProfileRepository : ProfileRepository {
    var getProfileResult: Result<RemoteProfile> = Result.success(RemoteProfile())
    var saveAvatarResult: Result<Unit> = Result.success(Unit)
    var removeAvatarResult: Result<Unit> = Result.success(Unit)
    var saveRecoveryEmailResult: Result<Unit> = Result.success(Unit)

    /** Suspends forever, like a Firestore read with no network. */
    var getProfileNeverCompletes: Boolean = false

    var getProfileCallCount = 0
    var savedAvatars = mutableListOf<String>()
    var removeAvatarCallCount = 0
    var savedRecoveryEmails = mutableListOf<String>()

    override suspend fun getProfile(uid: String): RemoteProfile {
        getProfileCallCount++
        if (getProfileNeverCompletes) awaitCancellation()
        return getProfileResult.getOrThrow()
    }

    override suspend fun saveAvatar(uid: String, avatarBase64: String) {
        savedAvatars += avatarBase64
        saveAvatarResult.getOrThrow()
    }

    override suspend fun removeAvatar(uid: String) {
        removeAvatarCallCount++
        removeAvatarResult.getOrThrow()
    }

    override suspend fun saveRecoveryEmail(uid: String, recoveryEmail: String) {
        savedRecoveryEmails += recoveryEmail
        saveRecoveryEmailResult.getOrThrow()
    }
}
