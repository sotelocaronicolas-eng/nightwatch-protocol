package com.example.nightwatchprotocol.ui.verifycode

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.AuthError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
    override var completionNotice: String? = null

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
    fun aValidCodeWithACompletionNotice_emitsVerifiedWithTheNotice() = runTest {
        flow.completionNotice = "aviso"
        val vm = viewModel()
        vm.onCodeChanged("123456")

        vm.events.test {
            vm.onConfirmClicked()
            assertEquals(VerifyCodeEvent.VerifiedWithNotice("aviso"), awaitItem())
        }
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

    /** Lets the 60 s countdown that starts with the screen run out, so "Reenviar" works. */
    private fun TestScope.waitForResend() = advanceTimeBy(RESEND_COOLDOWN_SECONDS * 1_000L + 1)

    @Test
    fun resending_callsTheFlowAndShowsANotice() = runTest {
        val vm = viewModel()
        waitForResend()

        vm.onResendClicked()
        runCurrent()

        assertEquals(1, flow.resendCalls)
        assertEquals(MESSAGE_CODE_RESENT, vm.uiState.value.notice)
    }

    @Test
    fun aFailedResend_showsTheError() = runTest {
        flow.resendResult = Result.failure(AuthError.NetworkError)
        val vm = viewModel()
        waitForResend()

        vm.onResendClicked()
        runCurrent()

        assertEquals(VerifyCodeError.Offline, vm.uiState.value.error)
        assertNull(vm.uiState.value.notice)
    }

    // ---- countdown ----

    @Test
    fun theCountdownStartsAt60_andTheLinkIsDisabled() = runTest {
        val vm = viewModel()

        assertEquals(60, vm.uiState.value.resendSecondsLeft)
        assertFalse(vm.uiState.value.canResend)
        assertEquals("Reenviar en 60 s", vm.uiState.value.resendLabel)
    }

    @Test
    fun theCountdownGoesDownOneSecondAtATime() = runTest {
        val vm = viewModel()

        advanceTimeBy(15_001)
        assertEquals(45, vm.uiState.value.resendSecondsLeft)
        assertEquals("Reenviar en 45 s", vm.uiState.value.resendLabel)

        advanceTimeBy(44_000)
        assertEquals(1, vm.uiState.value.resendSecondsLeft)
        assertFalse(vm.uiState.value.canResend)
    }

    @Test
    fun whenTheCountdownEnds_theLinkReadsAsBeforeAndWorks() = runTest {
        val vm = viewModel()

        waitForResend()

        assertEquals(0, vm.uiState.value.resendSecondsLeft)
        assertTrue(vm.uiState.value.canResend)
        assertEquals("Reenviar el código de 6 digitos", vm.uiState.value.resendLabel)
    }

    @Test
    fun resendingBeforeTheCountdownEnds_doesNothing() = runTest {
        val vm = viewModel()
        advanceTimeBy(30_000)

        vm.onResendClicked()
        runCurrent()

        assertEquals(0, flow.resendCalls)
    }

    @Test
    fun aSuccessfulResend_startsTheCountdownAgain_andClearsTheOldCode() = runTest {
        val vm = viewModel()
        vm.onCodeChanged("123456")
        waitForResend()

        vm.onResendClicked()
        runCurrent()

        assertEquals(60, vm.uiState.value.resendSecondsLeft)
        assertEquals("", vm.uiState.value.code)
        advanceTimeBy(10_001)
        assertEquals(50, vm.uiState.value.resendSecondsLeft)
    }

    @Test
    fun whenTheServerAsksForAWait_theCountdownUsesTheSecondsItGave() = runTest {
        flow.resendResult = Result.failure(AuthError.RateLimited(45))
        val vm = viewModel()
        waitForResend()

        vm.onResendClicked()
        runCurrent()

        assertEquals(45, vm.uiState.value.resendSecondsLeft)
        assertNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.notice)
        advanceTimeBy(45_001)
        assertTrue(vm.uiState.value.canResend)
    }

    @Test
    fun whenTheServerGivesNoSeconds_theCountdownIs60Again() = runTest {
        flow.resendResult = Result.failure(AuthError.RateLimited(null))
        val vm = viewModel()
        waitForResend()

        vm.onResendClicked()
        runCurrent()

        assertEquals(60, vm.uiState.value.resendSecondsLeft)
    }

    // ---- individual errors ----

    private fun TestScope.confirmWith(
        result: Throwable,
        clock: () -> Long = { testScheduler.currentTime },
    ): VerifyCodeUiState {
        flow.confirmResult = Result.failure(result)
        val vm = VerifyCodeViewModel(flow, "ana@example.com", clock)
        vm.onCodeChanged("123456")
        vm.onConfirmClicked()
        runCurrent()
        return vm.uiState.value
    }

    @Test
    fun aRefusedCode_withinItsValidity_isIncorrect() = runTest {
        val state = confirmWith(AuthError.InvalidCode)

        assertEquals(VerifyCodeError.InvalidCode, state.error)
        assertEquals("El código ingresado no es correcto.", state.error?.message)
    }

    @Test
    fun aRefusedCode_afterTenMinutes_isExpired() = runTest {
        flow.confirmResult = Result.failure(AuthError.InvalidCode)
        val vm = VerifyCodeViewModel(flow, "ana@example.com") { testScheduler.currentTime }
        vm.onCodeChanged("123456")
        advanceTimeBy(CODE_VALIDITY_MILLIS + 1)

        vm.onConfirmClicked()
        runCurrent()

        assertEquals(VerifyCodeError.Expired, vm.uiState.value.error)
        assertEquals("El código expiró. Solicita uno nuevo.", vm.uiState.value.error?.message)
    }

    @Test
    fun aRefusedCode_justBeforeTenMinutes_isStillIncorrect() = runTest {
        flow.confirmResult = Result.failure(AuthError.InvalidCode)
        val vm = VerifyCodeViewModel(flow, "ana@example.com") { testScheduler.currentTime }
        vm.onCodeChanged("123456")
        advanceTimeBy(CODE_VALIDITY_MILLIS - 1_000)

        vm.onConfirmClicked()
        runCurrent()

        assertEquals(VerifyCodeError.InvalidCode, vm.uiState.value.error)
    }

    @Test
    fun aResentCode_restartsTheTenMinutes() = runTest {
        flow.confirmResult = Result.failure(AuthError.InvalidCode)
        val vm = VerifyCodeViewModel(flow, "ana@example.com") { testScheduler.currentTime }
        advanceTimeBy(CODE_VALIDITY_MILLIS - 1_000)
        vm.onResendClicked()
        runCurrent()
        advanceTimeBy(CODE_VALIDITY_MILLIS - 2_000)
        vm.onCodeChanged("123456")

        vm.onConfirmClicked()
        runCurrent()

        assertEquals(VerifyCodeError.InvalidCode, vm.uiState.value.error)
    }

    @Test
    fun anExpiredCodeError_clearsTheFieldOnRetry() = runTest {
        flow.confirmResult = Result.failure(AuthError.CodeExpired)
        val vm = viewModel()
        vm.onCodeChanged("123456")
        vm.onConfirmClicked()
        runCurrent()
        assertEquals(VerifyCodeError.Expired, vm.uiState.value.error)

        vm.onConfirmClicked() // "REINTENTAR"

        assertNull(vm.uiState.value.error)
        assertEquals("", vm.uiState.value.code)
    }

    @Test
    fun tooManyAttempts_hasItsOwnMessage_andRetryKeepsTheCode() = runTest {
        val state = confirmWith(AuthError.TooManyAttempts)

        assertEquals(VerifyCodeError.TooManyAttempts, state.error)
        assertEquals("Demasiados intentos. Espera unos minutos y vuelve a intentarlo.", state.error?.message)
        assertEquals("123456", state.code)
    }

    @Test
    fun noConnection_usesTheExistingMessage() = runTest {
        val state = confirmWith(AuthError.NetworkError)

        assertEquals(VerifyCodeError.Offline, state.error)
        assertEquals("Sin conexión a internet", state.error?.message)
    }

    @Test
    fun anyOtherFailure_isTheGenericMessage() = runTest {
        assertEquals(VerifyCodeError.Generic, confirmWith(AuthError.Unknown).error)
        assertEquals(VerifyCodeError.Generic, confirmWith(RuntimeException("x")).error)
    }

    // ---- the field ----

    @Test
    fun pastingMoreThanSixDigits_keepsTheFirstSix() {
        val vm = viewModel()

        vm.onCodeChanged("123 456 789")

        assertEquals("123456", vm.uiState.value.code)
        assertTrue(vm.uiState.value.canConfirm)
    }

    @Test
    fun aFieldWithOnlyLetters_staysEmpty() {
        val vm = viewModel()

        vm.onCodeChanged("abcdef")

        assertEquals("", vm.uiState.value.code)
        assertFalse(vm.uiState.value.canConfirm)
    }
}
