package com.example.nightwatchprotocol.ui.profile.privacy

import com.example.nightwatchprotocol.MainDispatcherRule
import app.cash.turbine.test
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.biometric.BiometricAvailability
import com.example.nightwatchprotocol.data.biometric.FakeBiometricCapabilities
import com.example.nightwatchprotocol.data.device.DeviceRecord
import com.example.nightwatchprotocol.data.device.FakeDeviceRepository
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.profile.FakeDataExportWriter
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.profile.ProfileDataExporter
import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
import com.example.nightwatchprotocol.data.profile.RemoteProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySecurityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val profile = FakeProfileRepository()
    private val preferences = UserPreferences.inMemory()
    private val capabilities = FakeBiometricCapabilities()

    private val writer = FakeDataExportWriter()
    private val devices = FakeDeviceRepository()
    private val exporter = ProfileDataExporter(FakeAccountRepository(), profile, FakeQuizResultRepository(), clock = { 0L })

    private fun viewModel() = PrivacySecurityViewModel(auth, profile, preferences, capabilities, exporter, writer, devices)

    @Test
    fun startsWithDefaults() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hidePublicProgress)
        assertFalse(vm.uiState.value.biometricLockEnabled)
        assertEquals(0, vm.uiState.value.activeDevices) // nobody registered yet in the fake registry
    }

    @Test
    fun hidePublicProgress_isPersistedAndReflectedInTheState() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onHidePublicProgressChanged(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hidePublicProgress)
        assertTrue(preferences.settings.first()[UserSetting.HIDE_PUBLIC_PROGRESS])
    }

    @Test
    fun hidePublicProgress_canBeTurnedBackOff() = runTest {
        preferences.set(UserSetting.HIDE_PUBLIC_PROGRESS, true)
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hidePublicProgress)

        vm.onHidePublicProgressChanged(false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hidePublicProgress)
    }

    @Test
    fun recoveryEmail_isShownWhenSaved() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(recoveryEmail = "respaldo@example.com"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("respaldo@example.com", vm.uiState.value.recoveryEmail)
    }

    @Test
    fun recoveryEmail_isNullWhenMissingOrUnreadable() = runTest {
        profile.getProfileResult = Result.failure(RuntimeException("sin permiso"))
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.recoveryEmail)
    }

    @Test
    fun refresh_picksUpARecoveryEmailSavedAfterwards() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertNull(vm.uiState.value.recoveryEmail)

        profile.getProfileResult = Result.success(RemoteProfile(recoveryEmail = "nuevo@example.com"))
        vm.refreshRecoveryEmail()
        advanceUntilIdle()

        assertEquals("nuevo@example.com", vm.uiState.value.recoveryEmail)
    }

    @Test
    fun enablingTheLock_withAFingerprint_asksForTheTestPromptAndStaysOffUntilItSucceeds() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onBiometricLockToggled(true)
            assertEquals(PrivacySecurityEvent.VerifyBiometric, awaitItem())
        }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.biometricLockEnabled)
        assertNull(vm.uiState.value.biometricError)
    }

    @Test
    fun successfulTestPrompt_turnsTheLockOnAndSavesIt() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onBiometricVerified(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.biometricLockEnabled)
        assertTrue(preferences.settings.first()[UserSetting.BIOMETRIC_LOCK])
        assertNull(vm.uiState.value.biometricError)
    }

    @Test
    fun cancelledOrFailedTestPrompt_leavesTheLockOffWithARedMessage() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onBiometricVerified(false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.biometricLockEnabled)
        assertEquals("No se pudo verificar tu huella", vm.uiState.value.biometricError)
        assertFalse(preferences.settings.first()[UserSetting.BIOMETRIC_LOCK])
    }

    @Test
    fun noEnrolledFingerprint_showsTheMessageAndNeverAsksForAPrompt() = runTest {
        capabilities.availability = BiometricAvailability.NoneEnrolled
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onBiometricLockToggled(true)
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals("No hay una huella registrada en este teléfono", vm.uiState.value.biometricError)
        assertFalse(vm.uiState.value.biometricLockEnabled)
    }

    @Test
    fun noFingerprintHardware_showsItsOwnMessage() = runTest {
        capabilities.availability = BiometricAvailability.NoHardware
        val vm = viewModel()
        advanceUntilIdle()

        vm.onBiometricLockToggled(true)

        assertEquals("Este teléfono no tiene lector de huella", vm.uiState.value.biometricError)
    }

    @Test
    fun temporarilyUnavailableReader_showsItsOwnMessage() = runTest {
        capabilities.availability = BiometricAvailability.TemporarilyUnavailable
        val vm = viewModel()
        advanceUntilIdle()

        vm.onBiometricLockToggled(true)

        assertEquals("El lector no está disponible ahora, inténtalo más tarde", vm.uiState.value.biometricError)
    }

    @Test
    fun turningTheLockOff_isImmediateAndClearsTheMessage() = runTest {
        preferences.set(UserSetting.BIOMETRIC_LOCK, true)
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.biometricLockEnabled)

        vm.onBiometricLockToggled(false)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.biometricLockEnabled)
        assertFalse(preferences.settings.first()[UserSetting.BIOMETRIC_LOCK])
        assertNull(vm.uiState.value.biometricError)
    }

    @Test
    fun chosenExportDestination_receivesTheJsonAndReportsDone() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onExportDestinationChosen("content://documentos/datos.json")
        advanceUntilIdle()

        assertEquals(ExportStatus.Done, vm.uiState.value.exportStatus)
        assertEquals(1, writer.writes.size)
        assertEquals("content://documentos/datos.json", writer.writes.first().first)
        assertTrue(writer.writes.first().second.contains("nicolas@example.com"))
    }

    @Test
    fun cancelledFilePicker_doesNothing() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onExportDestinationChosen(null)
        advanceUntilIdle()

        assertEquals(ExportStatus.Idle, vm.uiState.value.exportStatus)
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun unwritableDestination_reportsFailed() = runTest {
        writer.result = Result.failure(java.io.IOException("sin permiso"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onExportDestinationChosen("content://documentos/datos.json")
        advanceUntilIdle()

        assertEquals(ExportStatus.Failed, vm.uiState.value.exportStatus)
    }

    @Test
    fun activeDevices_countsTheRealDevicesThatWereNotAskedToSignOut() = runTest {
        devices.put("test-uid", DeviceRecord("a", "Uno", 1L, revoked = false))
        devices.put("test-uid", DeviceRecord("b", "Dos", 1L, revoked = false))
        devices.put("test-uid", DeviceRecord("c", "Tres", 1L, revoked = true))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.activeDevices)
    }

    @Test
    fun activeDevices_isNullWhenTheRegistryCannotBeRead() = runTest {
        devices.listFails = true
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.activeDevices)
    }
}
