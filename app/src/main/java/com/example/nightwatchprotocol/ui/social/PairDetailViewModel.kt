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
import com.example.nightwatchprotocol.data.learning.ColombiaZone
import com.example.nightwatchprotocol.data.learning.LearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.deriveLearningProgress
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.data.social.PairMetrics
import com.example.nightwatchprotocol.data.social.PairStreakRepository
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.ui.navigation.PairDetailRoute
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One day of the week strip of the detail: [shared] when both did their exercise that day; [today] gets a ring. */
data class WeekDay(val date: LocalDate, val shared: Boolean, val today: Boolean)

/** What the button "Hacer mi ejercicio de hoy" does: the next island of the person, a lesson first and then its lab. */
sealed interface TodayExercise {
    /** Not read yet, or the read failed: the button is off and says nothing. */
    data object Unknown : TodayExercise

    data class OpenLesson(val topic: LearningTopic) : TodayExercise

    data class OpenLab(val topic: LearningTopic) : TodayExercise

    /** The six islands are complete: nothing to offer (decision O5). */
    data object AllDone : TodayExercise
}

data class PairDetailUiState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val username: String = "",
    val metrics: PairMetrics? = null,
    val week: List<WeekDay> = emptyList(),
    val exercise: TodayExercise = TodayExercise.Unknown,
    val loadError: SocialError? = null,
    val confirmingEnd: Boolean = false,
    val ending: Boolean = false,
    val actionError: SocialError? = null,
)

sealed interface PairDetailEvent {
    /** The pair is gone (ended by the person, or it did not exist any more): the screen goes back to the list. */
    data object Gone : PairDetailEvent
}

/**
 * The detail of one pair (the frame "Racha grupal" with ONE person in "hoy", decision D15). The numbers come from the server;
 * the week strip only places the shared days on the calendar of Colombia, and the button comes from the person's own progress.
 */
class PairDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val pairRepository: PairStreakRepository,
    private val progressRepository: LearningProgressRepository,
    private val clock: Clock = Clock.system(ColombiaZone),
) : ViewModel() {

    val friendCode: String = savedStateHandle.get<String>(KEY_FRIEND_CODE).orEmpty()

    private val _uiState = MutableStateFlow(PairDetailUiState())
    val uiState: StateFlow<PairDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<PairDetailEvent>(Channel.BUFFERED)
    val events: Flow<PairDetailEvent> = _events.receiveAsFlow()

    fun refresh() {
        _uiState.update { it.copy(loading = !it.loaded, loadError = null) }
        viewModelScope.launch {
            when (val result = pairRepository.detail(friendCode)) {
                is SocialResult.Failure -> _uiState.update { it.copy(loading = false, loadError = result.error) }
                is SocialResult.Success -> {
                    val pair = result.value
                    // A pair that is only an invitation has no numbers: it belongs to the list, not here.
                    val metrics = pair.metrics
                    if (metrics == null) {
                        _events.send(PairDetailEvent.Gone)
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loaded = true,
                            username = pair.username,
                            metrics = metrics,
                            week = weekOf(metrics),
                            loadError = null,
                        )
                    }
                    loadExercise()
                }
            }
        }
    }

    private fun weekOf(metrics: PairMetrics): List<WeekDay> {
        val today = LocalDate.now(clock)
        val monday = today.with(DayOfWeek.MONDAY)
        return (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            WeekDay(date, shared = date in metrics.sharedDays, today = date == today)
        }
    }

    private suspend fun loadExercise() {
        val result = runCatchingKeepCancellation {
            val island = deriveLearningProgress(progressRepository.getSnapshot()).islands
                .firstOrNull { it.isUnlocked && !it.isCompleted }
            when {
                island == null -> TodayExercise.AllDone
                !island.lessonViewed -> TodayExercise.OpenLesson(island.topic)
                else -> TodayExercise.OpenLab(island.topic)
            }
        }
        _uiState.update { it.copy(exercise = result.getOrDefault(TodayExercise.Unknown)) }
    }

    fun onEndClicked() {
        _uiState.update { it.copy(confirmingEnd = true, actionError = null) }
    }

    fun onEndDismissed() {
        if (_uiState.value.ending) return
        _uiState.update { it.copy(confirmingEnd = false) }
    }

    fun onEndConfirmed() {
        if (_uiState.value.ending) return
        _uiState.update { it.copy(ending = true, confirmingEnd = false, actionError = null) }
        viewModelScope.launch {
            when (val result = pairRepository.end(friendCode)) {
                is SocialResult.Success -> {
                    _uiState.update { it.copy(ending = false) }
                    _events.send(PairDetailEvent.Gone)
                }
                is SocialResult.Failure -> _uiState.update { it.copy(ending = false, actionError = result.error) }
            }
        }
    }

    companion object {
        const val KEY_FRIEND_CODE = "friendCode"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                val handle = createSavedStateHandle()
                handle[KEY_FRIEND_CODE] = handle.toRoute<PairDetailRoute>().friendCode
                PairDetailViewModel(handle, container.pairStreakRepository, container.learningProgressRepository)
            }
        }
    }
}
