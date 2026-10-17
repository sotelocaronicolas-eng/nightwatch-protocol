package com.example.nightwatchprotocol.ui.profile.recoveryemail

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.firestore.FailureKind
import com.example.nightwatchprotocol.data.firestore.FirestoreAccessException
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
    fun validEmail_isSavedTrimmedAndEmitsSaved() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("  respaldo@example.com ")

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(RecoveryEmailEvent.Saved, awaitItem())
        }

        assertEquals(listOf("respaldo@example.com"), profile.savedRecoveryEmails)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun invalidEmail_showsAnErrorAndSavesNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("no-es-un-correo")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Correo electrónico inválido", vm.uiState.value.error)
        assertTrue(profile.savedRecoveryEmails.isEmpty())
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
    fun permissionDeniedOrNoConnection_showsASaveErrorAndStaysOnTheScreen() = runTest {
        profile.saveRecoveryEmailResult = Result.failure(RuntimeException("PERMISSION_DENIED"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("respaldo@example.com")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("No se pudo guardar el correo. Inténtalo de nuevo.", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaving)
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
    fun noConnection_showsTheOfflineMessage() = runTest {
        profile.saveRecoveryEmailResult = Result.failure(FirestoreAccessException(FailureKind.Offline))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("respaldo@example.com")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(MESSAGE_RECOVERY_EMAIL_OFFLINE, vm.uiState.value.error)
        assertEquals("Sin conexión a internet. Revisa tu conexión e inténtalo de nuevo.", vm.uiState.value.error)
    }

    @Test
    fun deniedPermission_showsTheDifferentPermissionMessage() = runTest {
        profile.saveRecoveryEmailResult = Result.failure(FirestoreAccessException(FailureKind.PermissionDenied))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("respaldo@example.com")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(MESSAGE_RECOVERY_EMAIL_PERMISSION, vm.uiState.value.error)
        assertFalse(vm.uiState.value.error == MESSAGE_RECOVERY_EMAIL_OFFLINE)
    }

    @Test
    fun anUnknownFailure_showsTheGenericMessage_withoutTechnicalDetails() = runTest {
        profile.saveRecoveryEmailResult =
            Result.failure(FirestoreAccessException(FailureKind.Unknown, IllegalStateException("detalle interno")))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("respaldo@example.com")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(MESSAGE_RECOVERY_EMAIL_NOT_SAVED, vm.uiState.value.error)
        assertFalse(vm.uiState.value.error.orEmpty().contains("detalle interno"))
    }

    @Test
    fun aSuccessfulSave_leavesNoErrorAndStopsTheSpinner() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("respaldo@example.com")

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(RecoveryEmailEvent.Saved, awaitItem())
        }

        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaving)
    }

    // ---- the backend that verifies the address with a code ----

    private fun codeViewModel(): RecoveryEmailViewModel {
        profile.usesCode = true
        return viewModel()
    }

    @Test
    fun withCodes_savingAsksForACode_andNeverSavesDirectly() = runTest {
        val vm = codeViewModel()
        advanceUntilIdle()
        vm.onEmailChanged("  respaldo@example.com ")

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(RecoveryEmailEvent.CodeSent("respaldo@example.com"), awaitItem())
        }

        assertEquals(listOf("respaldo@example.com"), profile.requestedRecoveryEmailCodes)
        assertTrue(profile.savedRecoveryEmails.isEmpty())
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun withCodes_anInvalidEmail_asksForNothing() = runTest {
        val vm = codeViewModel()
        advanceUntilIdle()
        vm.onEmailChanged("no-es-un-correo")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(MESSAGE_RECOVERY_EMAIL_INVALID, vm.uiState.value.error)
        assertTrue(profile.requestedRecoveryEmailCodes.isEmpty())
    }

    private fun failingWith(error: Throwable): RecoveryEmailViewModel {
        profile.requestRecoveryEmailCodeResult = Result.failure(error)
        return codeViewModel()
    }

    private fun RecoveryEmailViewModel.saveAndGetError(): String? {
        onEmailChanged("respaldo@example.com")
        onSaveClicked()
        return uiState.value.error
    }

    @Test
    fun withCodes_theSameAddressAsTheMainOne_isTold() = runTest {
        val vm = failingWith(AuthError.SameAsPrimaryEmail)
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals("Usa un correo distinto al principal", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun withCodes_aRateLimit_saysHowLongToWait() = runTest {
        val vm = failingWith(AuthError.RateLimited(37))
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals("Espera 37 s antes de pedir otro código.", vm.uiState.value.error)
    }

    @Test
    fun withCodes_aRateLimitWithoutSeconds_saysToWaitAMoment() = runTest {
        val vm = failingWith(AuthError.RateLimited(null))
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals("Espera un momento antes de pedir otro código.", vm.uiState.value.error)
    }

    @Test
    fun withCodes_aSendFailure_isTold() = runTest {
        val vm = failingWith(AuthError.EmailSendFailed)
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals("No pudimos enviar el código. Inténtalo de nuevo.", vm.uiState.value.error)
    }

    @Test
    fun withCodes_noConnection_showsTheOfflineMessage() = runTest {
        val vm = failingWith(AuthError.NetworkError)
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals(MESSAGE_RECOVERY_EMAIL_OFFLINE, vm.uiState.value.error)
    }

    @Test
    fun withCodes_anUnknownFailure_showsTheGenericMessage() = runTest {
        val vm = failingWith(AuthError.Unknown)
        advanceUntilIdle()

        vm.saveAndGetError()
        advanceUntilIdle()

        assertEquals(MESSAGE_RECOVERY_EMAIL_NOT_SAVED, vm.uiState.value.error)
    }

    @Test
    fun withoutCodes_theFlowIsTheDirectSaveOfAlways() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("respaldo@example.com")

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(RecoveryEmailEvent.Saved, awaitItem())
        }

        assertTrue(profile.requestedRecoveryEmailCodes.isEmpty())
    }

    @Test
    fun withoutASession_itCannotSave() = runTest {
        auth.currentUserUid = null
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEmailChanged("respaldo@example.com")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("No se pudo guardar el correo. Inténtalo de nuevo.", vm.uiState.value.error)
        assertTrue(profile.savedRecoveryEmails.isEmpty())
    }
}
