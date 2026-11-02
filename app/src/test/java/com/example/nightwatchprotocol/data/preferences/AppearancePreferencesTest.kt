package com.example.nightwatchprotocol.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePreferencesTest {

    /** A DataStore that cannot be read (corrupt file, no disk) and cannot be written either. */
    private class UnreadableDataStore(private val error: Throwable = IOException("cannot read")) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw error }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw error
    }

    @Test
    fun theDefaultLook_isDark() = runTest {
        assertEquals(ThemeMode.DARK, ThemeMode.DEFAULT)
        assertEquals(ThemeMode.DARK, DataStoreAppearanceRepository.inMemory().themeMode.first())
    }

    @Test
    fun everyMode_isSavedAndReadBack() = runTest {
        ThemeMode.entries.forEach { mode ->
            val repository = DataStoreAppearanceRepository.inMemory()

            repository.setThemeMode(mode)

            assertEquals(mode, repository.themeMode.first())
        }
    }

    @Test
    fun theChoice_survivesANewRepositoryOverTheSameStore() = runTest {
        val store = InMemoryPreferencesDataStore()
        DataStoreAppearanceRepository(store).setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, DataStoreAppearanceRepository(store).themeMode.first())
    }

    @Test
    fun changingTheMode_emitsEachNewValueOnce() = runTest {
        val repository = DataStoreAppearanceRepository.inMemory()

        repository.themeMode.test {
            assertEquals(ThemeMode.DARK, awaitItem())
            repository.setThemeMode(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, awaitItem())
            repository.setThemeMode(ThemeMode.LIGHT)
            repository.setThemeMode(ThemeMode.SYSTEM)
            assertEquals(ThemeMode.SYSTEM, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun anUnknownStoredValue_fallsBackToTheDefault() = runTest {
        val store = InMemoryPreferencesDataStore()
        store.edit { it[stringPreferencesKey("theme_mode")] = "sepia" }

        assertEquals(ThemeMode.DARK, DataStoreAppearanceRepository(store).themeMode.first())
    }

    @Test
    fun theStoredValues_areStableStrings() {
        assertEquals("dark", ThemeMode.DARK.storageValue)
        assertEquals("light", ThemeMode.LIGHT.storageValue)
        assertEquals("system", ThemeMode.SYSTEM.storageValue)
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("system"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage(null))
    }

    @Test
    fun languageInitialized_isFalseUntilMarked_andThenStaysTrue() = runTest {
        val repository = DataStoreAppearanceRepository.inMemory()
        assertFalse(repository.languageInitialized.first())

        repository.markLanguageInitialized()
        repository.setThemeMode(ThemeMode.LIGHT)

        assertTrue(repository.languageInitialized.first())
        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
    }

    @Test
    fun theLanguageFlag_doesNotTouchTheThemeChoice() = runTest {
        val repository = DataStoreAppearanceRepository.inMemory()
        repository.setThemeMode(ThemeMode.SYSTEM)

        repository.markLanguageInitialized()

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun aFailedRead_fallsBackToTheDefaultsWithoutCrashing() = runTest {
        val repository = DataStoreAppearanceRepository(UnreadableDataStore())

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
        assertFalse(repository.languageInitialized.first())
    }

    @Test
    fun aReadFailureThatIsNotIo_isNotHidden() = runTest {
        val repository = DataStoreAppearanceRepository(UnreadableDataStore(IllegalStateException("bug")))

        val failure = runCatching { repository.themeMode.first() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun theLanguage_isNotStoredInDataStore() = runTest {
        val store = InMemoryPreferencesDataStore()
        val repository = DataStoreAppearanceRepository(store)
        repository.setThemeMode(ThemeMode.LIGHT)
        repository.markLanguageInitialized()

        val keys = store.data.first().asMap().keys.map { it.name }.sorted()

        assertEquals(listOf("language_initialized", "theme_mode"), keys)
        assertNull(store.data.first().asMap().keys.firstOrNull { it.name.contains("locale") || it.name == "language" })
    }
}
