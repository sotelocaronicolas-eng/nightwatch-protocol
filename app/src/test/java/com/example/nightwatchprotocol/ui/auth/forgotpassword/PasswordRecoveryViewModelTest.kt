package com.example.nightwatchprotocol.ui.auth.forgotpassword

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordRecoveryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()

    private fun viewModel(prefill: String = "ana@example.com") = PasswordRecoveryViewModel(auth, SessionPreferences.inMemory(), prefill)

    @Test
    fun startsOnTheEmailStep_withTheEmailTypedOnLogin() {
        val state = viewModel(" ana@example.com ").uiState.value

        assertEquals(RecoveryStep.Email, state.step)
        assertEquals("ana@example.com", state.email)
    }

    @Test
    fun anExistingAccount_sendsTheCode_andMovesToTheCodeStep() = runTest {
        val vm = viewModel()

        vm.onSendClicked()
        advanceUntilIdle()

        assertEquals(1, auth.sendPasswordResetEmailCallCount)
        assertEquals(RecoveryStep.Code, vm.uiState.value.step)
        assertEquals("ana@example.com", vm.uiState.value.email)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun anUnknownEmail_showsCorreoNoEncontrado_andStaysOnTheEmailStep() = runTest {
        auth.sendPasswordResetEmailResult = Result.failure(AuthError.UserNotFound)
        val vm = viewModel()

        vm.onSendClicked()
        advanceUntilIdle()

        assertEquals(RecoveryEmailError.NotFound, vm.uiState.value.error)
        assertEquals("Correo no encontrado", vm.uiState.value.error?.message)
        assertEquals(RecoveryStep.Email, vm.uiState.value.step)
    }

    @Test
    fun aGoogleOnlyAccount_saysSo() = runTest {
        auth.sendPasswordResetEmailResult = Result.failure(AuthError.GoogleAccountOnly)
        val vm = viewModel()

        vm.onSendClicked()
        advanceUntilIdle()

        assertEquals(RecoveryEmailError.GoogleAccountOnly, vm.uiState.value.error)
        assertEquals(RecoveryStep.Email, vm.uiState.value.step)
    }

    @Test
    fun anImplausibleEmail_isRejectedWithoutCallingTheRepository() = runTest {
        val vm = viewModel("esto-no-es-un-correo")

        vm.onSendClicked()
        advanceUntilIdle()

        assertEquals(RecoveryEmailError.InvalidEmail, vm.uiState.value.error)
        assertEquals(0, auth.sendPasswordResetEmailCallCount)
    }

    @Test
    fun noConnection_andOtherFailures_areTold() = runTest {
        auth.sendPasswordResetEmailResult = Result.failure(AuthError.NetworkError)
        val vm = viewModel()
        vm.onSendClicked()
        advanceUntilIdle()
        assertEquals(RecoveryEmailError.Offline, vm.uiState.value.error)

        auth.sendPasswordResetEmailResult = Result.failure(AuthError.Unknown)
        vm.onSendClicked()
        advanceUntilIdle()
        assertEquals(RecoveryEmailError.Generic, vm.uiState.value.error)
    }

    @Test
    fun typingClearsTheError() = runTest {
        auth.sendPasswordResetEmailResult = Result.failure(AuthError.UserNotFound)
        val vm = viewModel()
        vm.onSendClicked()
        advanceUntilIdle()

        vm.onEmailChanged("otro@example.com")

        assertNull(vm.uiState.value.error)
    }
}
