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
}
