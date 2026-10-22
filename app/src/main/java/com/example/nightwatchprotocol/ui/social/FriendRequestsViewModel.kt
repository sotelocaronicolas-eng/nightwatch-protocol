package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.social.FriendRequests
import com.example.nightwatchprotocol.data.social.FriendsRepository
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [busy] are the friend codes with an action running (a second tap on the same row is ignored). */
data class FriendRequestsUiState(
    val loading: Boolean = true,
    val requests: FriendRequests? = null,
    val loadError: SocialError? = null,
    val busy: Set<String> = emptySet(),
    val actionError: SocialError? = null,
)

/** "Solicitudes" (Figma 2446:242): the requests received (accept, decline) and sent (cancel). No suggestions (D12). */
class FriendRequestsViewModel(private val friendsRepository: FriendsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendRequestsUiState())
    val uiState: StateFlow<FriendRequestsUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        _uiState.update { it.copy(loading = it.requests == null, loadError = null) }
        job = viewModelScope.launch {
            when (val result = friendsRepository.requests()) {
                is SocialResult.Success -> _uiState.update { it.copy(loading = false, requests = result.value, loadError = null) }
                is SocialResult.Failure -> _uiState.update { it.copy(loading = false, loadError = result.error) }
            }
        }
    }

    fun onAccept(friendCode: String) = act(friendCode) { friendsRepository.accept(friendCode) }

    fun onDecline(friendCode: String) = act(friendCode) { friendsRepository.decline(friendCode) }

    fun onCancel(friendCode: String) = act(friendCode) { friendsRepository.cancel(friendCode) }

    /**
     * Runs one action on one row. On success the row leaves the list; if the server says the request is already gone (the other
     * person cancelled or answered first) the list is read again, so the screen shows what is true now.
     */
    private fun act(friendCode: String, call: suspend () -> SocialResult<Unit>) {
        if (friendCode in _uiState.value.busy) return
        _uiState.update { it.copy(busy = it.busy + friendCode, actionError = null) }
        viewModelScope.launch {
            val result = call()
            _uiState.update { state ->
                val requests = state.requests
                val next = when {
                    result is SocialResult.Success && requests != null -> FriendRequests(
                        received = requests.received.filterNot { it.friendCode == friendCode },
                        sent = requests.sent.filterNot { it.friendCode == friendCode },
                    )
                    else -> requests
                }
                state.copy(busy = state.busy - friendCode, requests = next, actionError = (result as? SocialResult.Failure)?.error)
            }
            val gone = (result as? SocialResult.Failure)?.error.let { it is SocialError.Rejected && it.reason == SocialRejection.RequestGone }
            if (gone) refresh()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                FriendRequestsViewModel(application.container.friendsRepository)
            }
        }
    }
}
