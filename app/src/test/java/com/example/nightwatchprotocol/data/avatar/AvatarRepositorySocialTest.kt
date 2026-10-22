package com.example.nightwatchprotocol.data.avatar

import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.social.FakeSocialAvatarRepository
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.fakeJpeg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saving or removing the profile photo also updates the photo in Social, and a failure there never loses the profile photo. */
@OptIn(ExperimentalCoroutinesApi::class)
class AvatarRepositorySocialTest {
    private val photo = fakeJpeg(3)

    private val profile = FakeProfileRepository()
    private val cache = FakeAvatarCache()
    private val social = FakeSocialAvatarRepository()

    private fun repository(testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        AvatarRepository(profile, cache, UnconfinedTestDispatcher(testScheduler), social)

    @Test
    fun save_updatesSocialWithTheSamePhoto() = runTest {
        val repo = repository(testScheduler)

        val result = repo.save("uid-1", photo)

        assertEquals(AvatarSyncResult.Synced, result)
        assertEquals(1, social.syncCalls.size)
        assertArrayEquals(photo, social.syncCalls[0])
    }

    @Test
    fun save_whenSocialFails_theProfilePhotoIsSavedAnyway_andItIsReported() = runTest {
        social.syncResult = SocialResult.Failure(SocialError.Offline)
        val repo = repository(testScheduler)

        val result = repo.save("uid-1", photo)

        assertEquals(AvatarSyncResult.SocialFailed, result)
        assertArrayEquals(photo, repo.avatar.value)
        assertArrayEquals(photo, cache.files["uid-1"])
        assertEquals(1, profile.savedAvatars.size)
    }

    @Test
    fun save_whenTheProfileWriteFails_socialIsNotTouched() = runTest {
        profile.saveAvatarResult = Result.failure(RuntimeException("sin red"))
        val repo = repository(testScheduler)

        val result = repo.save("uid-1", photo)

        assertEquals(AvatarSyncResult.LocalOnly, result)
        assertTrue(social.syncCalls.isEmpty())
    }

    @Test
    fun remove_updatesSocialWithNoPhoto() = runTest {
        val repo = repository(testScheduler)
        repo.save("uid-1", photo)

        val result = repo.remove("uid-1")

        assertEquals(AvatarSyncResult.Synced, result)
        assertNull(repo.avatar.value)
        assertEquals(2, social.syncCalls.size)
        assertNull(social.syncCalls[1])
    }

    @Test
    fun remove_whenSocialFails_theProfilePhotoIsStillRemoved() = runTest {
        val repo = repository(testScheduler)
        repo.save("uid-1", photo)
        social.syncResult = SocialResult.Failure(SocialError.PermissionDenied)

        val result = repo.remove("uid-1")

        assertEquals(AvatarSyncResult.SocialFailed, result)
        assertNull(repo.avatar.value)
        assertNull(cache.files["uid-1"])
        assertEquals(1, profile.removeAvatarCallCount)
    }

    @Test
    fun syncSocialIfMissing_sendsThePhotoOnScreen() = runTest {
        val repo = repository(testScheduler)
        repo.save("uid-1", photo)
        social.syncCalls.clear()

        repo.syncSocialIfMissing()

        assertEquals(1, social.ensureCalls.size)
        assertArrayEquals(photo, social.ensureCalls[0])
        assertTrue(social.syncCalls.isEmpty())
    }

    @Test
    fun syncSocialIfMissing_withoutAPhoto_doesNothing() = runTest {
        val repo = repository(testScheduler)

        repo.syncSocialIfMissing()

        assertTrue(social.ensureCalls.isEmpty())
    }

    @Test
    fun syncSocialIfMissing_whenItFails_changesNothing() = runTest {
        val repo = repository(testScheduler)
        repo.save("uid-1", photo)
        social.syncResult = SocialResult.Failure(SocialError.Offline)

        repo.syncSocialIfMissing()

        assertArrayEquals(photo, repo.avatar.value)
    }
}
