package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.FriendsRepository
import com.example.nightwatchprotocol.data.social.PairStreak
import com.example.nightwatchprotocol.data.social.PairStreakRepository
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [pairs] is null until the first read. [inviteCandidates] are the friends with no pair yet (the server also checks it).
 * [busy] are the friend codes with an action running.
 */
data class PairStreaksUiState(
    val loading: Boolean = true,
    val pairs: List<PairStreak>? = null,
    val inviteCandidates: List<Friend> = emptyList(),
    val loadError: SocialError? = null,
    val busy: Set<String> = emptySet(),
    val actionError: SocialError? = null,
)

/** "Racha en pareja" (D15): the list of pairs and invitations, and the friends that can be invited. All numbers are the server's. */
class PairStreaksViewModel(
    private val pairRepository: PairStreakRepository,
    private val friendsRepository: FriendsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairStreaksUiState())
    val uiState: StateFlow<PairStreaksUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        _uiState.update { it.copy(loading = it.pairs == null, loadError = null) }
        job = viewModelScope.launch {
            val (pairs, friends) = coroutineScope {
                val p = async { pairRepository.overview() }
                val f = async { friendsRepository.list() }
                p.await() to f.await()
            }
            _uiState.update { state ->
                when (pairs) {
                    is SocialResult.Failure -> state.copy(loading = false, loadError = pairs.error)
                    is SocialResult.Success -> {
                        val list = pairs.value.pairs
                        // A failed read of the friends only hides the invite section; it is not an error of the screen.
                        val friendList = (friends as? SocialResult.Success)?.value ?: state.inviteCandidates
                        state.copy(
                            loading = false,
                            pairs = list,
                            loadError = null,
                            inviteCandidates = friendList.filter { friend -> list.none { it.friendCode == friend.friendCode } },
                        )
                    }
                }
            }
        }
    }

    fun onAccept(friendCode: String) = act(friendCode) { pairRepository.accept(friendCode) }

    fun onDecline(friendCode: String) = act(friendCode) { pairRepository.decline(friendCode) }

    fun onCancel(friendCode: String) = act(friendCode) { pairRepository.cancel(friendCode) }

    fun onInvite(friendCode: String) = act(friendCode) { pairRepository.invite(friendCode) }

    /** Runs one action on one row and reads the list again: the server decides what exists now (it can have changed already). */
    private fun act(friendCode: String, call: suspend () -> SocialResult<Unit>) {
        if (friendCode in _uiState.value.busy) return
        _uiState.update { it.copy(busy = it.busy + friendCode, actionError = null) }
        viewModelScope.launch {
            val result = call()
            _uiState.update { it.copy(busy = it.busy - friendCode, actionError = (result as? SocialResult.Failure)?.error) }
            val changed = result is SocialResult.Success ||
                ((result as? SocialResult.Failure)?.error as? SocialError.Rejected)?.reason.let {
                    it == SocialRejection.PairGone || it == SocialRejection.PairExists
                }
            if (changed) refresh()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                PairStreaksViewModel(container.pairStreakRepository, container.friendsRepository)
            }
        }
    }
}
