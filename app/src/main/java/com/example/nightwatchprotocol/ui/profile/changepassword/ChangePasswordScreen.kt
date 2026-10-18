package com.example.nightwatchprotocol.ui.profile.changepassword

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.auth.register.PasswordRules
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.components.TitleTopBar
import com.example.nightwatchprotocol.ui.profile.MESSAGE_PASSWORDS_DO_NOT_MATCH
import com.example.nightwatchprotocol.ui.profile.editprofile.GOOGLE_ACCOUNT_NOTICE
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSuccess
import com.example.nightwatchprotocol.ui.theme.NightWatchPillShape
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

@Composable
fun ChangePasswordScreen(
    onClose: () -> Unit,
    onPasswordChanged: () -> Unit,
    viewModel: ChangePasswordViewModel = viewModel(factory = ChangePasswordViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ChangePasswordEvent.PasswordChanged -> onPasswordChanged()
            }
        }
    }

    ChangePasswordContent(
        uiState = uiState,
        onClose = onClose,
        onCurrentChange = viewModel::onCurrentPasswordChanged,
        onNewChange = viewModel::onNewPasswordChanged,
        onConfirmChange = viewModel::onConfirmPasswordChanged,
        onSave = viewModel::onSaveClicked,
    )
}

@Composable
private fun ChangePasswordContent(
    uiState: ChangePasswordUiState,
    onClose: () -> Unit,
    onCurrentChange: (String) -> Unit,
    onNewChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = "Cambiar Contraseña", onClose = onClose)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
            ) {
                if (!uiState.hasPassword) {
                    Text(
                        text = GOOGLE_ACCOUNT_NOTICE,
                        color = NightWatchHeaderPanel,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                } else {
                    // Fields, requirements and button share the same width and the same 20dp side margins.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    ) {
                        NightWatchTextField(
                            label = "Contraseña actual",
                            value = uiState.currentPassword,
                            onValueChange = onCurrentChange,
                            isPassword = true,
                            isError = uiState.currentError != null,
                            supportingText = uiState.currentError,
                        )
                        NightWatchTextField(
                            label = "Nueva contraseña",
                            value = uiState.newPassword,
                            onValueChange = onNewChange,
                            isPassword = true,
                            // Live: "igual a la actual" shows while typing; the rule list below shows the rest.
                            isError = uiState.newError != null || uiState.sameAsCurrent,
                            supportingText = uiState.newError
                                ?: if (uiState.sameAsCurrent) MESSAGE_SAME_PASSWORD else null,
                        )
                        PasswordRequirements(uiState.rules)
                        NightWatchTextField(
                            label = "Confirmar nueva contraseña",
                            value = uiState.confirmPassword,
                            onValueChange = onConfirmChange,
                            isPassword = true,
                            isError = uiState.confirmError != null || uiState.confirmationMismatch,
                            supportingText = uiState.confirmError
                                ?: if (uiState.confirmationMismatch) MESSAGE_PASSWORDS_DO_NOT_MATCH else null,
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
                        onClick = onSave,
                        modifier = Modifier.padding(top = 28.dp, bottom = 32.dp),
                    )
                }
            }
        }
    }
}

/** One line per sign-up password rule, green when met: the same rules and look as the registration screen. */
@Composable
internal fun PasswordRequirements(rules: PasswordRules) {
    Column(modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
        passwordRequirements(rules).forEach { (text, satisfied) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "$text: ${if (satisfied) "cumplido" else "pendiente"}",
                    tint = if (satisfied) NightWatchSuccess else NightWatchOnBackground.copy(alpha = 0.4f),
                )
                Text(text, color = NightWatchOnBackground, fontFamily = NightWatchBodyFontFamily, fontSize = 14.sp)
            }
        }
    }
}

/** The wide "Guardar cambios" pill from the Cambiar Contraseña frames (372x48). */
@Composable
internal fun SaveButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Guardar cambios",
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .shadow(4.dp, NightWatchPillShape)
            .background(NightWatchSurfaceButton, NightWatchPillShape)
            .clip(NightWatchPillShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = text,
            color = Color(0xFF162523),
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}
