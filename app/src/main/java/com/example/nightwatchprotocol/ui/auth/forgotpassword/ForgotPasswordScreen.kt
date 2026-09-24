package com.example.nightwatchprotocol.ui.auth.forgotpassword

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.theme.NightWatchLinkAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(factory = ForgotPasswordViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()

    NightWatchScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .wrapContentSize(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Recuperación de contraseña",
                style = MaterialTheme.typography.titleLarge,
                color = NightWatchOnBackground,
            )
            Text(
                text = if (uiState.isEmailSent) {
                    "Si el correo existe, te enviamos un enlace para restablecer tu contraseña. Revisa tu bandeja de entrada."
                } else {
                    "Ingresa tu correo electrónico para recibir un enlace de restablecimiento."
                },
                color = NightWatchOnBackground,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            )
            if (!uiState.isEmailSent) {
                NightWatchTextField(
                    label = "Correo electrónico",
                    value = uiState.email,
                    onValueChange = viewModel::onEmailChanged,
                    isError = uiState.invalidEmailError,
                    supportingText = when {
                        uiState.invalidEmailError -> "Correo electrónico inválido"
                        uiState.networkError -> "Sin conexión a internet"
                        else -> null
                    },
                )
                NightWatchButton(
                    text = "Confirmar",
                    onClick = viewModel::onSendClicked,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Text(
                "Volver al menú",
                color = NightWatchLinkAccent,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clickable(onClick = onNavigateBack),
            )
        }
    }
}
