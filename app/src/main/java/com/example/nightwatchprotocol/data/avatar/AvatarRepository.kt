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

/**
 * Single source of the current user's avatar bytes, shared by every screen that shows it.
 * Stage 1 only loads; saving and removing arrive with the profile editor.
 */
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
        val remoteBytes = remote.avatarBase64?.let(::decodeOrNull) ?: return
        if (loadedUid != uid || remoteBytes.contentEquals(cached)) return

        withContext(ioDispatcher) { cache.write(uid, remoteBytes) }
        if (loadedUid == uid) _avatar.value = remoteBytes
    }

    fun clear() {
        loadedUid = null
        refreshedUid = null
        _avatar.value = null
    }

    private fun decodeOrNull(base64: String): ByteArray? =
        runCatching { Base64.Default.decode(base64) }.getOrNull()?.takeIf { it.isNotEmpty() }
}
