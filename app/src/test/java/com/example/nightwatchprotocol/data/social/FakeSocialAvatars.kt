package com.example.nightwatchprotocol.data.social

import kotlinx.coroutines.delay

/*
 * Fakes for the photos of Social: the files and the server function in memory, a disk cache in a map, and a whole repository that
 * only records what it was asked. The photos are the smallest thing that passes for a JPEG (FF D8 FF and some bytes).
 */

/** A "JPEG" of [size] bytes; [seed] tells two photos apart. */
fun fakeJpeg(seed: Int = 1, size: Int = 8): ByteArray =
    ByteArray(size) { index -> if (index < 3) listOf(0xFF, 0xD8, 0xFF)[index].toByte() else (seed + index).toByte() }

class FakeSocialAvatarRemote(
    /** The key the server has for the person (changes when a photo is removed, like `social_set_avatar(false)`). */
    var myKey: String = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
) : SocialAvatarRemote {
    val files = mutableMapOf<String, ByteArray>()
    val downloads = mutableListOf<String>()
    val uploads = mutableListOf<String>()
    val deletes = mutableListOf<String>()
    val marks = mutableListOf<Boolean>()

    var version: Int? = null
    var downloadFailure: SocialError? = null
    var uploadFailure: SocialError? = null
    var deleteFailure: SocialError? = null
    var markFailure: SocialError? = null

    /** Makes a download take this long (virtual time in a test), so two requests can overlap. */
    var downloadMillis: Long = 0

    override suspend fun download(key: String): SocialResult<ByteArray> {
        downloads += key
        if (downloadMillis > 0) delay(downloadMillis)
        downloadFailure?.let { return SocialResult.Failure(it) }
        return files[key]?.let { SocialResult.Success(it) } ?: SocialResult.Failure(SocialError.Unknown)
    }

    override suspend fun upload(key: String, bytes: ByteArray): SocialResult<Unit> {
        uploads += key
        uploadFailure?.let { return SocialResult.Failure(it) }
        files[key] = bytes
        return SocialResult.Success(Unit)
    }

    override suspend fun delete(key: String): SocialResult<Unit> {
        deletes += key
        deleteFailure?.let { return SocialResult.Failure(it) }
        files.remove(key)
        return SocialResult.Success(Unit)
    }

    override suspend fun mark(hasPhoto: Boolean): SocialResult<AvatarMark> {
        marks += hasPhoto
        markFailure?.let { return SocialResult.Failure(it) }
        if (hasPhoto) {
            version = (version ?: 0) + 1
        } else if (version != null) {
            version = null
            myKey = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        }
        return SocialResult.Success(AvatarMark(myKey, version))
    }
}

class InMemorySocialPhotoDisk : SocialPhotoDiskCache {
    val files = linkedMapOf<String, ByteArray>()

    override fun read(name: String): ByteArray? = files[name]

    override fun write(name: String, bytes: ByteArray) {
        files[name] = bytes
    }

    override fun removeKey(key: String) {
        files.keys.removeAll { it.startsWith("$key-") }
    }
}

/** A whole [SocialAvatarRepository] that answers what the test says and records the calls (for the tests of its users). */
class FakeSocialAvatarRepository : SocialAvatarRepository {
    val photos = mutableMapOf<Pair<String, Int>, ByteArray>()
    val syncCalls = mutableListOf<ByteArray?>()
    val ensureCalls = mutableListOf<ByteArray?>()
    var photoRequests = 0
    var syncResult: SocialResult<Unit> = SocialResult.Success(Unit)

    override suspend fun photo(key: String?, version: Int?): ByteArray? {
        photoRequests++
        if (key == null || version == null) return null
        return photos[key to version]
    }

    override suspend fun syncMine(photo: ByteArray?): SocialResult<Unit> {
        syncCalls += photo
        return syncResult
    }

    override suspend fun ensureMine(photo: ByteArray?): SocialResult<Unit> {
        ensureCalls += photo
        return syncResult
    }
}
