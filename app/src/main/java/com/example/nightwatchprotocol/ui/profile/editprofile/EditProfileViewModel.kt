package com.example.nightwatchprotocol.ui.profile.editprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.data.avatar.AvatarImageException
import com.example.nightwatchprotocol.data.avatar.AvatarImageProcessor
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.AvatarSyncResult
import com.example.nightwatchprotocol.data.avatar.avatarDisplay
import com.example.nightwatchprotocol.data.profile.BirthDate
import com.example.nightwatchprotocol.data.profile.Gender
import com.example.nightwatchprotocol.data.profile.ProfileExtras
import com.example.nightwatchprotocol.data.profile.ProfileExtrasRepository
import com.example.nightwatchprotocol.data.profile.birthDateIssue
import com.example.nightwatchprotocol.data.profile.message
import com.example.nightwatchprotocol.ui.auth.register.PasswordRules
import com.example.nightwatchprotocol.ui.profile.MESSAGE_GENERIC_ERROR
import com.example.nightwatchprotocol.ui.profile.MESSAGE_PASSWORDS_DO_NOT_MATCH
import com.example.nightwatchprotocol.ui.profile.MESSAGE_PASSWORD_RULES
import com.example.nightwatchprotocol.ui.profile.isPlausibleEmail
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MESSAGE_ENTER_NAME = "Ingresa tu nombre"
const val MESSAGE_INVALID_EMAIL = "Correo electrónico inválido"
const val MESSAGE_AVATAR_UNREADABLE = "No pudimos leer esa imagen"
const val MESSAGE_AVATAR_LOCAL_ONLY = "No se pudo guardar la foto en tu cuenta. Quedó guardada en este teléfono."
const val MESSAGE_AVATAR_SOCIAL_FAILED = "Tu foto se guardó, pero no se pudo actualizar en Social. Vuelve a elegirla para reintentarlo."
const val MESSAGE_AVATAR_SOCIAL_REMOVE_FAILED = "Tu foto se quitó de tu perfil, pero sigue visible en Social. Elige una foto y quítala de nuevo para reintentarlo."

private fun AvatarSyncResult.toMessage(): String? = when (this) {
    AvatarSyncResult.Synced -> null
    AvatarSyncResult.LocalOnly -> MESSAGE_AVATAR_LOCAL_ONLY
    AvatarSyncResult.SocialFailed -> MESSAGE_AVATAR_SOCIAL_FAILED
    AvatarSyncResult.SocialRemoveFailed -> MESSAGE_AVATAR_SOCIAL_REMOVE_FAILED
}

data class EditProfileUiState(
    val name: String = "",
    val email: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    /** False for Google-only accounts: the password and email fields are replaced by a notice. */
    val hasPassword: Boolean = true,
    val avatar: AvatarDisplay = AvatarDisplay.Initial("?"),
    val isSaving: Boolean = false,
    val isAvatarBusy: Boolean = false,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    val avatarError: String? = null,
    val formError: String? = null,
    /** Optional: null means "no definido". */
    val gender: Gender? = null,
    val birthDate: BirthDate? = null,
    val birthDateError: String? = null,
) {
    val hasPhoto: Boolean get() = avatar is AvatarDisplay.Photo
}

sealed interface EditProfileEvent {
    data object NavigateToConfirmIdentity : EditProfileEvent
    data class NavigateToChangeEmail(val newEmail: String) : EditProfileEvent
    data object NavigateToProfileUpdated : EditProfileEvent
}

class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val avatarRepository: AvatarRepository,
    private val avatarImageProcessor: AvatarImageProcessor,
    private val extrasRepository: ProfileExtrasRepository,
    private val today: () -> BirthDate = { BirthDate.today() },
) : ViewModel() {

    private val account = accountRepository.currentProfile()
    private val uid = authRepository.currentUserUid

    private var savedName = account?.displayName?.trim().orEmpty()
    private var passwordSaved = false

    /** What is stored now; the extras are written only when the form differs from this. */
    private var savedExtras = ProfileExtras()

    /** Once the user touches gender or birth date, a late answer from storage must not overwrite their choice. */
    private var extrasTouched = false

    private val _uiState = MutableStateFlow(
        EditProfileUiState(
            name = savedName,
            email = account?.email.orEmpty(),
            hasPassword = account?.hasPassword ?: true,
            avatar = avatarDisplay(null, account?.displayName, account?.email),
        ),
    )
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditProfileEvent>(Channel.BUFFERED)
    val events: Flow<EditProfileEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            avatarRepository.avatar.collect { bytes ->
                _uiState.update { it.copy(avatar = avatarDisplay(bytes, account?.displayName, account?.email)) }
            }
        }
        uid?.let { viewModelScope.launch { avatarRepository.load(it) } }
        viewModelScope.launch {
            extrasRepository.extras.collect { extras ->
                savedExtras = extras
                if (!extrasTouched) _uiState.update { it.copy(gender = extras.gender, birthDate = extras.birthDate) }
            }
        }
        uid?.let { viewModelScope.launch { extrasRepository.load(it) } }
    }

    fun onGenderChanged(value: Gender?) {
        extrasTouched = true
        _uiState.update { it.copy(gender = value, formError = null) }
    }

    /** [value] null clears the date. A date the picker should never offer (future, over 120 years) shows an error. */
    fun onBirthDateChanged(value: BirthDate?) {
        extrasTouched = true
        val issue = value?.let { birthDateIssue(it, today()) }
        _uiState.update { it.copy(birthDate = value, birthDateError = issue?.message(), formError = null) }
    }

    fun onNameChanged(value: String) = _uiState.update { it.copy(name = value, nameError = null, formError = null) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, emailError = null, formError = null) }
    fun onNewPasswordChanged(value: String) =
        _uiState.update { it.copy(newPassword = value, passwordError = null, confirmError = null, formError = null) }
    fun onConfirmPasswordChanged(value: String) =
        _uiState.update { it.copy(confirmPassword = value, confirmError = null, formError = null) }

    fun onSaveClicked() {
        val state = _uiState.value
        if (state.isSaving) return
        if (!validate(state)) return
        save()
    }

    /** The user proved their password on "Confirma tu identidad": run the pending save again. */
    fun onIdentityConfirmed() {
        if (_uiState.value.isSaving) return
        save()
    }

    private fun validate(state: EditProfileUiState): Boolean {
        val nameError = if (state.name.isBlank()) MESSAGE_ENTER_NAME else null
        val emailChanged = state.hasPassword && emailChanged(state)
        val emailError = if (emailChanged && !isPlausibleEmail(state.email)) MESSAGE_INVALID_EMAIL else null

        val wantsNewPassword = state.hasPassword && !passwordSaved &&
            (state.newPassword.isNotEmpty() || state.confirmPassword.isNotEmpty())
        val passwordError = when {
            wantsNewPassword && !PasswordRules.from(state.newPassword).allSatisfied -> MESSAGE_PASSWORD_RULES
            else -> null
        }
        val confirmError = when {
            wantsNewPassword && passwordError == null && state.newPassword != state.confirmPassword ->
                MESSAGE_PASSWORDS_DO_NOT_MATCH
            else -> null
        }

        val birthDateError = state.birthDate?.let { birthDateIssue(it, today())?.message() }

        _uiState.update {
            it.copy(
                nameError = nameError,
                emailError = emailError,
                passwordError = passwordError,
                confirmError = confirmError,
                birthDateError = birthDateError,
                formError = null,
            )
        }
        return nameError == null && emailError == null && passwordError == null && confirmError == null &&
            birthDateError == null
    }

    private fun emailChanged(state: EditProfileUiState): Boolean =
        !state.email.trim().equals(account?.email.orEmpty(), ignoreCase = true)

    private fun save() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, formError = null) }
            val state = _uiState.value
            try {
                val newName = state.name.trim()
                if (newName != savedName) {
                    accountRepository.updateDisplayName(newName)
                    savedName = newName
                }
                if (state.hasPassword && !passwordSaved && state.newPassword.isNotEmpty()) {
                    accountRepository.updatePassword(state.newPassword)
                    passwordSaved = true
                    _uiState.update { it.copy(newPassword = "", confirmPassword = "") }
                }
                // Gender and birth date: local copy first, then the server. If only the remote write fails, the
                // data is kept on this phone (and sent again with the next save), so the flow continues.
                val newExtras = ProfileExtras(state.gender, state.birthDate)
                val currentUid = uid
                if (currentUid != null && newExtras != savedExtras) {
                    extrasRepository.save(currentUid, newExtras)
                    savedExtras = newExtras
                }
                _uiState.update { it.copy(isSaving = false) }
                if (state.hasPassword && emailChanged(state)) {
                    _events.send(EditProfileEvent.NavigateToChangeEmail(state.email.trim()))
                } else {
                    _events.send(EditProfileEvent.NavigateToProfileUpdated)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError.RecentLoginRequired) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(EditProfileEvent.NavigateToConfirmIdentity)
            } catch (e: AuthError.WeakPassword) {
                _uiState.update { it.copy(isSaving = false, passwordError = MESSAGE_PASSWORD_RULES) }
            } catch (e: AuthError) {
                _uiState.update { it.copy(isSaving = false, formError = e.message ?: MESSAGE_GENERIC_ERROR) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, formError = MESSAGE_GENERIC_ERROR) }
            }
        }
    }

    /** The photo is saved as soon as it is picked, not on "GUARDAR CAMBIOS" (it has its own cache and error). */
    fun onAvatarPicked(sourceUri: String) {
        val uid = uid ?: return
        if (_uiState.value.isAvatarBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAvatarBusy = true, avatarError = null) }
            val bytes = try {
                avatarImageProcessor.process(sourceUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AvatarImageException) {
                _uiState.update { it.copy(isAvatarBusy = false, avatarError = MESSAGE_AVATAR_UNREADABLE) }
                return@launch
            } catch (e: Exception) {
                _uiState.update { it.copy(isAvatarBusy = false, avatarError = MESSAGE_AVATAR_UNREADABLE) }
                return@launch
            }
            val result = avatarRepository.save(uid, bytes)
            _uiState.update {
                it.copy(
                    isAvatarBusy = false,
                    avatarError = result.toMessage(),
                )
            }
        }
    }

    fun onRemoveAvatar() {
        val uid = uid ?: return
        if (_uiState.value.isAvatarBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAvatarBusy = true, avatarError = null) }
            val result = avatarRepository.remove(uid)
            _uiState.update {
                it.copy(
                    isAvatarBusy = false,
                    avatarError = result.toMessage(),
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                EditProfileViewModel(
                    container.authRepository,
                    container.accountRepository,
                    container.avatarRepository,
                    container.avatarImageProcessor,
                    container.profileExtrasRepository,
                )
            }
        }
    }
}
