package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [data] is the last thing read: a failed re-read keeps it and only adds [loadError]. */
data class MyProfileUiState(
    val loading: Boolean = true,
    val data: MyProfileData? = null,
    val loadError: SocialError? = null,
) {
    val loaded: Boolean get() = data != null
}

/** "Tu perfil" (Figma 2653:543): the person's own social profile, read again each time the screen comes back (after editing it). */
class MyProfileViewModel(private val loader: MyProfileLoader) : ViewModel() {

    private val _uiState = MutableStateFlow(MyProfileUiState())
    val uiState: StateFlow<MyProfileUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        _uiState.update { it.copy(loading = !it.loaded, loadError = null) }
        refreshJob = viewModelScope.launch {
            when (val result = loader.load()) {
                is SocialResult.Success -> _uiState.update {
                    MyProfileUiState(loading = false, data = result.value, loadError = result.value.partialError)
                }
                is SocialResult.Failure -> _uiState.update { it.copy(loading = false, loadError = result.error) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                MyProfileViewModel(myProfileLoader(application))
            }
        }
    }
}

/** The loader of the two own-profile screens, wired to the real repositories. */
internal fun myProfileLoader(application: NightWatchApplication): MyProfileLoader {
    val container = application.container
    return MyProfileLoader(container.socialProfileRepository, container.fighterRepository, { container.progressSummaryProvider.get() })
}
