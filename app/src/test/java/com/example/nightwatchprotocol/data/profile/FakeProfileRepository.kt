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

    /** What a backend that keeps the photo as a file returns from [downloadAvatar]. */
    var downloadAvatarResult: Result<ByteArray?> = Result.success(null)
    var downloadAvatarCallCount = 0

    override suspend fun downloadAvatar(uid: String, profile: RemoteProfile): ByteArray? {
        downloadAvatarCallCount++
        return downloadAvatarResult.getOrThrow()
    }

    var saveExtrasResult: Result<Unit> = Result.success(Unit)
    var savedExtras = mutableListOf<Pair<String?, String?>>()

    override suspend fun saveExtras(uid: String, gender: String?, birthDate: String?) {
        savedExtras += gender to birthDate
        saveExtrasResult.getOrThrow()
    }

    override suspend fun saveRecoveryEmail(uid: String, recoveryEmail: String) {
        savedRecoveryEmails += recoveryEmail
        saveRecoveryEmailResult.getOrThrow()
    }

    /** Set to true to behave like the backend that saves the recovery email only after a code is confirmed. */
    var usesCode = false
    var requestRecoveryEmailCodeResult: Result<Unit> = Result.success(Unit)
    var verifyRecoveryEmailCodeResult: Result<Unit> = Result.success(Unit)
    val requestedRecoveryEmailCodes = mutableListOf<String>()
    val verifiedRecoveryEmailCodes = mutableListOf<String>()

    override val verifiesRecoveryEmailWithCode: Boolean get() = usesCode

    override suspend fun requestRecoveryEmailCode(email: String) {
        requestedRecoveryEmailCodes += email
        requestRecoveryEmailCodeResult.getOrThrow()
    }

    override suspend fun verifyRecoveryEmailCode(code: String) {
        verifiedRecoveryEmailCodes += code
        verifyRecoveryEmailCodeResult.getOrThrow()
    }
}
