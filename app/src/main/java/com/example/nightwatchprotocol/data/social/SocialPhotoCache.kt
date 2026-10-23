package com.example.nightwatchprotocol.data.social

import java.io.File
import java.io.IOException

/*
 * The two caches of the photos of Social, both keyed by `<key>-<version>` and both with a size limit. They hold only JPEG bytes of
 * photos that anyone with a session can see anyway, so they are not secret; they exist so a photo is not downloaded twice.
 */

private const val DEFAULT_MEMORY_BYTES = 4 * 1024 * 1024
private const val DEFAULT_DISK_BYTES = 16L * 1024 * 1024

/** In memory, least recently used out first. Thread safe. */
class SocialPhotoMemoryCache(private val maxBytes: Int = DEFAULT_MEMORY_BYTES) {
    private val entries = LinkedHashMap<String, ByteArray>(16, 0.75f, true)
    private var totalBytes = 0

    @Synchronized
    fun get(name: String): ByteArray? = entries[name]

    @Synchronized
    fun put(name: String, bytes: ByteArray) {
        if (bytes.size > maxBytes) return
        entries.put(name, bytes)?.let { totalBytes -= it.size }
        totalBytes += bytes.size
        val oldest = entries.entries.iterator()
        while (totalBytes > maxBytes && oldest.hasNext()) {
            val entry = oldest.next()
            totalBytes -= entry.value.size
            oldest.remove()
        }
    }

    /** Forgets every version of the photo with this [key]. */
    @Synchronized
    fun removeKey(key: String) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.startsWith("$key-")) {
                totalBytes -= entry.value.size
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun sizeBytes(): Int = totalBytes
}

/** On disk. Blocking: call it off the main thread. A file that cannot be read or written is a miss, never an error. */
interface SocialPhotoDiskCache {
    fun read(name: String): ByteArray?

    fun write(name: String, bytes: ByteArray)

    /** Deletes every version of the photo with this [key]. */
    fun removeKey(key: String)
}

/**
 * One file per photo in [directory] (the app's cache folder, which the system may also clear). When the files add up to more than
 * [maxBytes] the oldest written go first: the phone's clock is not read here, so this is first in, first out.
 */
class FileSocialPhotoDiskCache(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_DISK_BYTES,
) : SocialPhotoDiskCache {

    override fun read(name: String): ByteArray? = try {
        file(name).takeIf { it.isFile }?.readBytes()
    } catch (e: IOException) {
        null
    }

    override fun write(name: String, bytes: ByteArray) {
        if (bytes.size > maxBytes) return
        try {
            directory.mkdirs()
            file(name).writeBytes(bytes)
            trim()
        } catch (e: IOException) {
            // The photo still shows from memory; it is just downloaded again next time.
        }
    }

    override fun removeKey(key: String) {
        directory.listFiles()?.filter { it.name.startsWith("$key-") }?.forEach { it.delete() }
    }

    private fun trim() {
        val files = directory.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    private fun file(name: String) = File(directory, "$name.jpg")
}
