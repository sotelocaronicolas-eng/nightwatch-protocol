package com.example.nightwatchprotocol.ui.profile.notifications

import com.example.nightwatchprotocol.MainDispatcherRule
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
class NotificationSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = UserPreferences.inMemory()

    @Test
    fun startsWithTheFigmaDefaults() = runTest {
        val vm = NotificationSettingsViewModel(preferences)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.dailyReminder)
        assertTrue(state.achievements)
        assertTrue(state.streakAtRisk)
        assertFalse(state.newLabs)
        assertFalse(state.news)
    }

    @Test
    fun changingASwitch_isPersistedAndShownInTheState() = runTest {
        val vm = NotificationSettingsViewModel(preferences)
        advanceUntilIdle()

        vm.onSettingChanged(UserSetting.NEW_LABS, true)
        vm.onSettingChanged(UserSetting.DAILY_REMINDER, false)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.newLabs)
        assertFalse(vm.uiState.value.dailyReminder)
        assertTrue(preferences.settings.first()[UserSetting.NEW_LABS])
        assertFalse(preferences.settings.first()[UserSetting.DAILY_REMINDER])
    }

    @Test
    fun savedChoicesSurviveANewViewModel() = runTest {
        preferences.set(UserSetting.NEWS_AND_UPDATES, true)
        preferences.set(UserSetting.STREAK_AT_RISK, false)

        val vm = NotificationSettingsViewModel(preferences)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.news)
        assertFalse(vm.uiState.value.streakAtRisk)
    }

    @Test
    fun theNotificationSwitchesDoNotTouchThePrivacySettings() = runTest {
        val vm = NotificationSettingsViewModel(preferences)
        advanceUntilIdle()

        vm.onSettingChanged(UserSetting.NEWS_AND_UPDATES, true)
        advanceUntilIdle()

        assertFalse(preferences.settings.first()[UserSetting.HIDE_PUBLIC_PROGRESS])
        assertFalse(preferences.settings.first()[UserSetting.BIOMETRIC_LOCK])
    }
}
