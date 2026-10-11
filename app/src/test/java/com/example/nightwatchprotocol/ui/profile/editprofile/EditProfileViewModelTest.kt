package com.example.nightwatchprotocol.ui.profile.editprofile

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.AccountProfile
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.data.avatar.AvatarImageException
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.FakeAvatarCache
import com.example.nightwatchprotocol.data.avatar.FakeAvatarImageProcessor
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val account = FakeAccountRepository()
    private val profile = FakeProfileRepository()
    private val cache = FakeAvatarCache()
    private val avatars = AvatarRepository(profile, cache, UnconfinedTestDispatcher())
    private val processor = FakeAvatarImageProcessor()

    private fun viewModel() = EditProfileViewModel(auth, account, avatars, processor)

    private val strongPassword = "Secreta1!"

    @Test
    fun startsWithTheCurrentNameAndEmail() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Nicolás Sotelo", vm.uiState.value.name)
        assertEquals("nicolas@example.com", vm.uiState.value.email)
        assertTrue(vm.uiState.value.hasPassword)
    }

    @Test
    fun blankName_showsAnErrorAndSavesNothing() = runTest {
        val vm = viewModel()
        vm.onNameChanged("   ")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Ingresa tu nombre", vm.uiState.value.nameError)
        assertTrue(account.updateDisplayNameCalls.isEmpty())
    }

    @Test
    fun changedName_isSavedAndOpensProfileUpdated() = runTest {
        val vm = viewModel()
        vm.onNameChanged("  Nicolás S.  ")

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(EditProfileEvent.NavigateToProfileUpdated, awaitItem())
        }

        assertEquals(listOf("Nicolás S."), account.updateDisplayNameCalls)
    }

    @Test
    fun unchangedName_isNotSentToFirebase() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(EditProfileEvent.NavigateToProfileUpdated, awaitItem())
        }

        assertTrue(account.updateDisplayNameCalls.isEmpty())
    }

    @Test
    fun weakNewPassword_showsTheRulesError() = runTest {
        val vm = viewModel()
        vm.onNewPasswordChanged("abc")
        vm.onConfirmPasswordChanged("abc")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("La contraseña no cumple con los requerimientos", vm.uiState.value.passwordError)
        assertTrue(account.updatePasswordCalls.isEmpty())
    }

    @Test
    fun passwordsThatDoNotMatch_showTheMismatchError() = runTest {
        val vm = viewModel()
        vm.onNewPasswordChanged(strongPassword)
        vm.onConfirmPasswordChanged("Otra1!clave")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Las contraseñas no coinciden", vm.uiState.value.confirmError)
        assertTrue(account.updatePasswordCalls.isEmpty())
    }

    @Test
    fun validNewPassword_isChangedAndFieldsAreCleared() = runTest {
        val vm = viewModel()
        vm.onNewPasswordChanged(strongPassword)
        vm.onConfirmPasswordChanged(strongPassword)

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(EditProfileEvent.NavigateToProfileUpdated, awaitItem())
        }

        assertEquals(listOf(strongPassword), account.updatePasswordCalls)
        assertEquals("", vm.uiState.value.newPassword)
    }

    @Test
    fun recentLoginRequired_opensConfirmIdentity_andRetryingAfterConfirmingSucceeds() = runTest {
        account.updatePasswordResults = mutableListOf(Result.failure(AuthError.RecentLoginRequired), Result.success(Unit))
        val vm = viewModel()
        vm.onNameChanged("Nuevo Nombre")
        vm.onNewPasswordChanged(strongPassword)
        vm.onConfirmPasswordChanged(strongPassword)

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(EditProfileEvent.NavigateToConfirmIdentity, awaitItem())

            vm.onIdentityConfirmed()
            assertEquals(EditProfileEvent.NavigateToProfileUpdated, awaitItem())
        }

        // The name was saved on the first attempt and is not sent again; the password is retried.
        assertEquals(listOf("Nuevo Nombre"), account.updateDisplayNameCalls)
        assertEquals(listOf(strongPassword, strongPassword), account.updatePasswordCalls)
    }

    @Test
    fun changedEmail_goesToChangeEmailInsteadOfSavingIt() = runTest {
        val vm = viewModel()
        vm.onEmailChanged("nuevo@example.com")

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(EditProfileEvent.NavigateToChangeEmail("nuevo@example.com"), awaitItem())
        }

        assertTrue(account.requestEmailChangeCalls.isEmpty())
    }

    @Test
    fun invalidNewEmail_showsAnError() = runTest {
        val vm = viewModel()
        vm.onEmailChanged("esto-no-es-un-correo")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Correo electrónico inválido", vm.uiState.value.emailError)
    }

    @Test
    fun unexpectedAuthError_showsAMessageAndStaysOnTheScreen() = runTest {
        account.updateDisplayNameResult = Result.failure(AuthError.NetworkError)
        val vm = viewModel()
        vm.onNameChanged("Otro")

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals("Sin conexión a internet", vm.uiState.value.formError)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun googleAccount_hidesPasswordFieldsAndNeverTouchesThePasswordOrEmail() = runTest {
        account.profile = AccountProfile("Nicolás", "g@example.com", null, hasPassword = false)
        val vm = viewModel()
        vm.onNewPasswordChanged("lo-que-sea")
        vm.onEmailChanged("otro@example.com")

        vm.events.test {
            vm.onSaveClicked()
            assertEquals(EditProfileEvent.NavigateToProfileUpdated, awaitItem())
        }

        assertFalse(vm.uiState.value.hasPassword)
        assertTrue(account.updatePasswordCalls.isEmpty())
    }

    @Test
    fun pickedPhoto_isProcessedAndSavedImmediately() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        processor.result = Result.success(bytes)
        val vm = viewModel()

        vm.onAvatarPicked("content://foto")
        advanceUntilIdle()

        assertEquals(listOf("content://foto"), processor.processedUris)
        assertArrayEquals(bytes, cache.files["test-uid"])
        assertEquals(AvatarDisplay.Photo(bytes), vm.uiState.value.avatar)
        assertTrue(vm.uiState.value.hasPhoto)
        assertNull(vm.uiState.value.avatarError)
        assertFalse(vm.uiState.value.isAvatarBusy)
    }

    @Test
    fun unreadablePhoto_showsAShortErrorAndKeepsThePreviousAvatar() = runTest {
        val previous = byteArrayOf(9, 9)
        val vm = viewModel()
        processor.result = Result.success(previous)
        vm.onAvatarPicked("content://buena")
        advanceUntilIdle()

        processor.result = Result.failure(AvatarImageException("ilegible"))
        vm.onAvatarPicked("content://rota")
        advanceUntilIdle()

        assertEquals("No pudimos leer esa imagen", vm.uiState.value.avatarError)
        assertEquals(AvatarDisplay.Photo(previous), vm.uiState.value.avatar)
    }

    @Test
    fun photoWhenFirestoreFails_isKeptLocallyAndShowsTheLocalOnlyMessage() = runTest {
        profile.saveAvatarResult = Result.failure(RuntimeException("sin red"))
        val vm = viewModel()

        vm.onAvatarPicked("content://foto")
        advanceUntilIdle()

        assertEquals(MESSAGE_AVATAR_LOCAL_ONLY, vm.uiState.value.avatarError)
        assertTrue(vm.uiState.value.hasPhoto)
    }

    @Test
    fun removePhoto_goesBackToTheInitial() = runTest {
        val vm = viewModel()
        vm.onAvatarPicked("content://foto")
        advanceUntilIdle()

        vm.onRemoveAvatar()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasPhoto)
        assertEquals(AvatarDisplay.Initial("N"), vm.uiState.value.avatar)
        assertNull(cache.files["test-uid"])
    }
}
