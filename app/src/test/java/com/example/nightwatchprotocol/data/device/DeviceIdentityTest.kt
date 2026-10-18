package com.example.nightwatchprotocol.data.device

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityTest {
    @Test
    fun theIdIsCreatedOnceAndThenReused() = runTest {
        var created = 0
        val store = DeviceIdStore.inMemory { "id-${++created}" }

        val first = store.getOrCreate()
        val second = store.getOrCreate()

        assertEquals("id-1", first)
        assertEquals(first, second)
        assertEquals(1, created)
    }

    @Test
    fun theDefaultIdIsAUuid() = runTest {
        val id = DeviceIdStore.inMemory().getOrCreate()

        // The `devices.device_id` column (uuid) requires this exact shape.
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(id))
    }

    @Test
    fun deviceName_doesNotRepeatTheMaker() {
        assertEquals("Samsung SM-S938B", formatDeviceName("samsung", "SM-S938B"))
        assertEquals("Google Pixel 8", formatDeviceName("Google", "Google Pixel 8"))
        assertEquals("Xiaomi 23049PCD8G", formatDeviceName("Xiaomi", "xiaomi 23049PCD8G"))
    }

    @Test
    fun deviceName_hasFallbacksAndALimit() {
        assertEquals("Dispositivo Android", formatDeviceName(null, null))
        assertEquals("Motorola", formatDeviceName("motorola", ""))
        assertEquals("Pixel", formatDeviceName("", "pixel"))
        assertEquals(80, formatDeviceName("A", "m".repeat(200)).length)
    }
}
