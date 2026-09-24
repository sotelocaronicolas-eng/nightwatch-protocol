package com.example.nightwatchprotocol.ui.postauth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PostAuthGateViewModel(
    authRepository: AuthRepository,
    quizResultRepository: QuizResultRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostAuthGateUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<PostAuthGateEvent>(Channel.BUFFERED)
    val events: Flow<PostAuthGateEvent> = _events.receiveAsFlow()

    init {
        val uid = authRepository.currentUserUid
        viewModelScope.launch {
            val event = if (uid == null) {
                PostAuthGateEvent.NavigateToMainMenu
            } else {
                val result = runCatching { quizResultRepository.getResult(uid) }.getOrNull()
                if (result != null) PostAuthGateEvent.NavigateToMainMenu else PostAuthGateEvent.NavigateToPlacementQuiz
            }
            _uiState.update { it.copy(isLoading = false) }
            _events.send(event)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                PostAuthGateViewModel(application.container.authRepository, application.container.quizResultRepository)
            }
        }
    }
}
