package com.example.nightwatchprotocol.ui.profile.changepassword

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.AccountProfile
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.ui.auth.register.PasswordRules
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

    // ---- Same standards as the sign-up screen (PasswordRules), live validation and the submit button ----

    @Test
    fun passwordsAcceptedBySignUp_enableTheButton_andOnesItRejectsDoNot() = runTest {
        val accepted = listOf("Abcdef1!", "Nueva1clave!", "Z9\$aaaaa", "Contra5eña_larga")
        val rejected = listOf(
            "Abc1!", // too short
            "abcdefg1!", // no uppercase
            "Abcdefgh!", // no digit
            "Abcdefg12", // no special character
            "abcdefgh", // only lowercase
            "ABCDEFG1!", // no lowercase
            "ÁÉÍÓÚ1234!x", // accented capitals do not count as uppercase for Firebase
        )
        for (password in accepted) {
            val vm = viewModel()
            vm.fill(current = "Actual1!", new = password, confirm = password)
            assertTrue("debía aceptarse: $password", PasswordRules.from(password).allSatisfied)
            assertTrue("el botón debía habilitarse: $password", vm.uiState.value.canSubmit)
        }
        for (password in rejected) {
            val vm = viewModel()
            vm.fill(current = "Actual1!", new = password, confirm = password)
            assertEquals(PasswordRules.from(password).allSatisfied, vm.uiState.value.rules.allSatisfied)
            if (!PasswordRules.from(password).allSatisfied) {
                assertFalse("el botón debía bloquearse: $password", vm.uiState.value.canSubmit)
            }
        }
    }

    @Test
    fun theLiveRules_areExactlyThoseOfTheSignUpValidator() = runTest {
        val vm = viewModel()
        vm.onNewPasswordChanged("abc")

        assertEquals(PasswordRules.from("abc"), vm.uiState.value.rules)

        vm.onNewPasswordChanged("Abcdef1!")

        assertEquals(PasswordRules.from("Abcdef1!"), vm.uiState.value.rules)
        assertTrue(vm.uiState.value.rules.allSatisfied)
    }

    @Test
    fun theRequirementLines_followEachRuleOfTheValidator() {
        val partial = passwordRequirements(PasswordRules.from("abcdefgh"))

        assertEquals(
            listOf(
                "Mínimo 8 caracteres" to true,
                "Incluye una mayúscula y un número" to false,
                "Incluye una minúscula" to true,
                "Incluye al menos un carácter especial" to false,
            ),
            partial,
        )
        assertTrue(passwordRequirements(PasswordRules.from("Abcdef1!")).all { it.second })
    }

    @Test
    fun aNewPasswordEqualToTheCurrent_isFlaggedLive_andBlocksTheButton() = runTest {
        val vm = viewModel()
        vm.fill(current = "Misma1clave!", new = "Misma1clave!", confirm = "Misma1clave!")

        assertTrue(vm.uiState.value.sameAsCurrent)
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun sameAsCurrent_isAlsoRejectedOnSave_withoutCallingFirebase() = runTest {
        val vm = viewModel()
        vm.fill(current = "Misma1clave!", new = "Misma1clave!", confirm = "Misma1clave!")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("La nueva contraseña no puede ser igual a la actual", vm.uiState.value.newError)
        assertTrue(account.changePasswordCalls.isEmpty())
    }

    @Test
    fun aConfirmationThatDiffers_isFlaggedLive_andBlocksTheButton() = runTest {
        val vm = viewModel()
        vm.fill(current = "Actual1!", new = strong, confirm = "Otra1clave!")

        assertTrue(vm.uiState.value.confirmationMismatch)
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun anEmptyConfirmation_blocksTheButtonWithoutShowingAMismatchYet() = runTest {
        val vm = viewModel()
        vm.fill(current = "Actual1!", new = strong, confirm = "")

        assertFalse(vm.uiState.value.confirmationMismatch)
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun withoutTheCurrentPassword_theButtonIsBlocked() = runTest {
        val vm = viewModel()
        vm.fill(current = "", new = strong, confirm = strong)

        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun whileSaving_theButtonIsBlocked() {
        val valid = ChangePasswordUiState(currentPassword = "Actual1!", newPassword = strong, confirmPassword = strong)

        assertTrue(valid.canSubmit)
        assertFalse(valid.copy(isLoading = true).canSubmit)
    }

    @Test
    fun existingErrorHandling_isKept_wrongCurrentPasswordAndNoConnection() = runTest {
        account.reauthenticateResult = Result.failure(AuthError.WrongPassword)
        val wrong = viewModel()
        wrong.fill()
        wrong.onSaveClicked()
        advanceUntilIdle()
        assertEquals("La contraseña actual no es correcta", wrong.uiState.value.currentError)

        account.reauthenticateResult = Result.failure(AuthError.NetworkError)
        val offline = viewModel()
        offline.fill()
        offline.onSaveClicked()
        advanceUntilIdle()
        assertEquals("Sin conexión a internet", offline.uiState.value.formError)
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
