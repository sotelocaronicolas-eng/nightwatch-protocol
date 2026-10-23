package com.example.nightwatchprotocol.data.social

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialAvatarRepositoryTest {
    private val keyA = "11111111-1111-4111-8111-111111111111"
    private val myKey = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

    private val remote = FakeSocialAvatarRemote(myKey)
    private val disk = InMemorySocialPhotoDisk()
    private val profiles = FakeSocialProfileRepository(SocialProfile("valeNightwatch", "NIGHT-7F3K2A", false, myKey, null))
    private var signedIn = true

    private fun repository(io: CoroutineDispatcher, memory: SocialPhotoMemoryCache = SocialPhotoMemoryCache()) =
        DefaultSocialAvatarRepository(remote, profiles, disk, { signedIn }, memory, io)

    // ---- looking at the photo of someone else ----

    @Test
    fun withoutKeyOrVersion_thereIsNoPhoto_andNothingIsRequested() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        assertNull(repo.photo(null, null))
        assertNull(repo.photo(keyA, null))
        assertNull(repo.photo(null, 1))
        assertNull(repo.photo(keyA, 0))
        assertTrue(remote.downloads.isEmpty())
    }

    @Test
    fun aKeyThatIsNotAUuid_isNeverRequested() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        assertNull(repo.photo("../avatars/otra-persona", 1))
        assertTrue(remote.downloads.isEmpty())
    }

    @Test
    fun aPhoto_isDownloadedOnce_thenServedFromMemory() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        remote.files[keyA] = fakeJpeg(1)

        assertArrayEquals(fakeJpeg(1), repo.photo(keyA, 1))
        assertArrayEquals(fakeJpeg(1), repo.photo(keyA, 1))

        assertEquals(listOf(keyA), remote.downloads)
    }

    @Test
    fun afterTheAppRestarts_thePhotoComesFromDisk() = runTest {
        remote.files[keyA] = fakeJpeg(1)
        repository(UnconfinedTestDispatcher(testScheduler)).photo(keyA, 1)

        // A new repository has an empty memory but shares the disk.
        val restarted = repository(UnconfinedTestDispatcher(testScheduler))

        assertArrayEquals(fakeJpeg(1), restarted.photo(keyA, 1))
        assertEquals(1, remote.downloads.size)
    }

    @Test
    fun aNewVersion_isDownloadedAgain_andReplacesTheOldOne() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        remote.files[keyA] = fakeJpeg(1)
        repo.photo(keyA, 1)
        remote.files[keyA] = fakeJpeg(2)

        assertArrayEquals(fakeJpeg(2), repo.photo(keyA, 2))

        assertEquals(2, remote.downloads.size)
        assertEquals(listOf("$keyA-2"), disk.files.keys.toList())
    }

    @Test
    fun withoutASession_thereIsNoPhoto_evenIfItIsCached() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        remote.files[keyA] = fakeJpeg(1)
        repo.photo(keyA, 1)
        signedIn = false

        assertNull(repo.photo(keyA, 1))
        assertEquals(1, remote.downloads.size)
    }

    @Test
    fun whenTheDownloadFails_thereIsNoPhoto_andItIsTriedAgainNextTime() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        remote.files[keyA] = fakeJpeg(1)
        remote.downloadFailure = SocialError.Offline

        assertNull(repo.photo(keyA, 1))

        remote.downloadFailure = null
        assertArrayEquals(fakeJpeg(1), repo.photo(keyA, 1))
        assertEquals(2, remote.downloads.size)
    }

    @Test
    fun withoutPermission_thereIsNoPhoto() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        remote.files[keyA] = fakeJpeg(1)
        remote.downloadFailure = SocialError.PermissionDenied

        assertNull(repo.photo(keyA, 1))
        assertTrue(disk.files.isEmpty())
    }

    @Test
    fun whatIsNotAJpeg_isNeverShownNorKept() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        remote.files[keyA] = byteArrayOf(1, 2, 3, 4)

        assertNull(repo.photo(keyA, 1))
        assertTrue(disk.files.isEmpty())
    }

    @Test
    fun twoScreensAskingForTheSamePhotoAtOnce_downloadItOnce() = runTest {
        val repo = repository(StandardTestDispatcher(testScheduler))
        remote.files[keyA] = fakeJpeg(1)
        remote.downloadMillis = 100

        val both = listOf(async { repo.photo(keyA, 1) }, async { repo.photo(keyA, 1) }).awaitAll()

        assertArrayEquals(fakeJpeg(1), both[0])
        assertArrayEquals(fakeJpeg(1), both[1])
        assertEquals(1, remote.downloads.size)
    }

    // ---- the own photo ----

    @Test
    fun syncMine_uploadsTheFile_tellsTheServer_andKeepsThePhotoForTheArena() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        val result = repo.syncMine(fakeJpeg(5))

        assertTrue(result is SocialResult.Success)
        assertEquals(listOf(myKey), remote.uploads)
        assertEquals(listOf(true), remote.marks)
        // The version the server gave (1) is already in the cache: no download to see the own photo.
        assertArrayEquals(fakeJpeg(5), repo.photo(myKey, 1))
        assertTrue(remote.downloads.isEmpty())
    }

    @Test
    fun syncMine_withNull_deletesTheFile_tellsTheServer_andForgetsThePhoto() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        repo.syncMine(fakeJpeg(5))
        profiles.profile = profiles.profile?.copy(avatarVersion = 1)

        val result = repo.syncMine(null)

        assertTrue(result is SocialResult.Success)
        assertEquals(listOf(myKey), remote.deletes)
        assertEquals(listOf(true, false), remote.marks)
        assertTrue(disk.files.isEmpty())
        assertNull(repo.photo(myKey, 1))
    }

    @Test
    fun syncMine_withNull_whenTheFileCannotBeDeleted_doesNotTellTheServer_andKeepsTheKey() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        repo.syncMine(fakeJpeg(5))
        profiles.profile = profiles.profile?.copy(avatarVersion = 1)
        remote.marks.clear()
        remote.deleteFailure = SocialError.Offline

        val result = repo.syncMine(null)

        assertEquals(SocialError.Offline, (result as SocialResult.Failure).error)
        assertEquals(listOf(myKey), remote.deletes)
        // The server was never asked to rotate the key: the file is still there, under the key the person still has.
        assertTrue(remote.marks.isEmpty())
        assertEquals(myKey, remote.myKey)
        assertEquals(1, remote.version)
        assertArrayEquals(fakeJpeg(5), remote.files[myKey])
        // The photo is still theirs in Social, so the caches keep it.
        assertArrayEquals(fakeJpeg(5), repo.photo(myKey, 1))
    }

    @Test
    fun syncMine_withNull_afterAFailedDelete_worksOnTheNextTry() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        repo.syncMine(fakeJpeg(5))
        profiles.profile = profiles.profile?.copy(avatarVersion = 1)
        remote.deleteFailure = SocialError.Offline
        repo.syncMine(null)
        remote.deleteFailure = null

        val result = repo.syncMine(null)

        assertTrue(result is SocialResult.Success)
        assertEquals(listOf(myKey, myKey), remote.deletes)
        assertEquals(listOf(true, false), remote.marks)
        assertNull(remote.version)
        assertTrue(remote.files.isEmpty())
    }

    @Test
    fun syncMine_withNull_whenTheDeleteWorksButTheMarkFails_keepsTheCaches() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))
        repo.syncMine(fakeJpeg(5))
        profiles.profile = profiles.profile?.copy(avatarVersion = 1)
        remote.markFailure = SocialError.Offline

        val result = repo.syncMine(null)

        assertEquals(SocialError.Offline, (result as SocialResult.Failure).error)
        assertTrue(remote.files.isEmpty())
        assertEquals(1, remote.version)
        // The removal is not finished, so nothing is forgotten; trying again finishes it.
        assertArrayEquals(fakeJpeg(5), repo.photo(myKey, 1))
        remote.markFailure = null
        assertTrue(repo.syncMine(null) is SocialResult.Success)
        assertNull(remote.version)
    }

    @Test
    fun syncMine_withNull_whenSocialHasNoPhoto_doesNothing() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        assertTrue(repo.syncMine(null) is SocialResult.Success)
        assertTrue(remote.deletes.isEmpty())
        assertTrue(remote.marks.isEmpty())
    }

    @Test
    fun syncMine_whenSocialIsNotEnabled_doesNothing_andIsNotAFailure() = runTest {
        profiles.profile = null
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        assertTrue(repo.syncMine(fakeJpeg(5)) is SocialResult.Success)
        assertTrue(remote.uploads.isEmpty())
        assertTrue(remote.marks.isEmpty())
    }

    @Test
    fun syncMine_whenTheServerDoesNotSendTheKeyYet_doesNothing() = runTest {
        profiles.profile = profiles.profile?.copy(avatarKey = null)
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        assertTrue(repo.syncMine(fakeJpeg(5)) is SocialResult.Success)
        assertTrue(remote.uploads.isEmpty())
    }

    @Test
    fun syncMine_whenTheUploadFails_failsAndDoesNotTellTheServer() = runTest {
        remote.uploadFailure = SocialError.Offline
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        val result = repo.syncMine(fakeJpeg(5))

        assertEquals(SocialError.Offline, (result as SocialResult.Failure).error)
        assertTrue(remote.marks.isEmpty())
        assertTrue(disk.files.isEmpty())
    }

    @Test
    fun syncMine_whenTheServerMarkFails_fails() = runTest {
        remote.markFailure = SocialError.PermissionDenied
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        val result = repo.syncMine(fakeJpeg(5))

        assertEquals(SocialError.PermissionDenied, (result as SocialResult.Failure).error)
        assertTrue(disk.files.isEmpty())
    }

    @Test
    fun syncMine_whenTheFileIsNotAJpegOrTooBig_isRefusedBeforeAnyCall() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        assertEquals(SocialError.InvalidInput, (repo.syncMine(byteArrayOf(1, 2, 3)) as SocialResult.Failure).error)
        assertEquals(SocialError.InvalidInput, (repo.syncMine(fakeJpeg(1, size = 262_145)) as SocialResult.Failure).error)
        assertTrue(remote.uploads.isEmpty())
    }

    @Test
    fun syncMine_withoutASession_failsAsNoSession() = runTest {
        signedIn = false
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        assertEquals(SocialError.NoSession, (repo.syncMine(fakeJpeg(5)) as SocialResult.Failure).error)
        assertEquals(0, profiles.getCalls)
    }

    @Test
    fun syncMine_whenTheProfileCannotBeRead_failsWithThatError() = runTest {
        profiles.failure = SocialError.Offline
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        assertEquals(SocialError.Offline, (repo.syncMine(fakeJpeg(5)) as SocialResult.Failure).error)
        assertTrue(remote.uploads.isEmpty())
    }

    @Test
    fun ensureMine_uploadsOnlyWhenSocialHasNoPhotoYet() = runTest {
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        repo.ensureMine(fakeJpeg(5))
        assertEquals(listOf(myKey), remote.uploads)

        // Social already has a photo: the one saved before is not uploaded again.
        profiles.profile = profiles.profile?.copy(avatarVersion = 1)
        repo.ensureMine(fakeJpeg(6))
        assertEquals(1, remote.uploads.size)

        // Nothing to put in Social.
        profiles.profile = profiles.profile?.copy(avatarVersion = null)
        repo.ensureMine(null)
        assertEquals(1, remote.uploads.size)
    }

    @Test
    fun theRepositoryWithoutSocial_hasNoPhotosAndNothingToSync() = runTest {
        assertNull(NoSocialAvatarRepository.photo(keyA, 1))
        assertTrue(NoSocialAvatarRepository.syncMine(fakeJpeg()) is SocialResult.Success)
        assertTrue(NoSocialAvatarRepository.ensureMine(fakeJpeg()) is SocialResult.Success)
    }
}
