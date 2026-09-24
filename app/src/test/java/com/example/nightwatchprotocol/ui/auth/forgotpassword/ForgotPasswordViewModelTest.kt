package com.example.nightwatchprotocol.ui.auth.forgotpassword

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun send_success_marksEmailSent() = runTest {
        val fakeRepo = FakeAuthRepository()
        val viewModel = ForgotPasswordViewModel(fakeRepo)
        viewModel.onEmailChanged("user@example.com")

        viewModel.onSendClicked()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmailSent)
        assertEquals(1, fakeRepo.sendPasswordResetEmailCallCount)
    }

    @Test
    fun send_userNotFound_stillMarksEmailSent_toAvoidLeakingAccountExistence() = runTest {
        val fakeRepo = FakeAuthRepository()
        fakeRepo.sendPasswordResetEmailResult = Result.failure(AuthError.UserNotFound)
        val viewModel = ForgotPasswordViewModel(fakeRepo)
        viewModel.onEmailChanged("nobody@example.com")

        viewModel.onSendClicked()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmailSent)
    }

    @Test
    fun send_invalidEmail_showsInvalidEmailErrorWithoutClaimingSent() = runTest {
        val fakeRepo = FakeAuthRepository()
        fakeRepo.sendPasswordResetEmailResult = Result.failure(AuthError.InvalidEmail)
        val viewModel = ForgotPasswordViewModel(fakeRepo)
        viewModel.onEmailChanged("not-an-email")

        viewModel.onSendClicked()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.invalidEmailError)
        assertEquals(false, viewModel.uiState.value.isEmailSent)
    }

    @Test
    fun send_networkError_showsNetworkErrorWithoutClaimingSent() = runTest {
        val fakeRepo = FakeAuthRepository()
        fakeRepo.sendPasswordResetEmailResult = Result.failure(AuthError.NetworkError)
        val viewModel = ForgotPasswordViewModel(fakeRepo)
        viewModel.onEmailChanged("user@example.com")

        viewModel.onSendClicked()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.networkError)
        assertEquals(false, viewModel.uiState.value.isEmailSent)
    }

    @Test
    fun send_blankEmail_neverCallsRepository() = runTest {
        val fakeRepo = FakeAuthRepository()
        val viewModel = ForgotPasswordViewModel(fakeRepo)

        viewModel.onSendClicked()
        advanceUntilIdle()

        assertEquals(0, fakeRepo.sendPasswordResetEmailCallCount)
    }
}
