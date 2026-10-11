package com.example.nightwatchprotocol.ui.profile.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationSettingsUiState(
    val dailyReminder: Boolean = UserSetting.DAILY_REMINDER.default,
    val achievements: Boolean = UserSetting.ACHIEVEMENT_NOTIFICATIONS.default,
    val streakAtRisk: Boolean = UserSetting.STREAK_AT_RISK.default,
    val newLabs: Boolean = UserSetting.NEW_LABS.default,
    val news: Boolean = UserSetting.NEWS_AND_UPDATES.default,
)

/** The switches only remember the choice on this device: nothing sends notifications yet. */
class NotificationSettingsViewModel(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.settings.collect { settings ->
                _uiState.update {
                    NotificationSettingsUiState(
                        dailyReminder = settings[UserSetting.DAILY_REMINDER],
                        achievements = settings[UserSetting.ACHIEVEMENT_NOTIFICATIONS],
                        streakAtRisk = settings[UserSetting.STREAK_AT_RISK],
                        newLabs = settings[UserSetting.NEW_LABS],
                        news = settings[UserSetting.NEWS_AND_UPDATES],
                    )
                }
            }
        }
    }

    fun onSettingChanged(setting: UserSetting, enabled: Boolean) {
        viewModelScope.launch { userPreferences.set(setting, enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                NotificationSettingsViewModel(application.container.userPreferences)
            }
        }
    }
}
