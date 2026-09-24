package com.example.nightwatchprotocol.ui.auth.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.GoogleSignInButton
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.theme.NightWatchDisplayStyle
import com.example.nightwatchprotocol.ui.theme.NightWatchLinkAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dialogKind by remember { mutableStateOf<ErrorDialogKind?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LoginEvent.NavigateToMainMenu -> onLoginSuccess()
                LoginEvent.NavigateToRegister -> onNavigateToRegister()
                LoginEvent.NavigateToForgotPassword -> onNavigateToForgotPassword()
                is LoginEvent.ShowErrorDialog -> dialogKind = event.kind
            }
        }
    }

    LoginScreenContent(
        uiState = uiState,
        onUsernameChanged = viewModel::onUsernameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onRememberSessionChanged = viewModel::onRememberSessionChanged,
        onSignInClicked = viewModel::onSignInClicked,
        onRegisterLinkClicked = viewModel::onRegisterLinkClicked,
        onForgotPasswordClicked = viewModel::onForgotPasswordClicked,
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
                } catch (e: GoogleIdTokenParsingException) {
                    viewModel.onGoogleSignInFailed()
                }
            }
        },
    )

    dialogKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { dialogKind = null },
            title = { Text("Error de inicio de sesión") },
            text = {
                Text(
                    if (kind == ErrorDialogKind.NETWORK) {
                        "Sin conexión a internet. Verifica tu conexión e intenta de nuevo."
                    } else {
                        "Se produjo un error inesperado. Intenta iniciar sesión de nuevo."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { dialogKind = null }) { Text("Aceptar") }
            },
        )
    }
}

@Composable
private fun LoginScreenContent(
    uiState: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRememberSessionChanged: (Boolean) -> Unit,
    onSignInClicked: () -> Unit,
    onRegisterLinkClicked: () -> Unit,
    onForgotPasswordClicked: () -> Unit,
    onGoogleSignInClicked: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 32.dp)
                .wrapContentSize(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_logo_nightwatch),
                contentDescription = null,
                modifier = Modifier.size(180.dp),
            )
            Text(
                text = "Nightwatch Protocol",
                style = NightWatchDisplayStyle,
                color = NightWatchOnBackground,
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
            )
            NightWatchTextField(
                label = "Nombre de usuario/correo electrónico",
                value = uiState.usernameOrEmail,
                onValueChange = onUsernameChanged,
                isError = uiState.fieldError == LoginFieldError.UserNotFound ||
                    uiState.fieldError == LoginFieldError.InvalidEmail,
                supportingText = when (uiState.fieldError) {
                    LoginFieldError.UserNotFound -> "Correo no encontrado"
                    LoginFieldError.InvalidEmail -> "Correo electrónico inválido"
                    LoginFieldError.BlankFields -> "Completa ambos campos"
                    else -> null
                },
            )
            NightWatchTextField(
                label = "Contraseña",
                value = uiState.password,
                onValueChange = onPasswordChanged,
                isPassword = true,
                isError = uiState.fieldError == LoginFieldError.WrongPassword,
                supportingText = if (uiState.fieldError == LoginFieldError.WrongPassword) {
                    "Correo o contraseña incorrectos"
                } else {
                    null
                },
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "¿Olvidaste tu contraseña?",
                color = NightWatchLinkAccent,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.End)
                    .clickableText(onForgotPasswordClicked),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Checkbox(checked = uiState.rememberSession, onCheckedChange = onRememberSessionChanged)
                Text("Mantener sesión iniciada", color = NightWatchOnBackground)
            }
            NightWatchButton(
                text = "INICIAR SESIÓN",
                onClick = onSignInClicked,
                enabled = !uiState.isLoading,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text("O", color = NightWatchOnBackground, modifier = Modifier.padding(vertical = 8.dp))
            GoogleSignInButton(onClick = onGoogleSignInClicked, enabled = !uiState.isLoading)
            Row(modifier = Modifier.padding(top = 24.dp)) {
                Text("¿Aún no tienes cuenta? ", color = NightWatchOnBackground)
                Text(
                    text = "Regístrate aquí",
                    color = NightWatchLinkAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickableText(onRegisterLinkClicked),
                )
            }
        }
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
