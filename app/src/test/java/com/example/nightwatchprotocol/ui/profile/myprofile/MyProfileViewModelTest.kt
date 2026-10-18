package com.example.nightwatchprotocol.ui.profile.myprofile

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.AccountProfile
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.FakeAvatarCache
import com.example.nightwatchprotocol.data.profile.BirthDate
import com.example.nightwatchprotocol.data.profile.DataStoreProfileExtrasCache
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.profile.Gender
import com.example.nightwatchprotocol.data.profile.ProfileExtras
import com.example.nightwatchprotocol.data.profile.ProfileExtrasRepository
import com.example.nightwatchprotocol.data.profile.RemoteProfile
import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.PlacementResult
import kotlin.io.encoding.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val account = FakeAccountRepository()
    private val quiz = FakeQuizResultRepository()
    private val profile = FakeProfileRepository()
    private val avatars = AvatarRepository(profile, FakeAvatarCache(), UnconfinedTestDispatcher())

    private val extrasCache = DataStoreProfileExtrasCache.inMemory()
    private val extras = ProfileExtrasRepository(profile, extrasCache)

    private fun viewModel() = MyProfileViewModel(auth, account, quiz, profile, avatars, extras)

    @Test
    fun showsTheRealAccountData() = runTest {
        account.profile = AccountProfile("Nicolás Sotelo", "n@example.com", 1_785_000_000_000L, hasPassword = true)
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Nicolás Sotelo", state.displayName)
        assertEquals("n@example.com", state.email)
        assertEquals(15, state.streakDays)
        assertEquals(AvatarDisplay.Initial("N"), state.avatar)
        assertTrue(state.memberSince != "—")
    }

    @Test
    fun withoutDisplayName_usesTheEmailPrefix() = runTest {
        account.profile = AccountProfile(null, "euzuki1205@gmail.com", null, hasPassword = true)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("euzuki1205", vm.uiState.value.displayName)
        assertEquals("—", vm.uiState.value.memberSince)
        assertEquals(AvatarDisplay.Initial("E"), vm.uiState.value.avatar)
    }

    @Test
    fun levelIsNullWhileLoadingAndThenShowsTheQuizLevel() = runTest {
        quiz.getResultResult = Result.success(PlacementResult(PlacementLevel.MEDIO, emptySet()))
        val vm = viewModel()
        assertNull(vm.uiState.value.level)

        advanceUntilIdle()

        assertEquals("MEDIO", vm.uiState.value.level)
    }

    @Test
    fun noQuizResult_showsNotAssessed() = runTest {
        quiz.getResultResult = Result.success(null)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Sin evaluar", vm.uiState.value.level)
    }

    @Test
    fun quizReadFailure_showsNotAssessed() = runTest {
        quiz.getResultResult = Result.failure(RuntimeException("sin red"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Sin evaluar", vm.uiState.value.level)
    }

    @Test
    fun quizReadThatNeverAnswers_showsNotAssessedAfterTheTimeout() = runTest {
        quiz.getResultNeverCompletes = true
        val vm = viewModel()
        advanceTimeBy(4_000)
        assertNull(vm.uiState.value.level)

        advanceTimeBy(1_500)

        assertEquals("Sin evaluar", vm.uiState.value.level)
    }

    @Test
    fun recoveryEmail_isShownWhenThereIsOne() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(recoveryEmail = "respaldo@example.com"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("respaldo@example.com", vm.uiState.value.recoveryEmail)
    }

    @Test
    fun recoveryEmail_isNullWhenMissingBlankOrUnreadable() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(recoveryEmail = "   "))
        val blank = viewModel()
        advanceUntilIdle()
        assertNull(blank.uiState.value.recoveryEmail)

        profile.getProfileResult = Result.failure(RuntimeException("sin red"))
        val failing = viewModel()
        advanceUntilIdle()
        assertNull(failing.uiState.value.recoveryEmail)
    }

    @Test
    fun avatarFromTheServer_replacesTheInitial() = runTest {
        val photo = byteArrayOf(5, 6, 7)
        profile.getProfileResult = Result.success(RemoteProfile(avatarBase64 = Base64.Default.encode(photo)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(AvatarDisplay.Photo(photo), vm.uiState.value.avatar)
    }

    @Test
    fun genderAndBirthDate_areShownFromTheServer() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(gender = "female", birthDate = "1999-03-07"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Femenino", vm.uiState.value.gender)
        assertEquals("07/03/1999", vm.uiState.value.birthDate)
    }

    @Test
    fun whenNotDefined_theyAreNull_soTheScreenShowsADash() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.gender)
        assertNull(vm.uiState.value.birthDate)
    }

    @Test
    fun theLocalCopy_isShownEvenWhenTheServerFails() = runTest {
        extrasCache.write("test-uid", ProfileExtras(Gender.OTHER, BirthDate(1990, 12, 25)))
        profile.getProfileResult = Result.failure(RuntimeException("sin red"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Otro", vm.uiState.value.gender)
        assertEquals("25/12/1990", vm.uiState.value.birthDate)
    }

    @Test
    fun unknownStoredValues_areIgnoredInsteadOfBreakingTheScreen() = runTest {
        profile.getProfileResult = Result.success(RemoteProfile(gender = "alien", birthDate = "31/12/1999"))
        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.gender)
        assertNull(vm.uiState.value.birthDate)
    }

    @Test
    fun noSignedInUser_doesNotReadAnythingAndShowsNotAssessed() = runTest {
        auth.currentUserUid = null
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Sin evaluar", vm.uiState.value.level)
        assertEquals(0, quiz.getResultCallCount)
        assertEquals(0, profile.getProfileCallCount)
    }
}
