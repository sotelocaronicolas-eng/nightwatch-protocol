package com.example.nightwatchprotocol.data.preferences

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture rules of Module 5 (checked on the source code, like `SocialBoundaryTest`):
 *  - The data layer does not depend on Android resources or Compose: the UI turns types into text and colours
 *    (decision D10). `data/notifications` is the one documented exception today (it builds the notification with `R`);
 *    Stage 3 and Stage 10 of Module 5 remove it.
 *  - Nothing in the app erases the `user_preferences` DataStore, so the look chosen on this device survives signing out.
 */
class PersonalizationBoundaryTest {

    private fun mainFolder(relative: String): File = listOf(
        File("src/main/java/com/example/nightwatchprotocol/$relative"),
        File("app/src/main/java/com/example/nightwatchprotocol/$relative"),
    ).first { it.isDirectory }

    private fun kotlinFiles(folder: File): List<File> = folder.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun codeOf(file: File): String = file.readLines()
        .filterNot { line -> line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") } }
        .joinToString("\n")

    @Test
    fun theDataLayer_doesNotUseAndroidResourcesOrCompose() {
        val files = kotlinFiles(mainFolder("data")).filterNot { it.invariantSeparatorsPath.contains("/data/notifications/") }
        assertTrue("no se encontró la capa data", files.size > 30)
        val forbidden = listOf(
            "com.example.nightwatchprotocol.R", "android.content.res", "androidx.annotation.StringRes",
            "androidx.annotation.PluralsRes", "androidx.compose", "stringResource", "getString(",
        )

        files.forEach { file ->
            val code = codeOf(file)
            forbidden.forEach { word -> assertFalse("${file.name} usa $word", code.contains(word)) }
        }
    }

    @Test
    fun theNewPreferenceFiles_existAndStayFreeOfResources() {
        val names = listOf("ThemeMode.kt", "AppearancePreferences.kt")
        names.forEach { name ->
            val file = File(mainFolder("data/preferences"), name)
            assertTrue("falta $name", file.isFile)
            assertFalse("$name importa recursos de Android", codeOf(file).contains("nightwatchprotocol.R"))
        }
        assertTrue(File(mainFolder("data/language"), "LanguageRepository.kt").isFile)
    }

    @Test
    fun theLanguage_isNotKeptInDataStore() {
        val code = codeOf(File(mainFolder("data/preferences"), "AppearancePreferences.kt"))

        assertFalse(code.contains("stringPreferencesKey(\"language"))
        assertFalse(code.contains("stringPreferencesKey(\"locale"))
    }

    @Test
    fun nothingInTheApp_clearsTheUserPreferencesStore() {
        val clearing = Regex("""edit\s*\{[^}]*\.clear\(\)""")

        kotlinFiles(mainFolder("")).forEach { file ->
            assertFalse("${file.name} borra un DataStore completo", clearing.containsMatchIn(codeOf(file)))
        }
    }
}
