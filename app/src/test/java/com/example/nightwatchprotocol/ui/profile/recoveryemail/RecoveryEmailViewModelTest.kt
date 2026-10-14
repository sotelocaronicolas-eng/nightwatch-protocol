package com.example.nightwatchprotocol.ui.profile.recoveryemail

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
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
