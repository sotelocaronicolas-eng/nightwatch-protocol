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
import com.example.nightwatchprotocol.data.learning.BadgeState
import com.example.nightwatchprotocol.data.learning.evaluateBadges
import com.example.nightwatchprotocol.data.social.BattleLobbyRepository
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.FriendProfile
import com.example.nightwatchprotocol.data.social.FriendsRepository
import com.example.nightwatchprotocol.data.social.PairStreakRepository
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.ui.navigation.FriendProfileRoute
import java.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A battle between the two, as the profile lists it: who won and how long ago ([daysAgo] is null without a date). */
data class FriendBattleRow(val outcome: BattleOutcome, val daysAgo: Int?)

/**
 * What the profile shows. A friend who hides the progress (decision D11) has [hidden] true and no [badges], [exercises],
 * [labs] or [fighterLevel]: the server does not send them, so there is nothing to show by mistake.
 */
data class FriendProfileUiState(
    val loading: Boolean = true,
    val username: String = "",
    val streakDays: Int = 0,
    val trainedToday: Boolean = false,
    val hidden: Boolean = false,
    val fighterLevel: Int? = null,
    val exercises: Int? = null,
    val labs: Int? = null,
    val unlockedBadges: List<BadgeState> = emptyList(),
    val history: List<FriendBattleRow> = emptyList(),
    val loaded: Boolean = false,
    val loadError: SocialError? = null,
    val confirmingRemove: Boolean = false,
    val removing: Boolean = false,
    val challenging: Boolean = false,
    val invitingPair: Boolean = false,
    val pairInviteSent: Boolean = false,
    val actionError: SocialError? = null,
)

sealed interface FriendProfileEvent {
    /** The friend was removed: the screen goes back to the list. */
    data object Removed : FriendProfileEvent

    /** The challenge was sent: the lobby shows "Esperando a ..." with its countdown. */
    data object ChallengeSent : FriendProfileEvent
}

/** "Perfil amigo" (Figma 2446:265). The badges are evaluated here with the same catalog as the own ones. */
class FriendProfileViewModel(
    savedStateHandle: SavedStateHandle,
    private val friendsRepository: FriendsRepository,
    private val pairStreakRepository: PairStreakRepository,
    private val lobbyRepository: BattleLobbyRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    /** The friend is identified by their code (the route); who the person is never comes from anywhere else. */
    val friendCode: String = savedStateHandle.get<String>(KEY_FRIEND_CODE).orEmpty()

    private val _uiState = MutableStateFlow(FriendProfileUiState())
    val uiState: StateFlow<FriendProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<FriendProfileEvent>(Channel.BUFFERED)
    val events: Flow<FriendProfileEvent> = _events.receiveAsFlow()

    fun refresh() {
        _uiState.update { it.copy(loading = !it.loaded, loadError = null) }
        viewModelScope.launch {
            when (val result = friendsRepository.profile(friendCode)) {
                is SocialResult.Success -> _uiState.update { show(result.value) }
                is SocialResult.Failure -> _uiState.update { it.copy(loading = false, loadError = result.error) }
            }
        }
    }

    private fun show(profile: FriendProfile): FriendProfileUiState {
        val progress = profile.progress.takeUnless { profile.hidden }
        val now = clock.instant()
        return _uiState.value.copy(
            loading = false,
            loaded = true,
            loadError = null,
            username = profile.username,
            streakDays = profile.streakDays,
            trainedToday = profile.trainedToday,
            hidden = profile.hidden,
            fighterLevel = progress?.fighterLevel,
            exercises = progress?.viewedLessons?.size,
            labs = progress?.completedLabs?.size,
            unlockedBadges = progress?.let { evaluateBadges(it.toBadgeFacts()).filter { badge -> badge.unlocked } }.orEmpty(),
            history = profile.history.map { FriendBattleRow(it.outcome, daysAgo(it.finishedAt, now)) },
        )
    }

    /** "⚔️ Batallar": sends the challenge (2 minutes) and the screen goes to the lobby, where the wait is shown. */
    fun onBattleClicked() {
        val state = _uiState.value
        if (state.challenging) return
        _uiState.update { it.copy(challenging = true, actionError = null) }
        viewModelScope.launch {
            when (val result = lobbyRepository.sendChallenge(friendCode)) {
                is SocialResult.Success -> {
                    _uiState.update { it.copy(challenging = false) }
                    _events.send(FriendProfileEvent.ChallengeSent)
                }
                is SocialResult.Failure -> _uiState.update { it.copy(challenging = false, actionError = result.error) }
            }
        }
    }

    /** "Invitar a una racha en pareja" (M5): the server checks that this person is a friend and that there is no pair yet. */
    fun onInvitePairClicked() {
        val state = _uiState.value
        if (state.invitingPair || state.pairInviteSent) return
        _uiState.update { it.copy(invitingPair = true, actionError = null) }
        viewModelScope.launch {
            when (val result = pairStreakRepository.invite(friendCode)) {
                is SocialResult.Success -> _uiState.update { it.copy(invitingPair = false, pairInviteSent = true) }
                is SocialResult.Failure -> _uiState.update { it.copy(invitingPair = false, actionError = result.error) }
            }
        }
    }

    fun onRemoveClicked() {
        _uiState.update { it.copy(confirmingRemove = true, actionError = null) }
    }

    fun onRemoveDismissed() {
        if (_uiState.value.removing) return
        _uiState.update { it.copy(confirmingRemove = false) }
    }

    fun onRemoveConfirmed() {
        if (_uiState.value.removing) return
        _uiState.update { it.copy(removing = true, confirmingRemove = false, actionError = null) }
        viewModelScope.launch {
            when (val result = friendsRepository.remove(friendCode)) {
                is SocialResult.Success -> {
                    _uiState.update { it.copy(removing = false) }
                    _events.send(FriendProfileEvent.Removed)
                }
                is SocialResult.Failure -> _uiState.update { it.copy(removing = false, actionError = result.error) }
            }
        }
    }

    companion object {
        const val KEY_FRIEND_CODE = "friendCode"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val handle = createSavedStateHandle()
                // The route carries the friend code; it is copied under a fixed key so the tests do not need the navigation library.
                handle[KEY_FRIEND_CODE] = handle.toRoute<FriendProfileRoute>().friendCode
                val container = application.container
                FriendProfileViewModel(
                    handle,
                    container.friendsRepository,
                    container.pairStreakRepository,
                    container.battleLobbyRepository,
                )
            }
        }
    }
}
