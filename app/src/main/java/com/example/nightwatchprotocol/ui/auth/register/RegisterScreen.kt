package com.example.nightwatchprotocol.ui.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind
import com.example.nightwatchprotocol.ui.components.GoogleSignInButton
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dialogKind by remember { mutableStateOf<ErrorDialogKind?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RegisterEvent.NavigateToMainMenu -> onRegisterSuccess()
                RegisterEvent.NavigateBack -> onNavigateBack()
                is RegisterEvent.ShowErrorDialog -> dialogKind = event.kind
            }
        }
    }

    RegisterScreenContent(
        uiState = uiState,
        onFullNameChanged = viewModel::onFullNameChanged,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
        onRegisterClicked = viewModel::onRegisterClicked,
        onCloseClicked = viewModel::onNavigateBackClicked,
        onGoogleSignInClicked = {
            coroutineScope.launch {
                val credentialManager = CredentialManager.create(context)
                val option = GetSignInWithGoogleOption.Builder(
                    serverClientId = context.getString(R.string.default_web_client_id),
                ).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                try {
                    val result = credentialManager.getCredential(context, request)
                    val credential = result.credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        viewModel.onGoogleIdTokenReceived(googleIdTokenCredential.idToken)
                    } else {
                        viewModel.onGoogleSignInFailed()
                    }
                } catch (e: GetCredentialException) {
                    viewModel.onGoogleSignInFailed()
                }
            }
        },
    )

    dialogKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { dialogKind = null },
            title = { Text("Error de registro") },
            text = {
                Text(
                    if (kind == ErrorDialogKind.NETWORK) {
                        "Sin conexión a internet. Verifica tu conexión e intenta de nuevo."
                    } else {
                        "Se produjo un error inesperado. Intenta registrarte de nuevo."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { dialogKind = null }) { Text("Aceptar") } },
        )
    }
}

@Composable
private fun RegisterScreenContent(
    uiState: RegisterUiState,
    onFullNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onRegisterClicked: () -> Unit,
    onCloseClicked: () -> Unit,
    onGoogleSignInClicked: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(NightWatchHeaderPanel, RoundedCornerShape(bottomStart = 60.dp, bottomEnd = 60.dp)),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Registro de Usuario", style = MaterialTheme.typography.titleLarge, color = Color.Black)
                    Text(
                        "Ingrese los datos requeridos a continuación",
                        color = Color.Black,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                IconButton(onClick = onCloseClicked, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.Black)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                NightWatchTextField(
                    label = "Nombre Completo",
                    value = uiState.fullName,
                    onValueChange = onFullNameChanged,
                )
                NightWatchTextField(
                    label = "Correo electrónico",
                    value = uiState.email,
                    onValueChange = onEmailChanged,
                    isError = uiState.fieldError == RegisterFieldError.EmailAlreadyInUse ||
                        uiState.fieldError == RegisterFieldError.InvalidEmail,
                    supportingText = when (uiState.fieldError) {
                        RegisterFieldError.EmailAlreadyInUse -> "Ya tienes una cuenta con este correo"
                        RegisterFieldError.InvalidEmail -> "Correo electrónico inválido"
                        else -> null
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
                NightWatchTextField(
                    label = "Contraseña",
                    value = uiState.password,
                    onValueChange = onPasswordChanged,
                    isPassword = true,
                    modifier = Modifier.padding(top = 12.dp),
                )
                NightWatchTextField(
                    label = "Verifica la contraseña",
                    value = uiState.confirmPassword,
                    onValueChange = onConfirmPasswordChanged,
                    isPassword = true,
                    isError = uiState.fieldError == RegisterFieldError.PasswordMismatch,
                    supportingText = if (uiState.fieldError == RegisterFieldError.PasswordMismatch) {
                        "Contraseña no coincide"
                    } else {
                        null
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    PasswordRuleRow("Mínimo 8 caracteres", uiState.passwordRules.hasMinLength)
                    PasswordRuleRow("Incluye una mayúscula y un número", uiState.passwordRules.hasUpperCaseAndDigit)
                    PasswordRuleRow("Incluye al menos un carácter especial", uiState.passwordRules.hasSpecialChar)
                }
                NightWatchButton(
                    text = "REGISTRARSE",
                    onClick = onRegisterClicked,
                    enabled = uiState.isRegisterEnabled,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    "O",
                    color = NightWatchOnBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                GoogleSignInButton(onClick = onGoogleSignInClicked, enabled = !uiState.isLoading)
            }
        }
    }
}

@Composable
private fun PasswordRuleRow(text: String, satisfied: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = if (satisfied) Color(0xFF4CAF50) else NightWatchOnBackground.copy(alpha = 0.4f),
        )
        Text(text, color = NightWatchOnBackground)
    }
}

@Composable
internal fun RegisterScreenContentForTest() {
    var uiState by remember { mutableStateOf(RegisterUiState()) }
    RegisterScreenContent(
        uiState = uiState,
        onFullNameChanged = { uiState = uiState.copy(fullName = it) },
        onEmailChanged = { uiState = uiState.copy(email = it) },
        onPasswordChanged = { uiState = uiState.copy(password = it) },
        onConfirmPasswordChanged = { uiState = uiState.copy(confirmPassword = it) },
        onRegisterClicked = {},
        onCloseClicked = {},
        onGoogleSignInClicked = {},
    )
}
