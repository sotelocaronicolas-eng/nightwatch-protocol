package com.example.nightwatchprotocol.data.notifications

import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.preferences.UserSetting

/**
 * The reminders that can really be raised from the phone alone. Achievements, new labs and product news
 * need content or events that only a backend could provide, so they have no [ReminderKind].
 *
 * [channelName], [title] and [text] are [UiText]: the notifier resolves them with a `Context` when the channel is created
 * or the notification is posted, so they follow the language of that moment and not the one of the process start.
 */
enum class ReminderKind(
    val setting: UserSetting,
    val workName: String,
    val channelId: String,
    val channelName: UiText,
    val hour: Int,
    val minute: Int,
    val notificationId: Int,
    val title: UiText,
    val text: UiText,
) {
    DAILY_PRACTICE(
        setting = UserSetting.DAILY_REMINDER,
        workName = "reminder_daily_practice",
        channelId = "daily_reminder",
        channelName = UiText.of(R.string.notif_daily_channel_name),
        hour = 19,
        minute = 0,
        notificationId = 1001,
        title = UiText.of(R.string.notif_daily_title),
        text = UiText.of(R.string.notif_daily_text),
    ),
    STREAK_AT_RISK(
        setting = UserSetting.STREAK_AT_RISK,
        workName = "reminder_streak_at_risk",
        channelId = "streak_at_risk",
        channelName = UiText.of(R.string.notif_streak_channel_name),
        hour = 21,
        minute = 0,
        notificationId = 1002,
        title = UiText.of(R.string.notif_streak_title),
        text = UiText.of(R.string.notif_streak_text),
    ),
    ;

    companion object {
        fun forSetting(setting: UserSetting): ReminderKind? = entries.firstOrNull { it.setting == setting }
    }
}
