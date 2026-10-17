package com.example.nightwatchprotocol.ui.verifycode

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
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

/** A [CodeFlow] that records what the screen asked and answers with whatever the test sets. */
class FakeCodeFlow : CodeFlow {
    var confirmResult: Result<Unit> = Result.success(Unit)
    var resendResult: Result<Unit> = Result.success(Unit)
    val confirmed = mutableListOf<String>()
    var resendCalls = 0

    override suspend fun confirm(code: String) {
        confirmed += code
        confirmResult.getOrThrow()
    }

    override suspend fun resend() {
        resendCalls++
        resendResult.getOrThrow()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyCodeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val flow = FakeCodeFlow()
    private fun viewModel() = VerifyCodeViewModel(flow, "ana@example.com")

    @Test
    fun startsWithTheEmailAndAnEmptyCode() {
        val state = viewModel().uiState.value

        assertEquals("ana@example.com", state.email)
        assertEquals("", state.code)
        assertFalse(state.canConfirm)
    }

    @Test
    fun theField_keepsOnlyDigits_andAtMostSix() {
        val vm = viewModel()

        vm.onCodeChanged("12a3 4-5")
        assertEquals("12345", vm.uiState.value.code)

        vm.onCodeChanged("1234567890")
        assertEquals("123456", vm.uiState.value.code)
    }

    @Test
    fun sanitizeCode_rejectsNonAsciiDigits() {
        assertEquals("", sanitizeCode("٣٤٥٦٧٨"))
        assertEquals("2", sanitizeCode("１2２"))
    }

    @Test
    fun confirmIsEnabledOnlyWithSixDigits() {
        val vm = viewModel()

        vm.onCodeChanged("12345")
        assertFalse(vm.uiState.value.canConfirm)

        vm.onCodeChanged("123456")
        assertTrue(vm.uiState.value.canConfirm)
    }

    @Test
    fun confirmingWithFewerThanSixDigits_doesNothing() = runTest {
        val vm = viewModel()
        vm.onCodeChanged("123")

        vm.onConfirmClicked()
        advanceUntilIdle()

        assertTrue(flow.confirmed.isEmpty())
    }

    @Test
    fun aValidCode_emitsVerified() = runTest {
        val vm = viewModel()
        vm.onCodeChanged("123456")

        vm.events.test {
            vm.onConfirmClicked()
            assertEquals(VerifyCodeEvent.Verified, awaitItem())
        }
        assertEquals(listOf("123456"), flow.confirmed)
    }

    @Test
    fun aRefusedCode_showsTheDesignMessage_andRetryClearsTheField() = runTest {
        flow.confirmResult = Result.failure(AuthError.InvalidCode)
        val vm = viewModel()
        vm.onCodeChanged("111111")

        vm.onConfirmClicked()
        advanceUntilIdle()

        assertEquals(VerifyCodeError.InvalidCode, vm.uiState.value.error)
        assertEquals("El código ingresado no es correcto.", vm.uiState.value.error?.message)

        vm.onConfirmClicked() // "REINTENTAR"
        assertNull(vm.uiState.value.error)
        assertEquals("", vm.uiState.value.code)
        assertEquals(1, flow.confirmed.size)
    }

    @Test
    fun noConnection_showsTheExistingMessage_andRetrySendsTheSameCodeAgain() = runTest {
        flow.confirmResult = Result.failure(AuthError.NetworkError)
        val vm = viewModel()
        vm.onCodeChanged("123456")

        vm.onConfirmClicked()
        advanceUntilIdle()
        assertEquals("Sin conexión a internet", vm.uiState.value.error?.message)

        flow.confirmResult = Result.success(Unit)
        vm.events.test {
            vm.onConfirmClicked()
            assertEquals(VerifyCodeEvent.Verified, awaitItem())
        }
        assertEquals(listOf("123456", "123456"), flow.confirmed)
    }

    @Test
    fun typingAfterAnErrorClearsIt() = runTest {
        flow.confirmResult = Result.failure(AuthError.InvalidCode)
        val vm = viewModel()
        vm.onCodeChanged("111111")
        vm.onConfirmClicked()
        advanceUntilIdle()

        vm.onCodeChanged("22222")

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun resending_callsTheFlowAndShowsANotice() = runTest {
        val vm = viewModel()

        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(1, flow.resendCalls)
        assertEquals(MESSAGE_CODE_RESENT, vm.uiState.value.notice)
    }

    @Test
    fun aFailedResend_showsTheError() = runTest {
        flow.resendResult = Result.failure(AuthError.NetworkError)
        val vm = viewModel()

        vm.onResendClicked()
        advanceUntilIdle()

        assertEquals(VerifyCodeError.Offline, vm.uiState.value.error)
        assertNull(vm.uiState.value.notice)
    }
}
