package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.FriendsRepository
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [friends] is null until the first read; a read that fails keeps the last list and sets [error]. */
data class FriendsUiState(
    val loading: Boolean = true,
    val friends: List<Friend>? = null,
    val error: SocialError? = null,
) {
    /** "3 mantienen la racha hoy": the friends who practiced today (Figma 2446:181). */
    val keepingToday: Int get() = friends.orEmpty().count { it.trainedToday }
}

/** "Amigos" (Figma 2446:181): the list of friends with their personal streak. The server calculates the streak. */
class FriendsViewModel(private val friendsRepository: FriendsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    /** Reads the list. Safe to call again (on returning to the screen or "Reintentar"): a read already running is not duplicated. */
    fun refresh() {
        if (job?.isActive == true) return
        _uiState.update { it.copy(loading = it.friends == null, error = null) }
        job = viewModelScope.launch {
            when (val result = friendsRepository.list()) {
                is SocialResult.Success -> _uiState.update { FriendsUiState(loading = false, friends = result.value) }
                is SocialResult.Failure -> _uiState.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                FriendsViewModel(application.container.friendsRepository)
            }
        }
    }
}
