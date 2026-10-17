package com.example.nightwatchprotocol.ui.auth.login

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.auth.GoogleNonce
import com.example.nightwatchprotocol.data.session.SessionPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        fakeAuthRepository: FakeAuthRepository = FakeAuthRepository(),
    ): Pair<LoginViewModel, FakeAuthRepository> {
        val viewModel = LoginViewModel(fakeAuthRepository, SessionPreferences.inMemory(), "test-web-client-id")
        return viewModel to fakeAuthRepository
    }

    @Test
    fun `exposes the google web client id it was given`() {
        val (viewModel, _) = createViewModel()

        assertEquals("test-web-client-id", viewModel.googleWebClientId)
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
    fun signIn_onlyUsernameBlank_flagsOnlyTheUsernameAndNeverCallsRepository() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        viewModel.onUsernameChanged("   ")
        viewModel.onPasswordChanged("Sup3r\$ecret")

        viewModel.onSignInClicked()

        assertTrue(viewModel.uiState.value.showUsernameRequired)
        assertFalse(viewModel.uiState.value.showPasswordRequired)
        assertEquals(0, fakeRepo.signInCallCount)
    }

    @Test
    fun signIn_onlyPasswordBlank_flagsOnlyThePasswordAndNeverCallsRepository() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        viewModel.onUsernameChanged("user@example.com")
        viewModel.onPasswordChanged("")

        viewModel.onSignInClicked()

        assertFalse(viewModel.uiState.value.showUsernameRequired)
        assertTrue(viewModel.uiState.value.showPasswordRequired)
        assertEquals(0, fakeRepo.signInCallCount)
    }

    @Test
    fun signIn_bothBlank_flagsBothAndNeverCallsRepository() = runTest {
        val (viewModel, fakeRepo) = createViewModel()

        viewModel.onSignInClicked()

        assertTrue(viewModel.uiState.value.showUsernameRequired)
        assertTrue(viewModel.uiState.value.showPasswordRequired)
        assertEquals(0, fakeRepo.signInCallCount)
    }

    @Test
    fun typingInOneBlankField_clearsOnlyThatFieldsError() = runTest {
        val (viewModel, _) = createViewModel()
        viewModel.onSignInClicked()

        viewModel.onUsernameChanged("user@example.com")

        assertFalse(viewModel.uiState.value.showUsernameRequired)
        assertTrue(viewModel.uiState.value.showPasswordRequired)

        viewModel.onPasswordChanged("x")

        assertFalse(viewModel.uiState.value.showPasswordRequired)
    }

    @Test
    fun signIn_withBothFieldsFilled_clearsBlankFlagsAndCallsRepository() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        viewModel.onSignInClicked()
        viewModel.onUsernameChanged("user@example.com")
        viewModel.onPasswordChanged("Sup3r\$ecret")

        viewModel.onSignInClicked()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showUsernameRequired)
        assertFalse(viewModel.uiState.value.showPasswordRequired)
        assertEquals(1, fakeRepo.signInCallCount)
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
        val viewModel = LoginViewModel(fakeRepo, SessionPreferences.inMemory(), "test-web-client-id")

        viewModel.events.test {
            viewModel.onGoogleIdTokenReceived("fake-id-token")
            assertEquals(LoginEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN), awaitItem())
        }
    }

    @Test
    fun signInWithGoogle_withANonceBackend_sendsTheRawNonceOfTheAttempt() = runTest {
        val fakeRepo = FakeAuthRepository().apply { googleUsesNonce = true }
        val viewModel = LoginViewModel(fakeRepo, SessionPreferences.inMemory(), "test-web-client-id")

        val hashed = viewModel.onGoogleSignInStarted()
        viewModel.onGoogleIdTokenReceived("fake-id-token")
        advanceUntilIdle()

        val raw = fakeRepo.signInWithGoogleNonces.single()
        assertEquals(GoogleNonce.sha256Hex(raw!!), hashed)
    }

    @Test
    fun signInWithGoogle_onFirebase_neverUsesANonce() = runTest {
        val (viewModel, fakeRepo) = createViewModel()

        assertNull(viewModel.onGoogleSignInStarted())
        viewModel.onGoogleIdTokenReceived("fake-id-token")
        advanceUntilIdle()

        assertEquals(listOf<String?>(null), fakeRepo.signInWithGoogleNonces)
    }

    @Test
    fun googleCancelled_withANonceBackend_showsNothing() = runTest {
        val fakeRepo = FakeAuthRepository().apply { googleUsesNonce = true }
        val viewModel = LoginViewModel(fakeRepo, SessionPreferences.inMemory(), "test-web-client-id")

        viewModel.events.test {
            viewModel.onGoogleSignInCancelled()
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun googleCancelled_onFirebase_keepsTheOldGenericDialog() = runTest {
        val (viewModel, _) = createViewModel()

        viewModel.events.test {
            viewModel.onGoogleSignInCancelled()
            assertEquals(LoginEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN), awaitItem())
        }
    }

    @Test
    fun signInWithGoogle_noConnection_withANonceBackend_showsTheNetworkDialog() = runTest {
        val fakeRepo = FakeAuthRepository().apply {
            googleUsesNonce = true
            signInWithGoogleResult = Result.failure(AuthError.NetworkError)
        }
        val viewModel = LoginViewModel(fakeRepo, SessionPreferences.inMemory(), "test-web-client-id")

        viewModel.events.test {
            viewModel.onGoogleIdTokenReceived("fake-id-token")
            assertEquals(LoginEvent.ShowErrorDialog(ErrorDialogKind.NETWORK), awaitItem())
        }
    }

    @Test
    fun signInWithGoogle_noConnection_onFirebase_keepsTheOldGenericDialog() = runTest {
        val fakeRepo = FakeAuthRepository().apply { signInWithGoogleResult = Result.failure(AuthError.NetworkError) }
        val viewModel = LoginViewModel(fakeRepo, SessionPreferences.inMemory(), "test-web-client-id")

        viewModel.events.test {
            viewModel.onGoogleIdTokenReceived("fake-id-token")
            assertEquals(LoginEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN), awaitItem())
        }
    }

    @Test
    fun signInWithGoogle_respectsUncheckedRememberSession() = runTest {
        val fakeRepo = FakeAuthRepository()
        val sessionPreferences = SessionPreferences.inMemory()
        val viewModel = LoginViewModel(fakeRepo, sessionPreferences, "test-web-client-id")
        viewModel.onRememberSessionChanged(false)

        viewModel.onGoogleIdTokenReceived("fake-id-token")
        advanceUntilIdle()

        assertEquals(false, sessionPreferences.rememberSessionFlow.first())
    }
}
