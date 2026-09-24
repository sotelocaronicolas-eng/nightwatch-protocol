package com.example.nightwatchprotocol.ui.postauth

data class PostAuthGateUiState(
    val isLoading: Boolean = true,
)

sealed interface PostAuthGateEvent {
    data object NavigateToMainMenu : PostAuthGateEvent
    data object NavigateToPlacementQuiz : PostAuthGateEvent
}
