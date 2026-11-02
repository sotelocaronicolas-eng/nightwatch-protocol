package com.example.nightwatchprotocol

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard of Stage 3 (F-09) of Module 5: the areas already moved to string resources must not get fixed Spanish text again.
 * It reads the source code (like `PersonalizationBoundaryTest`) and looks for string literals that read like a sentence or a
 * label. Each part of the stage adds its packages to [guardedFolders]/[guardedFiles] as it migrates them (B2: lesson, lab,
 * exam; B3: social).
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

    private val guardedFolders = listOf(
        // Part A: Perfil y Ajustes
        "ui/profile",
        "ui/verifycode",
        "data/profile",
        // Part B1: autenticación, quiz, inicio, navegación, bloqueo de la app y lo compartido
        "ui/auth",
        "ui/quiz",
        "ui/home",
        "ui/navigation",
        "ui/applock",
        "ui/biometric",
        "ui/common",
        "ui/components",
        // Part B2: aprendizaje (lección, lab, informe, labs, ruta, examen, resultado) y su lógica
        "ui/lesson",
        "ui/lab",
        "ui/labreport",
        "ui/labs",
        "ui/path",
        "ui/exam",
        "ui/examresult",
        "data/learning",
    )

    private val guardedFiles = listOf(
        "MainActivity.kt",
        "data/auth/AuthError.kt",
        "data/quiz/StudyPlan.kt",
        "data/learning/Badges.kt",
        "data/notifications/ReminderKind.kt",
        "data/device/DeviceFormatters.kt",
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
            if (line.startsWith("@Preview")) {
                skippingPreview = true
                return@forEachIndexed
            }
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("import ") || line.startsWith("package ")) return@forEachIndexed
            if (raw.contains("PENDING_PART_B") || line.contains("Figma") || line.contains("SUPUESTO") || line.contains("Regla")) {
                return@forEachIndexed
            }
            // The message of an exception is for the logs and is never shown: the screens show their own resource text.
            if (line.contains("throw ") || line.startsWith("error(") || line.contains("require(") || line.contains("check(")) {
                return@forEachIndexed
            }
            val code = line.substringBefore(" // ")
            if (literal.findAll(code).any { looksLikeUserText(it.groupValues[1]) }) found += "${file.name}:${index + 1}: $line"
        }
        return found
    }

    @Test
    fun theMigratedAreas_haveNoFixedUserText() {
        val root = mainRoot()
        val files = guardedFolders.flatMap { folder ->
            File(root, folder).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        } + guardedFiles.map { File(root, it) }
        assertTrue("no se encontraron los archivos protegidos", files.size > 40)
        files.forEach { assertTrue("no existe ${it.path}", it.isFile) }

        val offenders = files.flatMap { offendingLines(it) }

        assertTrue(
            "Texto fijo en código ya migrado (muévelo a strings_*.xml con UiText/stringResource):\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
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
