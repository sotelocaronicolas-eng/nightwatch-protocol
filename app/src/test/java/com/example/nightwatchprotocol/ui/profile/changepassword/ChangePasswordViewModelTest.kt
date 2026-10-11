package com.example.nightwatchprotocol.ui.profile.changepassword

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.AccountProfile
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
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
class ChangePasswordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private fun viewModel() = ChangePasswordViewModel(account)

    private val strong = "Nueva1clave!"

    private fun ChangePasswordViewModel.fill(current: String = "Actual1!", new: String = strong, confirm: String = new) {
        onCurrentPasswordChanged(current)
        onNewPasswordChanged(new)
        onConfirmPasswordChanged(confirm)
    }

    @Test
    fun validChange_callsChangePasswordAndEmitsSuccess() = runTest {
        val vm = viewModel()
        vm.fill()

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(ChangePasswordEvent.PasswordChanged, awaitItem())
        }

        assertEquals(listOf("Actual1!" to strong), account.changePasswordCalls)
        assertEquals("", vm.uiState.value.newPassword)
    }

    @Test
    fun emptyCurrentPassword_showsAnErrorAndDoesNotCallFirebase() = runTest {
        val vm = viewModel()
        vm.fill(current = "")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Ingresa tu contraseña actual", vm.uiState.value.currentError)
        assertTrue(account.changePasswordCalls.isEmpty())
    }

    @Test
    fun newPasswordThatBreaksTheRules_isRejectedLocally() = runTest {
        val vm = viewModel()
        vm.fill(new = "corta", confirm = "corta")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("La contraseña no cumple con los requerimientos", vm.uiState.value.newError)
        assertTrue(account.changePasswordCalls.isEmpty())
    }

    @Test
    fun passwordsThatDoNotMatch_showTheMismatchError_withoutCallingFirebase() = runTest {
        val vm = viewModel()
        vm.fill(confirm = "Otra1clave!")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Las contraseñas no coinciden", vm.uiState.value.confirmError)
        assertNull(vm.uiState.value.currentError)
        assertTrue(account.changePasswordCalls.isEmpty())
    }

    @Test
    fun wrongCurrentPassword_showsTheCurrentPasswordError() = runTest {
        account.reauthenticateResult = Result.failure(AuthError.WrongPassword)
        val vm = viewModel()
        vm.fill()

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("La contraseña actual no es correcta", vm.uiState.value.currentError)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun invalidCredentials_isTreatedAsAWrongCurrentPassword() = runTest {
        account.reauthenticateResult = Result.failure(AuthError.InvalidCredentials)
        val vm = viewModel()
        vm.fill()

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("La contraseña actual no es correcta", vm.uiState.value.currentError)
    }

    @Test
    fun firebaseRejectsTheNewPassword_showsTheRulesErrorOnTheNewField() = runTest {
        account.updatePasswordResults = mutableListOf(Result.failure(AuthError.WeakPassword))
        val vm = viewModel()
        vm.fill()

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("La contraseña no cumple con los requerimientos", vm.uiState.value.newError)
    }

    @Test
    fun noConnection_showsAFormLevelMessage() = runTest {
        account.reauthenticateResult = Result.failure(AuthError.NetworkError)
        val vm = viewModel()
        vm.fill()

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Sin conexión a internet", vm.uiState.value.formError)
    }

    @Test
    fun editingAField_clearsItsError() = runTest {
        val vm = viewModel()
        vm.fill(confirm = "Otra1clave!")
        vm.onSaveClicked()
        advanceUntilIdle()

        vm.onConfirmPasswordChanged(strong)

        assertNull(vm.uiState.value.confirmError)
    }

    @Test
    fun googleAccount_hasNoFormAndNeverCallsFirebase() = runTest {
        account.profile = AccountProfile("Nicolás", "g@example.com", null, hasPassword = false)
        val vm = viewModel()
        vm.fill()

        vm.onSaveClicked()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasPassword)
        assertTrue(account.changePasswordCalls.isEmpty())
    }
}
