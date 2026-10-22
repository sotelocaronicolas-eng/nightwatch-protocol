package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.social.FriendRelation
import com.example.nightwatchprotocol.data.social.FriendSearchHit
import com.example.nightwatchprotocol.data.social.FriendsRepository
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfileRepository
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The friend code or username is searched from 3 to 40 characters; shorter or longer is not even sent. */
private const val QUERY_MIN = 3
private const val QUERY_MAX = 40

sealed interface AddFriendNotice {
    data class Invited(val username: String) : AddFriendNotice

    data class Accepted(val username: String) : AddFriendNotice
}

data class AddFriendUiState(
    val myFriendCode: String? = null,
    val query: String = "",
    val searching: Boolean = false,
    val hit: FriendSearchHit? = null,
    val working: Boolean = false,
    val error: SocialError? = null,
    val notice: AddFriendNotice? = null,
    val pendingRequests: Int = 0,
)

/**
 * "Agregar amigos" (Figma 2446:212): look for a person by exact username or friend code and invite them, copy the own friend
 * code, and the way to the requests. There are no suggestions (decision D12) and no lists of people: the search matches exactly.
 */
class AddFriendViewModel(
    private val friendsRepository: FriendsRepository,
    private val profileRepository: SocialProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddFriendUiState())
    val uiState: StateFlow<AddFriendUiState> = _uiState.asStateFlow()

    /** The own friend code and the count of requests received. Read again each time the screen shows. */
    fun refresh() {
        viewModelScope.launch {
            (profileRepository.getMyProfile() as? SocialResult.Success)?.value?.let { profile ->
                _uiState.update { it.copy(myFriendCode = profile.friendCode) }
            }
            (friendsRepository.requests() as? SocialResult.Success)?.value?.let { requests ->
                _uiState.update { it.copy(pendingRequests = requests.received.size) }
            }
        }
    }

    fun onQueryChanged(text: String) {
        _uiState.update { it.copy(query = text.take(QUERY_MAX), hit = null, error = null, notice = null) }
    }

    fun onSearch() {
        val state = _uiState.value
        if (state.searching || state.working) return
        val query = state.query.trim()
        if (query.length !in QUERY_MIN..QUERY_MAX) {
            _uiState.update { it.copy(hit = null, notice = null, error = SocialError.InvalidInput) }
            return
        }
        _uiState.update { it.copy(searching = true, hit = null, error = null, notice = null) }
        viewModelScope.launch {
            when (val result = friendsRepository.search(query)) {
                is SocialResult.Success -> _uiState.update { it.copy(searching = false, hit = result.value) }
                is SocialResult.Failure -> _uiState.update { it.copy(searching = false, error = result.error) }
            }
        }
    }

    /** "Agregar +": sends the request to the person found. The server tells if they are already friends or there is a request. */
    fun onInvite() {
        val hit = _uiState.value.hit ?: return
        if (hit.relation != FriendRelation.None || _uiState.value.working) return
        _uiState.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            when (val result = friendsRepository.invite(hit.friendCode)) {
                is SocialResult.Success -> _uiState.update {
                    it.copy(
                        working = false,
                        hit = hit.copy(relation = FriendRelation.Sent),
                        notice = AddFriendNotice.Invited(hit.username),
                    )
                }
                is SocialResult.Failure -> _uiState.update {
                    it.copy(working = false, hit = hit.withRelationFrom(result.error), error = result.error)
                }
            }
        }
    }

    /** The person found already invited me: "Aceptar" accepts their request. */
    fun onAcceptIncoming() {
        val hit = _uiState.value.hit ?: return
        if (hit.relation != FriendRelation.Received || _uiState.value.working) return
        _uiState.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            when (val result = friendsRepository.accept(hit.friendCode)) {
                is SocialResult.Success -> _uiState.update {
                    it.copy(
                        working = false,
                        hit = hit.copy(relation = FriendRelation.Friends),
                        notice = AddFriendNotice.Accepted(hit.username),
                        pendingRequests = (it.pendingRequests - 1).coerceAtLeast(0),
                    )
                }
                is SocialResult.Failure -> _uiState.update { it.copy(working = false, error = result.error) }
            }
        }
    }

    /** What the server said already tells the real relation, so the button stops offering something that cannot be done. */
    private fun FriendSearchHit.withRelationFrom(error: SocialError): FriendSearchHit {
        val reason = (error as? SocialError.Rejected)?.reason ?: return this
        return when (reason) {
            SocialRejection.AlreadyFriends -> copy(relation = FriendRelation.Friends)
            SocialRejection.RequestExists -> copy(relation = FriendRelation.Sent)
            SocialRejection.RequestIncoming -> copy(relation = FriendRelation.Received)
            else -> this
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                AddFriendViewModel(container.friendsRepository, container.socialProfileRepository)
            }
        }
    }
}
