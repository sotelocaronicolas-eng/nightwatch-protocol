package com.example.nightwatchprotocol.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 7: with AppCompat in the app, changing the look (Oscuro/Claro/Automático) must stay a new value in Compose and never recreate
 * the Activity (that would cancel what is on the screen, R-01). Checked on the sources and on the XML, because nothing here runs on a device:
 *  - nobody calls `AppCompatDelegate.setDefaultNightMode` or `recreate()` in the app;
 *  - the base theme is AppCompat but NOT DayNight (the look is the app's, not the phone's `uiMode`);
 *  - the Activity does not claim `uiMode` in `configChanges` (the phone's own switch is handled like any other change: R-01).
 */
class ThemeDoesNotRecreateTest {

    private fun file(relative: String): File = listOf(File("src/main/$relative"), File("app/src/main/$relative")).first { it.exists() }

    private fun codeOf(file: File): String = file.readLines()
        .filterNot { line -> line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") } }
        .joinToString("\n")

    @Test
    fun noCodeSetsTheDefaultNightModeOrRecreates() {
        val files = file("java/com/example/nightwatchprotocol").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.size > 100)
        files.forEach { source ->
            val code = codeOf(source)
            assertFalse("${source.name} llama a setDefaultNightMode", code.contains("setDefaultNightMode"))
            assertFalse("${source.name} llama a recreate()", Regex("""\brecreate\(\)""").containsMatchIn(code))
        }
    }

    @Test
    fun theBaseTheme_isAppCompat_andNotDayNight() {
        val themes = file("res/values/themes.xml").readText()
        val parent = Regex("""<style name="Theme\.NightWatchProtocol" parent="([^"]+)"""").find(themes)!!.groupValues[1]

        assertEquals("Theme.AppCompat.Light.NoActionBar", parent)
        assertFalse(parent.contains("DayNight"))
    }

    @Test
    fun theActivity_doesNotClaimUiModeChanges() {
        val manifest = file("AndroidManifest.xml").readText()

        assertFalse("la actividad declara configChanges", manifest.contains("configChanges"))
    }

    @Test
    fun theManifest_declaresTheLanguagesAndTheLocalesService() {
        val manifest = file("AndroidManifest.xml").readText()
        val locales = file("res/xml/locales_config.xml").readText()

        assertTrue(manifest.contains("""android:localeConfig="@xml/locales_config""""))
        assertTrue(manifest.contains("androidx.appcompat.app.AppLocalesMetadataHolderService"))
        assertTrue(manifest.contains("autoStoreLocales"))
        assertEquals(listOf("es", "en"), Regex("""<locale android:name="([^"]+)"""").findAll(locales).map { it.groupValues[1] }.toList())
    }
}
