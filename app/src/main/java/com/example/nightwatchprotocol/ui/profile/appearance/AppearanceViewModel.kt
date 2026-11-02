package com.example.nightwatchprotocol.ui.profile.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.preferences.AppearanceRepository
import com.example.nightwatchprotocol.data.preferences.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** [themeMode] is the saved choice; it is [ThemeMode.DEFAULT] until the first read arrives (that read is immediate). */
data class AppearanceUiState(
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
)

/**
 * The state behind "Apariencia". The screen is still inert (Stage 4 of Module 5 connects it); this view model already reads
 * and saves the choice. The state always mirrors what is saved, so a failed save leaves the old choice showing.
 */
class AppearanceViewModel(
    private val appearance: AppearanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppearanceUiState())
    val uiState: StateFlow<AppearanceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appearance.themeMode.collect { mode -> _uiState.value = AppearanceUiState(themeMode = mode) }
        }
    }

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch {
            try {
                appearance.setThemeMode(mode)
            } catch (_: IOException) {
                // Nothing could be written: the state keeps showing the saved choice.
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                AppearanceViewModel(application.container.appearanceRepository)
            }
        }
    }
}
