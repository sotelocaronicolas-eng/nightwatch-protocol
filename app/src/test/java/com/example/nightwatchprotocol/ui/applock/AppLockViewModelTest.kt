package com.example.nightwatchprotocol.ui.applock

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.biometric.BiometricAvailability
import com.example.nightwatchprotocol.data.biometric.FakeBiometricCapabilities
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val preferences = UserPreferences.inMemory()
    private val capabilities = FakeBiometricCapabilities()

    // The stored preference matches the value the app would have read at start-up.
    private suspend fun viewModel(lockEnabled: Boolean): AppLockViewModel {
        preferences.set(UserSetting.BIOMETRIC_LOCK, lockEnabled)
        return AppLockViewModel(auth, preferences, capabilities, lockEnabled)
    }

    @Test
    fun coldStartWithSessionAndLock_startsLockedAndHidesTheContent() = runTest {
        val vm = viewModel(lockEnabled = true)

        assertTrue(vm.uiState.value.isLocked)
        assertFalse(vm.uiState.value.showContent)
    }

    @Test
    fun coldStartWithTheLockOff_startsUnlockedWithContent() = runTest {
        val vm = viewModel(lockEnabled = false)

        assertFalse(vm.uiState.value.isLocked)
        assertTrue(vm.uiState.value.showContent)
    }

    @Test
    fun coldStartWithoutSession_neverLocks() = runTest {
        auth.currentUserUid = null
        val vm = viewModel(lockEnabled = true)

        assertFalse(vm.uiState.value.isLocked)
        assertTrue(vm.uiState.value.showContent)
    }

    @Test
    fun successfulAuthentication_unlocksAndShowsTheContent() = runTest {
        val vm = viewModel(lockEnabled = true)

        vm.onAuthenticationSucceeded()

        assertFalse(vm.uiState.value.isLocked)
        assertTrue(vm.uiState.value.showContent)
    }

    @Test
    fun cancelledPrompt_leavesTheAppLocked() = runTest {
        val vm = viewModel(lockEnabled = true)

        // The prompt was shown and cancelled: no success is reported, so nothing changes.
        assertTrue(vm.requestUnlock())

        assertTrue(vm.uiState.value.isLocked)
    }

    @Test
    fun backgroundForLessThanThirtySeconds_doesNotRelock() = runTest {
        val vm = viewModel(lockEnabled = true)
        vm.onAuthenticationSucceeded()
        advanceUntilIdle()

        vm.onActivityStopped(nowMillis = 100_000)
        vm.onActivityStarted(nowMillis = 129_000)

        assertFalse(vm.uiState.value.isLocked)
    }

    @Test
    fun backgroundForThirtySecondsOrMore_relocksButKeepsTheContent() = runTest {
        val vm = viewModel(lockEnabled = true)
        vm.onAuthenticationSucceeded()
        advanceUntilIdle()

        vm.onActivityStopped(nowMillis = 100_000)
        vm.onActivityStarted(nowMillis = 130_000)

        assertTrue(vm.uiState.value.isLocked)
        assertTrue(vm.uiState.value.showContent)
    }

    @Test
    fun turningTheLockOnLater_appliesFromTheNextReturn() = runTest {
        val vm = viewModel(lockEnabled = false)
        advanceUntilIdle()
        preferences.set(UserSetting.BIOMETRIC_LOCK, true)
        advanceUntilIdle()

        vm.onActivityStopped(nowMillis = 0)
        vm.onActivityStarted(nowMillis = 60_000)

        assertTrue(vm.uiState.value.isLocked)
    }

    @Test
    fun theLockOff_neverRelocks() = runTest {
        val vm = viewModel(lockEnabled = false)
        advanceUntilIdle()

        vm.onActivityStopped(nowMillis = 0)
        vm.onActivityStarted(nowMillis = 600_000)

        assertFalse(vm.uiState.value.isLocked)
    }

    @Test
    fun returningWithoutASession_doesNotLock() = runTest {
        val vm = viewModel(lockEnabled = true)
        vm.onAuthenticationSucceeded()
        advanceUntilIdle()
        auth.currentUserUid = null

        vm.onActivityStopped(nowMillis = 0)
        vm.onActivityStarted(nowMillis = 600_000)

        assertFalse(vm.uiState.value.isLocked)
    }

    @Test
    fun whenBiometricsDisappear_theLockTurnsItselfOffAndTheAppOpens() = runTest {
        preferences.set(UserSetting.BIOMETRIC_LOCK, true)
        val vm = viewModel(lockEnabled = true)
        capabilities.availability = BiometricAvailability.NoneEnrolled

        val canPrompt = vm.requestUnlock()
        advanceUntilIdle()

        assertFalse(canPrompt)
        assertFalse(vm.uiState.value.isLocked)
        assertTrue(vm.uiState.value.showContent)
        assertFalse(preferences.settings.first()[UserSetting.BIOMETRIC_LOCK])
    }

    @Test
    fun requestUnlockWhenNotLocked_doesNotPrompt() = runTest {
        val vm = viewModel(lockEnabled = false)

        assertFalse(vm.requestUnlock())
    }
}
