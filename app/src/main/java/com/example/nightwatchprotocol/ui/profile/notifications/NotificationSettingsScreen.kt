package com.example.nightwatchprotocol.ui.profile.notifications

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.common.asString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.data.preferences.UserSetting
import com.example.nightwatchprotocol.ui.components.SettingsRow
import com.example.nightwatchprotocol.ui.components.SettingsScaffold
import com.example.nightwatchprotocol.ui.components.SettingsSwitch
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchSecondaryText
import com.example.nightwatchprotocol.ui.theme.NightWatchTextError
import com.example.nightwatchprotocol.ui.theme.errorMark

@Composable
fun NotificationSettingsScreen(
    onClose: () -> Unit,
    viewModel: NotificationSettingsViewModel = viewModel(factory = NotificationSettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                NotificationSettingsEvent.RequestPermission ->
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Composable
    fun Item(emoji: String, label: String, subtitle: String, setting: UserSetting, enabled: Boolean) {
        SettingsRow(
            emoji = emoji,
            label = label,
            subtitle = subtitle,
            trailing = {
                SettingsSwitch(checked = enabled, onCheckedChange = { viewModel.onSettingChanged(setting, it) })
            },
            onClick = { viewModel.onSettingChanged(setting, !enabled) },
        )
    }

    // The design leaves a gap under the header before the first card.
    SettingsScaffold(title = stringResource(R.string.settings_notifications_title), onClose = onClose, topPadding = 137.dp) {
        Item("📘", stringResource(R.string.settings_notifications_daily_label), stringResource(R.string.settings_notifications_daily_subtitle),
            UserSetting.DAILY_REMINDER, state.dailyReminder)
        Item("🏆", stringResource(R.string.settings_notifications_achievements_label), stringResource(R.string.settings_notifications_achievements_subtitle),
            UserSetting.ACHIEVEMENT_NOTIFICATIONS, state.achievements)
        Item("🔥", stringResource(R.string.settings_notifications_streak_label), stringResource(R.string.settings_notifications_streak_subtitle),
            UserSetting.STREAK_AT_RISK, state.streakAtRisk)
        Item("🧪", stringResource(R.string.settings_notifications_labs_label), stringResource(R.string.settings_notifications_labs_subtitle),
            UserSetting.NEW_LABS, state.newLabs)
        Item("📣", stringResource(R.string.settings_notifications_news_label), stringResource(R.string.settings_notifications_news_subtitle),
            UserSetting.NEWS_AND_UPDATES, state.news)
        state.permissionMessage?.let { message ->
            Text(text = errorMark(message.asString()), color = NightWatchTextError, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
        }
        Text(
            text = stringResource(R.string.settings_notifications_footnote),
            color = NightWatchSecondaryText,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
