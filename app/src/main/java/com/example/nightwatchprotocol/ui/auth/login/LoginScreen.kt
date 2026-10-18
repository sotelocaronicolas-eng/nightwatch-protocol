package com.example.nightwatchprotocol.ui.auth.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.auth.GoogleCredentialResult
import com.example.nightwatchprotocol.ui.auth.forgotpassword.PasswordRecoveryDialog
import com.example.nightwatchprotocol.ui.auth.requestGoogleIdToken
import com.example.nightwatchprotocol.ui.components.GoogleSignInButton
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.theme.NightWatchDisplayStyle
import com.example.nightwatchprotocol.ui.theme.NightWatchLinkAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToVerifyCode: (email: String) -> Unit,
    onNavigateToNewPassword: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dialogKind by rememberSaveable { mutableStateOf<ErrorDialogKind?>(null) }
    // The recovery dialog: the email it starts from (null = closed) and a number that is new at every opening.
    var recoveryPrefill by rememberSaveable { mutableStateOf<String?>(null) }
    var recoveryOpenId by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.OpenRecoveryDialog -> {
                    recoveryOpenId++
                    recoveryPrefill = event.prefillEmail
                }
                LoginEvent.NavigateToMainMenu -> onLoginSuccess()
                LoginEvent.NavigateToRegister -> onNavigateToRegister()
                LoginEvent.NavigateToForgotPassword -> onNavigateToForgotPassword()
                is LoginEvent.NavigateToVerifyCode -> onNavigateToVerifyCode(event.email)
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
                val hashedNonce = viewModel.onGoogleSignInStarted()
                when (val result = requestGoogleIdToken(context, viewModel.googleWebClientId, hashedNonce)) {
                    is GoogleCredentialResult.IdToken -> viewModel.onGoogleIdTokenReceived(result.idToken)
                    GoogleCredentialResult.Cancelled -> viewModel.onGoogleSignInCancelled()
                    GoogleCredentialResult.NoAccount -> viewModel.onGoogleNoAccount()
                    GoogleCredentialResult.Failed -> viewModel.onGoogleSignInFailed()
                }
            }
        },
    )

    recoveryPrefill?.let { prefill ->
        PasswordRecoveryDialog(
            openId = recoveryOpenId,
            prefillEmail = prefill,
            onDismiss = { recoveryPrefill = null },
            onVerified = {
                recoveryPrefill = null
                onNavigateToNewPassword()
            },
        )
    }

    dialogKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { dialogKind = null },
            title = { Text("Error de inicio de sesión") },
            text = { Text(kind.message(unknown = "Se produjo un error inesperado. Intenta iniciar sesión de nuevo.")) },
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
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
                .wrapContentSize(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_logo_nightwatch),
                contentDescription = null,
                modifier = Modifier.size(140.dp),
            )
            Text(
                text = "Nightwatch Protocol",
                style = NightWatchDisplayStyle.copy(fontSize = 28.sp),
                color = NightWatchOnBackground,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            NightWatchTextField(
                label = "Nombre de usuario/Email",
                value = uiState.usernameOrEmail,
                onValueChange = onUsernameChanged,
                isError = uiState.fieldError == LoginFieldError.UserNotFound ||
                    uiState.fieldError == LoginFieldError.InvalidEmail ||
                    uiState.fieldError == LoginFieldError.GoogleAccountOnly ||
                    uiState.showUsernameRequired,
                supportingText = when {
                    uiState.showUsernameRequired -> "Ingresa tu correo"
                    uiState.fieldError == LoginFieldError.UserNotFound -> "El correo no está registrado"
                    uiState.fieldError == LoginFieldError.InvalidEmail -> "Correo electrónico inválido"
                    uiState.fieldError == LoginFieldError.GoogleAccountOnly ->
                        "Esta cuenta se creó con Google. Usa «Continuar con Google»"
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            NightWatchTextField(
                label = "Contraseña",
                value = uiState.password,
                onValueChange = onPasswordChanged,
                isPassword = true,
                isError = uiState.fieldError == LoginFieldError.WrongPassword ||
                    uiState.fieldError == LoginFieldError.InvalidCredentials ||
                    uiState.showPasswordRequired,
                supportingText = when {
                    uiState.showPasswordRequired -> "Ingresa tu contraseña"
                    uiState.fieldError == LoginFieldError.WrongPassword -> "La contraseña es incorrecta"
                    uiState.fieldError == LoginFieldError.InvalidCredentials -> "Correo o contraseña incorrectos"
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSignInClicked() }),
                modifier = Modifier.padding(top = 8.dp),
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
                modifier = Modifier.padding(top = 12.dp),
            )
            Text("O", color = NightWatchOnBackground, modifier = Modifier.padding(vertical = 8.dp))
            GoogleSignInButton(onClick = onGoogleSignInClicked, enabled = !uiState.isLoading)
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Text("¿Aún no tienes cuenta? ", color = NightWatchOnBackground, fontSize = 14.sp)
                Text(
                    text = "Regístrate aquí",
                    color = NightWatchLinkAccent,
                    fontSize = 14.sp,
                    modifier = Modifier.clickableText(onRegisterLinkClicked),
                )
            }
        }
    }
}

private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
