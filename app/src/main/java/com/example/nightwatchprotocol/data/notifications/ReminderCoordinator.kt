package com.example.nightwatchprotocol.data.notifications

import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import kotlinx.coroutines.flow.first

/**
 * Keeps the scheduled reminders equal to the switches: a reminder is planned only while its switch is on AND
 * notifications are allowed, and cancelled otherwise.
 */
class ReminderCoordinator(
    private val scheduler: ReminderScheduler,
    private val permission: NotificationPermission,
    private val userPreferences: UserPreferences,
) {
    /** Re-aligns every reminder with the stored switches; run at app start. */
    suspend fun sync() {
        val settings = userPreferences.settings.first()
        ReminderKind.entries.forEach { kind ->
            if (settings[kind.setting] && permission.isGranted()) scheduler.schedule(kind) else scheduler.cancel(kind)
        }
    }

    /** Applies one switch change to its reminder (no-op for switches that have no local reminder). */
    fun apply(setting: UserSetting, enabled: Boolean) {
        val kind = ReminderKind.forSetting(setting) ?: return
        if (enabled && permission.isGranted()) scheduler.schedule(kind) else scheduler.cancel(kind)
    }
}
