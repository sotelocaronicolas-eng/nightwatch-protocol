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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.ui.common.asString
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
            title = { Text(stringResource(R.string.auth_login_error_title)) },
            text = { Text(kind.message(unknown = UiText.of(R.string.auth_login_error_unknown)).asString()) },
            confirmButton = {
                TextButton(onClick = { dialogKind = null }) { Text(stringResource(R.string.common_accept)) }
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
                text = stringResource(R.string.common_app_title),
                style = NightWatchDisplayStyle.copy(fontSize = 28.sp),
                color = NightWatchOnBackground,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
            NightWatchTextField(
                label = stringResource(R.string.auth_login_username_label),
                value = uiState.usernameOrEmail,
                onValueChange = onUsernameChanged,
                isError = uiState.fieldError == LoginFieldError.UserNotFound ||
                    uiState.fieldError == LoginFieldError.InvalidEmail ||
                    uiState.fieldError == LoginFieldError.GoogleAccountOnly ||
                    uiState.showUsernameRequired,
                supportingText = when {
                    uiState.showUsernameRequired -> stringResource(R.string.auth_login_enter_email)
                    uiState.fieldError == LoginFieldError.UserNotFound -> stringResource(R.string.error_auth_user_not_found)
                    uiState.fieldError == LoginFieldError.InvalidEmail -> stringResource(R.string.common_invalid_email)
                    uiState.fieldError == LoginFieldError.GoogleAccountOnly ->
                        stringResource(R.string.auth_google_account_hint)
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            NightWatchTextField(
                label = stringResource(R.string.auth_password_label),
                value = uiState.password,
                onValueChange = onPasswordChanged,
                isPassword = true,
                isError = uiState.fieldError == LoginFieldError.WrongPassword ||
                    uiState.fieldError == LoginFieldError.InvalidCredentials ||
                    uiState.showPasswordRequired,
                supportingText = when {
                    uiState.showPasswordRequired -> stringResource(R.string.auth_login_enter_password)
                    uiState.fieldError == LoginFieldError.WrongPassword -> stringResource(R.string.error_auth_wrong_password)
                    uiState.fieldError == LoginFieldError.InvalidCredentials -> stringResource(R.string.error_auth_invalid_credentials)
                    else -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSignInClicked() }),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.auth_login_forgot_password),
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
                Text(stringResource(R.string.auth_login_keep_session), color = NightWatchOnBackground)
            }
            NightWatchButton(
                text = stringResource(R.string.auth_login_button),
                onClick = onSignInClicked,
                enabled = !uiState.isLoading,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(stringResource(R.string.auth_login_or), color = NightWatchOnBackground, modifier = Modifier.padding(vertical = 8.dp))
            GoogleSignInButton(onClick = onGoogleSignInClicked, enabled = !uiState.isLoading)
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Text(stringResource(R.string.auth_login_no_account), color = NightWatchOnBackground, fontSize = 14.sp)
                Text(
                    text = stringResource(R.string.auth_login_register_here),
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
