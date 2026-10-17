package com.example.nightwatchprotocol.ui.profile.changeemail

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
    fun sendingTheLink_callsFirebaseAndEmitsLinkSent() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onSendLinkClicked()
            assertEquals(ChangeEmailEvent.LinkSent("nuevo@example.com"), awaitItem())
        }

        assertEquals(listOf("nuevo@example.com"), account.requestEmailChangeCalls)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun withTheCodeFlow_theTextsAskForACode_andNothingIsSentBeforeTheIdentityIsConfirmed() = runTest {
        account.usesCode = true
        val vm = viewModel()
        assertTrue(vm.uiState.value.usesCode)

        vm.events.test {
            vm.onSendLinkClicked()
            assertEquals(ChangeEmailEvent.NavigateToConfirmIdentity, awaitItem())
        }
        assertTrue(account.requestEmailChangeCalls.isEmpty())
    }

    @Test
    fun withTheCodeFlow_afterConfirmingTheIdentity_theCodeIsSentAndTheCodeScreenFollows() = runTest {
        account.usesCode = true
        val vm = viewModel()

        vm.events.test {
            vm.onIdentityConfirmed()
            assertEquals(ChangeEmailEvent.CodeSent("nuevo@example.com"), awaitItem())
        }
        assertEquals(listOf("nuevo@example.com"), account.requestEmailChangeCalls)
    }

    @Test
    fun withTheCodeFlow_onceConfirmed_aSecondSendDoesNotAskAgain() = runTest {
        account.usesCode = true
        val vm = viewModel()

        vm.events.test {
            vm.onIdentityConfirmed()
            assertEquals(ChangeEmailEvent.CodeSent("nuevo@example.com"), awaitItem())
            vm.onSendLinkClicked()
            assertEquals(ChangeEmailEvent.CodeSent("nuevo@example.com"), awaitItem())
        }
        assertEquals(2, account.requestEmailChangeCalls.size)
    }

    @Test
    fun withTheCodeFlow_aRejectedAddressStillShowsItsError() = runTest {
        account.usesCode = true
        account.requestEmailChangeResults = mutableListOf(Result.failure(AuthError.EmailAlreadyInUse))
        val vm = viewModel()
        vm.onIdentityConfirmed()
        advanceUntilIdle()

        assertEquals(MESSAGE_EMAIL_IN_USE, vm.uiState.value.error)
    }

    @Test
    fun withTheLinkFlow_theTextsAreTheOldOnes_andNoIdentityIsAskedUpFront() = runTest {
        val vm = viewModel()
        assertFalse(vm.uiState.value.usesCode)

        vm.events.test {
            vm.onSendLinkClicked()
            assertEquals(ChangeEmailEvent.LinkSent("nuevo@example.com"), awaitItem())
        }
    }

    @Test
    fun theEmailIsTrimmedBeforeSending() = runTest {
        val vm = viewModel(" nuevo@example.com ")

        vm.events.test {
            vm.onSendLinkClicked()
            assertEquals(ChangeEmailEvent.LinkSent("nuevo@example.com"), awaitItem())
        }
    }

    @Test
    fun invalidEmail_isRejectedLocally() = runTest {
        val vm = viewModel("esto-no-es-un-correo")

        vm.onSendLinkClicked()
        advanceUntilIdle()

        assertEquals("Correo electrónico inválido", vm.uiState.value.error)
        assertTrue(account.requestEmailChangeCalls.isEmpty())
    }

    @Test
    fun theSameEmailAsTheAccount_isRejectedLocally() = runTest {
        val vm = viewModel("NICOLAS@example.com")

        vm.onSendLinkClicked()
        advanceUntilIdle()

        assertEquals("Ese ya es el correo de tu cuenta", vm.uiState.value.error)
        assertTrue(account.requestEmailChangeCalls.isEmpty())
    }

    @Test
    fun recentLoginRequired_opensConfirmIdentity_andRetryingSendsTheLink() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(AuthError.RecentLoginRequired), Result.success(Unit))
        val vm = viewModel()

        vm.events.test {
            vm.onSendLinkClicked()
            assertEquals(ChangeEmailEvent.NavigateToConfirmIdentity, awaitItem())

            vm.onIdentityConfirmed()
            assertEquals(ChangeEmailEvent.LinkSent("nuevo@example.com"), awaitItem())
        }

        assertEquals(2, account.requestEmailChangeCalls.size)
    }

    @Test
    fun emailAlreadyInUse_showsItsMessage() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(AuthError.EmailAlreadyInUse))
        val vm = viewModel()

        vm.onSendLinkClicked()
        advanceUntilIdle()

        assertEquals("Ese correo ya está en uso en otra cuenta", vm.uiState.value.error)
    }

    @Test
    fun noConnection_showsTheNetworkMessage() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(AuthError.NetworkError))
        val vm = viewModel()

        vm.onSendLinkClicked()
        advanceUntilIdle()

        assertEquals("Sin conexión a internet", vm.uiState.value.error)
    }

    @Test
    fun unexpectedError_showsTheGenericMessage() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(RuntimeException("boom")))
        val vm = viewModel()

        vm.onSendLinkClicked()
        advanceUntilIdle()

        assertEquals("Se produjo un error inesperado. Intenta de nuevo.", vm.uiState.value.error)
    }

    @Test
    fun editingTheEmail_clearsTheError() = runTest {
        val vm = viewModel("mal")
        vm.onSendLinkClicked()
        advanceUntilIdle()

        vm.onEmailChanged("mal@")

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun googleAccount_cannotSendALink() = runTest {
        account.profile = AccountProfile("Nicolás", "g@example.com", null, hasPassword = false)
        val vm = viewModel()

        vm.onSendLinkClicked()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasPassword)
        assertTrue(account.requestEmailChangeCalls.isEmpty())
    }
}
