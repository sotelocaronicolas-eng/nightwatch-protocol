package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.social.BattleLobbyRepository
import com.example.nightwatchprotocol.data.social.ChallengeStatus
import com.example.nightwatchprotocol.data.social.ChallengesPoller
import com.example.nightwatchprotocol.data.social.ChallengesView
import com.example.nightwatchprotocol.data.social.FighterRepository
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.FriendsRepository
import com.example.nightwatchprotocol.data.social.ReceivedChallenge
import com.example.nightwatchprotocol.data.social.SentChallenge
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

/**
 * [friends] and the score are read once per visit. [received] and [sent] are the challenges as the server last said: they refresh
 * while the screen is visible. [sent] is the one the person made (waiting, answered or expired).
 */
data class BattlesUiState(
    val loading: Boolean = true,
    val wins: Int? = null,
    val losses: Int? = null,
    val draws: Int = 0,
    val friends: List<Friend>? = null,
    val received: List<ReceivedChallenge> = emptyList(),
    val sent: SentChallenge? = null,
    val loadError: SocialError? = null,
    val busy: Set<String> = emptySet(),
    val actionError: SocialError? = null,
) {
    /** A challenge of mine that the other person has not answered yet: there can be only one, and it blocks the other "Retar". */
    val waiting: SentChallenge? get() = sent?.takeIf { it.status == ChallengeStatus.Pending }
}

sealed interface BattlesEvent {
    /** A battle exists for the person (a challenge was accepted, by them or by the friend): go to the arena. */
    data class OpenBattle(val battleId: String) : BattlesEvent
}

/**
 * "Batallas" (Figma 2448:187): the score, the quick match, the challenges received and the friends to challenge. The challenges
 * list is read again every few seconds only while the screen is visible ([onStart] and [onStop]); a challenge the person sent is
 * cancelled when they leave the screen ([onLeft]), so nobody accepts against someone who is already gone.
 */
class BattlesViewModel(
    private val lobbyRepository: BattleLobbyRepository,
    private val friendsRepository: FriendsRepository,
    private val fighterRepository: FighterRepository,
    private val challengesPoller: ChallengesPoller,
    /** Outlives the view model: the cancel on leaving must finish even if the screen is already gone. */
    private val leaveScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BattlesUiState())
    val uiState: StateFlow<BattlesUiState> = _uiState.asStateFlow()

    private val _events = Channel<BattlesEvent>(Channel.BUFFERED)
    val events: Flow<BattlesEvent> = _events.receiveAsFlow()

    private var pollJob: Job? = null
    private var openedBattle: String? = null

    /** The screen is visible: read the friends and the score, and keep the challenges fresh. */
    fun onStart() {
        loadStatic()
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            challengesPoller.observe().collect { result -> applyChallenges(result) }
        }
    }

    /** The screen is hidden (another app, another screen): no more calls. */
    fun onStop() {
        pollJob?.cancel()
        pollJob = null
    }

    /** The screen was left for good: a challenge still waiting is cancelled. */
    fun onLeft() {
        if (_uiState.value.waiting == null) return
        leaveScope.launch { lobbyRepository.cancelChallenge() }
        _uiState.update { it.copy(sent = null) }
    }

    private fun loadStatic() {
        viewModelScope.launch {
            val fighter = fighterRepository.getSummary()
            val friends = friendsRepository.list()
            _uiState.update { state ->
                state.copy(
                    wins = (fighter as? SocialResult.Success)?.value?.wins ?: state.wins,
                    losses = (fighter as? SocialResult.Success)?.value?.losses ?: state.losses,
                    draws = (fighter as? SocialResult.Success)?.value?.draws ?: state.draws,
                    friends = (friends as? SocialResult.Success)?.value ?: state.friends,
                    loadError = (friends as? SocialResult.Failure)?.error ?: (fighter as? SocialResult.Failure)?.error,
                )
            }
        }
    }

    private suspend fun applyChallenges(result: SocialResult<ChallengesView>) {
        when (result) {
            is SocialResult.Failure -> _uiState.update { it.copy(loading = false, loadError = result.error) }
            is SocialResult.Success -> {
                val view = result.value
                _uiState.update { it.copy(loading = false, received = view.received, sent = view.sent) }
                val battleId = view.activeBattleId ?: view.sent?.takeIf { it.status == ChallengeStatus.Accepted }?.battleId
                if (battleId != null && battleId != openedBattle) {
                    openedBattle = battleId
                    _events.send(BattlesEvent.OpenBattle(battleId))
                }
            }
        }
    }

    /** "Retar": the server checks that it is a friend, that nobody is in a battle and the rate limit. */
    fun onChallenge(friendCode: String) {
        val state = _uiState.value
        if (state.waiting != null || friendCode in state.busy) return
        _uiState.update { it.copy(busy = it.busy + friendCode, actionError = null) }
        viewModelScope.launch {
            val sent = lobbyRepository.sendChallenge(friendCode)
            _uiState.update { it.copy(busy = it.busy - friendCode, actionError = (sent as? SocialResult.Failure)?.error) }
            refreshChallenges()
        }
    }

    fun onCancelWaiting() {
        if (_uiState.value.waiting == null) return
        viewModelScope.launch {
            val result = lobbyRepository.cancelChallenge()
            _uiState.update { it.copy(actionError = (result as? SocialResult.Failure)?.error) }
            refreshChallenges()
        }
    }

    fun onAccept(friendCode: String) {
        if (friendCode in _uiState.value.busy) return
        _uiState.update { it.copy(busy = it.busy + friendCode, actionError = null) }
        viewModelScope.launch {
            when (val result = lobbyRepository.acceptChallenge(friendCode)) {
                is SocialResult.Success -> {
                    _uiState.update { it.copy(busy = it.busy - friendCode) }
                    openedBattle = result.value
                    _events.send(BattlesEvent.OpenBattle(result.value))
                }
                is SocialResult.Failure -> {
                    _uiState.update { it.copy(busy = it.busy - friendCode, actionError = result.error) }
                    refreshChallenges()
                }
            }
        }
    }

    fun onDecline(friendCode: String) {
        if (friendCode in _uiState.value.busy) return
        _uiState.update { it.copy(busy = it.busy + friendCode, actionError = null) }
        viewModelScope.launch {
            val result = lobbyRepository.declineChallenge(friendCode)
            _uiState.update { it.copy(busy = it.busy - friendCode, actionError = (result as? SocialResult.Failure)?.error) }
            refreshChallenges()
        }
    }

    private suspend fun refreshChallenges() {
        applyChallenges(lobbyRepository.challenges())
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                BattlesViewModel(
                    lobbyRepository = container.battleLobbyRepository,
                    friendsRepository = container.friendsRepository,
                    fighterRepository = container.fighterRepository,
                    challengesPoller = container.challengesPoller,
                )
            }
        }
    }
}
