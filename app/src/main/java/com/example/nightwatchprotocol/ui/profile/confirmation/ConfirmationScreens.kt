package com.example.nightwatchprotocol.ui.profile.confirmation

import androidx.compose.runtime.Composable
import com.example.nightwatchprotocol.ui.components.ConfirmationContent

// The five static "¡... actualizado!" screens. Texts are the ones in the Figma frames.

@Composable
fun ProfileUpdatedScreen(onBackToProfile: () -> Unit) {
    ConfirmationContent(
        title = "¡Perfil actualizado!",
        message = "Tus datos se guardaron correctamente.",
        buttonText = "VOLVER A MI PERFIL",
        onButtonClick = onBackToProfile,
        onClose = onBackToProfile,
        messageSizeSp = 14,
    )
}

@Composable
fun EmailUpdatedScreen(onBackToEditProfile: () -> Unit) {
    ConfirmationContent(
        title = "¡Correo actualizado!",
        message = "Tu correo electrónico fue verificado y actualizado con éxito.",
        buttonText = "VOLVER A EDITAR PERFIL",
        onButtonClick = onBackToEditProfile,
        onClose = onBackToEditProfile,
    )
}

@Composable
fun RecoveryEmailSavedScreen(onBackToAccounts: () -> Unit) {
    ConfirmationContent(
        title = "¡Correo guardado!",
        message = "Tu correo de recuperación fue guardado con éxito.",
        buttonText = "VOLVER A CUENTAS",
        onButtonClick = onBackToAccounts,
        onClose = onBackToAccounts,
    )
}

@Composable
fun PasswordUpdatedScreen(onDone: () -> Unit, headerTitle: String = "Cambiar Contraseña") {
    ConfirmationContent(
        title = "¡Contraseña actualizada!",
        message = "Usa tu nueva contraseña la próxima vez que inicies sesión.",
        buttonText = "Listo",
        onButtonClick = onDone,
        onClose = onDone,
        headerTitle = headerTitle,
    )
}

@Composable
fun DeviceSignedOutScreen(onBackToDevices: () -> Unit) {
    ConfirmationContent(
        title = "¡Sesión cerrada!",
        message = "Ese dispositivo cerrará su sesión la próxima vez que abra NightWatch. No revoca de inmediato el acceso de tu cuenta.",
        buttonText = "VOLVER A DISPOSITIVOS",
        onButtonClick = onBackToDevices,
        onClose = onBackToDevices,
    )
}
