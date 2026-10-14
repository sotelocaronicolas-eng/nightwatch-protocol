package com.example.nightwatchprotocol.ui.session

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = "test-uid"
    private val auth = FakeAuthRepository()
    private val devices = FakeDeviceRepository()
    private val manager = DeviceSessionManager(
        auth,
        devices,
        DeviceIdStore.inMemory { "device-me" },
        FakeDeviceInfoProvider(),
        AvatarRepository(FakeProfileRepository(), FakeAvatarCache(), UnconfinedTestDispatcher()),
    )

    private fun viewModel() = DeviceSessionViewModel(manager)

    @Test
    fun aNormalStart_registersTheDeviceAndRaisesNoNotice() = runTest {
        val vm = viewModel()

        vm.onActivityStarted(1_000)
        advanceUntilIdle()

        assertFalse(vm.sessionEndedByAnotherDevice.value)
        assertEquals(1, devices.saveCalls.size)
    }

    @Test
    fun whenAnotherDeviceRevokedThisOne_itSignsOutAndRaisesTheNotice() = runTest {
        devices.put(uid, DeviceRecord("device-me", "Este", 5L, revoked = true))
        val vm = viewModel()

        vm.onActivityStarted(1_000)
        advanceUntilIdle()

        assertTrue(vm.sessionEndedByAnotherDevice.value)
        assertEquals(1, auth.signOutCallCount)
    }

    @Test
    fun theNoticeCanBeDismissed() = runTest {
        devices.put(uid, DeviceRecord("device-me", "Este", 5L, revoked = true))
        val vm = viewModel()
        vm.onActivityStarted(1_000)
        advanceUntilIdle()

        vm.onNoticeDismissed()

        assertFalse(vm.sessionEndedByAnotherDevice.value)
    }

    @Test
    fun quickRestarts_doNotAskFirestoreAgain() = runTest {
        val vm = viewModel()

        vm.onActivityStarted(1_000)
        advanceUntilIdle()
        vm.onActivityStarted(1_000 + MIN_DEVICE_CHECK_INTERVAL_MILLIS - 1)
        advanceUntilIdle()

        assertEquals(1, devices.saveCalls.size)
    }

    @Test
    fun afterTheMinimumInterval_itChecksAgain() = runTest {
        val vm = viewModel()
        vm.onActivityStarted(1_000)
        advanceUntilIdle()

        vm.onActivityStarted(1_000 + MIN_DEVICE_CHECK_INTERVAL_MILLIS)
        advanceUntilIdle()

        assertEquals(2, devices.saveCalls.size)
    }

    @Test
    fun withoutASession_nothingIsRegistered() = runTest {
        auth.currentUserUid = null
        val vm = viewModel()

        vm.onActivityStarted(1_000)
        advanceUntilIdle()

        assertTrue(devices.saveCalls.isEmpty())
        assertFalse(vm.sessionEndedByAnotherDevice.value)
    }
}
