package com.example.nightwatchprotocol.ui.profile.appearance

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.preferences.FakeAppearanceRepository
import com.example.nightwatchprotocol.data.preferences.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAppearanceRepository()

    private fun viewModel() = AppearanceViewModel(repository)

    @Test
    fun startsWithTheDefaultDarkLook() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
    }

    @Test
    fun showsTheSavedChoice() = runTest {
        val vm = AppearanceViewModel(FakeAppearanceRepository(initialThemeMode = ThemeMode.SYSTEM))
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, vm.uiState.value.themeMode)
    }

    @Test
    fun selectingAMode_isSavedAndShown() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.uiState.test {
            assertEquals(ThemeMode.DARK, awaitItem().themeMode)
            vm.onThemeSelected(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, awaitItem().themeMode)
        }
        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
    }

    @Test
    fun aChangeMadeElsewhere_isShown() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        repository.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, vm.uiState.value.themeMode)
    }

    @Test
    fun aFailedSave_keepsTheSavedChoiceAndDoesNotCrash() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        repository.failWrites = true

        vm.onThemeSelected(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(1, repository.setThemeModeCalls)
        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
    }
}
