package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.social.FriendsRepository
import com.example.nightwatchprotocol.data.social.PersonRef
import com.example.nightwatchprotocol.data.social.REPORT_NOTE_MAX_LENGTH
import com.example.nightwatchprotocol.data.social.ReportReason
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * Reporting and blocking people (script `0011_reportes_bloqueos.sql`). The person is named by [username], which every place that
 * offers these actions has (the profile of a friend, a search result, a request, the rival of a battle) and which the server
 * accepts in place of the friend code. Until 0011 is applied the server answers `bad_action` and every call fails as
 * [SocialError.Unavailable]: the dialogs say so and nothing else breaks.
 */

// ---- Reportar ----

/**
 * The form of a report: one [reason] from a list and an optional [note] of at most [REPORT_NOTE_MAX_LENGTH] characters. [sent] means
 * the server took the report; reporting does not block the person and does not change anything else for the reporter.
 */
data class ReportUiState(
    val username: String,
    val reason: ReportReason? = null,
    val note: String = "",
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: SocialError? = null,
) {
    /** Characters typed in the note: the counter shows "37/200". */
    val noteLength: Int get() = note.length

    val canSend: Boolean get() = reason != null && !sending && !sent
}

class ReportViewModel(
    private val username: String,
    private val friendsRepository: FriendsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState(username))
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    fun onReasonSelected(reason: ReportReason) {
        _uiState.update { if (it.sending || it.sent) it else it.copy(reason = reason, error = null) }
    }

    /** The note never goes past the limit: what is typed or pasted beyond it is cut. */
    fun onNoteChanged(text: String) {
        _uiState.update { if (it.sending || it.sent) it else it.copy(note = text.take(REPORT_NOTE_MAX_LENGTH), error = null) }
    }

    fun onSend() {
        val state = _uiState.value
        val reason = state.reason ?: return
        if (!state.canSend) return
        _uiState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            when (val result = friendsRepository.report(username, reason, state.note)) {
                is SocialResult.Success -> _uiState.update { it.copy(sending = false, sent = true) }
                is SocialResult.Failure -> _uiState.update { it.copy(sending = false, error = result.error) }
            }
        }
    }

    companion object {
        fun factory(username: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                ReportViewModel(username, application.container.friendsRepository)
            }
        }
    }
}

// ---- Bloquear ----

/** [blocked] means the server blocked the person; the dialog then says so and the screen reacts when it is closed. */
data class BlockUiState(
    val username: String,
    val blocking: Boolean = false,
    val blocked: Boolean = false,
    val error: SocialError? = null,
)

/** The confirmation of "Bloquear". A second tap while the call runs is ignored, so the person is blocked once. */
class BlockViewModel(
    private val username: String,
    private val friendsRepository: FriendsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockUiState(username))
    val uiState: StateFlow<BlockUiState> = _uiState.asStateFlow()

    fun onConfirm() {
        val state = _uiState.value
        if (state.blocking || state.blocked) return
        _uiState.update { it.copy(blocking = true, error = null) }
        viewModelScope.launch {
            when (val result = friendsRepository.block(username)) {
                is SocialResult.Success -> _uiState.update { it.copy(blocking = false, blocked = true) }
                is SocialResult.Failure -> _uiState.update { it.copy(blocking = false, error = result.error) }
            }
        }
    }

    companion object {
        fun factory(username: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                BlockViewModel(username, application.container.friendsRepository)
            }
        }
    }
}

// ---- Personas bloqueadas ----

/** [people] is null until the first read; [busy] are the usernames with "Desbloquear" running. */
data class BlockedPeopleUiState(
    val loading: Boolean = true,
    val people: List<PersonRef>? = null,
    val loadError: SocialError? = null,
    val busy: Set<String> = emptySet(),
    val actionError: SocialError? = null,
)

/** "Personas bloqueadas" (reached from Amigos): the people the person blocked, each with "Desbloquear". */
class BlockedPeopleViewModel(private val friendsRepository: FriendsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockedPeopleUiState())
    val uiState: StateFlow<BlockedPeopleUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    /** Reads the list. A read already running is not duplicated; a failed one keeps the last list. */
    fun refresh() {
        if (job?.isActive == true) return
        _uiState.update { it.copy(loading = it.people == null, loadError = null) }
        job = viewModelScope.launch {
            when (val result = friendsRepository.blocks()) {
                is SocialResult.Success -> _uiState.update { it.copy(loading = false, people = result.value, loadError = null) }
                is SocialResult.Failure -> _uiState.update { it.copy(loading = false, loadError = result.error) }
            }
        }
    }

    fun onUnblock(username: String) {
        if (username in _uiState.value.busy) return
        _uiState.update { it.copy(busy = it.busy + username, actionError = null) }
        viewModelScope.launch {
            val result = friendsRepository.unblock(username)
            _uiState.update { state ->
                state.copy(
                    busy = state.busy - username,
                    people = if (result is SocialResult.Success) state.people?.filterNot { it.username == username } else state.people,
                    actionError = (result as? SocialResult.Failure)?.error,
                )
            }
            // The server says the person is not blocked (another phone lifted it first): the list was out of date, read it again.
            val notBlocked = (result as? SocialResult.Failure)?.error.let { it is SocialError.Rejected && it.reason == SocialRejection.NotFound }
            if (notBlocked) refresh()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                BlockedPeopleViewModel(application.container.friendsRepository)
            }
        }
    }
}
