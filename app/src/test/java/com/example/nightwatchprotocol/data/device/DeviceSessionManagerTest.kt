package com.example.nightwatchprotocol.data.device

import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.FakeAvatarCache
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionManagerTest {
    private val uid = "test-uid"
    private val auth = FakeAuthRepository()
    private val devices = FakeDeviceRepository()
    private val idStore = DeviceIdStore.inMemory { "device-aaaa" }
    private val info = FakeDeviceInfoProvider("Pixel de prueba")
    private val cache = FakeAvatarCache()
    private val avatars = AvatarRepository(FakeProfileRepository(), cache, UnconfinedTestDispatcher())

    private fun manager() = DeviceSessionManager(auth, devices, idStore, info, avatars)

    @Test
    fun withoutASession_nothingHappens() = runTest {
        auth.currentUserUid = null

        assertEquals(SessionCheck.NotSignedIn, manager().onAppActive())
        assertTrue(devices.saveCalls.isEmpty())
    }

    @Test
    fun aNewDevice_registersItselfAsNotRevoked() = runTest {
        assertEquals(SessionCheck.Active, manager().onAppActive())

        val record = devices.record(uid, "device-aaaa")
        assertNotNull(record)
        assertEquals("Pixel de prueba", record!!.name)
        assertFalse(record.revoked)
        assertEquals(false, devices.saveCalls.single().revoked)
    }

    @Test
    fun anExistingDevice_isOnlyTouched_withoutWritingTheRevokedField() = runTest {
        devices.put(uid, DeviceRecord("device-aaaa", "Viejo nombre", 5L, revoked = false))

        assertEquals(SessionCheck.Active, manager().onAppActive())

        assertNull(devices.saveCalls.single().revoked)
        assertEquals("Pixel de prueba", devices.record(uid, "device-aaaa")!!.name)
    }

    @Test
    fun theDeviceIdIsStableAcrossChecks() = runTest {
        val manager = manager()

        manager.onAppActive()
        manager.onAppActive()

        assertEquals(setOf("device-aaaa"), devices.saveCalls.map { it.deviceId }.toSet())
        assertEquals("device-aaaa", manager.currentDeviceId())
    }

    @Test
    fun aRevokedDevice_signsItselfOut_clearsLocalDataAndRemovesItsDocument() = runTest {
        devices.put(uid, DeviceRecord("device-aaaa", "Pixel de prueba", 5L, revoked = true))
        cache.files[uid] = byteArrayOf(1, 2, 3)

        val result = manager().onAppActive()

        assertEquals(SessionCheck.Revoked, result)
        assertEquals(1, auth.signOutCallCount)
        assertNull(cache.files[uid])
        assertNull(devices.record(uid, "device-aaaa"))
        assertTrue(devices.saveCalls.isEmpty())
    }

    @Test
    fun aRevokedDevice_stillSignsOutWhenTheCleanupFails() = runTest {
        devices.put(uid, DeviceRecord("device-aaaa", "Pixel de prueba", 5L, revoked = true))
        devices.deleteFails = true

        assertEquals(SessionCheck.Revoked, manager().onAppActive())
        assertEquals(1, auth.signOutCallCount)
    }

    @Test
    fun whenTheRegistryCannotBeRead_theSessionIsLeftAlone() = runTest {
        devices.getFails = true

        assertEquals(SessionCheck.Unknown, manager().onAppActive())

        assertEquals(0, auth.signOutCallCount)
    }

    @Test
    fun aFailedRegistration_doesNotCloseTheSession() = runTest {
        devices.saveFails = true

        assertEquals(SessionCheck.Active, manager().onAppActive())
        assertEquals(0, auth.signOutCallCount)
    }

    @Test
    fun theUsersOwnSignOut_removesTheDeviceThenSignsOut() = runTest {
        devices.put(uid, DeviceRecord("device-aaaa", "Pixel de prueba", 5L, revoked = false))

        manager().endSession()

        assertNull(devices.record(uid, "device-aaaa"))
        assertEquals(1, auth.signOutCallCount)
    }

    @Test
    fun theUsersOwnSignOut_worksEvenIfTheDeleteFails() = runTest {
        devices.deleteFails = true

        manager().endSession()

        assertEquals(1, auth.signOutCallCount)
    }

    @Test
    fun afterBeingRevoked_aFreshSignInRegistersTheDeviceAgainAsActive() = runTest {
        devices.put(uid, DeviceRecord("device-aaaa", "Pixel de prueba", 5L, revoked = true))
        val manager = manager()
        manager.onAppActive()

        // Someone signs in again on this device: the document is gone, so it registers as a new, active device.
        assertEquals(SessionCheck.Active, manager.onAppActive())
        assertFalse(devices.record(uid, "device-aaaa")!!.revoked)
    }
}
