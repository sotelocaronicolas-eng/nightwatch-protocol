package com.example.nightwatchprotocol.ui.profile.changeemail

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.AccountProfile
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.ui.verifycode.MESSAGE_WAIT_BEFORE_NEW_CODE
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
class ChangeEmailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private fun viewModel(email: String = "nuevo@example.com") = ChangeEmailViewModel(account, email)

    @Test
    fun startsWithTheEmailTypedOnEditProfile() = runTest {
        assertEquals("nuevo@example.com", viewModel().uiState.value.email)
    }

    @Test
    fun nothingIsSentBeforeTheIdentityIsConfirmed() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onSendCodeClicked()
            assertEquals(ChangeEmailEvent.NavigateToConfirmIdentity, awaitItem())
        }
        assertTrue(account.requestEmailChangeCalls.isEmpty())
    }

    @Test
    fun afterConfirmingTheIdentity_theCodeIsSentAndTheCodeScreenFollows() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onIdentityConfirmed()
            assertEquals(ChangeEmailEvent.CodeSent("nuevo@example.com"), awaitItem())
        }
        assertEquals(listOf("nuevo@example.com"), account.requestEmailChangeCalls)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun onceConfirmed_aSecondSendDoesNotAskAgain() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onIdentityConfirmed()
            assertEquals(ChangeEmailEvent.CodeSent("nuevo@example.com"), awaitItem())
            vm.onSendCodeClicked()
            assertEquals(ChangeEmailEvent.CodeSent("nuevo@example.com"), awaitItem())
        }
        assertEquals(2, account.requestEmailChangeCalls.size)
    }

    @Test
    fun aRejectedAddressStillShowsItsError() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(AuthError.EmailAlreadyInUse))
        val vm = viewModel()
        vm.onIdentityConfirmed()
        advanceUntilIdle()

        assertEquals(MESSAGE_EMAIL_IN_USE, vm.uiState.value.error)
    }

    @Test
    fun askingForACodeTooSoon_saysToWait() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(AuthError.RateLimited(30)))
        val vm = viewModel()
        vm.onIdentityConfirmed()
        advanceUntilIdle()

        assertEquals(MESSAGE_WAIT_BEFORE_NEW_CODE, vm.uiState.value.error)
    }

    @Test
    fun theEmailIsTrimmedBeforeSending() = runTest {
        val vm = viewModel(" nuevo@example.com ")

        vm.events.test {
            vm.onIdentityConfirmed()
            assertEquals(ChangeEmailEvent.CodeSent("nuevo@example.com"), awaitItem())
        }
    }

    @Test
    fun invalidEmail_isRejectedLocally() = runTest {
        val vm = viewModel("esto-no-es-un-correo")

        vm.onSendCodeClicked()
        advanceUntilIdle()

        assertEquals("Correo electrónico inválido", vm.uiState.value.error)
        assertTrue(account.requestEmailChangeCalls.isEmpty())
    }

    @Test
    fun theSameEmailAsTheAccount_isRejectedLocally() = runTest {
        val vm = viewModel("NICOLAS@example.com")

        vm.onSendCodeClicked()
        advanceUntilIdle()

        assertEquals("Ese ya es el correo de tu cuenta", vm.uiState.value.error)
        assertTrue(account.requestEmailChangeCalls.isEmpty())
    }

    @Test
    fun recentLoginRequired_opensConfirmIdentity_andRetryingSendsTheCode() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(AuthError.RecentLoginRequired), Result.success(Unit))
        val vm = viewModel()

        vm.events.test {
            vm.onIdentityConfirmed()
            assertEquals(ChangeEmailEvent.NavigateToConfirmIdentity, awaitItem())

            vm.onIdentityConfirmed()
            assertEquals(ChangeEmailEvent.CodeSent("nuevo@example.com"), awaitItem())
        }

        assertEquals(2, account.requestEmailChangeCalls.size)
    }

    @Test
    fun emailAlreadyInUse_showsItsMessage() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(AuthError.EmailAlreadyInUse))
        val vm = viewModel()

        vm.onIdentityConfirmed()
        advanceUntilIdle()

        assertEquals("Ese correo ya está en uso en otra cuenta", vm.uiState.value.error)
    }

    @Test
    fun noConnection_showsTheNetworkMessage() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(AuthError.NetworkError))
        val vm = viewModel()

        vm.onIdentityConfirmed()
        advanceUntilIdle()

        assertEquals("Sin conexión a internet", vm.uiState.value.error)
    }

    @Test
    fun unexpectedError_showsTheGenericMessage() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(RuntimeException("boom")))
        val vm = viewModel()

        vm.onIdentityConfirmed()
        advanceUntilIdle()

        assertEquals("Se produjo un error inesperado. Intenta de nuevo.", vm.uiState.value.error)
    }

    @Test
    fun editingTheEmail_clearsTheError() = runTest {
        val vm = viewModel("mal")
        vm.onSendCodeClicked()
        advanceUntilIdle()

        vm.onEmailChanged("mal@")

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun googleAccount_cannotSendACode() = runTest {
        account.profile = AccountProfile("Nicolás", "g@example.com", null, hasPassword = false)
        val vm = viewModel()

        vm.onSendCodeClicked()
        vm.onIdentityConfirmed()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasPassword)
        assertTrue(account.requestEmailChangeCalls.isEmpty())
    }
}
