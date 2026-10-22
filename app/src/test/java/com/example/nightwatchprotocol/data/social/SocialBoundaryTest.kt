package com.example.nightwatchprotocol.data.social

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture rules of the module, checked on the source code (the same way the exam screens are checked):
 *  - No screen and no view model knows Supabase or BuildConfig (CLAUDE.md, section 3).
 *  - The Social domain package (`data/social`) knows neither Supabase nor the supabase package, and never reads the phone's date
 *    or time: the server decides every deadline and every streak, and the countdown uses a monotonic clock.
 */
class SocialBoundaryTest {

    private fun mainFolder(relative: String): File = listOf(
        File("src/main/java/com/example/nightwatchprotocol/$relative"),
        File("app/src/main/java/com/example/nightwatchprotocol/$relative"),
    ).first { it.isDirectory }

    private fun kotlinFiles(folder: File): List<File> = folder.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** The code of a file without its comments, so a comment that explains the rule does not trip the rule. */
    private fun codeOf(file: File): String = file.readLines()
        .filterNot { line -> line.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") } }
        .joinToString("\n")

    @Test
    fun noScreenAndNoViewModel_importsSupabaseOrBuildConfig() {
        val files = kotlinFiles(mainFolder("ui"))
        assertTrue("no se encontró código de ui", files.size > 20)

        files.forEach { file ->
            val code = codeOf(file)
            assertFalse("${file.name} importa Supabase", code.contains("io.github.jan.supabase"))
            assertFalse("${file.name} usa el paquete data.supabase", code.contains("nightwatchprotocol.data.supabase"))
            assertFalse("${file.name} usa BuildConfig", code.contains("BuildConfig"))
        }
    }

    @Test
    fun theSocialDomain_knowsNeitherSupabaseNorBuildConfig() {
        val files = kotlinFiles(mainFolder("data/social"))
        assertTrue("no se encontró el dominio de Social", files.size >= 5)

        files.forEach { file ->
            val code = codeOf(file)
            assertFalse("${file.name} importa Supabase", code.contains("io.github.jan.supabase"))
            assertFalse("${file.name} usa el paquete data.supabase", code.contains("nightwatchprotocol.data.supabase"))
            assertFalse("${file.name} usa BuildConfig", code.contains("BuildConfig"))
            assertFalse("${file.name} usa Ktor", code.contains("io.ktor"))
        }
    }

    @Test
    fun theSocialCode_neverReadsThePhonesDateOrTime() {
        val socialFiles = kotlinFiles(mainFolder("data/social")) +
            kotlinFiles(mainFolder("data/supabase")).filter {
                it.name.contains("Social", ignoreCase = true)
            }
        val forbidden = listOf(
            "currentTimeMillis", "Instant.now", "LocalDate.now", "LocalDateTime.now", "ZonedDateTime.now", "OffsetDateTime.now",
            "Clock.system", "SystemClock", "Date()", "Calendar.getInstance", "TimeZone.getDefault", "ZoneId.systemDefault",
        )
        assertTrue(socialFiles.size >= 8)

        socialFiles.forEach { file ->
            val code = codeOf(file)
            forbidden.forEach { word -> assertFalse("${file.name} usa $word", code.contains(word)) }
        }
    }

    @Test
    fun theSocialRepositories_neverSendOrNameAUserId() {
        val files = kotlinFiles(mainFolder("data/social")) + listOf(
            "SupabaseSocialRepositories.kt", "SupabaseSocialRpcClient.kt",
        ).map { File(mainFolder("data/supabase"), it) }

        files.forEach { file ->
            val code = codeOf(file)
            assertFalse("${file.name} envía un user_id", code.contains("\"p_user") || code.contains("\"user_id\""))
        }
    }

    @Test
    fun theSocialDomain_hasNoPlaceToKeepACorrectAnswer() {
        val code = kotlinFiles(mainFolder("data/social")).joinToString("\n") { codeOf(it) }

        // A question has an id, a text and four options: the correct letter is never part of the model.
        assertFalse(Regex("""(?i)val\s+correct(Option|Answer|Letter)""").containsMatchIn(code))
        assertFalse(Regex("""(?i)val\s+answer\b""").containsMatchIn(code))
    }
}
