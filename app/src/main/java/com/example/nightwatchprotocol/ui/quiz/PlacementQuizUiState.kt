package com.example.nightwatchprotocol.ui.quiz

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

    val progressLabel: String
        get() = "Pregunta ${currentIndex + 1} de ${questions.size}"
}

sealed interface PlacementQuizEvent {
    data class NavigateToResult(
        val level: PlacementLevel,
        val weakCategories: Set<QuizCategory>,
        val saveFailed: Boolean,
    ) : PlacementQuizEvent
}
