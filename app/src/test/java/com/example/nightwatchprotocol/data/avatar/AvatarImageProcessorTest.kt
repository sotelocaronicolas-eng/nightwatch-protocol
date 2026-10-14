package com.example.nightwatchprotocol.data.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarImageProcessorTest {
    private fun bytesOf(size: Int) = ByteArray(size)

    @Test
    fun smallImage_usesTheFirstQuality() {
        val triedQualities = mutableListOf<Int>()
        val result = compressWithinLimit(encode = { q -> triedQualities += q; bytesOf(20_000) })

        assertEquals(20_000, result.size)
        assertEquals(listOf(80), triedQualities)
    }

    @Test
    fun imageOverTheLimit_stepsTheQualityDownUntilItFits() {
        val triedQualities = mutableListOf<Int>()
        val sizeByQuality = mapOf(80 to 150_000, 60 to 110_000, 40 to 90_000)
        val result = compressWithinLimit(encode = { q -> triedQualities += q; bytesOf(sizeByQuality.getValue(q)) })

        assertEquals(90_000, result.size)
        assertEquals(listOf(80, 60, 40), triedQualities)
    }

    @Test
    fun imageThatNeverFits_returnsTheSmallestAttempt() {
        val result = compressWithinLimit(encode = { q -> bytesOf(300_000 - q * 1_000) })

        assertEquals(300_000 - 40 * 1_000, result.size)
        assertTrue(result.size > AVATAR_MAX_BYTES)
    }

    @Test
    fun limitIsInclusive() {
        val result = compressWithinLimit(encode = { bytesOf(AVATAR_MAX_BYTES) })

        assertEquals(AVATAR_MAX_BYTES, result.size)
    }
}
