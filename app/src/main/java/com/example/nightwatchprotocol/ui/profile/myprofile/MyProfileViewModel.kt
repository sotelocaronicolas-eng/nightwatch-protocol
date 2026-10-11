package com.example.nightwatchprotocol.ui.profile.myprofile

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
import com.example.nightwatchprotocol.data.profile.LEVEL_NOT_ASSESSED
import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.profile.levelLabel
import com.example.nightwatchprotocol.data.profile.memberSinceLabel
import com.example.nightwatchprotocol.data.profile.profileDisplayName
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val READ_TIMEOUT_MILLIS = 5_000L

class MyProfileViewModel(
    authRepository: AuthRepository,
    accountRepository: AccountRepository,
    private val quizResultRepository: QuizResultRepository,
    private val profileRepository: ProfileRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {

    private val uid = authRepository.currentUserUid
    private val account = accountRepository.currentProfile()

    private val _uiState = MutableStateFlow(
        MyProfileUiState(
            displayName = profileDisplayName(account?.displayName, account?.email),
            email = account?.email.orEmpty(),
            memberSince = memberSinceLabel(account?.creationTimeMillis),
            avatar = avatarDisplay(null, account?.displayName, account?.email),
        ),
    )
    val uiState: StateFlow<MyProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            avatarRepository.avatar.collect { bytes ->
                _uiState.update { it.copy(avatar = avatarDisplay(bytes, account?.displayName, account?.email)) }
            }
        }
        if (uid == null) {
            _uiState.update { it.copy(level = LEVEL_NOT_ASSESSED) }
        } else {
            viewModelScope.launch { avatarRepository.load(uid) }
            viewModelScope.launch { loadLevel(uid) }
            viewModelScope.launch { loadRecoveryEmail(uid) }
        }
    }

    // A failed or slow read is shown as "not assessed" rather than as an error: this screen is informational.
    private suspend fun loadLevel(uid: String) {
        val result = withTimeoutOrNull(READ_TIMEOUT_MILLIS) {
            runCatchingKeepCancellation { quizResultRepository.getResult(uid) }.getOrNull()
        }
        _uiState.update { it.copy(level = levelLabel(result)) }
    }

    private suspend fun loadRecoveryEmail(uid: String) {
        val profile = withTimeoutOrNull(READ_TIMEOUT_MILLIS) {
            runCatchingKeepCancellation { profileRepository.getProfile(uid) }.getOrNull()
        }
        val recovery = profile?.recoveryEmail?.trim()?.takeIf { it.isNotEmpty() }
        _uiState.update { it.copy(recoveryEmail = recovery) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                MyProfileViewModel(
                    container.authRepository,
                    container.accountRepository,
                    container.quizResultRepository,
                    container.profileRepository,
                    container.avatarRepository,
                )
            }
        }
    }
}
