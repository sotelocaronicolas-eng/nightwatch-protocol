package com.example.nightwatchprotocol.ui.path

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.ExamGate
import com.example.nightwatchprotocol.data.learning.IslandState
import com.example.nightwatchprotocol.data.learning.LearningContent
import com.example.nightwatchprotocol.data.learning.LearningContentRepository
import com.example.nightwatchprotocol.data.learning.LearningProgress
import com.example.nightwatchprotocol.data.learning.LearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningSnapshot
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.deriveLearningProgress
import com.example.nightwatchprotocol.data.profile.LEVEL_NOT_ASSESSED
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
import com.example.nightwatchprotocol.ui.home.readLevelLabel
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
 * The "Ruta" tab: the map of six islands plus the "Prueba Final". Everything is derived from what is saved with
 * [deriveLearningProgress] (island completed = lesson seen AND lab done; unlocked in order; exam after the six).
 * The level is only shown: it never changes what is unlocked (decision P3).
 */
class LearningPathViewModel(
    private val authRepository: AuthRepository,
    private val quizResultRepository: QuizResultRepository,
    private val learningProgressRepository: LearningProgressRepository,
    private val learningContentRepository: LearningContentRepository,
) : ViewModel() {

    // What the map shows when the progress cannot be read: nothing done, so island 1 is open and the rest are locked.
    private var progress: LearningProgress = deriveLearningProgress(LearningSnapshot.Empty)
    private var content: LearningContent? = null
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(LearningPathUiState())
    val uiState: StateFlow<LearningPathUiState> = _uiState.asStateFlow()

    private val _events = Channel<LearningPathEvent>(Channel.BUFFERED)
    val events: Flow<LearningPathEvent> = _events.receiveAsFlow()

    init {
        val uid = authRepository.currentUserUid
        if (uid == null) {
            _uiState.update { it.copy(level = LEVEL_NOT_ASSESSED) }
        } else {
            viewModelScope.launch {
                val level = readLevelLabel(quizResultRepository, uid)
                _uiState.update { it.copy(level = level) }
            }
        }
    }

    /**
     * Reads the progress. Safe to call again (on returning to the tab, or "Reintentar"): a read already running is not
     * duplicated, and the map keeps its last answer until the new one arrives.
     */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        // Set before launching, so "Reintentar" shows the loading state at once.
        _uiState.update { if (it.status is PathStatus.Failed) it.copy(status = PathStatus.Loading) else it }
        refreshJob = viewModelScope.launch {
            val contentResult = runCatchingKeepCancellation { learningContentRepository.getContent() }
            val snapshotResult = runCatchingKeepCancellation { learningProgressRepository.getSnapshot() }

            contentResult.getOrNull()?.let { content = it }
            snapshotResult.getOrNull()?.let { progress = deriveLearningProgress(it) }

            val failure = contentResult.exceptionOrNull() ?: snapshotResult.exceptionOrNull()
            val status = if (failure == null) {
                PathStatus.Ready
            } else {
                PathStatus.Failed(offline = (failure as? DataAccessException)?.kind == FailureKind.Offline)
            }
            _uiState.update { render(it, status) }
        }
    }

    /** A locked island only says why; an open or completed one opens its lesson. Ignored while the first read runs. */
    fun onIslandClicked(topic: LearningTopic) {
        if (_uiState.value.status is PathStatus.Loading) return
        val event = if (progress.canOpenLesson(topic)) {
            LearningPathEvent.OpenLesson(topic)
        } else {
            LearningPathEvent.ShowMessage(PathMessage.CompletePreviousIsland)
        }
        viewModelScope.launch { _events.send(event) }
    }

    /** "Prueba Final": locked says why; unlocked opens the exam introduction (decision P1). It can be retaken (P11). */
    fun onFinalTestClicked() {
        if (_uiState.value.status is PathStatus.Loading) return
        val event = if (progress.examGate == ExamGate.Locked) {
            LearningPathEvent.ShowMessage(PathMessage.CompleteAllIslands)
        } else {
            LearningPathEvent.OpenExam
        }
        viewModelScope.launch { _events.send(event) }
    }

    private fun render(previous: LearningPathUiState, status: PathStatus): LearningPathUiState {
        val lessons = content?.lessons.orEmpty().associateBy { it.topic }
        return previous.copy(
            status = status,
            islands = progress.islands.mapNotNull { island ->
                lessons[island.topic]?.let { IslandUi(island.topic, it.islandName, it.emoji, island.state) }
            },
            finalTest = when {
                progress.examPassed -> IslandState.Completed
                progress.examGate == ExamGate.Available -> IslandState.Available
                else -> IslandState.Locked
            },
            completedCount = progress.completedCount,
            totalCount = progress.totalCount,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                LearningPathViewModel(
                    authRepository = container.authRepository,
                    quizResultRepository = container.quizResultRepository,
                    learningProgressRepository = container.learningProgressRepository,
                    learningContentRepository = container.learningContentRepository,
                )
            }
        }
    }
}
