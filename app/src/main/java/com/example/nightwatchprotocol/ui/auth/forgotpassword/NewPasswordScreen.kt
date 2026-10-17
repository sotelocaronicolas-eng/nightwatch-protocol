package com.example.nightwatchprotocol.ui.auth.forgotpassword

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.components.TitleTopBar
import com.example.nightwatchprotocol.ui.profile.changepassword.PasswordRequirements
import com.example.nightwatchprotocol.ui.profile.changepassword.SaveButton
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchError

/**
 * Not in Figma: the "Cambiar Contraseña" screen without the current password (the user forgot it), with the same
 * fields, requirement list and button.
 */
@Composable
fun NewPasswordScreen(
    onPasswordReset: () -> Unit,
    onCancelled: () -> Unit,
    viewModel: NewPasswordViewModel = viewModel(factory = NewPasswordViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                NewPasswordEvent.PasswordReset -> onPasswordReset()
                NewPasswordEvent.Cancelled -> onCancelled()
            }
        }
    }

    // Leaving any other way would leave the recovery session open.
    BackHandler(onBack = viewModel::onCancelClicked)

    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = "Nueva Contraseña", onClose = viewModel::onCancelClicked)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                ) {
                    NightWatchTextField(
                        label = "Nueva contraseña",
                        value = uiState.newPassword,
                        onValueChange = viewModel::onNewPasswordChanged,
                        isPassword = true,
                        isError = uiState.newError != null,
                        supportingText = uiState.newError,
                    )
                    PasswordRequirements(uiState.rules)
                    NightWatchTextField(
                        label = "Confirmar nueva contraseña",
                        value = uiState.confirmPassword,
                        onValueChange = viewModel::onConfirmPasswordChanged,
                        isPassword = true,
                        isError = uiState.confirmError != null || uiState.confirmationMismatch,
                        supportingText = uiState.confirmError
                            ?: if (uiState.confirmationMismatch) "Las contraseñas no coinciden" else null,
                    )
                }
                uiState.formError?.let { error ->
                    Text(
                        text = error,
                        color = NightWatchError,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                SaveButton(
                    enabled = uiState.canSubmit,
                    onClick = viewModel::onSaveClicked,
                    modifier = Modifier.padding(top = 28.dp, bottom = 32.dp),
                )
            }
        }
    }
}
