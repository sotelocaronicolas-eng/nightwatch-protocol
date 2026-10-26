package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.social.ProfileDisplaySettings
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfileRepository
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.normalizeProfileBg
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [draft] is what the person has chosen so far: it is NOT saved until "GUARDAR CAMBIOS". [data] is what the server has; [saved] is
 * the draft the server last confirmed (so a change can be told from no change). A failed save keeps the draft.
 */
data class EditMyProfileUiState(
    val loading: Boolean = true,
    val data: MyProfileData? = null,
    val loadError: SocialError? = null,
    val draft: ProfileDisplaySettings = ProfileDisplaySettings(),
    val saving: Boolean = false,
    val saveError: SocialError? = null,
    val pickerOpen: Boolean = false,
) {
    val loaded: Boolean get() = data != null
}

sealed interface EditMyProfileEvent {
    /** The choices were saved: the screen goes back to "Tu perfil", which reads them again. */
    data object Saved : EditMyProfileEvent

    /** "CANCELAR": nothing was saved and the draft is dropped with the screen. */
    data object Discarded : EditMyProfileEvent
}

/**
 * "Editar tu perfil" (Figma 2653:611). The eyes and the background are a local draft until "GUARDAR CAMBIOS"; "CANCELAR" (and the
 * back button) drop it without calling the server. The draft starts from what the server has and is never overwritten by a re-read.
 */
class EditMyProfileViewModel(
    private val loader: MyProfileLoader,
    private val profileRepository: SocialProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditMyProfileUiState())
    val uiState: StateFlow<EditMyProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditMyProfileEvent>(Channel.BUFFERED)
    val events: Flow<EditMyProfileEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    /** Reads the profile once; calling it again (the screen coming back to the front) does not touch a draft that exists. */
    fun load() {
        if (_uiState.value.loaded || loadJob?.isActive == true) return
        _uiState.update { it.copy(loading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            when (val result = loader.load()) {
                is SocialResult.Success -> _uiState.update {
                    it.copy(loading = false, data = result.value, loadError = result.value.partialError, draft = result.value.display)
                }
                is SocialResult.Failure -> _uiState.update { it.copy(loading = false, loadError = result.error) }
            }
        }
    }

    fun onToggle(section: ProfileSection) {
        _uiState.update { state ->
            if (state.saving) return@update state
            val draft = state.draft
            state.copy(
                saveError = null,
                draft = when (section) {
                    ProfileSection.Badges -> draft.copy(showBadges = !draft.showBadges)
                    ProfileSection.Stats -> draft.copy(showStats = !draft.showStats)
                    ProfileSection.History -> draft.copy(showHistory = !draft.showHistory)
                },
            )
        }
    }

    fun onPickerOpened() {
        _uiState.update { it.copy(pickerOpen = !it.saving) }
    }

    fun onPickerDismissed() {
        _uiState.update { it.copy(pickerOpen = false) }
    }

    /** [profileBg] 0 is "sin fondo"; a number the app does not have is also "sin fondo". */
    fun onBackgroundSelected(profileBg: Int) {
        _uiState.update { it.copy(draft = it.draft.copy(profileBg = normalizeProfileBg(profileBg)), pickerOpen = false, saveError = null) }
    }

    /** "GUARDAR CAMBIOS". A second tap while it saves does nothing (nothing is saved twice). */
    fun onSaveClicked() {
        val state = _uiState.value
        if (state.saving || !state.loaded) return
        _uiState.update { it.copy(saving = true, saveError = null, pickerOpen = false) }
        viewModelScope.launch {
            when (val result = profileRepository.updateDisplaySettings(state.draft)) {
                is SocialResult.Success -> {
                    _uiState.update { it.copy(saving = false) }
                    _events.send(EditMyProfileEvent.Saved)
                }
                // The draft stays: the person can retry without choosing again.
                is SocialResult.Failure -> _uiState.update { it.copy(saving = false, saveError = result.error) }
            }
        }
    }

    fun onCancelClicked() {
        if (_uiState.value.saving) return
        viewModelScope.launch { _events.send(EditMyProfileEvent.Discarded) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                EditMyProfileViewModel(myProfileLoader(application), application.container.socialProfileRepository)
            }
        }
    }
}
