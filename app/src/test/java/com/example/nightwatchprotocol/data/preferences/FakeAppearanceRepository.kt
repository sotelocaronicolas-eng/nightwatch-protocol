package com.example.nightwatchprotocol.data.preferences

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test double for [AppearanceRepository]: keeps the choice in memory and can be told that writes fail. */
class FakeAppearanceRepository(
    initialThemeMode: ThemeMode = ThemeMode.DEFAULT,
    initialLanguageInitialized: Boolean = false,
) : AppearanceRepository {
    private val theme = MutableStateFlow(initialThemeMode)
    private val initialized = MutableStateFlow(initialLanguageInitialized)

    /** When true, every write throws like a DataStore that cannot write. */
    var failWrites = false

    var setThemeModeCalls = 0
        private set

    override val themeMode: Flow<ThemeMode> = theme
    override val languageInitialized: Flow<Boolean> = initialized

    override suspend fun setThemeMode(mode: ThemeMode) {
        setThemeModeCalls++
        if (failWrites) throw IOException("cannot write")
        theme.value = mode
    }

    override suspend fun markLanguageInitialized() {
        if (failWrites) throw IOException("cannot write")
        initialized.value = true
    }
}
