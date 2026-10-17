package com.example.nightwatchprotocol.ui.auth.forgotpassword

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.theme.NightWatchLinkAccent
import com.example.nightwatchprotocol.ui.verifycode.VerifyCodeContent
import com.example.nightwatchprotocol.ui.verifycode.VerifyCodeEvent
import com.example.nightwatchprotocol.ui.verifycode.VerifyCodeLayout
import com.example.nightwatchprotocol.ui.verifycode.VerifyCodeViewModel

const val RECOVERY_DIALOG_TITLE = "Recuperación de contraseña"

/**
 * "Recuperación de contraseña" over Login (Figma 113:38 and 110:332), adapted to email: first the email field, then the
 * same dialog with the six boxes. [openId] is a new number every time the dialog is opened, so each opening gets
 * its own state; [onVerified] is called once the code was accepted (a recovery session is then open).
 */
@Composable
fun PasswordRecoveryDialog(
    openId: Int,
    prefillEmail: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    val recoveryViewModel: PasswordRecoveryViewModel = viewModel(
        key = "password-recovery-$openId",
        factory = PasswordRecoveryViewModel.factory(prefillEmail),
    )
    val state by recoveryViewModel.uiState.collectAsStateWithLifecycle()

    when (state.step) {
        RecoveryStep.Email -> RecoveryEmailDialog(
            state = state,
            onEmailChange = recoveryViewModel::onEmailChanged,
            onConfirm = recoveryViewModel::onSendClicked,
            onDismiss = onDismiss,
        )

        RecoveryStep.Code -> {
            val codeViewModel: VerifyCodeViewModel = viewModel(
                key = "password-recovery-code-$openId",
                factory = remember(state.email) { recoveryViewModel.codeViewModelFactory() },
            )
            val codeState by codeViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(codeViewModel) {
                codeViewModel.events.collect { event ->
                    when (event) {
                        VerifyCodeEvent.Verified -> onVerified()
                    }
                }
            }
            VerifyCodeContent(
                title = RECOVERY_DIALOG_TITLE,
                recipient = state.email,
                uiState = codeState,
                onCodeChange = codeViewModel::onCodeChanged,
                onConfirm = codeViewModel::onConfirmClicked,
                onResend = codeViewModel::onResendClicked,
                onClose = onDismiss,
                layout = VerifyCodeLayout.Dialog,
            )
        }
    }
}

@Composable
private fun RecoveryEmailDialog(
    state: PasswordRecoveryUiState,
    onEmailChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(RECOVERY_DIALOG_TITLE, modifier = Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.img_logo_nightwatch),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 12.dp).size(44.dp),
                )
            }
        },
        text = {
            Column {
                Text(
                    "Para recuperar su contraseña, ingrese su correo electrónico. Recibirá un código de " +
                        "verificación de 6 dígitos para continuar con el proceso.",
                )
                NightWatchTextField(
                    label = "Correo electrónico",
                    value = state.email,
                    onValueChange = onEmailChange,
                    isError = state.error != null,
                    supportingText = state.error?.message,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Volver al menú") } },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !state.isSending && state.email.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NightWatchLinkAccent,
                    contentColor = Color.Black,
                ),
            ) { Text("Confirmar") }
        },
    )
}
