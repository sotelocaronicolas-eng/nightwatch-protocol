package com.example.nightwatchprotocol.ui.auth.register

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.auth.GoogleNonce
import com.example.nightwatchprotocol.data.session.SessionPreferences
import com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        fakeAuthRepository: FakeAuthRepository = FakeAuthRepository(),
    ): Pair<RegisterViewModel, FakeAuthRepository> {
        val viewModel = RegisterViewModel(fakeAuthRepository, SessionPreferences.inMemory(), "test-web-client-id")
        return viewModel to fakeAuthRepository
    }

    @Test
    fun `exposes the google web client id it was given`() {
        val (viewModel, _) = createViewModel()

        assertEquals("test-web-client-id", viewModel.googleWebClientId)
    }

    private fun RegisterViewModel.fillValidForm() {
        onFullNameChanged("Nicolás Sotelo")
        onEmailChanged("nicolas@example.com")
        onPasswordChanged("Sup3r\$ecret!")
        onConfirmPasswordChanged("Sup3r\$ecret!")
    }

    @Test
    fun register_success_navigatesToMainMenu() = runTest {
        val (viewModel, _) = createViewModel()
        viewModel.fillValidForm()

        viewModel.events.test {
            viewModel.onRegisterClicked()
            assertEquals(RegisterEvent.NavigateToMainMenu, awaitItem())
        }
    }

    @Test
    fun register_blankFields_neverCallsRepository() = runTest {
        val (viewModel, fakeRepo) = createViewModel()

        viewModel.onRegisterClicked()

        assertEquals(0, fakeRepo.registerCallCount)
    }

    @Test
    fun register_whitespaceOnlyFields_neverCallsRepository() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        viewModel.onFullNameChanged("   ")
        viewModel.onEmailChanged("   ")
        viewModel.onPasswordChanged("Sup3r\$ecret!")
        viewModel.onConfirmPasswordChanged("Sup3r\$ecret!")

        viewModel.onRegisterClicked()

        assertEquals(0, fakeRepo.registerCallCount)
    }

    @Test
    fun register_passwordMismatch_showsPasswordMismatchWithoutCallingRepository() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        viewModel.fillValidForm()
        viewModel.onConfirmPasswordChanged("Different1!")

        viewModel.onRegisterClicked()

        assertEquals(RegisterFieldError.PasswordMismatch, viewModel.uiState.value.fieldError)
        assertEquals(0, fakeRepo.registerCallCount)
    }

    @Test
    fun register_emailAlreadyInUse_showsEmailAlreadyInUseError() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.registerResult = Result.failure(AuthError.EmailAlreadyInUse)
        viewModel.fillValidForm()

        viewModel.onRegisterClicked()
        advanceUntilIdle()

        assertEquals(RegisterFieldError.EmailAlreadyInUse, viewModel.uiState.value.fieldError)
    }

    @Test
    fun register_emailRegisteredWithGoogle_showsItsOwnFieldError() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.registerResult = Result.failure(AuthError.GoogleAccountOnly)
        viewModel.fillValidForm()

        viewModel.onRegisterClicked()
        advanceUntilIdle()

        assertEquals(RegisterFieldError.EmailRegisteredWithGoogle, viewModel.uiState.value.fieldError)
    }

    @Test
    fun register_invalidEmail_showsInvalidEmailError() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.registerResult = Result.failure(AuthError.InvalidEmail)
        viewModel.fillValidForm()

        viewModel.onRegisterClicked()
        advanceUntilIdle()

        assertEquals(RegisterFieldError.InvalidEmail, viewModel.uiState.value.fieldError)
    }

    @Test
    fun register_backendRejectsPasswordPolicy_showsWeakPasswordError() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.registerResult = Result.failure(AuthError.WeakPassword)
        viewModel.fillValidForm()

        viewModel.onRegisterClicked()
        advanceUntilIdle()

        assertEquals(RegisterFieldError.WeakPassword, viewModel.uiState.value.fieldError)
    }

    @Test
    fun register_networkError_showsNetworkErrorDialog() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        fakeRepo.registerResult = Result.failure(AuthError.NetworkError)
        viewModel.fillValidForm()

        viewModel.events.test {
            viewModel.onRegisterClicked()
            assertEquals(RegisterEvent.ShowErrorDialog(ErrorDialogKind.NETWORK), awaitItem())
        }
    }

    @Test
    fun register_secondClickWhileLoading_isIgnored() = runTest {
        val (viewModel, fakeRepo) = createViewModel()
        viewModel.fillValidForm()

        viewModel.onRegisterClicked()
        viewModel.onRegisterClicked()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.registerCallCount)
    }

    @Test
    fun passwordRules_allSatisfied_whenAllThreeRulesPass() {
        val rules = PasswordRules.from("Sup3r\$ecret!")

        assertTrue(rules.allSatisfied)
    }

    @Test
    fun passwordRules_notSatisfied_whenMissingSpecialChar() {
        val rules = PasswordRules.from("Sup3rSecret1")

        assertFalse(rules.allSatisfied)
    }

    @Test
    fun passwordRules_notSatisfied_whenThereIsNoLowercaseLetter() {
        val rules = PasswordRules.from("SUP3R\$ECRET!")

        assertFalse(rules.hasLowercase)
        assertFalse(rules.allSatisfied)
    }

    @Test
    fun passwordRules_lowercaseRule_isAsciiOnly_andOneLetterIsEnough() {
        assertFalse(PasswordRules.from("ÑANDÚ-1Ñ!ñé").hasLowercase) // ñ and é are not a-z
        assertTrue(PasswordRules.from("SUP3R\$ECRETa").hasLowercase)
    }

    @Test
    fun registerButton_staysDisabled_whenThePasswordHasNoLowercaseLetter() {
        val (viewModel, _) = createViewModel()
        viewModel.fillValidForm()
        viewModel.onPasswordChanged("SUP3R\$ECRET!")
        viewModel.onConfirmPasswordChanged("SUP3R\$ECRET!")

        assertFalse(viewModel.uiState.value.isRegisterEnabled)

        viewModel.onPasswordChanged("Sup3r\$ecret!")
        viewModel.onConfirmPasswordChanged("Sup3r\$ecret!")

        assertTrue(viewModel.uiState.value.isRegisterEnabled)
    }

    @Test
    fun passwordRules_everyFirebaseSpecialCharacterCounts() {
        for (special in PasswordRules.FIREBASE_SPECIAL_CHARACTERS) {
            assertTrue("'$special' should count as special", PasswordRules.from("Passw0rd$special").hasSpecialChar)
        }
    }

    @Test
    fun passwordRules_charactersFirebaseDoesNotCountAsSpecial_areRejectedLocally() {
        // !isLetterOrDigit() would accept all of these, but Firebase would not.
        for (notSpecial in listOf(" ", "ñ", "é", "€", "¿", "¡")) {
            assertFalse("'$notSpecial' must not count as special", PasswordRules.from("Passw0rd$notSpecial").hasSpecialChar)
        }
    }

    @Test
    fun passwordRules_nonAsciiUppercaseOrDigit_doesNotSatisfyTheUppercaseAndDigitRule() {
        assertFalse(PasswordRules.from("Éxito-ñandú1").hasUpperCaseAndDigit)
        assertFalse(PasswordRules.from("Passw٣rd!").hasUpperCaseAndDigit)
        assertTrue(PasswordRules.from("Passw0rd!").hasUpperCaseAndDigit)
    }

    @Test
    fun register_withoutASession_isNotAnError_andGoesToTheCodeScreenWithTheEmail() = runTest {
        val fakeRepo = FakeAuthRepository().apply {
            registerResult = Result.failure(AuthError.EmailConfirmationRequired)
        }
        val sessionPreferences = SessionPreferences.inMemory()
        val viewModel = RegisterViewModel(fakeRepo, sessionPreferences, "test-web-client-id")
        viewModel.fillValidForm()

        viewModel.events.test {
            viewModel.onRegisterClicked()
            assertEquals(RegisterEvent.NavigateToVerifyCode("nicolas@example.com"), awaitItem())
        }
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.fieldError)
        assertTrue(sessionPreferences.rememberSessionFlow.first())
    }

    @Test
    fun registerWithGoogle_success_navigatesToMainMenu() = runTest {
        val (viewModel, _) = createViewModel()

        viewModel.events.test {
            viewModel.onGoogleIdTokenReceived("fake-id-token")
            assertEquals(RegisterEvent.NavigateToMainMenu, awaitItem())
        }
    }

    @Test
    fun registerWithGoogle_withANonceBackend_sendsTheRawNonceOfTheAttempt() = runTest {
        val fakeRepo = FakeAuthRepository().apply { googleUsesNonce = true }
        val viewModel = RegisterViewModel(fakeRepo, SessionPreferences.inMemory(), "test-web-client-id")

        val hashed = viewModel.onGoogleSignInStarted()
        viewModel.onGoogleIdTokenReceived("fake-id-token")
        advanceUntilIdle()

        assertEquals(GoogleNonce.sha256Hex(fakeRepo.signInWithGoogleNonces.single()!!), hashed)
    }

    @Test
    fun googleCancelled_withANonceBackend_showsNothing_andOnFirebaseKeepsTheDialog() = runTest {
        val nonceRepo = FakeAuthRepository().apply { googleUsesNonce = true }
        val quiet = RegisterViewModel(nonceRepo, SessionPreferences.inMemory(), "test-web-client-id")
        quiet.events.test {
            quiet.onGoogleSignInCancelled()
            advanceUntilIdle()
            expectNoEvents()
        }

        val (firebase, _) = createViewModel()
        firebase.events.test {
            firebase.onGoogleSignInCancelled()
            assertEquals(RegisterEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN), awaitItem())
        }
    }

    @Test
    fun registerWithGoogle_phoneWithoutAGoogleAccount_showsTheNoAccountDialog() = runTest {
        val (viewModel, _) = createViewModel()

        viewModel.events.test {
            viewModel.onGoogleNoAccount()
            assertEquals(RegisterEvent.ShowErrorDialog(ErrorDialogKind.NO_GOOGLE_ACCOUNT), awaitItem())
        }
    }

    @Test
    fun registerWithGoogle_noConnection_withANonceBackend_showsTheNetworkDialog() = runTest {
        val fakeRepo = FakeAuthRepository().apply {
            googleUsesNonce = true
            signInWithGoogleResult = Result.failure(AuthError.NetworkError)
        }
        val viewModel = RegisterViewModel(fakeRepo, SessionPreferences.inMemory(), "test-web-client-id")

        viewModel.events.test {
            viewModel.onGoogleIdTokenReceived("fake-id-token")
            assertEquals(RegisterEvent.ShowErrorDialog(ErrorDialogKind.NETWORK), awaitItem())
        }
    }

    @Test
    fun registerWithGoogle_failure_showsUnknownErrorDialog() = runTest {
        val fakeRepo = FakeAuthRepository()
        fakeRepo.signInWithGoogleResult = Result.failure(AuthError.Unknown)
        val viewModel = RegisterViewModel(fakeRepo, SessionPreferences.inMemory(), "test-web-client-id")

        viewModel.events.test {
            viewModel.onGoogleIdTokenReceived("fake-id-token")
            assertEquals(RegisterEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN), awaitItem())
        }
    }
}
