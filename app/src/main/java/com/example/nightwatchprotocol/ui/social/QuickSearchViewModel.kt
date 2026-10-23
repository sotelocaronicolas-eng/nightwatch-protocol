package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.social.BattleLobbyRepository
import com.example.nightwatchprotocol.data.social.QuickMatchPoller
import com.example.nightwatchprotocol.data.social.QuickMatchStatus
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface QuickSearchStatus {
    /** Not looking yet (just opened) or the search ended without a battle. */
    data object Idle : QuickSearchStatus

    /** In the queue. [suggestFriend] turns true at 60 s without a rival (D6): the search goes on. */
    data class Searching(val waitedSeconds: Int, val suggestFriend: Boolean) : QuickSearchStatus

    /** The search was left (the screen was hidden) and the person was taken out of the queue. */
    data object Stopped : QuickSearchStatus

    data class Failed(val error: SocialError) : QuickSearchStatus
}

data class QuickSearchUiState(val status: QuickSearchStatus = QuickSearchStatus.Idle)

sealed interface QuickSearchEvent {
    /** A rival was found (or the person was already in a battle): the battle already exists, so the arena opens at once. */
    data class OpenBattle(val battleId: String) : QuickSearchEvent

    /** "Cancelar búsqueda": the person is out of the queue and goes back. */
    data object Cancelled : QuickSearchEvent
}

/**
 * "Buscando oponente" (Figma 2448:223). The search lives only while the screen is visible: whoever leaves it (cancel, back, "X",
 * another app) is taken out of the queue by [BattleLobbyRepository.quickCancel], because stopping the polling alone does not do it.
 * When the rival is found the arena opens without waiting for a tap: the battle exists and its 60 seconds of heartbeat already run.
 */
class QuickSearchViewModel(
    private val lobbyRepository: BattleLobbyRepository,
    private val poller: QuickMatchPoller,
    /** Outlives the view model: leaving the queue must finish even if the screen is already gone. */
    private val leaveScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickSearchUiState())
    val uiState: StateFlow<QuickSearchUiState> = _uiState.asStateFlow()

    private val _events = Channel<QuickSearchEvent>(Channel.BUFFERED)
    val events: Flow<QuickSearchEvent> = _events.receiveAsFlow()

    private var searchJob: Job? = null
    private var inQueue = false

    /** The screen is visible (or "Buscar de nuevo"): join the queue and keep asking. A search already running is not duplicated. */
    fun onStart() {
        if (searchJob?.isActive == true) return
        _uiState.update { QuickSearchUiState(QuickSearchStatus.Searching(0, suggestFriend = false)) }
        searchJob = viewModelScope.launch {
            poller.search().collect { result -> apply(result) }
        }
    }

    private suspend fun apply(result: SocialResult<QuickMatchStatus>) {
        when (result) {
            is SocialResult.Failure -> _uiState.update { QuickSearchUiState(QuickSearchStatus.Failed(result.error)) }
            is SocialResult.Success -> when (val status = result.value) {
                is QuickMatchStatus.Waiting -> {
                    inQueue = true
                    _uiState.update { QuickSearchUiState(QuickSearchStatus.Searching(status.waitedSeconds, status.suggestFriend)) }
                }
                is QuickMatchStatus.Matched -> {
                    inQueue = false
                    _events.send(QuickSearchEvent.OpenBattle(status.battleId))
                }
                is QuickMatchStatus.InBattle -> {
                    inQueue = false
                    _events.send(QuickSearchEvent.OpenBattle(status.battleId))
                }
                QuickMatchStatus.Cancelled, QuickMatchStatus.Idle -> {
                    inQueue = false
                    _uiState.update { QuickSearchUiState(QuickSearchStatus.Stopped) }
                }
            }
        }
    }

    /** The screen is hidden: stop asking and leave the queue. "Buscar de nuevo" starts a new search. */
    fun onStop() {
        if (searchJob?.isActive != true && !inQueue) return
        stopAndLeaveQueue()
        _uiState.update { QuickSearchUiState(QuickSearchStatus.Stopped) }
    }

    /** "Cancelar búsqueda". */
    fun onCancel() {
        stopAndLeaveQueue()
        _events.trySend(QuickSearchEvent.Cancelled)
    }

    /** The screen goes away for good (back, "X"): the same as cancelling, without an event. */
    fun onLeft() {
        stopAndLeaveQueue()
    }

    private fun stopAndLeaveQueue() {
        // A join that was still in flight may have put the person in the queue, so a running search is cancelled in the server too.
        val searching = searchJob?.isActive == true || inQueue
        searchJob?.cancel()
        searchJob = null
        if (searching) {
            inQueue = false
            leaveScope.launch { lobbyRepository.quickCancel() }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                QuickSearchViewModel(container.battleLobbyRepository, container.quickMatchPoller)
            }
        }
    }
}
