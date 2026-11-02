package com.example.nightwatchprotocol

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard of Stage 3 (F-09) of Module 5: no file of `app/src/main` (ui, data, di, MainActivity...) may hold fixed Spanish text;
 * every text the person reads lives in a `strings_*.xml` resource. It reads the source code (like `PersonalizationBoundaryTest`)
 * and looks for string literals that read like a sentence or a label. Per-file exceptions go in [exceptionFiles].
 *
 * What it ignores, on purpose:
 *  - comments and KDoc, imports and the package line;
 *  - `@Preview` functions (they show sample data, not app text);
 *  - literals without letters ("X", "›", "—", "%d"), technical keys with no spaces and no accents ("prefer_not_to_say");
 *  - the message of `throw`/`require`/`check` (for the logs, never shown) and emoji or symbols used as icons;
 *  - lines marked `PENDING_PART_B`: a text that auth/quiz/home/social still use and Part B will move;
 *  - lines that say "Figma", "SUPUESTO" or "Regla": developer notes of the badge catalog, not shown to the person.
 */
class NoHardcodedTextTest {

    /**
     * Files the guard does not read, each with the reason. The list must stay short (4 files, end of Stage 3).
     * A file listed here that no longer exists fails the test, so the list cannot rot.
     *  - `data/device/DeviceIdentity.kt` and `data/device/SupabaseDeviceRepository.kt`: the default NAME of the phone
     *    ("Dispositivo Android", "Dispositivo") is DATA saved in the `devices` table, not a screen text; it cannot depend on the
     *    language of the moment. The list of devices already shows "Dispositivo sin nombre" from a resource when the name is blank.
     *  - `data/supabase/SupabaseErrors.kt`: English words of the server messages that are matched ("row-level security") and
     *    the label of a cause that goes to the logs.
     *  - `di/AppConfig.kt`: the developer message when `local.properties` lacks a key; it is for whoever builds the app, not
     *    for the person who learns, and it needs no translation.
     */
    private val exceptionFiles = listOf(
        "data/device/DeviceIdentity.kt",
        "data/device/SupabaseDeviceRepository.kt",
        "data/supabase/SupabaseErrors.kt",
        "di/AppConfig.kt",
    )

    private fun mainRoot(): File = listOf(
        File("src/main/java/com/example/nightwatchprotocol"),
        File("app/src/main/java/com/example/nightwatchprotocol"),
    ).first { it.isDirectory }

    private val literal = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** A literal that a person would read: has letters and a space, an accent, or a capital followed by lowercase. */
    private fun looksLikeUserText(content: String): Boolean {
        val withoutTemplates = content.replace(Regex("\\$\\{[^}]*}|\\$[A-Za-z_]\\w*"), "").replace(Regex("%\\d?\\$?[sd]"), "")
        if (withoutTemplates.none { it.isLetter() }) return false
        // A technical identifier with slashes and no spaces: "America/Bogota", "application/json".
        if (Regex("^[A-Za-z0-9_.+-]+(/[A-Za-z0-9_.+-]+)+$").matches(withoutTemplates)) return false
        // Accented Latin letters only (code < 0x250): emoji and symbols such as "ℹ" are icons, not words.
        return withoutTemplates.contains(' ') || withoutTemplates.any { it.isLetter() && it.code in 128..0x24F } ||
            Regex("^[A-Z][a-z]").containsMatchIn(withoutTemplates)
    }

    private fun offendingLines(file: File): List<String> {
        val found = mutableListOf<String>()
        var skippingPreview = false
        var inBlockComment = false
        var inExceptionBlock = false
        var inPreviewHelper = false
        file.readLines().forEachIndexed { index, raw ->
            val line = raw.trim()
            // `require(cond) {` / `check(cond) {` followed by the message on its own lines: logs only, never shown.
            if (inExceptionBlock) {
                if (line == "}") inExceptionBlock = false
                return@forEachIndexed
            }
            if (Regex("^(require|check|requireNotNull|checkNotNull)\\(.*\\)\\s*\\{$").containsMatchIn(line)) {
                inExceptionBlock = true
                return@forEachIndexed
            }
            if (inBlockComment) {
                if (line.contains("*/")) inBlockComment = false
                return@forEachIndexed
            }
            if (line.startsWith("/*")) {
                if (!line.contains("*/")) inBlockComment = true
                return@forEachIndexed
            }
            if (skippingPreview) {
                if (raw == "}") skippingPreview = false
                return@forEachIndexed
            }
            // The sample-data helpers of the previews (`private fun previewState(...)`, `PreviewEdit(...)`): from the line that
            // declares them to the next blank line. They are only called by @Preview functions.
            if (inPreviewHelper) {
                if (raw.isBlank()) inPreviewHelper = false
                return@forEachIndexed
            }
            if (Regex("^private fun [pP]review\\w*\\(").containsMatchIn(raw)) {
                inPreviewHelper = true
                return@forEachIndexed
            }
            if (line.startsWith("@Preview")) {
                skippingPreview = true
                return@forEachIndexed
            }
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("import ") || line.startsWith("package ")) return@forEachIndexed
            if (raw.contains("PENDING_PART_B") || line.contains("Figma") || line.contains("SUPUESTO") || line.contains("Regla")) {
                return@forEachIndexed
            }
            // The message of an exception is for the logs and is never shown: the screens show their own resource text.
            if (line.contains("throw ") || line.startsWith("error(") || line.contains("require(") || line.contains("check(") ||
                line.contains("Exception(") || line.contains("Log.") || line.contains("TAG =") || line.startsWith("@Suppress")
            ) {
                return@forEachIndexed
            }
            val code = line.substringBefore(" // ")
            if (literal.findAll(code).any { looksLikeUserText(it.groupValues[1]) }) found += "${file.name}:${index + 1}: $line"
        }
        return found
    }

    @Test
    fun theWholeApp_hasNoFixedUserText() {
        val root = mainRoot()
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("no se encontraron los archivos de la app", files.size > 200)
        exceptionFiles.forEach { assertTrue("la excepción $it ya no existe: quítala de la lista", File(root, it).isFile) }

        val offenders = files
            .filter { file -> exceptionFiles.none { file.invariantSeparatorsPath.endsWith("/$it") } }
            .flatMap { offendingLines(it) }

        assertTrue(
            "Texto fijo en el código (muévelo a strings_*.xml con UiText/stringResource):\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun thePendingMarkers_areGone() {
        val root = mainRoot()
        val left = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("PENDING_PART_B") }.map { it.name }.toList()
        assertTrue("quedan marcadores PENDING_PART_B en: $left", left.isEmpty())
    }

    @Test
    fun theGuard_recognizesWhatIsAndIsNotUserText() {
        assertTrue(looksLikeUserText("Guardar cambios"))
        assertTrue(looksLikeUserText("Sin conexión"))
        assertTrue(looksLikeUserText("Usuario"))
        assertTrue(!looksLikeUserText("prefer_not_to_say"))
        assertTrue(!looksLikeUserText("—"))
        assertTrue(!looksLikeUserText("%1\$d"))
        assertTrue(!looksLikeUserText("\${count}"))
        assertTrue(!looksLikeUserText("application/json"))
    }
}
