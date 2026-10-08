package com.example.nightwatchprotocol.ui.auth.register

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val viewModel = RegisterViewModel(fakeAuthRepository, SessionPreferences.inMemory())
        return viewModel to fakeAuthRepository
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

        assert(rules.allSatisfied)
    }

    @Test
    fun passwordRules_notSatisfied_whenMissingSpecialChar() {
        val rules = PasswordRules.from("Sup3rSecret1")

        assert(!rules.allSatisfied)
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
    fun registerWithGoogle_success_navigatesToMainMenu() = runTest {
        val (viewModel, _) = createViewModel()

        viewModel.events.test {
            viewModel.onGoogleIdTokenReceived("fake-id-token")
            assertEquals(RegisterEvent.NavigateToMainMenu, awaitItem())
        }
    }

    @Test
    fun registerWithGoogle_failure_showsUnknownErrorDialog() = runTest {
        val fakeRepo = FakeAuthRepository()
        fakeRepo.signInWithGoogleResult = Result.failure(AuthError.Unknown)
        val viewModel = RegisterViewModel(fakeRepo, SessionPreferences.inMemory())

        viewModel.events.test {
            viewModel.onGoogleIdTokenReceived("fake-id-token")
            assertEquals(RegisterEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN), awaitItem())
        }
    }
}
