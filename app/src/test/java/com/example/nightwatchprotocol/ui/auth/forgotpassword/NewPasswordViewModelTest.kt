package com.example.nightwatchprotocol.ui.auth.forgotpassword

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewPasswordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private val auth = FakeAuthRepository()
    private val preferences = SessionPreferences.inMemory()
    private fun viewModel() = NewPasswordViewModel(account, auth, preferences)

    private fun NewPasswordViewModel.fill(password: String = "Nueva\$Clave1", confirm: String = password) {
        onNewPasswordChanged(password)
        onConfirmPasswordChanged(confirm)
    }

    @Test
    fun saveIsEnabledOnlyWithAValidPassword_andAMatchingConfirmation() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.canSubmit)

        vm.fill("debil")
        assertFalse(vm.uiState.value.canSubmit)

        vm.fill("Nueva\$Clave1", "otra cosa")
        assertFalse(vm.uiState.value.canSubmit)
        assertTrue(vm.uiState.value.confirmationMismatch)

        vm.fill("Nueva\$Clave1")
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun savingSetsThePassword_signsOut_andEmitsPasswordReset() = runTest {
        val vm = viewModel()
        vm.fill()

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(NewPasswordEvent.PasswordReset, awaitItem())
        }
        assertEquals(listOf("Nueva\$Clave1"), account.updatePasswordCalls)
        assertEquals(1, auth.signOutCallCount)
        assertEquals("", vm.uiState.value.newPassword)
    }

    @Test
    fun aWeakPassword_neverReachesTheServer() = runTest {
        val vm = viewModel()
        vm.fill("debil")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertTrue(account.updatePasswordCalls.isEmpty())
        assertEquals(0, auth.signOutCallCount)
        assertEquals("La contraseña no cumple con los requerimientos", vm.uiState.value.newError)
    }

    @Test
    fun aMismatch_neverReachesTheServer() = runTest {
        val vm = viewModel()
        vm.fill("Nueva\$Clave1", "Nueva\$Clave2")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertTrue(account.updatePasswordCalls.isEmpty())
        assertEquals("Las contraseñas no coinciden", vm.uiState.value.confirmError)
    }

    @Test
    fun whenTheServerRefuses_theUserStaysSignedInToRetry_andSeesTheError() = runTest {
        account.updatePasswordResults = mutableListOf(Result.failure(AuthError.WeakPassword))
        val vm = viewModel()
        vm.fill()

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("La contraseña no cumple con los requerimientos", vm.uiState.value.newError)
        assertEquals(0, auth.signOutCallCount)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun noConnection_isTold_andNothingIsSignedOut() = runTest {
        account.updatePasswordResults = mutableListOf(Result.failure(AuthError.NetworkError))
        val vm = viewModel()
        vm.fill()

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Sin conexión a internet", vm.uiState.value.formError)
        assertEquals(0, auth.signOutCallCount)
    }

    @Test
    fun leavingWithoutSaving_closesTheRecoverySession() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onCancelClicked()
            assertEquals(NewPasswordEvent.Cancelled, awaitItem())
        }
        assertEquals(1, auth.signOutCallCount)
        assertTrue(account.updatePasswordCalls.isEmpty())
        assertNull(vm.uiState.value.formError)
    }

    // ---- the "recovery in progress" mark ----

    @Test
    fun savingThePassword_clearsTheRecoveryMark() = runTest {
        preferences.setRecoveryInProgress(true)
        val vm = viewModel()
        vm.fill()

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(NewPasswordEvent.PasswordReset, awaitItem())
        }

        assertFalse(preferences.isRecoveryInProgress())
    }

    @Test
    fun leavingWithoutSaving_clearsTheRecoveryMark() = runTest {
        preferences.setRecoveryInProgress(true)
        val vm = viewModel()

        vm.events.test {
            vm.onCancelClicked()
            assertEquals(NewPasswordEvent.Cancelled, awaitItem())
        }

        assertFalse(preferences.isRecoveryInProgress())
    }

    @Test
    fun aFailedSave_keepsTheRecoveryMark_becauseTheSessionIsStillOpen() = runTest {
        preferences.setRecoveryInProgress(true)
        account.updatePasswordResults = mutableListOf(Result.failure(AuthError.NetworkError))
        val vm = viewModel()
        vm.fill()

        vm.onSaveClicked()
        advanceUntilIdle()

        assertTrue(preferences.isRecoveryInProgress())
    }
}
