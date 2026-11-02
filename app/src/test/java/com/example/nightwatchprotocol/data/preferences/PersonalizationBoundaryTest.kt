package com.example.nightwatchprotocol.data.preferences

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture rules of Module 5 (checked on the source code, like `SocialBoundaryTest`):
 *  - The data layer does not use Compose and, except for a short documented list, names no Android text resource: it hands
 *    the UI a `UiText` or a type to translate (decision D10, Stage 3 of Module 5).
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

    /**
     * The only files of `data/` that may name an Android text resource (`R.string...`) so they can hand a [UiText] to the UI
     * (decision D10 and Stage 3 of Module 5). Everything else in `data/` knows no resources. Compose is forbidden for all.
     * To add a file here, say why in this comment: the list is meant to stay short.
     *  - `data/common/UiText.kt`: the type itself and its `Context` resolver.
     *  - `data/learning/Badges.kt`: the names of the nine badges (a fixed catalog).
     *  - `data/profile/BirthDate.kt`, `data/profile/ProfileFormatters.kt`: the messages of the profile fields.
     *  - the `data/notifications` folder: the text of the reminders and their channels, resolved with a `Context` when posted.
     */
    private val mayNameResources = listOf(
        "/data/common/UiText.kt",
        "/data/learning/Badges.kt",
        "/data/profile/BirthDate.kt",
        "/data/profile/ProfileFormatters.kt",
        "/data/notifications/",
    )

    @Test
    fun theDataLayer_doesNotUseAndroidResourcesOrCompose() {
        val files = kotlinFiles(mainFolder("data"))
        assertTrue("no se encontró la capa data", files.size > 30)
        val resourceWords = listOf(
            "com.example.nightwatchprotocol.R", "android.content.res", "androidx.annotation.StringRes",
            "androidx.annotation.PluralsRes", "stringResource", "getString(",
        )

        files.forEach { file ->
            val code = codeOf(file)
            assertFalse("${file.name} usa Compose", code.contains("androidx.compose"))
            if (mayNameResources.none { file.invariantSeparatorsPath.contains(it) }) {
                resourceWords.forEach { word -> assertFalse("${file.name} usa $word", code.contains(word)) }
            }
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
