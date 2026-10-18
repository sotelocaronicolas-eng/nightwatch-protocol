package com.example.nightwatchprotocol.data.avatar

import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.profile.RemoteProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A backend that stores the photo as a file (Supabase Storage): the profile only carries its path. */
@OptIn(ExperimentalCoroutinesApi::class)
class AvatarRepositoryFileStorageTest {
    private val localPhoto = byteArrayOf(1, 1, 1)
    private val remotePhoto = byteArrayOf(9, 9, 9)

    private val profile = FakeProfileRepository().apply {
        getProfileResult = Result.success(RemoteProfile(avatarPath = "uid-1/avatar.jpg"))
    }
    private val cache = FakeAvatarCache()

    private fun repository(testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        AvatarRepository(profile, cache, UnconfinedTestDispatcher(testScheduler))

    @Test
    fun downloadedPhoto_isShownAndCached() = runTest {
        profile.downloadAvatarResult = Result.success(remotePhoto)
        val repo = repository(testScheduler)

        repo.load("uid-1")

        assertArrayEquals(remotePhoto, repo.avatar.value)
        assertArrayEquals(remotePhoto, cache.files["uid-1"])
    }

    @Test
    fun downloadFailure_keepsTheLocalCopy() = runTest {
        cache.files["uid-1"] = localPhoto
        profile.downloadAvatarResult = Result.failure(RuntimeException("sin red"))
        val repo = repository(testScheduler)

        repo.load("uid-1")

        assertArrayEquals(localPhoto, repo.avatar.value)
        assertArrayEquals(localPhoto, cache.files["uid-1"])
    }

    @Test
    fun noPhotoOnTheServer_doesNotClearAnUnsyncedLocalCopy() = runTest {
        cache.files["uid-1"] = localPhoto
        profile.downloadAvatarResult = Result.success(null)
        val repo = repository(testScheduler)

        repo.load("uid-1")

        assertArrayEquals(localPhoto, repo.avatar.value)
    }

    @Test
    fun theSessionRefreshesOnce_soThePhotoIsDownloadedOnlyOnce() = runTest {
        profile.downloadAvatarResult = Result.success(remotePhoto)
        val repo = repository(testScheduler)

        repo.load("uid-1")
        repo.load("uid-1")

        assertEquals(1, profile.downloadAvatarCallCount)
    }

    @Test
    fun emptyDownload_isTreatedAsNoPhoto() = runTest {
        profile.downloadAvatarResult = Result.success(ByteArray(0))
        val repo = repository(testScheduler)

        repo.load("uid-1")

        assertNull(repo.avatar.value)
    }
}
