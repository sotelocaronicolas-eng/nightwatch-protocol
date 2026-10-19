package com.example.nightwatchprotocol.ui.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.learning.ExamQuestion
import com.example.nightwatchprotocol.data.learning.LearningContentRepository
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One attempt of the final exam, one question per screen (Figma EXAMEN PREGUNTA 1 a 6). Touching an option records it and
 * moves to the next question at once: Figma draws no "Siguiente" button and no chosen option (decision of the group). An
 * answer cannot be changed, and a question cannot be answered twice: each tap carries the index of the question it was
 * drawn on and is ignored unless that is the current one, so a double tap never answers two questions.
 *
 * The answers live in the [SavedStateHandle], so the attempt survives a rotation. The attempt starts again from question 1
 * with the same order every time this screen is opened (decision P11: no limit of attempts). Nothing is saved here:
 * "X" leaves and an abandoned exam is never registered; only the result screen registers a finished attempt.
 */
class ExamViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val learningContentRepository: LearningContentRepository,
) : ViewModel() {

    private var loadJob: Job? = null
    private var questions: List<ExamQuestion> = emptyList()

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExamEvent>(Channel.BUFFERED)
    val events: Flow<ExamEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** Reads the questions. Also the "Reintentar" of a read error. */
    fun load() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(status = ExamStatus.Loading) }
        loadJob = viewModelScope.launch {
            val exam = runCatchingKeepCancellation { learningContentRepository.getContent().exam }.getOrNull()
            if (exam.isNullOrEmpty()) {
                _uiState.update { it.copy(status = ExamStatus.Failed) }
                return@launch
            }
            questions = exam
            render()
            // The screen was recreated after the last answer (for example by the system): the result opens again.
            if (answers().size >= questions.size) _events.send(ExamEvent.Finished(answers()))
        }
    }

    /** Records [optionIndex] as the answer to question [questionIndex] and moves on. Late or repeated taps do nothing. */
    fun onOptionSelected(questionIndex: Int, optionIndex: Int) {
        val current = answers()
        if (questions.isEmpty() || questionIndex != current.size || questionIndex !in questions.indices) return
        if (optionIndex !in questions[questionIndex].options.indices) return

        val updated = current + optionIndex
        savedStateHandle[KEY_ANSWERS] = ArrayList(updated)
        render()
        if (updated.size == questions.size) viewModelScope.launch { _events.send(ExamEvent.Finished(updated)) }
    }

    fun onCloseClicked() {
        viewModelScope.launch { _events.send(ExamEvent.Close) }
    }

    private fun answers(): List<Int> = savedStateHandle.get<ArrayList<Int>>(KEY_ANSWERS).orEmpty()

    private fun render() {
        val index = answers().size
        val status = if (index >= questions.size) {
            ExamStatus.Finished
        } else {
            val question = questions[index]
            ExamStatus.Question(
                ExamQuestionUi(
                    index = index,
                    total = questions.size,
                    emoji = question.emoji,
                    topicLabel = question.topicLabel,
                    text = question.text,
                    options = question.options.indices.map { question.optionLabel(it) },
                ),
            )
        }
        _uiState.update { it.copy(status = status) }
    }

    companion object {
        private const val KEY_ANSWERS = "examAnswers"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                ExamViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    learningContentRepository = application.container.learningContentRepository,
                )
            }
        }
    }
}
