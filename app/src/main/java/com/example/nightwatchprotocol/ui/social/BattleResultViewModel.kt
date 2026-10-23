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
import com.example.nightwatchprotocol.data.learning.LearningContentRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.data.social.BattleEndReason
import com.example.nightwatchprotocol.data.social.BattleLobbyRepository
import com.example.nightwatchprotocol.data.social.BattleMode
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.BattleRepository
import com.example.nightwatchprotocol.data.social.BattleState
import com.example.nightwatchprotocol.data.social.BattleStatus
import com.example.nightwatchprotocol.data.social.FighterRepository
import com.example.nightwatchprotocol.data.social.FighterSummary
import com.example.nightwatchprotocol.data.social.FriendsRepository
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.ui.navigation.BattleResultRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the battle ended for the person. [Cancelled] is a battle with no winner and no XP (both left, or it never really started). */
enum class EndKind { Victory, Defeat, Draw, Cancelled }

/**
 * Everything the end of a battle shows, all of it from the server: the result of the battle (outcome, HP, XP, level, weak topics) and
 * the own totals read afterwards (score, battle streak, XP towards the next level).
 */
data class BattleResultUiState(
    val loading: Boolean = true,
    val kind: EndKind? = null,
    val rival: String = "",
    val reason: BattleEndReason? = null,
    val hpLeft: Int = 0,
    val xp: Int = 0,
    val xpTotal: Int = 0,
    val level: Int = 1,
    val nextLevelXp: Int? = null,
    val weakTopics: List<String> = emptyList(),
    val wins: Int? = null,
    val losses: Int? = null,
    val draws: Int = 0,
    val battleStreak: Int = 0,
    /** "Revancha" is only offered after a battle against a friend, and only while they are still a friend (decision O13). */
    val rematchFriendCode: String? = null,
    val rematching: Boolean = false,
    val error: SocialError? = null,
    val actionError: SocialError? = null,
)

sealed interface BattleResultEvent {
    /** The rematch challenge was sent: the lobby shows the wait. */
    data object RematchSent : BattleResultEvent
}

/**
 * "Fin de batalla" (Figma 2451:195 and 2451:209, plus the draw and the cancelled battle). It reads the finished battle once (no
 * heartbeat: the battle is over) and then the own totals.
 */
class BattleResultViewModel(
    savedStateHandle: SavedStateHandle,
    private val battleRepository: BattleRepository,
    private val fighterRepository: FighterRepository,
    private val friendsRepository: FriendsRepository,
    private val lobbyRepository: BattleLobbyRepository,
    private val contentRepository: LearningContentRepository,
) : ViewModel() {

    val battleId: String = savedStateHandle.get<String>(KEY_BATTLE_ID).orEmpty()

    private val _uiState = MutableStateFlow(BattleResultUiState())
    val uiState: StateFlow<BattleResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<BattleResultEvent>(Channel.BUFFERED)
    val events: Flow<BattleResultEvent> = _events.receiveAsFlow()

    private var started = false

    /** Reads the result. Called once when the screen opens and again by "Reintentar"; it is not repeated while a read is running. */
    fun load() {
        if (started && _uiState.value.error == null) return
        started = true
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val synced = battleRepository.sync(battleId)) {
                is SocialResult.Failure -> _uiState.update { it.copy(loading = false, error = synced.error) }
                is SocialResult.Success -> {
                    val battle = synced.value
                    if (battle.status == BattleStatus.Active) {
                        // The battle is still going (it should not be here): "Reintentar" reads it again.
                        _uiState.update { it.copy(loading = false, error = SocialError.Unknown) }
                        return@launch
                    }
                    show(battle)
                    loadTotals()
                    loadRematch(battle)
                }
            }
        }
    }

    private suspend fun show(battle: BattleState) {
        val result = battle.result
        val kind = when {
            battle.status == BattleStatus.Cancelled || result == null -> EndKind.Cancelled
            result.outcome == BattleOutcome.Win -> EndKind.Victory
            result.outcome == BattleOutcome.Loss -> EndKind.Defeat
            else -> EndKind.Draw
        }
        val titles = result?.weakTopics.orEmpty().mapNotNull { topicTitle(it) }
        _uiState.update {
            it.copy(
                loading = false,
                kind = kind,
                rival = battle.rivalUsername,
                reason = battle.endReason,
                hpLeft = result?.hpLeft ?: battle.myHp,
                xp = result?.xp ?: 0,
                xpTotal = result?.xpTotal ?: 0,
                level = result?.level ?: 1,
                weakTopics = titles,
            )
        }
    }

    private suspend fun topicTitle(topic: LearningTopic): String? =
        runCatchingKeepCancellation { contentRepository.getContent().lesson(topic).title }.getOrNull()

    /** The score, the battle streak and the XP still missing for the next level: the person's own rows, after this battle. */
    private suspend fun loadTotals() {
        val summary: FighterSummary = (fighterRepository.getSummary() as? SocialResult.Success)?.value ?: return
        _uiState.update {
            it.copy(
                wins = summary.wins,
                losses = summary.losses,
                draws = summary.draws,
                battleStreak = summary.battleStreak,
                nextLevelXp = summary.nextLevelXp,
                level = summary.level,
                xpTotal = summary.xp,
            )
        }
    }

    private suspend fun loadRematch(battle: BattleState) {
        if (battle.mode != BattleMode.Friend) return
        val friends = (friendsRepository.list() as? SocialResult.Success)?.value ?: return
        val friend = friends.firstOrNull { it.username.equals(battle.rivalUsername, ignoreCase = true) } ?: return
        _uiState.update { it.copy(rematchFriendCode = friend.friendCode) }
    }

    /** The rival was blocked from this screen: they are not a friend any more, so "Revancha" is no longer offered. */
    fun onRivalBlocked() {
        _uiState.update { it.copy(rematchFriendCode = null) }
    }

    /** "Revancha": a new challenge of 2 minutes to the same friend. */
    fun onRematch() {
        val state = _uiState.value
        val code = state.rematchFriendCode ?: return
        if (state.rematching) return
        _uiState.update { it.copy(rematching = true, actionError = null) }
        viewModelScope.launch {
            when (val result = lobbyRepository.sendChallenge(code)) {
                is SocialResult.Success -> {
                    _uiState.update { it.copy(rematching = false) }
                    _events.send(BattleResultEvent.RematchSent)
                }
                is SocialResult.Failure -> _uiState.update { it.copy(rematching = false, actionError = result.error) }
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
                handle[KEY_BATTLE_ID] = handle.toRoute<BattleResultRoute>().battleId
                BattleResultViewModel(
                    handle,
                    container.battleRepository,
                    container.fighterRepository,
                    container.friendsRepository,
                    container.battleLobbyRepository,
                    container.learningContentRepository,
                )
            }
        }
    }
}
