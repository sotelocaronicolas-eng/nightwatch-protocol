package com.example.nightwatchprotocol.ui.profile.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.notifications.NotificationPermission
import com.example.nightwatchprotocol.data.notifications.ReminderCoordinator
import com.example.nightwatchprotocol.data.notifications.ReminderKind
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MESSAGE_NOTIFICATIONS_DENIED =
    "Sin el permiso de notificaciones no podemos enviarte recordatorios. " +
        "Puedes permitirlo en Ajustes del teléfono > Aplicaciones > NightWatch > Notificaciones."

data class NotificationSettingsUiState(
    val dailyReminder: Boolean = UserSetting.DAILY_REMINDER.default,
    val achievements: Boolean = UserSetting.ACHIEVEMENT_NOTIFICATIONS.default,
    val streakAtRisk: Boolean = UserSetting.STREAK_AT_RISK.default,
    val newLabs: Boolean = UserSetting.NEW_LABS.default,
    val news: Boolean = UserSetting.NEWS_AND_UPDATES.default,
    /** Shown in red when the user turned a reminder on but notifications are not allowed. */
    val permissionMessage: String? = null,
)

sealed interface NotificationSettingsEvent {
    /** The screen must show the system dialog for POST_NOTIFICATIONS and report the answer. */
    data object RequestPermission : NotificationSettingsEvent
}

/**
 * Daily practice and streak reminders are real local reminders (see `ReminderCoordinator`); the other three
 * switches only remember the choice, because nothing local can tell when an achievement, lab or news item exists.
 */
class NotificationSettingsViewModel(
    private val userPreferences: UserPreferences,
    private val reminderCoordinator: ReminderCoordinator,
    private val permission: NotificationPermission,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<NotificationSettingsEvent>(Channel.BUFFERED)
    val events: Flow<NotificationSettingsEvent> = _events.receiveAsFlow()

    private var pendingSetting: UserSetting? = null

    init {
        viewModelScope.launch {
            userPreferences.settings.collect { settings ->
                _uiState.update {
                    it.copy(
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
        val needsPermission = enabled && ReminderKind.forSetting(setting) != null && !permission.isGranted()
        if (!needsPermission) {
            commit(setting, enabled)
            return
        }
        if (permission.requiresRuntimePermission) {
            // The switch stays off until the answer arrives.
            pendingSetting = setting
            viewModelScope.launch { _events.send(NotificationSettingsEvent.RequestPermission) }
        } else {
            // Before Android 13 there is no dialog to show: the user blocked notifications in system settings.
            _uiState.update { it.copy(permissionMessage = MESSAGE_NOTIFICATIONS_DENIED) }
        }
    }

    /** The answer to the system dialog shown after [NotificationSettingsEvent.RequestPermission]. */
    fun onPermissionResult(granted: Boolean) {
        val setting = pendingSetting ?: return
        pendingSetting = null
        if (granted && permission.isGranted()) {
            commit(setting, true)
        } else {
            _uiState.update { it.copy(permissionMessage = MESSAGE_NOTIFICATIONS_DENIED) }
        }
    }

    /** Back on the screen: a reminder whose permission was taken away in system settings goes back to off. */
    fun onResume() {
        if (permission.isGranted()) return
        viewModelScope.launch {
            val settings = userPreferences.settings.first()
            ReminderKind.entries.filter { settings[it.setting] }.forEach { kind ->
                userPreferences.set(kind.setting, false)
                reminderCoordinator.apply(kind.setting, false)
            }
        }
    }

    private fun commit(setting: UserSetting, enabled: Boolean) {
        _uiState.update { it.copy(permissionMessage = null) }
        viewModelScope.launch {
            userPreferences.set(setting, enabled)
            reminderCoordinator.apply(setting, enabled)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                NotificationSettingsViewModel(
                    container.userPreferences,
                    container.reminderCoordinator,
                    container.notificationPermission,
                )
            }
        }
    }
}
