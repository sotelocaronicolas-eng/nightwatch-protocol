package com.example.nightwatchprotocol.ui.profile.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.data.preferences.UserSetting
import com.example.nightwatchprotocol.ui.components.SettingsRow
import com.example.nightwatchprotocol.ui.components.SettingsScaffold
import com.example.nightwatchprotocol.ui.components.SettingsSwitch
import androidx.compose.ui.unit.dp

@Composable
fun NotificationSettingsScreen(
    onClose: () -> Unit,
    viewModel: NotificationSettingsViewModel = viewModel(factory = NotificationSettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
    SettingsScaffold(title = "Notificaciones", onClose = onClose, topPadding = 137.dp) {
        Item("📘", "Recordatorio de lección diaria", "Un aviso a las 7:00 p.m. si no has practicado",
            UserSetting.DAILY_REMINDER, state.dailyReminder)
        Item("🏆", "Logros e insignias", "Cuando desbloquees un nuevo logro",
            UserSetting.ACHIEVEMENT_NOTIFICATIONS, state.achievements)
        Item("🔥", "Racha en riesgo", "Si tu racha está por romperse",
            UserSetting.STREAK_AT_RISK, state.streakAtRisk)
        Item("🧪", "Nuevos laboratorios", "Cuando se publique un lab nuevo",
            UserSetting.NEW_LABS, state.newLabs)
        Item("📣", "Novedades de NightWatch", "Anuncios y noticias del producto",
            UserSetting.NEWS_AND_UPDATES, state.news)
    }
}
