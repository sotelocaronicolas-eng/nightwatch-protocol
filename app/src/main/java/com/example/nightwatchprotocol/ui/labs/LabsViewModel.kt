package com.example.nightwatchprotocol.ui.labs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
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
 * The "Labs" tab: six lab cards plus the "Examen Final". A lab is open when its island is unlocked (decision Q4: it does
 * NOT need the lesson to be seen); it shows completed once its lab is saved. Everything comes from
 * [deriveLearningProgress], like the map.
 */
class LabsViewModel(
    private val learningProgressRepository: LearningProgressRepository,
    private val learningContentRepository: LearningContentRepository,
) : ViewModel() {

    // What the list shows when the progress cannot be read: nothing done, so lab 1 is open and the rest are locked.
    private var progress: LearningProgress = deriveLearningProgress(LearningSnapshot.Empty)
    private var content: LearningContent? = null
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(LabsUiState())
    val uiState: StateFlow<LabsUiState> = _uiState.asStateFlow()

    private val _events = Channel<LabsEvent>(Channel.BUFFERED)
    val events: Flow<LabsEvent> = _events.receiveAsFlow()

    /**
     * Reads the progress. Safe to call again (on returning to the tab, or "Reintentar"): a read already running is not
     * duplicated, and the list keeps its last answer until the new one arrives.
     */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        // Set before launching, so "Reintentar" shows the loading state at once.
        _uiState.update { if (it.status is LabsStatus.Failed) it.copy(status = LabsStatus.Loading) else it }
        refreshJob = viewModelScope.launch {
            val contentResult = runCatchingKeepCancellation { learningContentRepository.getContent() }
            val snapshotResult = runCatchingKeepCancellation { learningProgressRepository.getSnapshot() }

            contentResult.getOrNull()?.let { content = it }
            snapshotResult.getOrNull()?.let { progress = deriveLearningProgress(it) }

            val failure = contentResult.exceptionOrNull() ?: snapshotResult.exceptionOrNull()
            val status = if (failure == null) {
                LabsStatus.Ready
            } else {
                LabsStatus.Failed(offline = (failure as? DataAccessException)?.kind == FailureKind.Offline)
            }
            _uiState.update { render(it, status) }
        }
    }

    /** A locked lab only says why; an open or completed one opens. Ignored while the first read runs. */
    fun onLabClicked(topic: LearningTopic) {
        if (_uiState.value.status is LabsStatus.Loading) return
        val event = if (progress.canOpenLab(topic)) {
            LabsEvent.OpenLab(topic)
        } else {
            LabsEvent.ShowMessage(LabsMessage.CompletePreviousIsland)
        }
        viewModelScope.launch { _events.send(event) }
    }

    /** "Examen Final": locked says why; unlocked is "Próximamente" until the exam is built (stage 5). */
    fun onFinalTestClicked() {
        if (_uiState.value.status is LabsStatus.Loading) return
        val message = if (progress.examGate == ExamGate.Locked) LabsMessage.CompleteAllIslands else LabsMessage.ComingSoon
        viewModelScope.launch { _events.send(LabsEvent.ShowMessage(message)) }
    }

    private fun render(previous: LabsUiState, status: LabsStatus): LabsUiState {
        val labs = content?.labs.orEmpty().associateBy { it.topic }
        return previous.copy(
            status = status,
            labs = progress.islands.mapNotNull { island ->
                labs[island.topic]?.let { lab ->
                    val state = when {
                        island.labCompleted -> IslandState.Completed
                        island.isUnlocked -> IslandState.Available
                        else -> IslandState.Locked
                    }
                    LabCardUi(island.topic, lab.cardTitle, lab.emoji, lab.difficulty, state)
                }
            },
            finalTest = when {
                progress.examPassed -> IslandState.Completed
                progress.examGate == ExamGate.Available -> IslandState.Available
                else -> IslandState.Locked
            },
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                LabsViewModel(
                    learningProgressRepository = container.learningProgressRepository,
                    learningContentRepository = container.learningContentRepository,
                )
            }
        }
    }
}
