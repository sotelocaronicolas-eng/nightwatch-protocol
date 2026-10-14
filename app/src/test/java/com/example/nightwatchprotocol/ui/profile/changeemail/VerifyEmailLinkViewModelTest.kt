package com.example.nightwatchprotocol.ui.profile.changeemail

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyEmailLinkViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private fun viewModel() = VerifyEmailLinkViewModel(account, "nuevo@example.com")

    @Test
    fun whenFirebaseSeesTheNewEmail_itEmitsEmailUpdated() = runTest {
        account.isEmailChangedResult = Result.success(true)
        val vm = viewModel()

        vm.events.test {
            vm.onCheckClicked()
            assertEquals(VerifyEmailLinkEvent.EmailUpdated, awaitItem())
        }

        assertEquals(listOf("nuevo@example.com"), account.isEmailChangedCalls)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun whenTheLinkWasNotOpened_itShowsTheErrorState() = runTest {
        account.isEmailChangedResult = Result.success(false)
        val vm = viewModel()

        vm.onCheckClicked()
        advanceUntilIdle()

        assertEquals("El enlace venció o no se ha verificado", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isChecking)
    }

    @Test
    fun retryingAfterTheLinkIsOpened_succeeds() = runTest {
        account.isEmailChangedResult = Result.success(false)
        val vm = viewModel()
        vm.onCheckClicked()
        advanceUntilIdle()

        account.isEmailChangedResult = Result.success(true)

        vm.events.test {
            vm.onCheckClicked()
            assertEquals(VerifyEmailLinkEvent.EmailUpdated, awaitItem())
        }
        assertEquals(2, account.isEmailChangedCalls.size)
    }

    @Test
    fun noConnection_showsTheNetworkMessage() = runTest {
        account.isEmailChangedResult = Result.failure(AuthError.NetworkError)
        val vm = viewModel()

        vm.onCheckClicked()
        advanceUntilIdle()

        assertEquals("Sin conexión a internet", vm.uiState.value.error)
    }

    @Test
    fun anExpiredSession_getsItsOwnMessageInsteadOfPretendingTheLinkWasNotOpened() = runTest {
        account.isEmailChangedResult = Result.failure(AuthError.Unknown)
        val vm = viewModel()

        vm.onCheckClicked()
        advanceUntilIdle()

        assertEquals(MESSAGE_SESSION_EXPIRED, vm.uiState.value.error)
    }

    @Test
    fun resendingTheLink_callsFirebaseAgainAndShowsANotice() = runTest {
        val vm = viewModel()

        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(listOf("nuevo@example.com"), account.requestEmailChangeCalls)
        assertTrue(vm.uiState.value.resentNotice)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun aFailedResend_showsAnError() = runTest {
        account.requestEmailChangeResults = mutableListOf(Result.failure(AuthError.NetworkError))
        val vm = viewModel()

        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals("Sin conexión a internet", vm.uiState.value.error)
        assertFalse(vm.uiState.value.resentNotice)
    }

    @Test
    fun checkingAgain_clearsThePreviousNoticeAndError() = runTest {
        account.isEmailChangedResult = Result.success(false)
        val vm = viewModel()
        vm.onResendClicked()
        advanceUntilIdle()

        vm.onCheckClicked()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.resentNotice)
    }
}
