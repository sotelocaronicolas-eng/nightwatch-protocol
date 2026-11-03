package com.example.nightwatchprotocol.ui.profile.appearance

import com.example.nightwatchprotocol.data.language.AppLanguage
import com.example.nightwatchprotocol.data.language.FakeLanguageRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageViewModelTest {

    @Test
    fun startsWithTheLanguageTheAppIsIn() {
        assertEquals(AppLanguage.EN, LanguageViewModel(FakeLanguageRepository(AppLanguage.EN)).uiState.value.selected)
    }

    @Test
    fun withNoLanguageFixedYet_itShowsSpanish() {
        assertEquals(AppLanguage.ES, LanguageViewModel(FakeLanguageRepository()).uiState.value.selected)
    }

    @Test
    fun choosingALanguage_savesItAndMovesTheMark() {
        val languages = FakeLanguageRepository(AppLanguage.ES)
        val vm = LanguageViewModel(languages)

        vm.onLanguageSelected(AppLanguage.EN)

        assertEquals(AppLanguage.EN, languages.currentLanguage())
        assertEquals(AppLanguage.EN, vm.uiState.value.selected)
    }

    @Test
    fun choosingTheLanguageAlreadyActive_changesNothing() {
        val languages = CountingLanguages(AppLanguage.ES)
        val vm = LanguageViewModel(languages)

        vm.onLanguageSelected(AppLanguage.ES)

        // Setting the same language would make AppCompat recreate the Activity for nothing.
        assertEquals(0, languages.setCalls)
    }

    @Test
    fun refresh_readsALanguageChangedFromThePhoneSettings() {
        val languages = FakeLanguageRepository(AppLanguage.ES)
        val vm = LanguageViewModel(languages)

        languages.setLanguage(AppLanguage.EN)
        vm.refresh()

        assertEquals(AppLanguage.EN, vm.uiState.value.selected)
    }

    private class CountingLanguages(private var language: AppLanguage?) : com.example.nightwatchprotocol.data.language.LanguageRepository {
        var setCalls = 0
        override fun currentLanguage(): AppLanguage? = language
        override fun setLanguage(language: AppLanguage) {
            setCalls++
            this.language = language
        }
    }
}
