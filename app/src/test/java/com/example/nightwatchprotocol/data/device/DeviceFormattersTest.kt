package com.example.nightwatchprotocol.data.device

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceFormattersTest {
    private val now = 10_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun lessThanAMinute_isNow() {
        assertEquals("Ahora", relativeTimeLabel(now - 30_000, now))
        assertEquals("Ahora", relativeTimeLabel(now, now))
    }

    @Test
    fun minutes() {
        assertEquals("Hace 1 min", relativeTimeLabel(now - minute, now))
        assertEquals("Hace 59 min", relativeTimeLabel(now - 59 * minute, now))
    }

    @Test
    fun hours() {
        assertEquals("Hace 1 h", relativeTimeLabel(now - hour, now))
        assertEquals("Hace 2 h", relativeTimeLabel(now - 2 * hour - 10 * minute, now))
        assertEquals("Hace 23 h", relativeTimeLabel(now - 23 * hour, now))
    }

    @Test
    fun days() {
        assertEquals("Hace 1 d", relativeTimeLabel(now - 24 * hour, now))
        assertEquals("Hace 12 d", relativeTimeLabel(now - 12 * 24 * hour, now))
    }

    @Test
    fun aMissingTimestamp_isADash() {
        assertEquals("—", relativeTimeLabel(null, now))
    }

    @Test
    fun aTimestampInTheFuture_isTreatedAsNow() {
        assertEquals("Ahora", relativeTimeLabel(now + hour, now))
    }
}
