package com.example.nightwatchprotocol.ui.auth.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.ui.auth.GoogleCredentialResult
import com.example.nightwatchprotocol.ui.auth.requestGoogleIdToken
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind
import com.example.nightwatchprotocol.ui.auth.login.message
import androidx.compose.ui.res.stringResource
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.ui.common.asString
import com.example.nightwatchprotocol.ui.components.GoogleSignInButton
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchTheme
import com.example.nightwatchprotocol.ui.theme.NightWatchSuccess
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToVerifyCode: (email: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var dialogKind by rememberSaveable { mutableStateOf<ErrorDialogKind?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RegisterEvent.NavigateToMainMenu -> onRegisterSuccess()
                RegisterEvent.NavigateBack -> onNavigateBack()
                is RegisterEvent.NavigateToVerifyCode -> onNavigateToVerifyCode(event.email)
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

    dialogKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { dialogKind = null },
            title = { Text(stringResource(R.string.auth_register_error_title)) },
            text = { Text(kind.message(unknown = UiText.of(R.string.auth_register_error_unknown)).asString()) },
            confirmButton = { TextButton(onClick = { dialogKind = null }) { Text(stringResource(R.string.common_accept)) } },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(NightWatchHeaderPanel, RoundedCornerShape(bottomStart = 60.dp, bottomEnd = 60.dp)),
            ) {
                Column(modifier = Modifier.padding(30.dp)) {
                    Text(stringResource(R.string.auth_register_title), style = MaterialTheme.typography.titleLarge, color = NightWatchTheme.colors.onSurfaceButton)
                    Text(
                        stringResource(R.string.auth_register_subtitle),
                        color = NightWatchTheme.colors.onSurfaceButton,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                IconButton(onClick = onCloseClicked, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = NightWatchTheme.colors.onSurfaceButton)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                NightWatchTextField(
                    label = stringResource(R.string.auth_register_full_name),
                    value = uiState.fullName,
                    onValueChange = onFullNameChanged,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )
                NightWatchTextField(
                    label = stringResource(R.string.common_email_label),
                    value = uiState.email,
                    onValueChange = onEmailChanged,
                    isError = uiState.fieldError == RegisterFieldError.EmailAlreadyInUse ||
                        uiState.fieldError == RegisterFieldError.EmailRegisteredWithGoogle ||
                        uiState.fieldError == RegisterFieldError.InvalidEmail,
                    supportingText = when (uiState.fieldError) {
                        RegisterFieldError.EmailAlreadyInUse -> stringResource(R.string.error_auth_email_already_in_use)
                        RegisterFieldError.EmailRegisteredWithGoogle ->
                            stringResource(R.string.auth_register_email_registered_with_google)
                        RegisterFieldError.InvalidEmail -> stringResource(R.string.common_invalid_email)
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.padding(top = 12.dp),
                )
                NightWatchTextField(
                    label = stringResource(R.string.auth_password_label),
                    value = uiState.password,
                    onValueChange = onPasswordChanged,
                    isPassword = true,
                    isError = uiState.fieldError == RegisterFieldError.WeakPassword,
                    supportingText = if (uiState.fieldError == RegisterFieldError.WeakPassword) {
                        stringResource(R.string.auth_register_weak_password)
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    modifier = Modifier.padding(top = 12.dp),
                )
                NightWatchTextField(
                    label = stringResource(R.string.auth_register_verify_password),
                    value = uiState.confirmPassword,
                    onValueChange = onConfirmPasswordChanged,
                    isPassword = true,
                    isError = uiState.showPasswordMismatch || uiState.fieldError == RegisterFieldError.PasswordMismatch,
                    supportingText = if (uiState.showPasswordMismatch || uiState.fieldError == RegisterFieldError.PasswordMismatch) {
                        stringResource(R.string.auth_register_password_mismatch)
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (uiState.isRegisterEnabled) onRegisterClicked() }),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    PasswordRuleRow(stringResource(R.string.profile_password_rule_length), uiState.passwordRules.hasMinLength)
                    PasswordRuleRow(stringResource(R.string.profile_password_rule_upper_digit), uiState.passwordRules.hasUpperCaseAndDigit)
                    PasswordRuleRow(stringResource(R.string.profile_password_rule_lower), uiState.passwordRules.hasLowercase)
                    PasswordRuleRow(stringResource(R.string.profile_password_rule_special), uiState.passwordRules.hasSpecialChar)
                }
                NightWatchButton(
                    text = stringResource(R.string.auth_register_button),
                    onClick = onRegisterClicked,
                    enabled = uiState.isRegisterEnabled,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(
                    stringResource(R.string.auth_login_or),
                    color = NightWatchOnBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
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
            contentDescription = stringResource(
                if (satisfied) R.string.profile_password_rule_met else R.string.profile_password_rule_pending,
                text,
            ),
            tint = if (satisfied) NightWatchSuccess else NightWatchOnBackground.copy(alpha = 0.4f),
        )
        Text(text, color = NightWatchOnBackground, fontSize = 14.sp)
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
