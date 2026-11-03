package com.example.nightwatchprotocol.ui.profile.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.language.AppLanguage
import com.example.nightwatchprotocol.data.language.LanguageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** [selected] is the language the app is in: the one marked with ✓ and the border. */
data class LanguageUiState(
    val selected: AppLanguage = AppLanguage.DEFAULT,
)

/**
 * The state behind "Idioma". The language lives in AppCompat (through [LanguageRepository]), never in DataStore. Choosing one makes
 * AppCompat recreate the Activity; this view model survives that, so the mark stays on the new choice without a flash.
 *
 * Until Stage 8 adds `values-en`, choosing English saves the language (and the phone's per-app setting follows) but the texts stay
 * in Spanish: that is expected, not an error.
 */
class LanguageViewModel(
    private val languages: LanguageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanguageUiState(languages.currentLanguage() ?: AppLanguage.DEFAULT))
    val uiState: StateFlow<LanguageUiState> = _uiState.asStateFlow()

    fun onLanguageSelected(language: AppLanguage) {
        if (language == _uiState.value.selected) return
        _uiState.value = LanguageUiState(language)
        languages.setLanguage(language)
    }

    /** The language may have been changed from the phone's settings while the screen was away: read it again. */
    fun refresh() {
        _uiState.value = LanguageUiState(languages.currentLanguage() ?: AppLanguage.DEFAULT)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                LanguageViewModel(application.container.languageRepository)
            }
        }
    }
}
