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
import com.example.nightwatchprotocol.data.social.UltiAttemptResult
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
enum class ArenaPhase { Loading, MyQuestion, UltiChoice, RivalTurn, Feedback, Ulti, UltiResult, Finished }

/**
 * How an ulti ended, from the last state the server sent: [byMe] is who threw it, [damage] what it did, [shield] whether the
 * defender solved their challenge first, and each side's result. The 45 or 23 HP are the server's numbers.
 */
data class UltiResultView(
    val turnNumber: Int,
    val byMe: Boolean,
    val damage: Int,
    val shield: Boolean,
    val attackerSolved: Boolean,
    val defenderSolved: Boolean,
)

data class ArenaUiState(
    val battle: BattleState? = null,
    val feedback: TurnFeedback? = null,
    val ultiResult: UltiResultView? = null,
    /** The turn whose "Ulti disponible" the person put aside with "Guardar para después" (the ulti stays charged). */
    val ultiChoiceDismissedTurn: Int? = null,
    val command: String = "",
    /** The last command sent was wrong: it used one of the 3 attempts and did not end the challenge. */
    val wrongCommand: Boolean = false,
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
                ultiResult != null -> ArenaPhase.UltiResult
                feedback != null -> ArenaPhase.Feedback
                turn == null -> ArenaPhase.RivalTurn
                turn.phase == TurnPhase.Ulti -> ArenaPhase.Ulti
                turn.isMine && state.question != null ->
                    if (state.myUltiCharged && ultiChoiceDismissedTurn != state.turnNumber) ArenaPhase.UltiChoice else ArenaPhase.MyQuestion
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
            var ultiResult = state.ultiResult
            if (last != null && last.byMe && last.number > lastMineHandled) {
                lastMineHandled = last.number
                if (last.outcome == TurnOutcome.Ulti) {
                    ultiResult = ultiResultOf(last, new)
                } else {
                    feedback = TurnFeedback(last.number, state.chosen, last.outcome, last.damage)
                }
                chosen = null
            }
            if (last != null && !last.byMe && last.number > lastRivalHandled) {
                lastRivalHandled = last.number
                if (last.outcome == TurnOutcome.Ulti) ultiResult = ultiResultOf(last, new) else rivalMove = last
            }
            // A new question of mine, two turns after the one I answered, replaces the feedback: its minute is already running.
            if (feedback != null && new.turn?.isMine == true && new.turnNumber > feedback.turnNumber + 1) feedback = null
            if (ultiResult != null && new.turn?.isMine == true && new.turnNumber > ultiResult.turnNumber + 1) ultiResult = null
            val inUlti = new.turn?.phase == TurnPhase.Ulti
            state.copy(
                battle = new,
                feedback = feedback,
                ultiResult = ultiResult,
                command = if (inUlti) state.command else "",
                wrongCommand = if (inUlti) state.wrongCommand else false,
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

    private fun ultiResultOf(last: LastTurn, state: BattleState) = UltiResultView(
        turnNumber = last.number,
        byMe = last.byMe,
        damage = last.damage,
        shield = state.lastUlti?.shield == true,
        attackerSolved = state.lastUlti?.attackerResult == UltiAttemptResult.Solved,
        defenderSolved = state.lastUlti?.defenderResult == UltiAttemptResult.Solved,
    )

    /** "Guardar para después": the question of this turn is answered as usual and the ulti stays charged. Nothing is sent. */
    fun onSaveUlti() {
        val turn = _uiState.value.battle?.turnNumber ?: return
        _uiState.update { it.copy(ultiChoiceDismissedTurn = turn) }
    }

    /** "Usar Ulti ahora": the turn becomes an ulti for both players (each gets their own challenge). */
    fun onUseUlti() {
        val state = _uiState.value
        if (state.phase != ArenaPhase.UltiChoice || state.submitting) return
        _uiState.update { it.copy(submitting = true, error = null, rivalMove = null) }
        viewModelScope.launch {
            when (val result = repository.useUlti(battleId)) {
                is SocialResult.Success -> apply(result.value, fromAction = true)
                is SocialResult.Failure -> _uiState.update { it.copy(submitting = false, error = result.error) }
            }
        }
    }

    fun onCommandChanged(text: String) {
        _uiState.update { it.copy(command = text.take(MAX_COMMAND_LENGTH), wrongCommand = false) }
    }

    /** "Enviar": one attempt of three. The command is typed from memory and the server compares it; a wrong one only costs the attempt. */
    fun onSubmitCommand() {
        val state = _uiState.value
        val ulti = state.battle?.ulti
        val command = state.command.trim()
        if (state.phase != ArenaPhase.Ulti || state.submitting || command.isEmpty()) return
        if (ulti == null || ulti.myResult != UltiAttemptResult.Pending || ulti.attemptsLeft <= 0) return
        val before = ulti.attemptsLeft
        _uiState.update { it.copy(submitting = true, error = null, wrongCommand = false) }
        viewModelScope.launch {
            when (val result = repository.submitUltiCommand(battleId, command)) {
                is SocialResult.Success -> {
                    apply(result.value, fromAction = true)
                    // Still in the challenge with fewer attempts, or out of attempts: the command did not solve it.
                    val now = result.value.ulti
                    val wrong = now != null && now.myResult != UltiAttemptResult.Solved && now.attemptsLeft < before
                    _uiState.update { it.copy(command = "", wrongCommand = wrong) }
                }
                is SocialResult.Failure -> _uiState.update { it.copy(submitting = false, error = result.error) }
            }
        }
    }

    /** "Continuar →" on the result of an ulti. */
    fun onContinueUlti() {
        _uiState.update { it.copy(ultiResult = null) }
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

        /** A command is short; the server compares it after normalizing it, so more than this is never right. */
        const val MAX_COMMAND_LENGTH = 200

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
