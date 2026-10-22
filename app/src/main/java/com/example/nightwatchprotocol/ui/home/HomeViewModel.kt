package com.example.nightwatchprotocol.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.avatarDisplay
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.device.DeviceSessionManager
import com.example.nightwatchprotocol.data.device.SessionCheck
import com.example.nightwatchprotocol.data.learning.LearningContentRepository
import com.example.nightwatchprotocol.data.learning.LearningProgressRepository
import com.example.nightwatchprotocol.data.learning.StreakState
import com.example.nightwatchprotocol.data.learning.computeStreak
import com.example.nightwatchprotocol.data.learning.deriveLearningProgress
import com.example.nightwatchprotocol.data.learning.practiceMoments
import java.time.Clock
import com.example.nightwatchprotocol.data.profile.LEVEL_NOT_ASSESSED
import com.example.nightwatchprotocol.data.profile.profileDisplayName
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
import com.example.nightwatchprotocol.ui.navigation.Home
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/** The tab survives a rotation (and the process dying) because it is kept in the saved state. */
private const val KEY_SELECTED_TAB = "selected_tab"

class HomeViewModel(
    initialTab: HomeTab,
    private val savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val avatarRepository: AvatarRepository,
    private val quizResultRepository: QuizResultRepository,
    private val learningProgressRepository: LearningProgressRepository,
    private val learningContentRepository: LearningContentRepository,
    private val sessionManager: DeviceSessionManager,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private var account = accountRepository.currentProfile()
    private var progressJob: Job? = null
    private var signingOut = false

    private val _uiState = MutableStateFlow(
        HomeUiState(
            selectedTab = HomeTab.fromName(savedStateHandle[KEY_SELECTED_TAB]) ?: initialTab,
            displayName = profileDisplayName(account?.displayName, account?.email),
            avatar = avatarDisplay(null, account?.displayName, account?.email),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            avatarRepository.avatar.collect { bytes ->
                _uiState.update { it.copy(avatar = avatarDisplay(bytes, account?.displayName, account?.email)) }
            }
        }
        val uid = authRepository.currentUserUid
        if (uid == null) {
            _uiState.update { it.copy(level = LEVEL_NOT_ASSESSED) }
        } else {
            viewModelScope.launch { avatarRepository.load(uid) }
            viewModelScope.launch { loadLevel(uid) }
        }
        // Registers this device in the cooperative device list and honours a pending sign-out request (as the old Main Menu did).
        viewModelScope.launch {
            if (sessionManager.onAppActive() == SessionCheck.Revoked) _events.send(HomeEvent.SignedOut)
        }
    }

    fun onTabSelected(tab: HomeTab) {
        savedStateHandle[KEY_SELECTED_TAB] = tab.name
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /** "Concepto nuevo" and "Prepárate con CiberMynd". */
    fun onComingSoonCardClicked() {
        viewModelScope.launch { _events.send(HomeEvent.ShowComingSoon) }
    }

    /**
     * "Lección diaria": opens the next pending lesson; with none, a Snackbar says so (decision Q3). While the progress is
     * loading or failed there is nothing to open, so the tap does nothing (the card offers "Reintentar").
     */
    fun onDailyLessonClicked() {
        when (val daily = _uiState.value.dailyLesson) {
            is DailyLessonState.Pending -> viewModelScope.launch { _events.send(HomeEvent.OpenLesson(daily.topic)) }
            DailyLessonState.NoneLeft -> viewModelScope.launch { _events.send(HomeEvent.ShowNoPendingLessons) }
            DailyLessonState.Loading, is DailyLessonState.Failed -> Unit
        }
    }

    /** "Perfil" is not content of Home: it opens the user menu, and Home (with its tab) stays below it. */
    fun onProfileClicked() {
        viewModelScope.launch { _events.send(HomeEvent.OpenUserMenu) }
    }

    fun onSignOutClicked() {
        if (signingOut) return
        signingOut = true
        viewModelScope.launch {
            // Removes this device from the registry (best effort), signs out and clears the in-memory avatar.
            sessionManager.endSession()
            _events.send(HomeEvent.SignedOut)
        }
    }

    /** Re-reads name and email, which may have changed on Editar Perfil while Home stayed in the back stack. */
    fun refreshAccount() {
        account = accountRepository.currentProfile()
        _uiState.update { it.copy(displayName = profileDisplayName(account?.displayName, account?.email)) }
    }

    /**
     * Reads the progress for the "Lección diaria" card. Safe to call again (on returning to Inicio, or "Reintentar"): a read
     * already running is not duplicated, and the card keeps showing its last answer until the new one arrives.
     */
    fun refreshProgress() {
        if (progressJob?.isActive == true) return
        // Set before launching, so "Reintentar" shows the spinner at once.
        _uiState.update { state ->
            if (state.dailyLesson is DailyLessonState.Failed) state.copy(dailyLesson = DailyLessonState.Loading) else state
        }
        progressJob = viewModelScope.launch {
            val result = runCatchingKeepCancellation {
                val snapshot = learningProgressRepository.getSnapshot()
                val progress = deriveLearningProgress(snapshot)
                val topic = progress.nextPendingLesson
                val daily = if (topic == null) {
                    DailyLessonState.NoneLeft
                } else {
                    DailyLessonState.Pending(topic, learningContentRepository.getContent().lesson(topic).title)
                }
                // The streak comes from the same read: the dates of what is saved, in the zone of the device.
                daily to computeStreak(practiceMoments(snapshot), clock.instant(), clock.zone)
            }
            result.onSuccess { (daily, streak) ->
                _uiState.update { it.copy(dailyLesson = daily, streak = StreakState.Days(streak.current)) }
            }
            result.onFailure { error ->
                val failed = DailyLessonState.Failed(offline = (error as? DataAccessException)?.kind == FailureKind.Offline)
                // Offline keeps the last known streak; with none yet it says it is not available.
                _uiState.update {
                    it.copy(
                        dailyLesson = failed,
                        streak = if (it.streak is StreakState.Days) it.streak else StreakState.Unavailable,
                    )
                }
            }
        }
    }

    private suspend fun loadLevel(uid: String) {
        val level = readLevelLabel(quizResultRepository, uid)
        _uiState.update { it.copy(level = level) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                val savedStateHandle = createSavedStateHandle()
                HomeViewModel(
                    initialTab = savedStateHandle.toRoute<Home>().tab,
                    savedStateHandle = savedStateHandle,
                    authRepository = container.authRepository,
                    accountRepository = container.accountRepository,
                    avatarRepository = container.avatarRepository,
                    quizResultRepository = container.quizResultRepository,
                    learningProgressRepository = container.learningProgressRepository,
                    learningContentRepository = container.learningContentRepository,
                    sessionManager = container.deviceSessionManager,
                )
            }
        }
    }
}
