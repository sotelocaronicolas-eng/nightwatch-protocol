package com.example.nightwatchprotocol.ui.profile.devices

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.FakeAvatarCache
import com.example.nightwatchprotocol.data.device.DeviceIdStore
import com.example.nightwatchprotocol.data.device.DeviceRecord
import com.example.nightwatchprotocol.data.device.DeviceSessionManager
import com.example.nightwatchprotocol.data.device.FakeDeviceInfoProvider
import com.example.nightwatchprotocol.data.device.FakeDeviceRepository
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = "test-uid"
    private val now = 1_000_000_000L
    private val hour = 3_600_000L

    private val auth = FakeAuthRepository()
    private val devices = FakeDeviceRepository()
    private val manager = DeviceSessionManager(
        auth,
        devices,
        DeviceIdStore.inMemory { "device-me" },
        FakeDeviceInfoProvider("Este telefono"),
        AvatarRepository(FakeProfileRepository(), FakeAvatarCache(), UnconfinedTestDispatcher()),
    )

    private fun viewModel() = DevicesViewModel(auth, devices, manager, clock = { now })

    private fun other(id: String, name: String, agoMillis: Long, revoked: Boolean = false) =
        DeviceRecord(id, name, now - agoMillis, revoked)

    @Test
    fun listsTheRealDevices_withThisOneFirstAndMarkedAsCurrent() = runTest {
        devices.put(uid, other("device-b", "Samsung S26", 2 * hour))
        devices.put(uid, other("device-c", "iPhone", 5 * hour))
        val vm = viewModel()
        advanceUntilIdle()

        val items = vm.uiState.value.devices
        assertEquals(listOf("device-me", "device-b", "device-c"), items.map { it.id })
        assertEquals("Actual", items[0].statusLabel)
        assertTrue(items[0].isCurrent)
        assertEquals("Hace 2 h", items[1].statusLabel)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun thisDeviceIsRegisteredEvenIfItWasNotInTheList() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("device-me"), vm.uiState.value.devices.map { it.id })
        assertEquals("Este telefono", vm.uiState.value.devices.single().name)
    }

    @Test
    fun aRequestedSignOutShowsAsPending() = runTest {
        devices.put(uid, other("device-b", "Samsung S26", hour, revoked = true))
        val vm = viewModel()
        advanceUntilIdle()

        val item = vm.uiState.value.devices.first { it.id == "device-b" }
        assertEquals("Cierre pendiente", item.statusLabel)
        assertTrue(item.isRevoked)
    }

    @Test
    fun tappingAnotherDevice_marksItRevokedAndOpensTheConfirmation() = runTest {
        devices.put(uid, other("device-b", "Samsung S26", hour))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onDeviceClicked("device-b")
            assertEquals(DevicesEvent.DeviceSignedOut, awaitItem())
        }
        advanceUntilIdle()

        assertTrue(devices.record(uid, "device-b")!!.revoked)
        assertEquals(listOf("device-b"), devices.revokeCalls)
        assertTrue(vm.uiState.value.devices.first { it.id == "device-b" }.isRevoked)
    }

    @Test
    fun tappingThisDevice_orOneAlreadyRequested_doesNothing() = runTest {
        devices.put(uid, other("device-b", "Samsung S26", hour, revoked = true))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onDeviceClicked("device-me")
        vm.onDeviceClicked("device-b")
        advanceUntilIdle()

        assertTrue(devices.revokeCalls.isEmpty())
    }

    @Test
    fun signOutAll_revokesEveryOtherDeviceButNeverThisOne() = runTest {
        devices.put(uid, other("device-b", "Samsung S26", hour))
        devices.put(uid, other("device-c", "iPhone", 2 * hour))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSignOutAllClicked()
            assertEquals(DevicesEvent.DeviceSignedOut, awaitItem())
        }
        advanceUntilIdle()

        assertEquals(setOf("device-b", "device-c"), devices.revokeCalls.toSet())
        assertFalse(devices.record(uid, "device-me")!!.revoked)
    }

    @Test
    fun signOutAll_withNoOtherDevices_explainsInsteadOfNavigating() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSignOutAllClicked()
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals("No hay otros dispositivos con sesión abierta.", vm.uiState.value.notice)
    }

    @Test
    fun aFailedRevoke_showsAnErrorAndDoesNotNavigate() = runTest {
        devices.put(uid, other("device-b", "Samsung S26", hour))
        val vm = viewModel()
        advanceUntilIdle()
        devices.setRevokedFails = true

        vm.events.test {
            vm.onDeviceClicked("device-b")
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals("No pudimos cerrar la sesión en ese dispositivo. Inténtalo de nuevo.", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isWorking)
    }

    @Test
    fun anUnreadableList_showsALoadError() = runTest {
        devices.listFails = true
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(MESSAGE_DEVICES_LOAD_FAILED, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.devices.isEmpty())
    }

    @Test
    fun ifThisDeviceWasRevokedMeanwhile_theSessionEndsFromTheList() = runTest {
        devices.put(uid, DeviceRecord("device-me", "Este telefono", now, revoked = true))

        val vm = viewModel()

        vm.events.test {
            assertEquals(DevicesEvent.SessionEnded, awaitItem())
        }
        advanceUntilIdle()
        assertEquals(1, auth.signOutCallCount)
        assertNull(devices.record(uid, "device-me"))
    }

    @Test
    fun theLimitationNoticeSaysItDoesNotRevokeFirebase() {
        assertTrue(DEVICES_LIMITATION_NOTICE.contains("No revoca"))
        assertTrue(DEVICES_LIMITATION_NOTICE.contains("Firebase"))
    }
}
