package com.example.nightwatchprotocol.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the person chose about how the app looks, kept **on this device** (not per account: signing out does not erase it).
 *
 * **The language is NOT here on purpose.** The app language lives in `AppCompatDelegate` (Android 13+ shares it with the
 * phone's per-app language settings), so there is one source of truth; it is wired in Stage 7 of Module 5. What this
 * repository keeps about the language is only [languageInitialized]: whether the first launch already fixed Spanish
 * explicitly (decision A4), so a phone set to English does not open the app in English by itself.
 */
interface AppearanceRepository {
    /** The chosen look; [ThemeMode.DEFAULT] until the person picks one or when the stored value cannot be read. */
    val themeMode: Flow<ThemeMode>

    /** True once the first launch fixed the app language explicitly; false on a fresh install or when it cannot be read. */
    val languageInitialized: Flow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun markLanguageInitialized()
}

/** [AppearanceRepository] in the existing `user_preferences` DataStore (see `Context.userDataStore`). */
class DataStoreAppearanceRepository(private val dataStore: DataStore<Preferences>) : AppearanceRepository {

    /** A read that fails (corrupt file, no disk) behaves like an empty store: the defaults, never a crash. */
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val themeMode: Flow<ThemeMode> = preferences
        .map { ThemeMode.fromStorage(it[THEME_MODE_KEY]) }
        .distinctUntilChanged()

    override val languageInitialized: Flow<Boolean> = preferences
        .map { it[LANGUAGE_INITIALIZED_KEY] ?: false }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE_KEY] = mode.storageValue }
    }

    override suspend fun markLanguageInitialized() {
        dataStore.edit { it[LANGUAGE_INITIALIZED_KEY] = true }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val LANGUAGE_INITIALIZED_KEY = booleanPreferencesKey("language_initialized")

        fun inMemory(): DataStoreAppearanceRepository = DataStoreAppearanceRepository(InMemoryPreferencesDataStore())
    }
}
