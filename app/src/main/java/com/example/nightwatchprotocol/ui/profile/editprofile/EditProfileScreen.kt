package com.example.nightwatchprotocol.ui.profile.editprofile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.stringResource
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.common.asString
import com.example.nightwatchprotocol.data.profile.BirthDate
import com.example.nightwatchprotocol.data.profile.Gender
import com.example.nightwatchprotocol.ui.components.BirthDatePickerDialog
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.NightWatchSelectField
import com.example.nightwatchprotocol.ui.components.NightWatchTextField
import com.example.nightwatchprotocol.ui.components.ProfileButton
import com.example.nightwatchprotocol.ui.components.ProfileTopBar
import com.example.nightwatchprotocol.ui.components.UserAvatar
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSecondaryText
import com.example.nightwatchprotocol.ui.theme.NightWatchTextError
import com.example.nightwatchprotocol.ui.theme.errorMark

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
        onGenderChange = viewModel::onGenderChanged,
        onBirthDateChange = viewModel::onBirthDateChanged,
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
    onGenderChange: (Gender?) -> Unit,
    onBirthDateChange: (BirthDate?) -> Unit,
    onSave: () -> Unit,
) {
    var genderMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val today = remember { BirthDate.today() }

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
                    text = stringResource(R.string.profile_edit_title),
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
                AvatarLink(text = stringResource(R.string.profile_edit_change_avatar), enabled = !uiState.isAvatarBusy, onClick = onChangeAvatar)
                if (uiState.hasPhoto) {
                    AvatarLink(text = stringResource(R.string.profile_edit_remove_photo), enabled = !uiState.isAvatarBusy, onClick = onRemoveAvatar)
                }
                uiState.avatarError?.let { error ->
                    Text(
                        text = errorMark(error.asString()),
                        color = NightWatchTextError,
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
                        label = stringResource(R.string.profile_edit_full_name),
                        value = uiState.name,
                        onValueChange = onNameChange,
                        isError = uiState.nameError != null,
                        supportingText = uiState.nameError?.asString(),
                    )
                    if (uiState.hasPassword) {
                        NightWatchTextField(
                            label = stringResource(R.string.common_email_label),
                            value = uiState.email,
                            onValueChange = onEmailChange,
                            isError = uiState.emailError != null,
                            supportingText = uiState.emailError?.asString(),
                        )
                        NightWatchTextField(
                            label = stringResource(R.string.common_new_password_label),
                            value = uiState.newPassword,
                            onValueChange = onNewPasswordChange,
                            isPassword = true,
                            isError = uiState.passwordError != null,
                            supportingText = uiState.passwordError?.asString(),
                        )
                        NightWatchTextField(
                            label = stringResource(R.string.profile_edit_verify_password),
                            value = uiState.confirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            isPassword = true,
                            isError = uiState.confirmError != null,
                            supportingText = uiState.confirmError?.asString(),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.profile_edit_google_account_notice),
                            color = NightWatchSecondaryText,
                            fontFamily = NightWatchBodyFontFamily,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    // Optional personal data (not in the Figma frame): gender menu and Material 3 date picker.
                    Box {
                        NightWatchSelectField(
                            label = stringResource(R.string.profile_field_gender),
                            valueText = uiState.gender?.let { stringResource(it.labelRes) }.orEmpty(),
                            onClick = { genderMenuOpen = true },
                        )
                        DropdownMenu(expanded = genderMenuOpen, onDismissRequest = { genderMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_undefined)) },
                                onClick = {
                                    genderMenuOpen = false
                                    onGenderChange(null)
                                },
                            )
                            Gender.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes)) },
                                    onClick = {
                                        genderMenuOpen = false
                                        onGenderChange(option)
                                    },
                                )
                            }
                        }
                    }
                    NightWatchSelectField(
                        label = stringResource(R.string.profile_field_birth_date),
                        valueText = uiState.birthDate?.display().orEmpty(),
                        onClick = { showDatePicker = true },
                        isError = uiState.birthDateError != null,
                        supportingText = uiState.birthDateError?.asString(),
                    )
                    if (uiState.birthDate != null) {
                        AvatarLink(text = stringResource(R.string.profile_edit_remove_date), enabled = true, onClick = { onBirthDateChange(null) })
                    }
                }

                if (showDatePicker) {
                    BirthDatePickerDialog(
                        initial = uiState.birthDate,
                        today = today,
                        onDismiss = { showDatePicker = false },
                        onConfirm = { date ->
                            showDatePicker = false
                            onBirthDateChange(date)
                        },
                    )
                }

                uiState.formError?.let { error ->
                    Text(
                        text = errorMark(error.asString()),
                        color = NightWatchTextError,
                        fontFamily = NightWatchBodyFontFamily,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                ProfileButton(
                    text = stringResource(R.string.profile_edit_save),
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
