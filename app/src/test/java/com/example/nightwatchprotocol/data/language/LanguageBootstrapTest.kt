package com.example.nightwatchprotocol.data.language

import androidx.datastore.preferences.core.Preferences
import com.example.nightwatchprotocol.data.preferences.DataStoreAppearanceRepository
import com.example.nightwatchprotocol.data.preferences.FakeAppearanceRepository
import com.example.nightwatchprotocol.data.preferences.InMemoryPreferencesDataStore
import com.example.nightwatchprotocol.data.preferences.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The first launch fixes Spanish explicitly (A4), once, and the language never goes to DataStore (A3). */
class LanguageBootstrapTest {

    @Test
    fun freshInstall_fixesSpanish_andMarksItDone() = runTest {
        val languages = FakeLanguageRepository()
        val appearance = FakeAppearanceRepository()

        val fixed = LanguageBootstrap(languages, appearance).ensureInitialized()

        assertTrue(fixed)
        assertEquals(AppLanguage.ES, languages.currentLanguage())
        assertTrue(appearance.languageInitialized.first())
    }

    @Test
    fun secondLaunch_doesNotTouchTheLanguage() = runTest {
        val languages = FakeLanguageRepository()
        val appearance = FakeAppearanceRepository()
        LanguageBootstrap(languages, appearance).ensureInitialized()
        languages.setLanguage(AppLanguage.EN) // the person chose English afterwards

        val fixed = LanguageBootstrap(languages, appearance).ensureInitialized()

        assertFalse(fixed)
        assertEquals(AppLanguage.EN, languages.currentLanguage())
    }

    @Test
    fun alreadyInitialized_neverFixesAnything() = runTest {
        val languages = FakeLanguageRepository()
        val appearance = FakeAppearanceRepository(initialLanguageInitialized = true)

        val fixed = LanguageBootstrap(languages, appearance).ensureInitialized()

        assertFalse(fixed)
        assertNull(languages.currentLanguage())
    }

    @Test
    fun aLanguageChosenBeforeTheFirstLaunch_isRespected_andMarkedDone() = runTest {
        // For example chosen in the phone's per-app language settings (Android 13+) before the app ever ran.
        val languages = FakeLanguageRepository(AppLanguage.EN)
        val appearance = FakeAppearanceRepository()

        val fixed = LanguageBootstrap(languages, appearance).ensureInitialized()

        assertFalse(fixed)
        assertEquals(AppLanguage.EN, languages.currentLanguage())
        assertTrue(appearance.languageInitialized.first())
    }

    @Test
    fun aFailedWrite_leavesItForTheNextLaunch() = runTest {
        val languages = FakeLanguageRepository()
        val appearance = FakeAppearanceRepository().apply { failWrites = true }

        try {
            LanguageBootstrap(languages, appearance).ensureInitialized()
        } catch (_: java.io.IOException) {
        }
        assertFalse(appearance.languageInitialized.first())

        appearance.failWrites = false
        LanguageBootstrap(languages, appearance).ensureInitialized()
        assertTrue(appearance.languageInitialized.first())
        assertEquals(AppLanguage.ES, languages.currentLanguage())
    }

    // ---- the language is not in DataStore ----

    @Test
    fun dataStore_keepsOnlyTheThemeAndTheInitializedFlag_neverTheLanguage() = runTest {
        val store = InMemoryPreferencesDataStore()
        val appearance = DataStoreAppearanceRepository(store)
        val languages = FakeLanguageRepository()

        appearance.setThemeMode(ThemeMode.LIGHT)
        LanguageBootstrap(languages, appearance).ensureInitialized()
        languages.setLanguage(AppLanguage.EN)

        val keys = store.data.first().asMap().keys.map { (it as Preferences.Key<*>).name }.toSet()
        assertEquals(setOf("theme_mode", "language_initialized"), keys)
    }

    @Test
    fun noPreferencesKey_namedAfterTheLanguage_existsInTheCode() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val keyDeclaration = Regex("""(string|boolean|int|long|stringSet)PreferencesKey\("([^"]+)"\)""")

        val names = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> keyDeclaration.findAll(file.readText()).map { it.groupValues[2] } }
            .toList()

        assertTrue("no se encontraron claves de DataStore", names.size > 3)
        names.filter { it.contains("language", ignoreCase = true) || it.contains("locale", ignoreCase = true) }
            .forEach { assertEquals("el idioma no se guarda en DataStore (A3)", "language_initialized", it) }
    }

    // ---- tags ----

    @Test
    fun languageOfTags_takesTheFirstTag() {
        assertEquals(AppLanguage.EN, languageOfTags("en-US,es"))
        assertEquals(AppLanguage.ES, languageOfTags("es"))
        assertNull(languageOfTags(""))
        assertNull(languageOfTags("fr,es"))
    }
}
