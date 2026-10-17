package com.example.nightwatchprotocol.data.avatar

import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class AvatarSyncResult { Synced, LocalOnly }

/** Single source of the current user's avatar bytes, shared by every screen that shows it. */
class AvatarRepository(
    private val profileRepository: ProfileRepository,
    private val cache: AvatarCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _avatar = MutableStateFlow<ByteArray?>(null)
    val avatar: StateFlow<ByteArray?> = _avatar.asStateFlow()

    private var loadedUid: String? = null
    private var refreshedUid: String? = null

    /**
     * Shows the local copy first, then refreshes from Firestore. A failed or slow read keeps what is
     * already on screen. A different uid clears the previous user's photo before anything loads.
     * Once a user has been refreshed in this session, later calls (another screen) do nothing.
     */
    suspend fun load(uid: String) {
        if (loadedUid != uid) {
            loadedUid = uid
            refreshedUid = null
            _avatar.value = null
        }
        if (refreshedUid == uid) return

        val cached = withContext(ioDispatcher) { cache.read(uid) }
        if (loadedUid == uid && cached != null) _avatar.value = cached

        val remote = runCatchingKeepCancellation { profileRepository.getProfile(uid) }.getOrNull() ?: return
        if (loadedUid == uid) refreshedUid = uid

        // Not clearing the local copy when Firestore has no avatar: it may be a local photo that has not synced yet.
        val remoteBytes = remote.avatarBase64?.let(::decodeOrNull)
            ?: runCatchingKeepCancellation { profileRepository.downloadAvatar(uid, remote) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: return
        if (loadedUid != uid || remoteBytes.contentEquals(cached)) return

        withContext(ioDispatcher) { cache.write(uid, remoteBytes) }
        if (loadedUid == uid) _avatar.value = remoteBytes
    }

    /**
     * Stores the photo locally first (it shows at once and works offline) and then in Firestore.
     * A failed or timed-out Firestore write keeps the local photo and reports [AvatarSyncResult.LocalOnly].
     */
    suspend fun save(uid: String, jpegBytes: ByteArray): AvatarSyncResult {
        adopt(uid)
        withContext(ioDispatcher) { cache.write(uid, jpegBytes) }
        _avatar.value = jpegBytes
        val synced = runCatchingKeepCancellation {
            profileRepository.saveAvatar(uid, Base64.Default.encode(jpegBytes))
        }.isSuccess
        return if (synced) AvatarSyncResult.Synced else AvatarSyncResult.LocalOnly
    }

    /** Removes the photo locally and in Firestore; the avatar goes back to the initial either way. */
    suspend fun remove(uid: String): AvatarSyncResult {
        adopt(uid)
        withContext(ioDispatcher) { cache.delete(uid) }
        _avatar.value = null
        val synced = runCatchingKeepCancellation { profileRepository.removeAvatar(uid) }.isSuccess
        return if (synced) AvatarSyncResult.Synced else AvatarSyncResult.LocalOnly
    }

    // After a local change the local copy is the truth, so a later load must not overwrite it with an older remote one.
    private fun adopt(uid: String) {
        loadedUid = uid
        refreshedUid = uid
    }

    /** Forgets the photo for good: the in-memory copy and the local file (used when the session is ended remotely). */
    suspend fun wipe(uid: String) {
        withContext(ioDispatcher) { cache.delete(uid) }
        clear()
    }

    fun clear() {
        loadedUid = null
        refreshedUid = null
        _avatar.value = null
    }

    private fun decodeOrNull(base64: String): ByteArray? =
        runCatching { Base64.Default.decode(base64) }.getOrNull()?.takeIf { it.isNotEmpty() }
}
