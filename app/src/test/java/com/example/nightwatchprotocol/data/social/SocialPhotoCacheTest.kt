package com.example.nightwatchprotocol.data.social

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SocialPhotoCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val keyA = "11111111-1111-4111-8111-111111111111"
    private val keyB = "22222222-2222-4222-8222-222222222222"

    // ---- memory ----

    @Test
    fun memory_keepsWhatFitsAndDropsTheLeastRecentlyUsedFirst() {
        val cache = SocialPhotoMemoryCache(maxBytes = 30)
        cache.put("a-1", ByteArray(10))
        cache.put("b-1", ByteArray(10))
        cache.put("c-1", ByteArray(10))
        cache.get("a-1")

        cache.put("d-1", ByteArray(10))

        assertNull("b-1 era el menos usado", cache.get("b-1"))
        assertTrue(cache.get("a-1") != null && cache.get("c-1") != null && cache.get("d-1") != null)
        assertEquals(30, cache.sizeBytes())
    }

    @Test
    fun memory_neverKeepsAPhotoBiggerThanItsLimit() {
        val cache = SocialPhotoMemoryCache(maxBytes = 10)

        cache.put("a-1", ByteArray(11))

        assertNull(cache.get("a-1"))
        assertEquals(0, cache.sizeBytes())
    }

    @Test
    fun memory_replacingAnEntryDoesNotCountItTwice() {
        val cache = SocialPhotoMemoryCache(maxBytes = 100)

        cache.put("a-1", ByteArray(40))
        cache.put("a-1", ByteArray(50))

        assertEquals(50, cache.sizeBytes())
    }

    @Test
    fun memory_removeKey_forgetsEveryVersionOfThatPhotoOnly() {
        val cache = SocialPhotoMemoryCache()
        cache.put("$keyA-1", ByteArray(5))
        cache.put("$keyA-2", ByteArray(5))
        cache.put("$keyB-1", ByteArray(5))

        cache.removeKey(keyA)

        assertNull(cache.get("$keyA-1"))
        assertNull(cache.get("$keyA-2"))
        assertTrue(cache.get("$keyB-1") != null)
        assertEquals(5, cache.sizeBytes())
    }

    // ---- disk ----

    private fun diskCache(maxBytes: Long = 1_000) = FileSocialPhotoDiskCache(File(folder.root, "social_avatars"), maxBytes)

    @Test
    fun disk_writesAndReadsBack_andAMissIsNull() {
        val cache = diskCache()

        cache.write("$keyA-1", byteArrayOf(1, 2, 3))

        assertArrayEquals(byteArrayOf(1, 2, 3), cache.read("$keyA-1"))
        assertNull(cache.read("$keyA-2"))
    }

    @Test
    fun disk_whenTheLimitIsPassed_theOldestFilesGo() {
        val cache = diskCache(maxBytes = 25)
        val directory = File(folder.root, "social_avatars")

        cache.write("$keyA-1", ByteArray(10))
        directory.listFiles()!!.forEach { it.setLastModified(1_000) }
        cache.write("$keyB-1", ByteArray(10))
        directory.listFiles()!!.first { it.name.startsWith(keyB) }.setLastModified(2_000)
        cache.write("33333333-3333-4333-8333-333333333333-1", ByteArray(10))

        assertNull("la más antigua debía salir", cache.read("$keyA-1"))
        assertTrue(cache.read("$keyB-1") != null)
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= 25)
    }

    @Test
    fun disk_removeKey_deletesEveryVersionOfThatPhotoOnly() {
        val cache = diskCache()
        cache.write("$keyA-1", ByteArray(5))
        cache.write("$keyA-2", ByteArray(5))
        cache.write("$keyB-1", ByteArray(5))

        cache.removeKey(keyA)

        assertNull(cache.read("$keyA-1"))
        assertNull(cache.read("$keyA-2"))
        assertTrue(cache.read("$keyB-1") != null)
    }

    @Test
    fun disk_aPhotoBiggerThanTheWholeCache_isNotWritten() {
        val cache = diskCache(maxBytes = 5)

        cache.write("$keyA-1", ByteArray(6))

        assertFalse(File(folder.root, "social_avatars").listFiles().orEmpty().any { it.length() > 0 })
    }

    @Test
    fun disk_removingFromAFolderThatDoesNotExist_isHarmless() {
        diskCache().removeKey(keyA)

        assertNull(diskCache().read("$keyA-1"))
    }
}
