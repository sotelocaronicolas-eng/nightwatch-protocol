package com.example.nightwatchprotocol.ui.profile.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.BadgeCatalog
import com.example.nightwatchprotocol.data.learning.BadgeState
import com.example.nightwatchprotocol.data.learning.ProgressSummaryProvider
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AchievementsStatus {
    /** The first read is running. */
    data object Loading : AchievementsStatus

    data object Ready : AchievementsStatus

    /** The progress could not be read. [AchievementsUiState.badges] keeps the last known state, if there was one. */
    data class Failed(val offline: Boolean) : AchievementsStatus
}

data class AchievementsUiState(
    val status: AchievementsStatus = AchievementsStatus.Loading,
    /** The nine badges. Until the first read ends (or if it never does) they are drawn locked, from the catalog. */
    val badges: List<BadgeState> = BadgeCatalog.map { BadgeState(it, unlocked = false) },
    /** True once a read succeeded: only then is [unlockedCount] a real number. */
    val loaded: Boolean = false,
) {
    val unlockedCount: Int get() = badges.count { it.unlocked }
    val totalCount: Int get() = badges.size
}

/**
 * "Logros e Insignias": the real state of the nine badges, derived every time from the saved progress (nothing is stored).
 * It is read again each time the screen comes back; a failed read keeps the last known state and shows a short notice.
 * The notification switch "Logros e insignias" is not touched (it needs push).
 */
class AchievementsViewModel(
    private val progressSummaryProvider: ProgressSummaryProvider,
) : ViewModel() {

    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    /** Reads the badges. Safe to call again (on returning, or "Reintentar"): a read already running is not duplicated. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        // Set before launching, so "Reintentar" shows the loading state at once.
        _uiState.update { if (it.status is AchievementsStatus.Failed) it.copy(status = AchievementsStatus.Loading) else it }
        refreshJob = viewModelScope.launch {
            val result = runCatchingKeepCancellation { progressSummaryProvider.get() }
            result.onSuccess { summary ->
                _uiState.update { it.copy(status = AchievementsStatus.Ready, badges = summary.badges, loaded = true) }
            }
            result.onFailure { error ->
                val offline = (error as? DataAccessException)?.kind == FailureKind.Offline
                _uiState.update { it.copy(status = AchievementsStatus.Failed(offline)) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                AchievementsViewModel(application.container.progressSummaryProvider)
            }
        }
    }
}
