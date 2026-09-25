package com.example.nightwatchprotocol.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.quiz.PlacementResult
import com.example.nightwatchprotocol.data.quiz.QuizContentRepository
import com.example.nightwatchprotocol.data.quiz.QuizQuestion
import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
import com.example.nightwatchprotocol.data.quiz.scoreAttempt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PlacementQuizViewModel(
    private val authRepository: AuthRepository,
    private val quizContentRepository: QuizContentRepository,
    private val quizResultRepository: QuizResultRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlacementQuizUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<PlacementQuizEvent>(Channel.BUFFERED)
    val events: Flow<PlacementQuizEvent> = _events.receiveAsFlow()

    private val answers = mutableMapOf<String, Int>()

    init {
        viewModelScope.launch {
            val questions = quizContentRepository.getAttempt()
            _uiState.update { it.copy(questions = questions, isLoading = false) }
        }
    }

    fun onOptionSelected(index: Int) {
        _uiState.update { it.copy(selectedOptionIndex = index) }
    }

    fun onNextClicked() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val question = state.currentQuestion ?: return
        val selected = state.selectedOptionIndex ?: return
        answers[question.id] = selected

        val nextIndex = state.currentIndex + 1
        if (nextIndex < state.questions.size) {
            _uiState.update { it.copy(currentIndex = nextIndex, selectedOptionIndex = null) }
        } else {
            _uiState.update { it.copy(isSubmitting = true) }
            finishQuiz(state.questions)
        }
    }

    private fun finishQuiz(questions: List<QuizQuestion>) {
        val score = scoreAttempt(questions, answers)
        val result = PlacementResult(score.level, score.weakCategories)
        val uid = authRepository.currentUserUid

        viewModelScope.launch {
            val saveFailed = if (uid == null) {
                true
            } else {
                // runCatching wraps the timeout (not the other way around) so a real exception
                // from saveResult is caught here, while a timeout is resolved by
                // withTimeoutOrNull itself (returning null) before it ever reaches runCatching.
                val outcome = runCatching {
                    withTimeoutOrNull(SAVE_TIMEOUT_MILLIS) { quizResultRepository.saveResult(uid, result) }
                }
                // Failure either way: the call threw (outcome.isFailure), or it timed out
                // (outcome succeeded with a null Unit? from withTimeoutOrNull).
                outcome.isFailure || outcome.getOrNull() == null
            }
            _events.send(
                PlacementQuizEvent.NavigateToResult(
                    level = score.level,
                    weakCategories = score.weakCategories,
                    saveFailed = saveFailed,
                ),
            )
        }
    }

    companion object {
        private const val SAVE_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                PlacementQuizViewModel(
                    application.container.authRepository,
                    application.container.quizContentRepository,
                    application.container.quizResultRepository,
                )
            }
        }
    }
}
