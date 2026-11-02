package com.example.nightwatchprotocol.ui.quiz

import com.example.nightwatchprotocol.data.common.UiText

data class PlacementResultUiState(
    val level: UiText = UiText.dynamic(""),
    val studyPlan: List<UiText> = emptyList(),
    val saveFailed: Boolean = false,
)

sealed interface PlacementResultEvent {
    data object NavigateToMainMenu : PlacementResultEvent
}
