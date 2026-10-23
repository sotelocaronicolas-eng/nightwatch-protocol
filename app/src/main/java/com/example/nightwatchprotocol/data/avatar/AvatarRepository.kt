package com.example.nightwatchprotocol.data.avatar

import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.data.social.NoSocialAvatarRepository
import com.example.nightwatchprotocol.data.social.SocialAvatarRepository
import com.example.nightwatchprotocol.data.social.SocialResult
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [Synced]: the profile photo reached the server (and Social, if the person has it). [LocalOnly]: the server write failed, so
 * the photo is only on this phone. [SocialFailed]: the profile photo is saved, but the copy in Social could not be updated.
 * [SocialRemoveFailed]: the profile photo is gone, but the copy in Social could not be deleted (it stays there, with its key unchanged).
 */
enum class AvatarSyncResult { Synced, LocalOnly, SocialFailed, SocialRemoveFailed }

/**
 * Single source of the current user's avatar bytes, shared by every screen that shows it. When [socialAvatars] is given, saving or
 * removing the photo also updates the person's photo in Social; if that fails the profile photo is still saved.
 */
class AvatarRepository(
    private val profileRepository: ProfileRepository,
    private val cache: AvatarCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val socialAvatars: SocialAvatarRepository = NoSocialAvatarRepository,
) {
    private val _avatar = MutableStateFlow<ByteArray?>(null)
    val avatar: StateFlow<ByteArray?> = _avatar.asStateFlow()

    private var loadedUid: String? = null
    private var refreshedUid: String? = null

    /**
     * Shows the local copy first, then refreshes from the server. A failed or slow read keeps what is
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

        // Not clearing the local copy when the server has no avatar: it may be a local photo that has not synced yet.
        val remoteBytes = remote.avatarBase64?.let(::decodeOrNull)
            ?: runCatchingKeepCancellation { profileRepository.downloadAvatar(uid, remote) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: return
        if (loadedUid != uid || remoteBytes.contentEquals(cached)) return

        withContext(ioDispatcher) { cache.write(uid, remoteBytes) }
        if (loadedUid == uid) _avatar.value = remoteBytes
    }

    /**
     * Stores the photo locally first (it shows at once and works offline) and then on the server.
     * A failed or timed-out server write keeps the local photo and reports [AvatarSyncResult.LocalOnly].
     */
    suspend fun save(uid: String, jpegBytes: ByteArray): AvatarSyncResult {
        adopt(uid)
        withContext(ioDispatcher) { cache.write(uid, jpegBytes) }
        _avatar.value = jpegBytes
        val synced = runCatchingKeepCancellation {
            profileRepository.saveAvatar(uid, Base64.Default.encode(jpegBytes))
        }.isSuccess
        if (!synced) return AvatarSyncResult.LocalOnly
        return socialResult { socialAvatars.syncMine(jpegBytes) }
    }

    /** Removes the photo locally and on the server; the avatar goes back to the initial either way. */
    suspend fun remove(uid: String): AvatarSyncResult {
        adopt(uid)
        withContext(ioDispatcher) { cache.delete(uid) }
        _avatar.value = null
        val synced = runCatchingKeepCancellation { profileRepository.removeAvatar(uid) }.isSuccess
        if (!synced) return AvatarSyncResult.LocalOnly
        return socialResult(AvatarSyncResult.SocialRemoveFailed) { socialAvatars.syncMine(null) }
    }

    /**
     * Puts into Social a photo that was saved before the person had Social (or before Social had photos). Quiet: it does nothing
     * without a photo or when Social already has one, and a failure changes nothing (it is tried again the next time).
     */
    suspend fun syncSocialIfMissing() {
        val photo = _avatar.value ?: return
        runCatchingKeepCancellation { socialAvatars.ensureMine(photo) }
    }

    // The copy in Social never decides whether the profile photo was saved: a failure here is only reported.
    private suspend fun socialResult(
        failure: AvatarSyncResult = AvatarSyncResult.SocialFailed,
        sync: suspend () -> SocialResult<Unit>,
    ): AvatarSyncResult {
        val result = runCatchingKeepCancellation { sync() }.getOrNull()
        return if (result is SocialResult.Success) AvatarSyncResult.Synced else failure
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
