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
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.data.preferences.UserSetting
import com.example.nightwatchprotocol.ui.components.SettingsRow
import com.example.nightwatchprotocol.ui.components.SettingsScaffold
import com.example.nightwatchprotocol.ui.components.SettingsSwitch
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel

const val NOTIFICATIONS_FOOTNOTE =
    "Los avisos de logros, laboratorios y novedades se activarán cuando esas funciones existan en la app."

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
        state.permissionMessage?.let { message ->
            Text(text = message, color = NightWatchError, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
        }
        Text(
            text = NOTIFICATIONS_FOOTNOTE,
            color = NightWatchHeaderPanel,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
