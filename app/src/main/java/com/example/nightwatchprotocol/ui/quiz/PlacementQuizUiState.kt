package com.example.nightwatchprotocol.ui.quiz

import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.QuizCategory
import com.example.nightwatchprotocol.data.quiz.QuizQuestion

data class PlacementQuizUiState(
    val questions: List<QuizQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOptionIndex: Int? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
) {
    val currentQuestion: QuizQuestion?
        get() = questions.getOrNull(currentIndex)

    val progressLabel: UiText
        get() = UiText.of(R.string.quiz_progress, currentIndex + 1, questions.size)
}

sealed interface PlacementQuizEvent {
    data class NavigateToResult(
        val level: PlacementLevel,
        val weakCategories: Set<QuizCategory>,
        val saveFailed: Boolean,
    ) : PlacementQuizEvent
}
