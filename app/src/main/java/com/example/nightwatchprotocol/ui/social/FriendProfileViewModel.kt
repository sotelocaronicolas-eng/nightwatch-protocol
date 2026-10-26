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
import com.example.nightwatchprotocol.data.social.BattleStats
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
    val avatarKey: String? = null,
    val avatarVersion: Int? = null,
    val streakDays: Int = 0,
    val trainedToday: Boolean = false,
    val hidden: Boolean = false,
    val fighterLevel: Int? = null,
    val exercises: Int? = null,
    val labs: Int? = null,
    /** The unlocked badges; null when the badges are not shown (the friend closed their eye, hides the progress or the server sent no facts). */
    val unlockedBadges: List<BadgeState>? = null,
    val history: List<FriendBattleRow> = emptyList(),
    /** What the friend chose for their profile (`0012`): the background and the three eyes. The defaults are "sin fondo" and all open. */
    val profileBg: Int = 0,
    val showBadges: Boolean = true,
    val showStats: Boolean = true,
    val showHistory: Boolean = true,
    /** The totals of the friend against anyone; null when they hide them or the server does not send them. */
    val stats: BattleStats? = null,
    /** False when the server answered without the fields of `0012`: the screen says "Función no disponible por ahora". */
    val extrasAvailable: Boolean = true,
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

/**
 * What the shared profile sections draw for this friend ([hiddenNotice] is "X oculta su progreso." when the friend hides it). A
 * section that the friend closed (the server leaves it out) or that the server did not send is null, so it is not drawn and the ones
 * below move up. The history is the last [PROFILE_HISTORY_LIMIT] battles between the two, the rival being the friend.
 */
internal fun FriendProfileUiState.toProfileContent(hiddenNotice: String?): ProfileContent {
    // The eye of the badges only takes the badges out: the level and the counters follow the progress ([hidden]) alone.
    val progressShown = !hidden
    return ProfileContent(
        username = username,
        isOwn = false,
        avatarKey = avatarKey,
        avatarVersion = avatarVersion,
        streakDays = streakDays,
        trainedToday = trainedToday,
        fighterLevel = fighterLevel.takeIf { progressShown },
        exercises = exercises.takeIf { progressShown },
        labs = labs.takeIf { progressShown },
        badges = unlockedBadges.takeIf { progressShown && showBadges },
        stats = stats.takeIf { showStats },
        history = history.takeIf { showHistory }?.take(PROFILE_HISTORY_LIMIT)?.map { ProfileBattleRow(it.outcome, username, it.daysAgo) },
        hiddenNotice = hiddenNotice.takeIf { hidden },
    )
}

/** "Perfil amigo" (Figma 2446:265, updated in 2638:374). The badges are evaluated here with the same catalog as the own ones. */
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
        // The server already leaves the progress out when the friend hides it, and the badge facts when they closed the eye of the badges;
        // this keeps it so even if not. The badges are only evaluated when the facts came.
        val progress = profile.progress.takeUnless { profile.hidden }
        val badgeFacts = progress?.badgeFacts?.takeIf { profile.display.showBadges }
        val now = clock.instant()
        return _uiState.value.copy(
            loading = false,
            loaded = true,
            loadError = null,
            username = profile.username,
            avatarKey = profile.avatarKey,
            avatarVersion = profile.avatarVersion,
            streakDays = profile.streakDays,
            trainedToday = profile.trainedToday,
            hidden = profile.hidden,
            fighterLevel = progress?.fighterLevel,
            exercises = progress?.viewedCount,
            labs = progress?.labsCount,
            unlockedBadges = badgeFacts?.let { evaluateBadges(it).filter { badge -> badge.unlocked } },
            history = profile.history.map { FriendBattleRow(it.outcome, daysAgo(it.finishedAt, now)) },
            profileBg = profile.display.profileBg,
            showBadges = profile.display.showBadges,
            showStats = profile.display.showStats,
            showHistory = profile.display.showHistory,
            stats = profile.stats.takeIf { profile.display.showStats },
            extrasAvailable = profile.extrasAvailable,
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
