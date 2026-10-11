package com.example.nightwatchprotocol.data.notifications

import com.example.nightwatchprotocol.data.preferences.UserSetting

/**
 * The reminders that can really be raised from the phone alone. Achievements, new labs and product news
 * need content or events that only a backend could provide, so they have no [ReminderKind].
 */
enum class ReminderKind(
    val setting: UserSetting,
    val workName: String,
    val channelId: String,
    val channelName: String,
    val hour: Int,
    val minute: Int,
    val notificationId: Int,
    val title: String,
    val text: String,
) {
    DAILY_PRACTICE(
        setting = UserSetting.DAILY_REMINDER,
        workName = "reminder_daily_practice",
        channelId = "daily_reminder",
        channelName = "Recordatorio de lección diaria",
        hour = 19,
        minute = 0,
        notificationId = 1001,
        title = "Es hora de practicar",
        text = "Una lección corta mantiene tu ritmo. Entra a NightWatch.",
    ),
    STREAK_AT_RISK(
        setting = UserSetting.STREAK_AT_RISK,
        workName = "reminder_streak_at_risk",
        channelId = "streak_at_risk",
        channelName = "Racha en riesgo",
        hour = 21,
        minute = 0,
        notificationId = 1002,
        title = "Tu racha está en riesgo",
        text = "Practica hoy para no perder tu racha.",
    ),
    ;

    companion object {
        fun forSetting(setting: UserSetting): ReminderKind? = entries.firstOrNull { it.setting == setting }
    }
}
