package com.example.nightwatchprotocol.ui.profile.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.fragment.app.FragmentActivity
import com.example.nightwatchprotocol.ui.biometric.BiometricPrompter
import com.example.nightwatchprotocol.ui.biometric.BiometricResult
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.SectionLabel
import com.example.nightwatchprotocol.ui.components.SettingsRow
import com.example.nightwatchprotocol.ui.components.SettingsSwitch
import com.example.nightwatchprotocol.ui.components.TitleTopBar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel

@Composable
fun PrivacySecurityScreen(
    onClose: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenRecoveryEmail: () -> Unit,
    onOpenDevices: () -> Unit,
    viewModel: PrivacySecurityViewModel = viewModel(factory = PrivacySecurityViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshRecoveryEmail() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> viewModel.onExportDestinationChosen(uri?.toString()) }

    val activity = LocalActivity.current as? FragmentActivity
    LaunchedEffect(viewModel, activity) {
        viewModel.events.collect { event ->
            when (event) {
                PrivacySecurityEvent.VerifyBiometric -> {
                    if (activity == null) {
                        viewModel.onBiometricVerified(false)
                    } else {
                        // Activation needs a real fingerprint: no PIN fallback here (spec 3c).
                        BiometricPrompter(activity).authenticate(
                            title = "Activar bloqueo",
                            subtitle = "Verifica tu huella para activarlo",
                            allowDeviceCredential = false,
                        ) { result -> viewModel.onBiometricVerified(result == BiometricResult.Success) }
                    }
                }
            }
        }
    }

    PrivacySecurityContent(
        uiState = uiState,
        onClose = onClose,
        onOpenChangePassword = onOpenChangePassword,
        onOpenRecoveryEmail = onOpenRecoveryEmail,
        onOpenDevices = onOpenDevices,
        onBiometricLockToggle = viewModel::onBiometricLockToggled,
        onHidePublicProgressChange = viewModel::onHidePublicProgressChanged,
        onDownloadData = { exportLauncher.launch(EXPORT_FILE_NAME) },
    )
}

private const val EXPORT_FILE_NAME = "nightwatch-mis-datos.json"

@Composable
private fun PrivacySecurityContent(
    uiState: PrivacySecurityUiState,
    onClose: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenRecoveryEmail: () -> Unit,
    onOpenDevices: () -> Unit,
    onBiometricLockToggle: (Boolean) -> Unit,
    onHidePublicProgressChange: (Boolean) -> Unit,
    onDownloadData: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = "Privacidad y Seguridad", onClose = onClose)
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
            ) {
                SectionLabel("SEGURIDAD DE LA CUENTA", dimmed = false)
                SettingsRow("🔑", "Cambiar Contraseña", onClick = onOpenChangePassword)
                SettingsRow(
                    emoji = "📧",
                    label = "Correo de Recuperación",
                    subtitle = uiState.recoveryEmail ?: "Agregar correo de recuperación",
                    onClick = onOpenRecoveryEmail,
                )
                SettingsRow(
                    emoji = "📱",
                    label = "Administrar Dispositivos",
                    subtitle = "${uiState.activeDevices} dispositivos activos",
                    onClick = onOpenDevices,
                )
                SectionLabel("PRIVACIDAD", dimmed = false, modifier = Modifier.padding(top = 14.dp))
                SettingsRow(
                    emoji = "🔒",
                    label = "Bloqueo Biométrico",
                    subtitle = "Pide huella o rostro al abrir la app",
                    trailing = {
                        SettingsSwitch(checked = uiState.biometricLockEnabled, onCheckedChange = onBiometricLockToggle)
                    },
                    onClick = { onBiometricLockToggle(!uiState.biometricLockEnabled) },
                )
                uiState.biometricError?.let { error ->
                    Text(text = error, color = NightWatchError, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
                }
                SettingsRow(
                    emoji = "👁️",
                    label = "Ocultar mi progreso público",
                    subtitle = "Otros no verán tu nivel ni tus islas",
                    trailing = {
                        SettingsSwitch(
                            checked = uiState.hidePublicProgress,
                            onCheckedChange = onHidePublicProgressChange,
                        )
                    },
                    onClick = { onHidePublicProgressChange(!uiState.hidePublicProgress) },
                )
                SettingsRow(
                    emoji = "⬇️",
                    label = "Descargar mis datos",
                    subtitle = "Exporta una copia de tu información",
                    onClick = onDownloadData,
                )
                when (uiState.exportStatus) {
                    ExportStatus.Done ->
                        Text(MESSAGE_EXPORT_DONE, color = NightWatchHeaderPanel, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
                    ExportStatus.Failed ->
                        Text(MESSAGE_EXPORT_FAILED, color = NightWatchError, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
                    ExportStatus.InProgress, ExportStatus.Idle -> Unit
                }
            }
        }
    }
}
