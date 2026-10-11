package com.example.nightwatchprotocol.ui.profile.usermenu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.ProfileTopBar
import com.example.nightwatchprotocol.ui.components.SectionLabel
import com.example.nightwatchprotocol.ui.components.SettingsRow
import com.example.nightwatchprotocol.ui.components.UserAvatar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchPillShape

@Composable
fun UserMenuScreen(
    onClose: () -> Unit,
    onOpenProfile: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: UserMenuViewModel = viewModel(factory = UserMenuViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                UserMenuEvent.SignedOut -> onSignedOut()
            }
        }
    }

    UserMenuContent(
        uiState = uiState,
        versionName = versionName,
        onClose = onClose,
        onOpenProfile = onOpenProfile,
        onSignOut = viewModel::onSignOutClicked,
    )
}

@Composable
private fun UserMenuContent(
    uiState: UserMenuUiState,
    versionName: String,
    onClose: () -> Unit,
    onOpenProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header and "X" stay fixed; only the list below scrolls.
            ProfileTopBar(onClose = onClose)
            UserSummaryPill(uiState)
            Column(
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 41.dp, end = 41.dp, top = 41.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
            ) {
                MenuSection("CUENTA") {
                    SettingsRow("👤", "Ver Perfil", onClick = onOpenProfile)
                    SettingsRow("✏️", "Editar Perfil")
                }
                MenuSection("PROGRESO") {
                    SettingsRow("🏆", "Logros e Insignias")
                }
                MenuSection("PREFERENCIAS") {
                    SettingsRow("🔔", "Notificaciones")
                    SettingsRow("🎨", "Apariencia")
                    SettingsRow("🌐", "Idioma")
                }
                MenuSection("PRIVACIDAD Y SEGURIDAD") {
                    SettingsRow("🔒", "Privacidad y Seguridad")
                }
                MenuSection("SOPORTE") {
                    SettingsRow("❓", "Ayuda y Soporte")
                    SettingsRow("ℹ️", "Acerca de")
                }
                SignOutButton(onClick = onSignOut)
                Text(
                    text = "Nightwatch Protocol · v$versionName",
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().alpha(0.5f),
                )
            }
        }
    }
}

@Composable
private fun UserSummaryPill(uiState: UserMenuUiState) {
    val shape = RoundedCornerShape(100.dp)
    // The pill bleeds off the left edge in the design: only its right end is visible.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 30.dp, end = 101.dp)
            .fillMaxWidth()
            .height(75.dp)
            .shadow(4.dp, shape)
            .background(Color(0x99D9D9D9), shape)
            .padding(start = 23.dp, end = 16.dp),
    ) {
        UserAvatar(display = uiState.avatar, size = 51.dp)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = uiState.displayName,
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Racha Activa de ${uiState.streakDays} días 🔥",
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MenuSection(title: String, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(title)
        rows()
    }
}

@Composable
private fun SignOutButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(2.dp, NightWatchBorderAccent, NightWatchPillShape)
            .clip(NightWatchPillShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = "🚪  Cerrar Sesión",
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}
