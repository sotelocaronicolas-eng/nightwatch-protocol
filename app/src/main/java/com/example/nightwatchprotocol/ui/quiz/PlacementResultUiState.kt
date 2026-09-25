package com.example.nightwatchprotocol.ui.quiz

data class PlacementResultUiState(
    val level: String = "",
    val studyPlan: List<String> = emptyList(),
    val saveFailed: Boolean = false,
)

sealed interface PlacementResultEvent {
    data object NavigateToMainMenu : PlacementResultEvent
}
