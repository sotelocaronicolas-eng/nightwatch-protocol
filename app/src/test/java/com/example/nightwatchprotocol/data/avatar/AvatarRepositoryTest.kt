package com.example.nightwatchprotocol.data.avatar

import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.profile.RemoteProfile
import kotlin.io.encoding.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AvatarRepositoryTest {
    private val localPhoto = byteArrayOf(1, 1, 1)
    private val remotePhoto = byteArrayOf(9, 9, 9)

    private val profile = FakeProfileRepository()
    private val cache = FakeAvatarCache()

    private fun repository(testDispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        AvatarRepository(profile, cache, testDispatcher)

    private fun remoteWith(bytes: ByteArray) =
        Result.success(RemoteProfile(avatarBase64 = Base64.Default.encode(bytes)))

    @Test
    fun remoteAvatar_isShownAndCached() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        profile.getProfileResult = remoteWith(remotePhoto)

        repo.load("uid-1")

        assertArrayEquals(remotePhoto, repo.avatar.value)
        assertArrayEquals(remotePhoto, cache.files["uid-1"])
    }

    @Test
    fun localCopy_isShownBeforeFirestoreAnswers() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        cache.files["uid-1"] = localPhoto
        profile.getProfileNeverCompletes = true

        val job = launch { repo.load("uid-1") }
        advanceUntilIdle()

        assertArrayEquals(localPhoto, repo.avatar.value)
        job.cancel()
    }

    @Test
    fun firestoreFailure_keepsTheLocalCopy() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        cache.files["uid-1"] = localPhoto
        profile.getProfileResult = Result.failure(RuntimeException("offline"))

        repo.load("uid-1")

        assertArrayEquals(localPhoto, repo.avatar.value)
        assertArrayEquals(localPhoto, cache.files["uid-1"])
    }

    @Test
    fun differentRemoteAvatar_replacesTheLocalCopy() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        cache.files["uid-1"] = localPhoto
        profile.getProfileResult = remoteWith(remotePhoto)

        repo.load("uid-1")

        assertArrayEquals(remotePhoto, repo.avatar.value)
        assertArrayEquals(remotePhoto, cache.files["uid-1"])
    }

    @Test
    fun remoteWithoutAvatar_doesNotDeleteALocalPhotoThatMayNotHaveSyncedYet() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        cache.files["uid-1"] = localPhoto
        profile.getProfileResult = Result.success(RemoteProfile(avatarBase64 = null))

        repo.load("uid-1")

        assertArrayEquals(localPhoto, repo.avatar.value)
        assertArrayEquals(localPhoto, cache.files["uid-1"])
    }

    @Test
    fun invalidBase64_isIgnored() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        cache.files["uid-1"] = localPhoto
        profile.getProfileResult = Result.success(RemoteProfile(avatarBase64 = "esto no es base64 !!!"))

        repo.load("uid-1")

        assertArrayEquals(localPhoto, repo.avatar.value)
    }

    @Test
    fun anotherUser_neverSeesThePreviousUsersPhoto() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        cache.files["uid-1"] = localPhoto
        profile.getProfileResult = Result.failure(RuntimeException("offline"))
        repo.load("uid-1")

        repo.load("uid-2")

        assertNull(repo.avatar.value)
    }

    @Test
    fun secondLoadForTheSameUser_doesNotReadFirestoreAgain() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        profile.getProfileResult = remoteWith(remotePhoto)

        repo.load("uid-1")
        repo.load("uid-1")

        assertEquals(1, profile.getProfileCallCount)
    }

    @Test
    fun failedRefresh_isRetriedOnTheNextLoad() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        profile.getProfileResult = Result.failure(RuntimeException("offline"))
        repo.load("uid-1")

        profile.getProfileResult = remoteWith(remotePhoto)
        repo.load("uid-1")

        assertArrayEquals(remotePhoto, repo.avatar.value)
        assertEquals(2, profile.getProfileCallCount)
    }

    @Test
    fun clear_emptiesTheAvatar() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        profile.getProfileResult = remoteWith(remotePhoto)
        repo.load("uid-1")

        repo.clear()

        assertNull(repo.avatar.value)
    }
}
