package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import com.example.nightwatchprotocol.data.social.FighterRepository
import com.example.nightwatchprotocol.data.social.FriendsRepository
import com.example.nightwatchprotocol.data.social.PairStreakRepository
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfileRepository
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.USERNAME_MAX_LENGTH
import com.example.nightwatchprotocol.data.social.isValidUsername
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Social tab shows. Social is NOT enabled until the person creates a username (decision D10). */
sealed interface SocialHomeStatus {
    data object Loading : SocialHomeStatus

    /** There is no `social_profile` yet: the tab shows the "crear nombre de usuario" screen. */
    data object NeedsUsername : SocialHomeStatus

    data object Ready : SocialHomeStatus

    data class Failed(val error: SocialError) : SocialHomeStatus
}

/**
 * [pairStreakDays] is the current streak of the pair with the most days (decision D15), [friendsCount] the friends and
 * [wins], [losses] and [draws] the score. Each is null until read or when its read failed ([tilesError] then says why).
 */
data class SocialHomeUiState(
    val status: SocialHomeStatus = SocialHomeStatus.Loading,
    val username: String = "",
    val creating: Boolean = false,
    val usernameError: SocialError? = null,
    val pairStreakDays: Int? = null,
    val friendsCount: Int? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val draws: Int = 0,
    val tilesError: SocialError? = null,
)

/**
 * "Social - Inicio" and the screen to create the username that comes before it. The server decides every number shown here:
 * the app only reads them (the pair streak and the friends through the functions, the score from the own rows).
 */
class SocialHomeViewModel(
    private val profileRepository: SocialProfileRepository,
    private val friendsRepository: FriendsRepository,
    private val pairStreakRepository: PairStreakRepository,
    private val fighterRepository: FighterRepository,
    /** The "ocultar mi progreso" chosen before Social existed is carried to the new profile, so nothing becomes visible by surprise. */
    private val userPreferences: UserPreferences? = null,
    /** Runs once, the first time Social is seen enabled: it puts into Social a profile photo saved before. A failure changes nothing. */
    private val onSocialReady: suspend () -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialHomeUiState())
    val uiState: StateFlow<SocialHomeUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var photoSyncStarted = false

    private fun syncPhotoOnce() {
        if (photoSyncStarted) return
        photoSyncStarted = true
        viewModelScope.launch { onSocialReady() }
    }

    /** Reads the profile and, with Social enabled, the three tiles. Safe to call again: a read already running is not duplicated. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        _uiState.update { if (it.status is SocialHomeStatus.Failed) it.copy(status = SocialHomeStatus.Loading) else it }
        refreshJob = viewModelScope.launch {
            when (val profile = profileRepository.getMyProfile()) {
                is SocialResult.Failure -> _uiState.update { it.copy(status = SocialHomeStatus.Failed(profile.error)) }
                is SocialResult.Success ->
                    if (profile.value == null) {
                        _uiState.update { it.copy(status = SocialHomeStatus.NeedsUsername) }
                    } else {
                        _uiState.update { it.copy(status = SocialHomeStatus.Ready) }
                        syncPhotoOnce()
                        loadTiles()
                    }
            }
        }
    }

    private suspend fun loadTiles() {
        val results = coroutineScope {
            val pairs = async { pairStreakRepository.overview() }
            val friends = async { friendsRepository.list() }
            val fighter = async { fighterRepository.getSummary() }
            Triple(pairs.await(), friends.await(), fighter.await())
        }
        val (pairs, friends, fighter) = results
        val errors = listOfNotNull(
            (pairs as? SocialResult.Failure)?.error,
            (friends as? SocialResult.Failure)?.error,
            (fighter as? SocialResult.Failure)?.error,
        )
        _uiState.update { state ->
            state.copy(
                // A read that failed keeps the last known value.
                pairStreakDays = (pairs as? SocialResult.Success)?.value?.topCurrentDays ?: state.pairStreakDays,
                friendsCount = (friends as? SocialResult.Success)?.value?.size ?: state.friendsCount,
                wins = (fighter as? SocialResult.Success)?.value?.wins ?: state.wins,
                losses = (fighter as? SocialResult.Success)?.value?.losses ?: state.losses,
                draws = (fighter as? SocialResult.Success)?.value?.draws ?: state.draws,
                tilesError = errors.firstOrNull(),
                status = if (errors.contains(SocialError.NotEnabled)) SocialHomeStatus.NeedsUsername else state.status,
            )
        }
    }

    fun onUsernameChanged(text: String) {
        _uiState.update { it.copy(username = text.take(USERNAME_MAX_LENGTH), usernameError = null) }
    }

    /** "CREAR USUARIO": the rule is checked here for a quick answer and the server checks it again (and the name must be free). */
    fun onCreateUsername() {
        val state = _uiState.value
        if (state.creating) return
        val name = state.username.trim()
        if (!isValidUsername(name)) {
            _uiState.update { it.copy(usernameError = SocialError.Rejected(SocialRejection.InvalidUsername)) }
            return
        }
        _uiState.update { it.copy(creating = true, usernameError = null) }
        viewModelScope.launch {
            when (val created = profileRepository.createProfile(name)) {
                is SocialResult.Failure -> _uiState.update { it.copy(creating = false, usernameError = created.error) }
                is SocialResult.Success -> {
                    // A person who already hid their progress on this device keeps it hidden in the new profile.
                    if (userPreferences?.settings?.first()?.get(UserSetting.HIDE_PUBLIC_PROGRESS) == true) {
                        profileRepository.setHideProgress(true)
                    }
                    _uiState.update { it.copy(creating = false, status = SocialHomeStatus.Ready) }
                    syncPhotoOnce()
                    loadTiles()
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                SocialHomeViewModel(
                    profileRepository = container.socialProfileRepository,
                    friendsRepository = container.friendsRepository,
                    pairStreakRepository = container.pairStreakRepository,
                    fighterRepository = container.fighterRepository,
                    userPreferences = container.userPreferences,
                    onSocialReady = { container.avatarRepository.syncSocialIfMissing() },
                )
            }
        }
    }
}
