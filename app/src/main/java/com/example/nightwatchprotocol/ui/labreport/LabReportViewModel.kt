package com.example.nightwatchprotocol.ui.labreport

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
import com.example.nightwatchprotocol.data.learning.Lab
import com.example.nightwatchprotocol.data.learning.LabTerminal
import com.example.nightwatchprotocol.data.learning.LearningContentRepository
import com.example.nightwatchprotocol.data.learning.LearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.ui.navigation.LabReport as LabReportRoute
import java.util.Locale
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
 * The report of a finished attempt. It receives the topic and the objectives done (from the terminal) and builds
 * everything from them and from the lab content: success with the three objectives, failure otherwise (decision P6), the
 * precision with [LabTerminal.precisionPercent] (decision Q1) and, in the passwords lab, the time of the strongest password
 * tried with [LabTerminal.strongestEstimatedTime] (decision Q2).
 */
class LabReportViewModel(
    private val topic: LearningTopic,
    private val completedObjectives: Set<Int>,
    private val learningContentRepository: LearningContentRepository,
    private val learningProgressRepository: LearningProgressRepository,
) : ViewModel() {

    private var loadJob: Job? = null
    private var saveJob: Job? = null

    private val _uiState = MutableStateFlow(LabReportUiState())
    val uiState: StateFlow<LabReportUiState> = _uiState.asStateFlow()

    private val _events = Channel<LabReportEvent>(Channel.BUFFERED)
    val events: Flow<LabReportEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** Reads the lab content and builds the report. Also the "Reintentar" of a read error. */
    fun load() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(status = LabReportStatus.Loading) }
        loadJob = viewModelScope.launch {
            val lab = runCatchingKeepCancellation { learningContentRepository.getContent().lab(topic) }.getOrNull()
            if (lab == null) {
                _uiState.update { it.copy(status = LabReportStatus.Failed) }
                return@launch
            }
            val content = buildContent(lab)
            _uiState.update { it.copy(status = LabReportStatus.Ready(content)) }
            // Only a completed lab is registered (decision P6).
            if (content.success) save()
        }
    }

    /** "Reintentar guardado": only after a failed save; a tap while saving (or after saving) does nothing. */
    fun onRetrySaveClicked() {
        if (_uiState.value.save is LabSaveStatus.Failed) save()
    }

    /** The buttons wait for the save, so the map that opens next already reads the completed lab. */
    fun onBackToPathClicked() {
        if (_uiState.value.save is LabSaveStatus.Saving) return
        viewModelScope.launch { _events.send(LabReportEvent.BackToPath) }
    }

    fun onRetryLabClicked() {
        if (_uiState.value.save is LabSaveStatus.Saving) return
        viewModelScope.launch { _events.send(LabReportEvent.RetryLab(topic)) }
    }

    /**
     * Registers the lab with [LearningProgressRepository.markLabCompleted] (idempotent: repeating a completed lab is not an
     * error and adds no row; the row's owner is the session's). A second call while one is running does nothing, so a
     * double tap never saves twice. NonCancellable: leaving the report right away must not lose the (short) write.
     */
    private fun save() {
        if (saveJob?.isActive == true || _uiState.value.save is LabSaveStatus.Saved) return
        _uiState.update { it.copy(save = LabSaveStatus.Saving) }
        saveJob = viewModelScope.launch {
            val result = withContext(NonCancellable) {
                runCatchingKeepCancellation { learningProgressRepository.markLabCompleted(topic) }
            }
            val failure = result.exceptionOrNull()
            val status = if (failure == null) {
                LabSaveStatus.Saved
            } else {
                // Only a kind of failure reaches the screen, never the exception itself.
                LabSaveStatus.Failed(offline = (failure as? DataAccessException)?.kind == FailureKind.Offline)
            }
            _uiState.update { it.copy(save = status) }
        }
    }

    private fun buildContent(lab: Lab): LabReportContent {
        // Only valid objective numbers count, whatever the route carried.
        val terminal = LabTerminal(lab, completedObjectives = completedObjectives.filter { it in lab.objectives.indices }.toSet())
        val success = terminal.isComplete
        val isPasswordsLab = lab.objectives.any { it.estimatedTime != null }
        return LabReportContent(
            success = success,
            successTitle = lab.report.successTitle,
            summary = if (success) lab.report.successSummary else lab.report.failureSummary,
            metricLabel = lab.report.metricLabel,
            metric = if (isPasswordsLab) {
                ReportMetric.EstimatedTime(terminal.strongestEstimatedTime?.let(::formatEstimatedTime))
            } else {
                ReportMetric.Percent(terminal.precisionPercent)
            },
            items = lab.objectives.mapIndexed { index, objective ->
                ReportItem(objective.reportLabel, done = index in terminal.completedObjectives)
            },
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val route = createSavedStateHandle().toRoute<LabReportRoute>()
                LabReportViewModel(
                    topic = route.topic,
                    completedObjectives = route.completedObjectives.toSet(),
                    learningContentRepository = application.container.learningContentRepository,
                    learningProgressRepository = application.container.learningProgressRepository,
                )
            }
        }
    }
}

/**
 * "300 años" -> "300 Años", "menos de 1 segundo" -> "Menos de 1 Segundo": Figma writes the time with a capital on each
 * word ("300 Años", "3 Horas"); the lab content keeps the lower case of the terminal output (SUPUESTO E1-6).
 */
fun formatEstimatedTime(raw: String): String = raw.trim().split(Regex("\\s+")).joinToString(" ") { word ->
    if (word == "de") word else word.replaceFirstChar { it.titlecase(Locale.ROOT) }
}
