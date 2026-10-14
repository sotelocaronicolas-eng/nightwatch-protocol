package com.example.nightwatchprotocol.ui.profile.notifications

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.notifications.FakeNotificationPermission
import com.example.nightwatchprotocol.data.notifications.FakeReminderScheduler
import com.example.nightwatchprotocol.data.notifications.ReminderCoordinator
import com.example.nightwatchprotocol.data.notifications.ReminderKind
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = UserPreferences.inMemory()
    private val scheduler = FakeReminderScheduler()
    private val permission = FakeNotificationPermission()
    private val coordinator = ReminderCoordinator(scheduler, permission, preferences)

    private fun viewModel() = NotificationSettingsViewModel(preferences, coordinator, permission)

    @Test
    fun startsWithTheFigmaDefaults() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.dailyReminder)
        assertTrue(state.achievements)
        assertTrue(state.streakAtRisk)
        assertFalse(state.newLabs)
        assertFalse(state.news)
    }

    @Test
    fun turningAReminderOn_withPermission_isSavedAndScheduled() = runTest {
        preferences.set(UserSetting.DAILY_REMINDER, false)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSettingChanged(UserSetting.DAILY_REMINDER, true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.dailyReminder)
        assertEquals(setOf(ReminderKind.DAILY_PRACTICE), scheduler.active)
        assertNull(vm.uiState.value.permissionMessage)
    }

    @Test
    fun turningAReminderOff_cancelsTheScheduledWork() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSettingChanged(UserSetting.STREAK_AT_RISK, true)
        advanceUntilIdle()

        vm.onSettingChanged(UserSetting.STREAK_AT_RISK, false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.streakAtRisk)
        assertFalse(ReminderKind.STREAK_AT_RISK in scheduler.active)
        assertFalse(preferences.settings.first()[UserSetting.STREAK_AT_RISK])
    }

    @Test
    fun withoutPermission_theSystemDialogIsRequested_andTheSwitchStaysOff() = runTest {
        permission.granted = false
        preferences.set(UserSetting.DAILY_REMINDER, false)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSettingChanged(UserSetting.DAILY_REMINDER, true)
            assertEquals(NotificationSettingsEvent.RequestPermission, awaitItem())
        }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.dailyReminder)
        assertTrue(scheduler.active.isEmpty())
    }

    @Test
    fun grantingThePermission_turnsTheSwitchOnAndSchedules() = runTest {
        permission.granted = false
        preferences.set(UserSetting.DAILY_REMINDER, false)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSettingChanged(UserSetting.DAILY_REMINDER, true)
        advanceUntilIdle()

        permission.granted = true
        vm.onPermissionResult(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.dailyReminder)
        assertEquals(setOf(ReminderKind.DAILY_PRACTICE), scheduler.active)
    }

    @Test
    fun denyingThePermission_keepsTheSwitchOffAndShowsAClearMessage() = runTest {
        permission.granted = false
        preferences.set(UserSetting.DAILY_REMINDER, false)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSettingChanged(UserSetting.DAILY_REMINDER, true)
        advanceUntilIdle()

        vm.onPermissionResult(false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.dailyReminder)
        assertEquals(MESSAGE_NOTIFICATIONS_DENIED, vm.uiState.value.permissionMessage)
        assertTrue(scheduler.active.isEmpty())
    }

    @Test
    fun beforeAndroid13_blockedNotificationsShowTheMessageWithoutADialog() = runTest {
        permission.granted = false
        permission.requiresRuntimePermission = false
        preferences.set(UserSetting.DAILY_REMINDER, false)
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSettingChanged(UserSetting.DAILY_REMINDER, true)
            advanceUntilIdle()
            expectNoEvents()
        }

        assertFalse(vm.uiState.value.dailyReminder)
        assertEquals(MESSAGE_NOTIFICATIONS_DENIED, vm.uiState.value.permissionMessage)
    }

    @Test
    fun theSwitchesWithoutALocalReminder_neverAskForPermission() = runTest {
        permission.granted = false
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSettingChanged(UserSetting.NEW_LABS, true)
            advanceUntilIdle()
            expectNoEvents()
        }

        assertTrue(vm.uiState.value.newLabs)
        assertNull(vm.uiState.value.permissionMessage)
    }

    @Test
    fun aMessageIsClearedWhenALaterChangeSucceeds() = runTest {
        permission.granted = false
        val vm = viewModel()
        advanceUntilIdle()
        vm.onSettingChanged(UserSetting.STREAK_AT_RISK, false)
        vm.onSettingChanged(UserSetting.STREAK_AT_RISK, true)
        vm.onPermissionResult(false)
        advanceUntilIdle()
        assertEquals(MESSAGE_NOTIFICATIONS_DENIED, vm.uiState.value.permissionMessage)

        vm.onSettingChanged(UserSetting.NEWS_AND_UPDATES, true)
        advanceUntilIdle()

        assertNull(vm.uiState.value.permissionMessage)
    }

    @Test
    fun onResume_withoutPermission_turnsOffTheRemindersThatWereOn() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        permission.granted = false

        vm.onResume()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.dailyReminder)
        assertFalse(vm.uiState.value.streakAtRisk)
        assertTrue(scheduler.active.isEmpty())
    }

    @Test
    fun onResume_withPermission_changesNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onResume()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.dailyReminder)
        assertTrue(vm.uiState.value.streakAtRisk)
    }

    @Test
    fun theNotificationSwitchesDoNotTouchThePrivacySettings() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSettingChanged(UserSetting.NEWS_AND_UPDATES, true)
        advanceUntilIdle()

        assertFalse(preferences.settings.first()[UserSetting.HIDE_PUBLIC_PROGRESS])
        assertFalse(preferences.settings.first()[UserSetting.BIOMETRIC_LOCK])
    }
}
