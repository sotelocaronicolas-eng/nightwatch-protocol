package com.example.nightwatchprotocol.ui.examresult

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.ExamGrade
import com.example.nightwatchprotocol.data.learning.LearningContentRepository
import com.example.nightwatchprotocol.data.learning.LearningProgressRepository
import com.example.nightwatchprotocol.data.learning.gradeExam
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.ui.navigation.ExamResult as ExamResultRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The result of one finished attempt. It grades the answers with the pure [gradeExam] (stage 1) and registers the attempt
 * once with [LearningProgressRepository.recordExamAttempt]: only `correct_count` is sent, the row's owner is the session's
 * and the server decides `passed`. **Passing changes nothing else (decision P5):** this class has no access to the quiz
 * result (`placement`), the profile or any other table, so the level never changes.
 *
 * A failed save keeps the result on screen with a notice and "Reintentar guardado"; the attempt is not counted until it is
 * saved. A second call while one is running does nothing (double tap). Unlike a lab, an attempt is NOT idempotent: every
 * call adds a row, so the save is sent once per result screen and again only after a failure.
 */
class ExamResultViewModel(
    private val answers: List<Int>,
    private val learningContentRepository: LearningContentRepository,
    private val learningProgressRepository: LearningProgressRepository,
) : ViewModel() {

    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var grade: ExamGrade? = null

    private val _uiState = MutableStateFlow(ExamResultUiState())
    val uiState: StateFlow<ExamResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExamResultEvent>(Channel.BUFFERED)
    val events: Flow<ExamResultEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** Reads the exam, grades the answers and registers the attempt. Also the "Reintentar" of a read error. */
    fun load() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(status = ExamResultStatus.Loading) }
        loadJob = viewModelScope.launch {
            val graded = runCatchingKeepCancellation { gradeExam(learningContentRepository.getContent().exam, answers) }
                .getOrNull()
            if (graded == null) {
                _uiState.update { it.copy(status = ExamResultStatus.Failed) }
                return@launch
            }
            grade = graded
            _uiState.update { it.copy(status = ExamResultStatus.Ready(outcomeOf(graded))) }
            save()
        }
    }

    /** "Reintentar guardado": only after a failed save; a tap while saving (or after saving) does nothing. */
    fun onRetrySaveClicked() {
        if (_uiState.value.save is ExamSaveStatus.Failed) save()
    }

    /** The buttons wait for the save, so the screens that open next already read the attempt. */
    fun onDestinationClicked(destination: ExamDestination) {
        if (_uiState.value.save is ExamSaveStatus.Saving) return
        viewModelScope.launch { _events.send(ExamResultEvent.Go(destination)) }
    }

    private fun save() {
        val correctCount = grade?.correctCount ?: return
        if (saveJob?.isActive == true || _uiState.value.save is ExamSaveStatus.Saved) return
        _uiState.update { it.copy(save = ExamSaveStatus.Saving) }
        saveJob = viewModelScope.launch {
            // NonCancellable: leaving the result right away must not lose the (short) write.
            val result = withContext(NonCancellable) {
                runCatchingKeepCancellation { learningProgressRepository.recordExamAttempt(correctCount) }
            }
            val failure = result.exceptionOrNull()
            val status = if (failure == null) {
                ExamSaveStatus.Saved
            } else {
                // Only a kind of failure reaches the screen, never the exception itself.
                ExamSaveStatus.Failed(offline = (failure as? DataAccessException)?.kind == FailureKind.Offline)
            }
            _uiState.update { it.copy(save = status) }
        }
    }

    private fun outcomeOf(grade: ExamGrade): ExamOutcome = when {
        !grade.passed -> ExamOutcome.NotPassed
        grade.singleError != null -> ExamOutcome.OneError(grade.singleError.topicLabel, grade.singleError.correctOptionLabel)
        else -> ExamOutcome.Perfect
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                ExamResultViewModel(
                    answers = createSavedStateHandle().toRoute<ExamResultRoute>().answers,
                    learningContentRepository = container.learningContentRepository,
                    learningProgressRepository = container.learningProgressRepository,
                )
            }
        }
    }
}
