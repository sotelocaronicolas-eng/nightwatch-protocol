package com.example.nightwatchprotocol.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.LearningContentRepository
import com.example.nightwatchprotocol.data.learning.LearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.ui.navigation.Lesson as LessonRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One lesson. It is marked as seen when it OPENS (decision of the group for stage 3): once the lesson is shown, it is
 * saved through [LearningProgressRepository.markLessonViewed], which is idempotent (opening it again is not an error and
 * adds no row) and takes the user from the session. A failed save never blocks reading: the screen gets a short notice,
 * and there is no retry until the lesson is opened again.
 */
class LessonViewModel(
    private val topic: LearningTopic,
    private val learningContentRepository: LearningContentRepository,
    private val learningProgressRepository: LearningProgressRepository,
) : ViewModel() {

    private var loadJob: Job? = null

    // One save per opening of the lesson, even if the content has to be read again after an error.
    private var saveAttempted = false

    private val _uiState = MutableStateFlow(
        LessonUiState(moduleNumber = topic.ordinal + 1, moduleTotal = LearningTopic.entries.size),
    )
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    private val _events = Channel<LessonEvent>(Channel.BUFFERED)
    val events: Flow<LessonEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    /** Reads the lesson and, once it is on screen, saves that it was seen. Also the "Reintentar" of a read error. */
    fun load() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(status = LessonStatus.Loading) }
        loadJob = viewModelScope.launch {
            val lesson = runCatchingKeepCancellation { learningContentRepository.getContent().lesson(topic) }.getOrNull()
            if (lesson == null) {
                _uiState.update { it.copy(status = LessonStatus.Failed) }
                return@launch
            }
            _uiState.update { it.copy(status = LessonStatus.Ready(lesson)) }
            markViewedOnce()
        }
    }

    private suspend fun markViewedOnce() {
        if (saveAttempted) return
        saveAttempted = true
        // NonCancellable: leaving the lesson right away must not lose the (short) write.
        val result = withContext(NonCancellable) {
            runCatchingKeepCancellation { learningProgressRepository.markLessonViewed(topic) }
        }
        result.exceptionOrNull()?.let { error ->
            // Only a kind of failure reaches the screen, never the exception itself.
            val offline = (error as? DataAccessException)?.kind == FailureKind.Offline
            _events.send(LessonEvent.SaveFailed(offline))
        }
    }

    fun onCloseClicked() {
        viewModelScope.launch { _events.send(LessonEvent.Close) }
    }

    fun onGoToLabClicked() {
        viewModelScope.launch { _events.send(LessonEvent.OpenLab(topic)) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                LessonViewModel(
                    topic = createSavedStateHandle().toRoute<LessonRoute>().topic,
                    learningContentRepository = container.learningContentRepository,
                    learningProgressRepository = container.learningProgressRepository,
                )
            }
        }
    }
}
