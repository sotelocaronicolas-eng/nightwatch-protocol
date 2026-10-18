package com.example.nightwatchprotocol.ui.profile.recoveryemail

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.profile.RemoteProfile
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
class RecoveryEmailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val profile = FakeProfileRepository()
    private fun viewModel() = RecoveryEmailViewModel(auth, profile)

    @Test
    fun prefillsTheSavedRecoveryEmail() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(recoveryEmail = "respaldo@example.com"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("respaldo@example.com", vm.uiState.value.email)
    }

    @Test
    fun aLateAnswer_doesNotOverwriteWhatTheUserAlreadyTyped() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(recoveryEmail = "viejo@example.com"))
        val vm = viewModel()
        vm.onEmailChanged("nuevo@example.com")
        advanceUntilIdle()

        assertEquals("nuevo@example.com", vm.uiState.value.email)
    }

    @Test
    fun invalidEmail_showsAnErrorAndAsksForNoCode() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("no-es-un-correo")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Correo electrónico inválido", vm.uiState.value.error)
        assertTrue(profile.requestedRecoveryEmailCodes.isEmpty())
    }

    @Test
    fun blankEmail_isRejected() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Correo electrónico inválido", vm.uiState.value.error)
    }

    @Test
    fun typingClearsTheError() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("mal")
        vm.onSaveClicked()
        advanceUntilIdle()

        vm.onEmailChanged("mal@")

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun saving_asksForACode_andTheCodeScreenFollows() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("  respaldo@example.com ")

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(RecoveryEmailEvent.CodeSent("respaldo@example.com"), awaitItem())
        }

        assertEquals(listOf("respaldo@example.com"), profile.requestedRecoveryEmailCodes)
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.error)
    }

    private fun failingWith(error: Throwable): RecoveryEmailViewModel {
        profile.requestRecoveryEmailCodeResult = Result.failure(error)
        return viewModel()
    }

    private fun RecoveryEmailViewModel.saveAndGetError(): String? {
        onEmailChanged("respaldo@example.com")
        onSaveClicked()
        return uiState.value.error
    }

    @Test
    fun theSameAddressAsTheMainOne_isTold() = runTest {
        val vm = failingWith(AuthError.SameAsPrimaryEmail)
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals("Usa un correo distinto al principal", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun aRateLimit_saysHowLongToWait() = runTest {
        val vm = failingWith(AuthError.RateLimited(37))
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals("Espera 37 s antes de pedir otro código.", vm.uiState.value.error)
    }

    @Test
    fun aRateLimitWithoutSeconds_saysToWaitAMoment() = runTest {
        val vm = failingWith(AuthError.RateLimited(null))
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals("Espera un momento antes de pedir otro código.", vm.uiState.value.error)
    }

    @Test
    fun aSendFailure_isTold() = runTest {
        val vm = failingWith(AuthError.EmailSendFailed)
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals("No pudimos enviar el código. Inténtalo de nuevo.", vm.uiState.value.error)
    }

    @Test
    fun noConnection_showsTheOfflineMessage() = runTest {
        val vm = failingWith(AuthError.NetworkError)
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals(MESSAGE_RECOVERY_EMAIL_OFFLINE, vm.uiState.value.error)
    }

    @Test
    fun anUnknownFailure_showsTheGenericMessage_withoutTechnicalDetails() = runTest {
        val vm = failingWith(IllegalStateException("detalle interno"))
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals(MESSAGE_RECOVERY_EMAIL_NOT_SAVED, vm.uiState.value.error)
        assertFalse(vm.uiState.value.error.orEmpty().contains("detalle interno"))
    }

    @Test
    fun withoutASession_itCannotAskForACode() = runTest {
        auth.currentUserUid = null
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("respaldo@example.com")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("No se pudo guardar el correo. Inténtalo de nuevo.", vm.uiState.value.error)
        assertTrue(profile.requestedRecoveryEmailCodes.isEmpty())
    }
}
