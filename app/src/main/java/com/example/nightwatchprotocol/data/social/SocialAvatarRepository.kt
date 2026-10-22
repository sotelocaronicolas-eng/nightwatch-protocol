package com.example.nightwatchprotocol.data.social

import com.example.nightwatchprotocol.data.avatar.canUploadAvatar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/*
 * The photos of the people of Social. A photo is a small JPEG in the private bucket `social-avatars` (see `0010_fotos_social.sql`),
 * named by a RANDOM key that the server hands out together with each person (never an Auth id) and a version that goes up each
 * time the photo changes. The app asks for a photo by (key, version): what was already downloaded is kept in memory and on disk
 * under that same pair, so it is never downloaded twice and a new version is a new entry.
 *
 * A photo is decoration: everything here answers "no photo" (null) instead of failing, and the screen keeps the emoji.
 */

/** Photos are looked up by a key that becomes a file name, so only the shape of a UUID is accepted. */
private val UUID_SHAPE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

fun isValidAvatarKey(key: String): Boolean = UUID_SHAPE.matches(key)

interface SocialAvatarRepository {
    /**
     * The JPEG bytes of a person's photo, or null: they have none ([key] or [version] null), there is no session, the call was
     * refused or failed, or what came back is not a photo. Never throws (a cancelled coroutine still cancels).
     */
    suspend fun photo(key: String?, version: Int?): ByteArray?

    /**
     * Makes the person's own photo in Social match [photo] (null = no photo): uploads the file and tells the server, or deletes
     * the file and tells the server. Succeeds without doing anything when Social is not enabled yet. Nothing here touches the
     * profile photo of Editar perfil.
     */
    suspend fun syncMine(photo: ByteArray?): SocialResult<Unit>

    /** Like [syncMine] with a photo, but only when Social has none yet (a photo saved before Social existed). */
    suspend fun ensureMine(photo: ByteArray?): SocialResult<Unit>
}

/** For places that have no Social (tests, previews): nobody has a photo and there is nothing to sync. */
object NoSocialAvatarRepository : SocialAvatarRepository {
    override suspend fun photo(key: String?, version: Int?): ByteArray? = null

    override suspend fun syncMine(photo: ByteArray?): SocialResult<Unit> = SocialResult.Success(Unit)

    override suspend fun ensureMine(photo: ByteArray?): SocialResult<Unit> = SocialResult.Success(Unit)
}

/** Where the photo files live. The only thing that knows how a call travels (Supabase) is its implementation. */
interface SocialAvatarRemote {
    /** Downloads `<key>.jpg` with the person's session (the bucket is private). */
    suspend fun download(key: String): SocialResult<ByteArray>

    /** Creates or replaces `<key>.jpg`; the server only lets a person write the file named by their own key. */
    suspend fun upload(key: String, bytes: ByteArray): SocialResult<Unit>

    /** Deletes `<key>.jpg`; a file that does not exist is not an error. */
    suspend fun delete(key: String): SocialResult<Unit>

    /** Tells the server the person uploaded (true) or deleted (false) their photo; it raises the version or clears it. */
    suspend fun mark(hasPhoto: Boolean): SocialResult<AvatarMark>
}

class DefaultSocialAvatarRepository(
    private val remote: SocialAvatarRemote,
    private val profiles: SocialProfileRepository,
    private val disk: SocialPhotoDiskCache,
    private val hasSession: () -> Boolean,
    private val memory: SocialPhotoMemoryCache = SocialPhotoMemoryCache(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SocialAvatarRepository {

    // Two screens asking for the same photo at once download it once: the second waits for the first and then finds it cached.
    // A fixed set of locks (chosen by the name) keeps this simple; photos that share a lock only wait for each other.
    private val locks = Array(LOCK_COUNT) { Mutex() }

    override suspend fun photo(key: String?, version: Int?): ByteArray? {
        if (key == null || version == null || version < 1 || !isValidAvatarKey(key) || !hasSession()) return null
        val name = cacheName(key, version)
        memory.get(name)?.let { return it }
        return locks[(name.hashCode() and Int.MAX_VALUE) % LOCK_COUNT].withLock {
            memory.get(name)
                ?: withContext(ioDispatcher) { disk.read(name) }?.also { memory.put(name, it) }
                ?: download(key, version)
        }
    }

    private suspend fun download(key: String, version: Int): ByteArray? {
        val bytes = (remote.download(key) as? SocialResult.Success)?.value?.takeIf(::isPhoto) ?: return null
        remember(key, version, bytes)
        return bytes
    }

    override suspend fun syncMine(photo: ByteArray?): SocialResult<Unit> = withMyProfile { profile ->
        if (photo != null) uploadMine(profile, photo) else deleteMine(profile)
    }

    override suspend fun ensureMine(photo: ByteArray?): SocialResult<Unit> = withMyProfile { profile ->
        if (photo == null || profile.avatarVersion != null) SocialResult.Success(Unit) else uploadMine(profile, photo)
    }

    private suspend fun withMyProfile(block: suspend (SocialProfile) -> SocialResult<Unit>): SocialResult<Unit> {
        if (!hasSession()) return SocialResult.Failure(SocialError.NoSession)
        return when (val result = profiles.getMyProfile()) {
            is SocialResult.Failure -> result
            // No Social profile yet: there is nowhere to put the photo, and nothing went wrong.
            is SocialResult.Success -> result.value?.let { block(it) } ?: SocialResult.Success(Unit)
        }
    }

    private suspend fun uploadMine(profile: SocialProfile, photo: ByteArray): SocialResult<Unit> {
        if (!isPhoto(photo)) return SocialResult.Failure(SocialError.InvalidInput)
        // A server that does not send the key (script 0010 not applied) has no place for the photo: nothing to do, nothing failed.
        val key = profile.avatarKey ?: return SocialResult.Success(Unit)
        return remote.upload(key, photo).flatMap { remote.mark(true) }.map { mark ->
            val version = mark.avatarVersion
            // The photo the person just chose is the one to show: no need to download it again.
            if (version != null) remember(mark.avatarKey ?: key, version, photo)
        }
    }

    private suspend fun deleteMine(profile: SocialProfile): SocialResult<Unit> {
        val key = profile.avatarKey
        if (key == null || profile.avatarVersion == null) return SocialResult.Success(Unit)
        // The file goes first: once the server marks the removal it gives the person a new key and the old name stops matching.
        return remote.delete(key).flatMap { remote.mark(false) }.map { forget(key) }
    }

    private suspend fun remember(key: String, version: Int, bytes: ByteArray) {
        val name = cacheName(key, version)
        memory.removeKey(key)
        memory.put(name, bytes)
        withContext(ioDispatcher) {
            disk.removeKey(key)
            disk.write(name, bytes)
        }
    }

    private suspend fun forget(key: String) {
        memory.removeKey(key)
        withContext(ioDispatcher) { disk.removeKey(key) }
    }

    private fun isPhoto(bytes: ByteArray): Boolean = canUploadAvatar(bytes)

    private companion object {
        const val LOCK_COUNT = 8
    }
}

/** The name an entry of the caches has: the key and the version, so a new version never meets an old photo. */
internal fun cacheName(key: String, version: Int): String = "$key-$version"
