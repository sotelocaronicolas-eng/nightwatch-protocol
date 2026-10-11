package com.example.nightwatchprotocol.data.profile

import kotlinx.coroutines.awaitCancellation

class FakeProfileRepository : ProfileRepository {
    var getProfileResult: Result<RemoteProfile> = Result.success(RemoteProfile())

    /** Suspends forever, like a Firestore read with no network. */
    var getProfileNeverCompletes: Boolean = false

    var getProfileCallCount = 0

    override suspend fun getProfile(uid: String): RemoteProfile {
        getProfileCallCount++
        if (getProfileNeverCompletes) awaitCancellation()
        return getProfileResult.getOrThrow()
    }
}
