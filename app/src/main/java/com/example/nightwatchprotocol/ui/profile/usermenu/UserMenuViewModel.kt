package com.example.nightwatchprotocol.ui.profile.usermenu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.avatarDisplay
import com.example.nightwatchprotocol.data.profile.profileDisplayName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserMenuViewModel(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {

    private var account = accountRepository.currentProfile()

    private val _uiState = MutableStateFlow(
        UserMenuUiState(
            displayName = profileDisplayName(account?.displayName, account?.email),
            avatar = avatarDisplay(null, account?.displayName, account?.email),
        ),
    )
    val uiState: StateFlow<UserMenuUiState> = _uiState.asStateFlow()

    private val _events = Channel<UserMenuEvent>(Channel.BUFFERED)
    val events: Flow<UserMenuEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            avatarRepository.avatar.collect { bytes ->
                _uiState.update { it.copy(avatar = avatarDisplay(bytes, account?.displayName, account?.email)) }
            }
        }
        authRepository.currentUserUid?.let { uid -> viewModelScope.launch { avatarRepository.load(uid) } }
    }

    /** Re-reads the name, which may have changed on Editar Perfil while this screen stayed in the back stack. */
    fun refreshAccount() {
        account = accountRepository.currentProfile()
        _uiState.update { it.copy(displayName = profileDisplayName(account?.displayName, account?.email)) }
    }

    fun onSignOutClicked() {
        authRepository.signOut()
        avatarRepository.clear()
        viewModelScope.launch { _events.send(UserMenuEvent.SignedOut) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                UserMenuViewModel(container.authRepository, container.accountRepository, container.avatarRepository)
            }
        }
    }
}
