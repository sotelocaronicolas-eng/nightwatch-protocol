package com.example.nightwatchprotocol.ui.profile.usermenu

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.AccountProfile
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.FakeAvatarCache
import com.example.nightwatchprotocol.data.device.DeviceIdStore
import com.example.nightwatchprotocol.data.device.DeviceSessionManager
import com.example.nightwatchprotocol.data.device.FakeDeviceInfoProvider
import com.example.nightwatchprotocol.data.device.FakeDeviceRepository
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.profile.RemoteProfile
import kotlin.io.encoding.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserMenuViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val account = FakeAccountRepository()
    private val profile = FakeProfileRepository()
    private val avatars = AvatarRepository(profile, FakeAvatarCache(), UnconfinedTestDispatcher())

    private val devices = FakeDeviceRepository()
    private val sessionManager = DeviceSessionManager(
        auth,
        devices,
        DeviceIdStore.inMemory { "device-me" },
        FakeDeviceInfoProvider(),
        avatars,
    )

    private fun viewModel() = UserMenuViewModel(auth, account, avatars, sessionManager)

    @Test
    fun showsNameStreakAndInitial() = runTest {
        account.profile = AccountProfile("Nicolás Sotelo", "n@example.com", null, hasPassword = true)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Nicolás Sotelo", vm.uiState.value.displayName)
        assertEquals(15, vm.uiState.value.streakDays)
        assertEquals(AvatarDisplay.Initial("N"), vm.uiState.value.avatar)
    }

    @Test
    fun avatarFromTheServer_replacesTheInitial() = runTest {
        val photo = byteArrayOf(1, 2, 3)
        profile.getProfileResult = Result.success(RemoteProfile(avatarBase64 = Base64.Default.encode(photo)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(AvatarDisplay.Photo(photo), vm.uiState.value.avatar)
    }

    @Test
    fun signOut_signsOutClearsTheAvatarAndEmitsTheEvent() = runTest {
        val photo = byteArrayOf(1, 2, 3)
        profile.getProfileResult = Result.success(RemoteProfile(avatarBase64 = Base64.Default.encode(photo)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSignOutClicked()
            assertEquals(UserMenuEvent.SignedOut, awaitItem())
        }

        assertEquals(1, auth.signOutCallCount)
        assertNull(avatars.avatar.value)
    }
}
