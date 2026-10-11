package com.example.nightwatchprotocol.ui.profile.editprofile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.components.ProfileButton
import com.example.nightwatchprotocol.ui.components.ProfileTopBar
import com.example.nightwatchprotocol.ui.components.UserAvatar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

@Composable
fun EditProfileScreen(
    identityConfirmed: Boolean,
    onIdentityConfirmedHandled: () -> Unit,
    onClose: () -> Unit,
    onNavigateToConfirmIdentity: () -> Unit,
    onNavigateToChangeEmail: (String) -> Unit,
    onNavigateToProfileUpdated: () -> Unit,
    viewModel: EditProfileViewModel = viewModel(factory = EditProfileViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onAvatarPicked(it.toString()) }
    }

    LaunchedEffect(identityConfirmed) {
        if (identityConfirmed) {
            onIdentityConfirmedHandled()
            viewModel.onIdentityConfirmed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditProfileEvent.NavigateToConfirmIdentity -> onNavigateToConfirmIdentity()
                is EditProfileEvent.NavigateToChangeEmail -> onNavigateToChangeEmail(event.newEmail)
                EditProfileEvent.NavigateToProfileUpdated -> onNavigateToProfileUpdated()
            }
        }
    }

    EditProfileContent(
        uiState = uiState,
        onClose = onClose,
        onNameChange = viewModel::onNameChanged,
        onEmailChange = viewModel::onEmailChanged,
        onNewPasswordChange = viewModel::onNewPasswordChanged,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChanged,
        onChangeAvatar = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemoveAvatar = viewModel::onRemoveAvatar,
        onSave = viewModel::onSaveClicked,
    )
}

@Composable
private fun EditProfileContent(
    uiState: EditProfileUiState,
    onClose: () -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onChangeAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onSave: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ProfileTopBar(onClose = onClose)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 41.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    text = "Editar Perfil",
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(top = 20.dp),
                )
                UserAvatar(
                    display = uiState.avatar,
                    size = 128.dp,
                    modifier = Modifier.padding(top = 14.dp).shadow(4.dp, CircleShape).alpha(if (uiState.isAvatarBusy) 0.5f else 1f),
                )
                AvatarLink(text = "Cambiar Avatar", enabled = !uiState.isAvatarBusy, onClick = onChangeAvatar)
                if (uiState.hasPhoto) {
                    AvatarLink(text = "Quitar foto", enabled = !uiState.isAvatarBusy, onClick = onRemoveAvatar)
                }
                uiState.avatarError?.let { error ->
                    Text(
                        text = error,
                        color = NightWatchError,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) {
                    NightWatchTextField(
                        label = "Nombre completo",
                        value = uiState.name,
                        onValueChange = onNameChange,
                        isError = uiState.nameError != null,
                        supportingText = uiState.nameError,
                    )
                    if (uiState.hasPassword) {
                        NightWatchTextField(
                            label = "Correo electrónico",
                            value = uiState.email,
                            onValueChange = onEmailChange,
                            isError = uiState.emailError != null,
                            supportingText = uiState.emailError,
                        )
                        NightWatchTextField(
                            label = "Nueva contraseña",
                            value = uiState.newPassword,
                            onValueChange = onNewPasswordChange,
                            isPassword = true,
                            isError = uiState.passwordError != null,
                            supportingText = uiState.passwordError,
                        )
                        NightWatchTextField(
                            label = "Verificar contraseña",
                            value = uiState.confirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            isPassword = true,
                            isError = uiState.confirmError != null,
                            supportingText = uiState.confirmError,
                        )
                    } else {
                        Text(
                            text = GOOGLE_ACCOUNT_NOTICE,
                            color = NightWatchHeaderPanel,
                            fontFamily = NightWatchBodyFontFamily,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                uiState.formError?.let { error ->
                    Text(
                        text = error,
                        color = NightWatchError,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                ProfileButton(
                    text = "GUARDAR CAMBIOS",
                    onClick = onSave,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.padding(top = 32.dp, bottom = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun AvatarLink(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier
            .padding(top = 8.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

const val GOOGLE_ACCOUNT_NOTICE =
    "Tu cuenta usa Google: la contraseña y el correo se gestionan desde tu cuenta de Google."
