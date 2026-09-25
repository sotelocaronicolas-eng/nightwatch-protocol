package com.example.nightwatchprotocol.ui.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.QuizCategory
import com.example.nightwatchprotocol.data.quiz.buildStudyPlan
import com.example.nightwatchprotocol.ui.navigation.PlacementResultRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class PlacementResultViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PlacementResultRoute>()
    private val level = runCatching { PlacementLevel.valueOf(route.level) }.getOrDefault(PlacementLevel.BAJO)
    private val weakCategories = route.weakCategories
        .mapNotNull { name -> runCatching { QuizCategory.valueOf(name) }.getOrNull() }
        .toSet()

    private val _uiState = MutableStateFlow(
        PlacementResultUiState(
            level = level.name,
            studyPlan = buildStudyPlan(level, weakCategories),
            saveFailed = route.saveFailed,
        ),
    )
    val uiState: StateFlow<PlacementResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlacementResultEvent>(Channel.BUFFERED)
    val events: Flow<PlacementResultEvent> = _events.receiveAsFlow()

    private var hasContinued = false

    fun onContinueClicked() {
        if (hasContinued) return
        hasContinued = true
        viewModelScope.launch { _events.send(PlacementResultEvent.NavigateToMainMenu) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlacementResultViewModel(createSavedStateHandle())
            }
        }
    }
}
