package com.example.nightwatchprotocol.ui.profile.confirmation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.common.asString
import com.example.nightwatchprotocol.ui.components.ConfirmationContent
import com.example.nightwatchprotocol.ui.verifycode.CompletionNotice

// The five static "¡... actualizado!" screens. Texts are the ones in the Figma frames.

@Composable
fun ProfileUpdatedScreen(onBackToProfile: () -> Unit) {
    ConfirmationContent(
        title = stringResource(R.string.profile_updated_title),
        message = stringResource(R.string.profile_updated_message),
        buttonText = stringResource(R.string.profile_updated_button),
        onButtonClick = onBackToProfile,
        onClose = onBackToProfile,
        messageSizeSp = 14,
    )
}

/** [notice] is the key of a [CompletionNotice] that travelled in the route (null when everything went well). */
@Composable
fun EmailUpdatedScreen(onBackToEditProfile: () -> Unit, notice: String? = null) {
    val message = stringResource(R.string.profile_email_updated_message)
    val noticeText = CompletionNotice.fromKey(notice)?.toUiText()?.asString()
    ConfirmationContent(
        title = stringResource(R.string.profile_email_updated_title),
        message = if (noticeText == null) message else "$message\n$noticeText",
        buttonText = stringResource(R.string.profile_email_updated_button),
        onButtonClick = onBackToEditProfile,
        onClose = onBackToEditProfile,
    )
}

@Composable
fun RecoveryEmailSavedScreen(onBackToAccounts: () -> Unit) {
    ConfirmationContent(
        title = stringResource(R.string.profile_recovery_saved_title),
        message = stringResource(R.string.profile_recovery_saved_message),
        buttonText = stringResource(R.string.profile_recovery_saved_button),
        onButtonClick = onBackToAccounts,
        onClose = onBackToAccounts,
    )
}

@Composable
fun PasswordUpdatedScreen(
    onDone: () -> Unit,
    headerTitle: String = stringResource(R.string.profile_change_password_title),
) {
    ConfirmationContent(
        title = stringResource(R.string.profile_password_updated_title),
        message = stringResource(R.string.profile_password_updated_message),
        buttonText = stringResource(R.string.profile_password_updated_button),
        onButtonClick = onDone,
        onClose = onDone,
        headerTitle = headerTitle,
    )
}

@Composable
fun DeviceSignedOutScreen(onBackToDevices: () -> Unit) {
    ConfirmationContent(
        title = stringResource(R.string.devices_signed_out_title),
        message = stringResource(R.string.devices_signed_out_message),
        buttonText = stringResource(R.string.devices_signed_out_button),
        onButtonClick = onBackToDevices,
        onClose = onBackToDevices,
    )
}
