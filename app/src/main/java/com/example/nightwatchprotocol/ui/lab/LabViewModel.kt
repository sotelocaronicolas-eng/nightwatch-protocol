package com.example.nightwatchprotocol.ui.lab

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.learning.LabTerminal
import com.example.nightwatchprotocol.data.learning.LearningContentRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.TerminalResult
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.ui.navigation.Lab as LabRoute
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
 * The terminal of one lab. The simulated terminal is [LabTerminal] (stage 1), used as it is. The attempt is kept in the
 * [SavedStateHandle] as the list of commands sent (plus the text of the field), and the terminal is rebuilt from it, so a
 * rotation (or the system recreating the screen) shows the same objectives, output and text. Nothing is saved to the
 * server here: "Finalizar laboratorio" opens the report, which saves a completed lab.
 */
class LabViewModel(
    private val topic: LearningTopic,
    private val savedStateHandle: SavedStateHandle,
    private val learningContentRepository: LearningContentRepository,
) : ViewModel() {

    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow(LabUiState(input = savedStateHandle.get<String>(KEY_INPUT).orEmpty()))
    val uiState: StateFlow<LabUiState> = _uiState.asStateFlow()

    private val _events = Channel<LabEvent>(Channel.BUFFERED)
    val events: Flow<LabEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** Reads the lab and replays the commands already sent. Also the "Reintentar" of a read error. */
    fun load() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(status = LabStatus.Loading) }
        loadJob = viewModelScope.launch {
            val lab = runCatchingKeepCancellation { learningContentRepository.getContent().lab(topic) }.getOrNull()
            if (lab == null) {
                _uiState.update { it.copy(status = LabStatus.Failed) }
                return@launch
            }
            val terminal = sentCommands().fold(LabTerminal(lab)) { current, command -> current.submit(command).terminal }
            _uiState.update { it.copy(status = LabStatus.Ready(terminal)) }
        }
    }

    fun onInputChanged(text: String) {
        savedStateHandle[KEY_INPUT] = text
        _uiState.update { it.copy(input = text) }
    }

    /** Tapping an objective copies its command into the field; it is not sent until "Enviar". */
    fun onObjectiveClicked(index: Int) {
        val terminal = (_uiState.value.status as? LabStatus.Ready)?.terminal ?: return
        val objective = terminal.lab.objectives.getOrNull(index) ?: return
        onInputChanged(objective.command)
    }

    /** Sends the text of the field. An empty field does nothing; anything else is added to the transcript. */
    fun onSendClicked() {
        val state = _uiState.value
        val terminal = (state.status as? LabStatus.Ready)?.terminal ?: return
        val submission = terminal.submit(state.input)
        if (submission.result is TerminalResult.Empty) return
        savedStateHandle[KEY_COMMANDS] = ArrayList(sentCommands() + state.input)
        _uiState.update { it.copy(status = LabStatus.Ready(submission.terminal)) }
    }

    /** Always allowed once the lab is on screen: with fewer than three objectives the report is the failure one. */
    fun onFinishClicked() {
        val terminal = (_uiState.value.status as? LabStatus.Ready)?.terminal ?: return
        val event = LabEvent.OpenReport(topic, terminal.completedObjectives.sorted())
        viewModelScope.launch { _events.send(event) }
    }

    fun onCloseClicked() {
        viewModelScope.launch { _events.send(LabEvent.Close) }
    }

    private fun sentCommands(): List<String> = savedStateHandle.get<ArrayList<String>>(KEY_COMMANDS).orEmpty()

    companion object {
        private const val KEY_INPUT = "labInput"
        private const val KEY_COMMANDS = "labCommands"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val handle = createSavedStateHandle()
                LabViewModel(
                    topic = handle.toRoute<LabRoute>().topic,
                    savedStateHandle = handle,
                    learningContentRepository = application.container.learningContentRepository,
                )
            }
        }
    }
}
