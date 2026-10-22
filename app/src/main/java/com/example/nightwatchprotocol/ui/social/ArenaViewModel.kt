package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.social.BattlePoller
import com.example.nightwatchprotocol.data.social.BattleQuestion
import com.example.nightwatchprotocol.data.social.BattleRepository
import com.example.nightwatchprotocol.data.social.BattleState
import com.example.nightwatchprotocol.data.social.LastTurn
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.TurnOutcome
import com.example.nightwatchprotocol.data.social.TurnPhase
import com.example.nightwatchprotocol.ui.navigation.BattleRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The result of the person's own last turn, shown until "Siguiente turno": [chosen] is null when the minute ran out. */
data class TurnFeedback(val turnNumber: Int, val chosen: Char?, val outcome: TurnOutcome, val damage: Int)

/** What the arena shows now. It is derived from the last state the server sent: the app decides nothing. */
enum class ArenaPhase { Loading, MyQuestion, RivalTurn, Feedback, Ulti, Finished }

data class ArenaUiState(
    val battle: BattleState? = null,
    val feedback: TurnFeedback? = null,
    /** What the rival did in their last turn (shown above my question), until the person answers. */
    val rivalMove: LastTurn? = null,
    val chosen: Char? = null,
    /** The question the person just answered, kept for the feedback (the server sends no question on the rival's turn). */
    val answered: BattleQuestion? = null,
    val submitting: Boolean = false,
    val error: SocialError? = null,
    val confirmingLeave: Boolean = false,
    val leaving: Boolean = false,
) {
    val phase: ArenaPhase
        get() {
            val state = battle ?: return ArenaPhase.Loading
            val turn = state.turn
            return when {
                !state.isOpen -> ArenaPhase.Finished
                feedback != null -> ArenaPhase.Feedback
                turn == null -> ArenaPhase.RivalTurn
                turn.phase == TurnPhase.Ulti -> ArenaPhase.Ulti
                turn.isMine && state.question != null -> ArenaPhase.MyQuestion
                else -> ArenaPhase.RivalTurn
            }
        }
}

sealed interface ArenaEvent {
    /** The battle is over (won, lost, drawn, abandoned or cancelled): the result screen opens. */
    data object Finished : ArenaEvent
}

/**
 * The arena (Figma 2449:189 to 2449:255). The battle is read with [BattlePoller] every 2 to 3 seconds while the screen is visible,
 * on the person's own turn too: each call is the heartbeat that keeps the battle alive (more than 60 seconds without one and the
 * server gives it as lost). The server decides every hit, damage, turn and deadline; this class only shows the last state, sends the
 * option the person taps with the turn it was drawn for, and keeps a short feedback of the person's own last turn.
 */
class ArenaViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BattleRepository,
    private val poller: BattlePoller,
) : ViewModel() {

    val battleId: String = savedStateHandle.get<String>(KEY_BATTLE_ID).orEmpty()

    private val _uiState = MutableStateFlow(ArenaUiState())
    val uiState: StateFlow<ArenaUiState> = _uiState.asStateFlow()

    private val _events = Channel<ArenaEvent>(Channel.BUFFERED)
    val events: Flow<ArenaEvent> = _events.receiveAsFlow()

    private var pollJob: Job? = null
    private var finishedSent = false

    /** The last turn of each side that the screen already handled, so a state read again does not show it twice. */
    private var lastMineHandled = Int.MAX_VALUE
    private var lastRivalHandled = Int.MAX_VALUE

    /** The screen is visible: keep the battle in sync. A second call while it runs does nothing. */
    fun onStart() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            poller.observe(battleId).collect { result ->
                when (result) {
                    is SocialResult.Success -> apply(result.value)
                    is SocialResult.Failure -> _uiState.update { it.copy(error = result.error) }
                }
            }
        }
    }

    /** The screen is hidden: no more heartbeats. Coming back after over a minute, the first read says the battle was lost. */
    fun onStop() {
        pollJob?.cancel()
        pollJob = null
    }

    /** [fromAction] is true for the answer to something the person did: only then is the request that was in flight finished. */
    private suspend fun apply(new: BattleState, fromAction: Boolean = false) {
        val previous = _uiState.value.battle
        // A late answer of the poll must not undo a newer state that an action already brought.
        if (previous != null && new.version < previous.version) return

        val last = new.lastTurn
        if (previous == null) {
            // The first state of this visit: the turn that is already over was seen before; nothing is replayed.
            lastMineHandled = last?.takeIf { it.byMe }?.number ?: 0
            lastRivalHandled = last?.takeIf { !it.byMe }?.number ?: 0
        }
        _uiState.update { state ->
            var feedback = state.feedback
            var rivalMove = state.rivalMove
            var chosen = state.chosen
            if (last != null && last.byMe && last.number > lastMineHandled) {
                lastMineHandled = last.number
                feedback = TurnFeedback(last.number, state.chosen, last.outcome, last.damage)
                chosen = null
            }
            if (last != null && !last.byMe && last.number > lastRivalHandled) {
                lastRivalHandled = last.number
                rivalMove = last
            }
            // A new question of mine, two turns after the one I answered, replaces the feedback: its minute is already running.
            if (feedback != null && new.turn?.isMine == true && new.turnNumber > feedback.turnNumber + 1) feedback = null
            state.copy(
                battle = new,
                feedback = feedback,
                answered = if (feedback == null && !state.submitting) null else state.answered,
                rivalMove = rivalMove,
                chosen = chosen,
                submitting = if (fromAction) false else state.submitting,
                error = null,
            )
        }
        if (!new.isOpen && !finishedSent) {
            finishedSent = true
            _events.send(ArenaEvent.Finished)
        }
    }

    /** The person taps an option: the answer goes with the turn number it was drawn for (the server rejects a turn that passed). */
    fun onOptionSelected(letter: Char) {
        val state = _uiState.value
        val battle = state.battle ?: return
        if (state.phase != ArenaPhase.MyQuestion || state.submitting) return
        _uiState.update { it.copy(chosen = letter, answered = battle.question, submitting = true, error = null, rivalMove = null) }
        viewModelScope.launch {
            when (val result = repository.answer(battleId, battle.turnNumber, letter)) {
                is SocialResult.Success -> apply(result.value, fromAction = true)
                is SocialResult.Failure -> _uiState.update { it.copy(chosen = null, answered = null, submitting = false, error = result.error) }
            }
        }
    }

    /** "Siguiente turno →": closes the feedback of the person's own turn. */
    fun onNextTurn() {
        _uiState.update { it.copy(feedback = null, answered = null) }
    }

    /** "X" or back: asks first, because leaving gives the battle as lost (decision O13). */
    fun onLeaveClicked() {
        if (_uiState.value.battle?.isOpen != true) return
        _uiState.update { it.copy(confirmingLeave = true) }
    }

    fun onLeaveDismissed() {
        if (_uiState.value.leaving) return
        _uiState.update { it.copy(confirmingLeave = false) }
    }

    fun onLeaveConfirmed() {
        if (_uiState.value.leaving) return
        _uiState.update { it.copy(leaving = true, confirmingLeave = false, error = null) }
        viewModelScope.launch {
            when (val result = repository.abandon(battleId)) {
                is SocialResult.Success -> {
                    _uiState.update { it.copy(leaving = false) }
                    apply(result.value, fromAction = true)
                }
                is SocialResult.Failure -> _uiState.update { it.copy(leaving = false, error = result.error) }
            }
        }
    }

    companion object {
        const val KEY_BATTLE_ID = "battleId"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                val handle = createSavedStateHandle()
                handle[KEY_BATTLE_ID] = handle.toRoute<BattleRoute>().battleId
                ArenaViewModel(handle, container.battleRepository, container.battlePoller)
            }
        }
    }
}
