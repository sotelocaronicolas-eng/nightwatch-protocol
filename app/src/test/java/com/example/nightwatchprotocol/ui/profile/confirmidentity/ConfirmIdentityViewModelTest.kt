package com.example.nightwatchprotocol.ui.profile.confirmidentity

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmIdentityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private fun viewModel() = ConfirmIdentityViewModel(account)

    @Test
    fun emptyPassword_showsAnErrorAndDoesNotCallTheServer() = runTest {
        val vm = viewModel()

        vm.onConfirmClicked()
        advanceUntilIdle()

        assertEquals("Ingresa tu contraseña actual", vm.uiState.value.error)
        assertEquals(0, account.reauthenticateCalls.size)
    }

    @Test
    fun correctPassword_emitsConfirmed() = runTest {
        val vm = viewModel()
        vm.onPasswordChanged("Secreta1!")

        vm.events.test {
            vm.onConfirmClicked()
            assertEquals(ConfirmIdentityEvent.Confirmed, awaitItem())
        }

        assertEquals(listOf("Secreta1!"), account.reauthenticateCalls)
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun wrongPassword_showsTheWrongPasswordMessage() = runTest {
        account.reauthenticateResult = Result.failure(AuthError.WrongPassword)
        val vm = viewModel()
        vm.onPasswordChanged("mala")

        vm.onConfirmClicked()
        advanceUntilIdle()

        assertEquals("La contraseña actual no es correcta", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun invalidCredentials_showsTheSameWrongPasswordMessage() = runTest {
        account.reauthenticateResult = Result.failure(AuthError.InvalidCredentials)
        val vm = viewModel()
        vm.onPasswordChanged("mala")

        vm.onConfirmClicked()
        advanceUntilIdle()

        assertEquals("La contraseña actual no es correcta", vm.uiState.value.error)
    }

    @Test
    fun noConnection_showsTheNetworkMessage() = runTest {
        account.reauthenticateResult = Result.failure(AuthError.NetworkError)
        val vm = viewModel()
        vm.onPasswordChanged("Secreta1!")

        vm.onConfirmClicked()
        advanceUntilIdle()

        assertEquals("Sin conexión a internet", vm.uiState.value.error)
    }

    @Test
    fun unexpectedFailure_showsTheGenericMessage() = runTest {
        account.reauthenticateResult = Result.failure(RuntimeException("boom"))
        val vm = viewModel()
        vm.onPasswordChanged("Secreta1!")

        vm.onConfirmClicked()
        advanceUntilIdle()

        assertEquals("Se produjo un error inesperado. Intenta de nuevo.", vm.uiState.value.error)
    }

    @Test
    fun typingClearsTheError() = runTest {
        account.reauthenticateResult = Result.failure(AuthError.WrongPassword)
        val vm = viewModel()
        vm.onPasswordChanged("mala")
        vm.onConfirmClicked()
        advanceUntilIdle()

        vm.onPasswordChanged("otra")

        assertNull(vm.uiState.value.error)
    }
}
