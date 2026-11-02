package com.example.nightwatchprotocol

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard of Stage 4 (theme) of Module 5: a color of the interface comes from `NightWatchTheme.colors` (or the `NightWatch*`
 * accessors), never from `Color(0x…)`, `Color.Black` or `Color.White` written in a screen. Only `ui/theme` may define colors.
 *
 * It covers the packages already migrated to the light theme ([migratedPackages]). The rest of `ui/` is listed in
 * [PENDING_STAGE_5]: Stage 5 moves each block and removes its files from the list. The list is a **ratchet**: a pending file
 * that no longer has a hard-coded color must be removed from it, and a file that is not listed must have none, so the list
 * can only shrink.
 *
 * Like `NoHardcodedTextTest` it reads the source code. It ignores comments and the `@Preview` functions (sample data).
 */
class NoHardcodedColorsTest {

    /** Packages (under `ui/`) that are fully on tokens: no pending list for them. */
    private val migratedPackages = listOf("profile", "verifycode", "components")

    /**
     * The files of `ui/` outside the migrated packages that still hold a hard-coded color, to migrate in Stage 5 (5a auth,
     * 5b quiz, 5c home/path/labs/lesson/lab/exam, 5d social). `social/SocialAvatar.kt` also keeps `#B07A4F`, which is drawn
     * into a bitmap and does not depend on the theme (spec 4.3): it will stay on the permanent allowlist.
     */
    private val PENDING_STAGE_5 = setOf(
        "auth/forgotpassword/PasswordRecoveryDialog.kt",
        "auth/register/RegisterScreen.kt",
        "exam/ExamIntroScreen.kt",
        "exam/ExamScreen.kt",
        "examresult/ExamResultScreen.kt",
        "home/HomeScreen.kt",
        "home/HomeTopBar.kt",
        "home/NightWatchBottomBar.kt",
        "home/Notices.kt",
        "lab/LabScreen.kt",
        "labreport/LabReportScreen.kt",
        "labs/LabsScreen.kt",
        "lesson/LessonScreen.kt",
        "path/LearningPathScreen.kt",
        "quiz/PlacementQuizScreen.kt",
        "quiz/PlacementResultScreen.kt",
        "social/ProfileSections.kt",
        "social/QuickSearchScreen.kt",
        "social/SocialAvatar.kt",
        "social/SocialComponents.kt",
    )

    private val hardcodedColor = Regex("""\bColor\(\s*0x[0-9A-Fa-f_]+|\bColor\.(Black|White)\b""")

    private fun uiRoot(): File = listOf(
        File("src/main/java/com/example/nightwatchprotocol/ui"),
        File("app/src/main/java/com/example/nightwatchprotocol/ui"),
    ).first { it.isDirectory }

    /** The lines with a hard-coded color, without comments and without `@Preview` functions. */
    private fun offendingLines(file: File): List<String> {
        val found = mutableListOf<String>()
        var inBlockComment = false
        var previewPending = false
        var inPreview = false
        var depth = 0
        file.readLines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (inBlockComment) {
                if (line.contains("*/")) inBlockComment = false
                return@forEachIndexed
            }
            if (line.startsWith("/*")) {
                if (!line.contains("*/")) inBlockComment = true
                return@forEachIndexed
            }
            if (line.startsWith("//") || line.startsWith("*")) return@forEachIndexed
            if (line.startsWith("@Preview")) previewPending = true
            if (previewPending && line.contains("fun ")) {
                previewPending = false
                inPreview = true
                depth = 0
            }
            if (inPreview) {
                depth += raw.count { it == '{' } - raw.count { it == '}' }
                if (depth <= 0 && raw.contains('}')) inPreview = false
                return@forEachIndexed
            }
            // `@Preview(backgroundColor = 0xFF…)` is an annotation constant, not a Color(): it never matches the regex.
            val code = line.substringBefore("//")
            if (hardcodedColor.containsMatchIn(code)) found += "${file.name}:${index + 1}: $line"
        }
        return found
    }

    private fun relative(root: File, file: File): String = file.relativeTo(root).path.replace('\\', '/')

    private fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun onlyTheThemeDefinesColors_inTheMigratedPackages() {
        val root = uiRoot()
        val offenders = migratedPackages.flatMap { pkg ->
            kotlinFiles(File(root, pkg)).flatMap { offendingLines(it).map { line -> "$pkg/${relative(File(root, pkg), it)} -> $line" } }
        }
        assertTrue("Hard-coded colors in migrated packages (use NightWatchTheme.colors):\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun theRestOfUi_hardCodedColorsAreExactlyThePendingList() {
        val root = uiRoot()
        val outside = kotlinFiles(root).filter { file ->
            val rel = relative(root, file)
            !rel.startsWith("theme/") && migratedPackages.none { rel.startsWith("$it/") }
        }
        val withColors = outside.filter { offendingLines(it).isNotEmpty() }.map { relative(root, it) }.toSet()

        val unlisted = withColors - PENDING_STAGE_5
        val staleEntries = PENDING_STAGE_5 - withColors
        assertTrue("Files with hard-coded colors that are not in PENDING_STAGE_5: $unlisted", unlisted.isEmpty())
        assertTrue("Listed in PENDING_STAGE_5 but already clean (remove them): $staleEntries", staleEntries.isEmpty())
    }

    @Test
    fun theThemePackage_isTheOnlyPlaceWithRawColors() {
        // A sanity check of the guard itself: ui/theme does hold raw colors (the palettes), so the scanner finds them.
        val themeFiles = kotlinFiles(File(uiRoot(), "theme"))
        assertTrue(themeFiles.any { offendingLines(it).isNotEmpty() })
    }
}
