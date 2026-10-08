package com.example.nightwatchprotocol.ui.auth.login

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        fakeAuthRepository: FakeAuthRepository = FakeAuthRepository(),
    ): Pair<LoginViewModel, FakeAuthRepository> {
        val viewModel = LoginViewModel(fakeAuthRepository, SessionPreferences.inMemory())
        return viewModel to fakeAuthRepository
    }

    @Test
    fun signIn_success_navigatesToMainMenu() = runTest {
        val (viewModel, _) = createViewModel()
        viewModel.onUsernameChanged("user@example.com")
        viewModel.onPasswordChanged("Sup3r\$ecret")

        viewModel.events.test {
            viewModel.onSignInClicked()
            assertEquals(LoginEvent.NavigateToMainMenu, awaitItem())
        }
    }

    @Test
    fun signIn_blankFields_showsBlankFieldsErrorAndNeverCallsRepository() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        viewModel.onUsernameChanged("   ")
        viewModel.onPasswordChanged("")

        viewModel.onSignInClicked()

        assertEquals(LoginFieldError.BlankFields, viewModel.uiState.value.fieldError)
        assertEquals(0, fakeRepo.signInCallCount)
    }

    @Test
    fun signIn_wrongPassword_showsWrongPasswordFieldError() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.signInResult = Result.failure(AuthError.WrongPassword)
        viewModel.onUsernameChanged("user@example.com")
        viewModel.onPasswordChanged("wrong")

        viewModel.onSignInClicked()
        advanceUntilIdle()

        assertEquals(LoginFieldError.WrongPassword, viewModel.uiState.value.fieldError)
    }

    @Test
    fun signIn_userNotFound_showsUserNotFoundFieldError_notWrongPassword() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.signInResult = Result.failure(AuthError.UserNotFound)
        viewModel.onUsernameChanged("nobody@example.com")
        viewModel.onPasswordChanged("Sup3r\$ecret")

        viewModel.onSignInClicked()
        advanceUntilIdle()

        assertEquals(LoginFieldError.UserNotFound, viewModel.uiState.value.fieldError)
    }

    @Test
    fun signIn_invalidCredentials_showsCombinedErrorBecauseBackendDoesNotSayWhichHalfWasWrong() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.signInResult = Result.failure(AuthError.InvalidCredentials)
        viewModel.onUsernameChanged("user@example.com")
        viewModel.onPasswordChanged("Sup3r\$ecret")

        viewModel.onSignInClicked()
        advanceUntilIdle()

        assertEquals(LoginFieldError.InvalidCredentials, viewModel.uiState.value.fieldError)
    }

    @Test
    fun signIn_googleOnlyAccount_showsGoogleAccountOnlyFieldError() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.signInResult = Result.failure(AuthError.GoogleAccountOnly)
        viewModel.onUsernameChanged("user@gmail.com")
        viewModel.onPasswordChanged("Sup3r\$ecret")

        viewModel.onSignInClicked()
        advanceUntilIdle()

        assertEquals(LoginFieldError.GoogleAccountOnly, viewModel.uiState.value.fieldError)
    }

    @Test
    fun signIn_invalidEmail_showsInvalidEmailFieldError() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.signInResult = Result.failure(AuthError.InvalidEmail)
        viewModel.onUsernameChanged("not-an-email")
        viewModel.onPasswordChanged("Sup3r\$ecret")

        viewModel.onSignInClicked()
        advanceUntilIdle()

        assertEquals(LoginFieldError.InvalidEmail, viewModel.uiState.value.fieldError)
    }

    @Test
    fun signIn_networkError_showsNetworkErrorDialogNotFieldError() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.signInResult = Result.failure(AuthError.NetworkError)
        viewModel.onUsernameChanged("user@example.com")
        viewModel.onPasswordChanged("Sup3r\$ecret")

        viewModel.events.test {
            viewModel.onSignInClicked()
            assertEquals(LoginEvent.ShowErrorDialog(ErrorDialogKind.NETWORK), awaitItem())
        }
        assertEquals(null, viewModel.uiState.value.fieldError)
    }

    @Test
    fun signIn_secondClickWhileLoading_isIgnored() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        viewModel.onUsernameChanged("user@example.com")
        viewModel.onPasswordChanged("Sup3r\$ecret")

        viewModel.onSignInClicked()
        viewModel.onSignInClicked()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.signInCallCount)
    }

    @Test
    fun signInWithGoogle_success_navigatesToMainMenu() = runTest {
        val (viewModel, _) = createViewModel()

        viewModel.events.test {
            viewModel.onGoogleIdTokenReceived("fake-id-token")
            assertEquals(LoginEvent.NavigateToMainMenu, awaitItem())
        }
    }

    @Test
    fun signInWithGoogle_failure_showsUnknownErrorDialog() = runTest {
        val fakeRepo = FakeAuthRepository()
        fakeRepo.signInWithGoogleResult = Result.failure(AuthError.Unknown)
        val viewModel = LoginViewModel(fakeRepo, SessionPreferences.inMemory())

        viewModel.events.test {
            viewModel.onGoogleIdTokenReceived("fake-id-token")
            assertEquals(LoginEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN), awaitItem())
        }
    }

    @Test
    fun signInWithGoogle_respectsUncheckedRememberSession() = runTest {
        val fakeRepo = FakeAuthRepository()
        val sessionPreferences = SessionPreferences.inMemory()
        val viewModel = LoginViewModel(fakeRepo, sessionPreferences)
        viewModel.onRememberSessionChanged(false)

        viewModel.onGoogleIdTokenReceived("fake-id-token")
        advanceUntilIdle()

        assertEquals(false, sessionPreferences.rememberSessionFlow.first())
    }
}
