package com.example.nightwatchprotocol.data.avatar

import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.profile.RemoteProfile
import com.example.nightwatchprotocol.data.profile.SupabaseProfileRepository
import com.example.nightwatchprotocol.data.supabase.unusedPostgrest
import com.example.nightwatchprotocol.data.supabase.unusedRecoveryEmailCodeApi
import java.io.IOException
import kotlin.io.encoding.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeAvatarStorage : AvatarStorage {
    val files = mutableMapOf<String, ByteArray>()
    val uploads = mutableListOf<String>()
    val deletes = mutableListOf<String>()
    var uploadFails = false
    var deleteFails = false
    var downloadFails = false

    override suspend fun upload(path: String, bytes: ByteArray, format: AvatarFormat) {
        if (uploadFails) throw IOException("sin red")
        uploads += path
        files[path] = bytes
    }

    override suspend fun download(path: String): ByteArray {
        if (downloadFails) throw IOException("sin red")
        return files[path] ?: throw IOException("no existe")
    }

    override suspend fun delete(path: String) {
        if (deleteFails) throw IOException("sin red")
        deletes += path
        files.remove(path)
    }
}

class AvatarStorageTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    // ---- pure decisions ----

    @Test
    fun detectAvatarFormat_recognisesJpegByItsFirstBytes() {
        assertEquals(AvatarFormat.Jpeg, detectAvatarFormat(jpeg))
        assertNull(detectAvatarFormat(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))) // PNG
        assertNull(detectAvatarFormat(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertNull(detectAvatarFormat(ByteArray(0)))
    }

    @Test
    fun avatarObjectPath_isTheUserFolderPlusAvatarDotExtension() {
        assertEquals("user-1/avatar.jpg", avatarObjectPath("user-1", AvatarFormat.Jpeg))
        assertEquals("image/jpeg", AvatarFormat.Jpeg.mimeType)
    }

    @Test
    fun isOwnAvatarPath_onlyAcceptsAFileDirectlyInTheUsersFolder() {
        assertTrue(isOwnAvatarPath("u1", "u1/avatar.jpg"))
        assertFalse(isOwnAvatarPath("u1", "u2/avatar.jpg"))
        assertFalse(isOwnAvatarPath("u1", "u1/../u2/avatar.jpg"))
        assertFalse(isOwnAvatarPath("u1", "u1/sub/avatar.jpg"))
        assertFalse(isOwnAvatarPath("u1", "u10/avatar.jpg"))
        assertFalse(isOwnAvatarPath("u1", "u1/"))
        assertFalse(isOwnAvatarPath("u1", ""))
        assertFalse(isOwnAvatarPath("u1", null))
        assertFalse(isOwnAvatarPath("", "/avatar.jpg"))
    }

    @Test
    fun canUploadAvatar_needsJpegWithinTheBucketLimit() {
        assertTrue(canUploadAvatar(jpeg))
        assertTrue(canUploadAvatar(jpeg + ByteArray(AVATAR_STORAGE_MAX_BYTES - jpeg.size)))
        assertFalse(canUploadAvatar(jpeg + ByteArray(AVATAR_STORAGE_MAX_BYTES)))
        assertFalse(canUploadAvatar(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun theProcessorsOutputFitsTheBucketLimit() {
        // The processor aims at AVATAR_MAX_BYTES (100 KB); the bucket allows 256 KB, so a normal photo always fits.
        assertTrue(AVATAR_MAX_BYTES < AVATAR_STORAGE_MAX_BYTES)
    }

    // ---- SupabaseProfileRepository: avatar paths that never reach the database ----

    private fun repository(storage: FakeAvatarStorage, userId: String? = "u1") =
        SupabaseProfileRepository(unusedPostgrest(), storage, currentUserId = { userId }, unusedRecoveryEmailCodeApi())

    private suspend fun assertFails(kind: FailureKind, call: suspend () -> Unit) {
        try {
            call()
            fail("debía lanzar")
        } catch (e: DataAccessException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun saveAvatar_rejectsWhatTheBucketWouldRefuse_withoutUploadingAnything() = runTest {
        val storage = FakeAvatarStorage()
        val repo = repository(storage)

        assertFails(FailureKind.Unknown) { repo.saveAvatar("x", Base64.Default.encode(byteArrayOf(1, 2, 3, 4))) }
        assertFails(FailureKind.Unknown) { repo.saveAvatar("x", "no es base64 !!") }
        assertTrue(storage.uploads.isEmpty())
    }

    @Test
    fun saveAvatar_uploadFailure_isClassified_andTheProfileRowIsNeverTouched() = runTest {
        val storage = FakeAvatarStorage().apply { uploadFails = true }

        // unusedPostgrest() would throw an AssertionError if the path were saved after a failed upload.
        assertFails(FailureKind.Offline) { repository(storage).saveAvatar("x", Base64.Default.encode(jpeg)) }
    }

    @Test
    fun removeAvatar_deleteFailure_leavesThePathAlone() = runTest {
        val storage = FakeAvatarStorage().apply { deleteFails = true }

        assertFails(FailureKind.Offline) { repository(storage).removeAvatar("x") }
    }

    @Test
    fun downloadAvatar_followsOnlyTheUsersOwnPath() = runTest {
        val storage = FakeAvatarStorage().apply {
            files["u1/avatar.jpg"] = jpeg
            files["u2/avatar.jpg"] = byteArrayOf(9)
        }
        val repo = repository(storage)

        assertArrayEquals(jpeg, repo.downloadAvatar("x", RemoteProfile(avatarPath = "u1/avatar.jpg")))
        assertNull(repo.downloadAvatar("x", RemoteProfile(avatarPath = "u2/avatar.jpg")))
        assertNull(repo.downloadAvatar("x", RemoteProfile(avatarPath = null)))
    }

    @Test
    fun downloadAvatar_failureIsClassified() = runTest {
        val storage = FakeAvatarStorage().apply { downloadFails = true }

        assertFails(FailureKind.Offline) { repository(storage).downloadAvatar("x", RemoteProfile(avatarPath = "u1/avatar.jpg")) }
    }

    @Test
    fun withoutASession_theAvatarCallsFailAsPermissionDenied() = runTest {
        val repo = repository(FakeAvatarStorage(), userId = null)

        assertFails(FailureKind.PermissionDenied) { repo.saveAvatar("x", Base64.Default.encode(jpeg)) }
        assertFails(FailureKind.PermissionDenied) { repo.removeAvatar("x") }
        assertFails(FailureKind.PermissionDenied) { repo.downloadAvatar("x", RemoteProfile(avatarPath = "u1/avatar.jpg")) }
    }
}
