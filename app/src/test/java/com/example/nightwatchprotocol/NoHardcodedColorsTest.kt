package com.example.nightwatchprotocol

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard of the theme (Module 5, Stages 4 and 5): a color of the interface comes from `NightWatchTheme.colors` (or the
 * `NightWatch*` accessors), never from `Color(0x…)`, `Color.Black` or `Color.White` written in a screen. Only `ui/theme` may
 * define colors.
 *
 * It covers all of `app/src/main/java` (not only `ui/`). The only exceptions are in [PERMANENT_EXCEPTIONS], each with its
 * reason; the list is a **ratchet** in both directions: a file that is not listed must have no hard-coded color, and a listed
 * file that no longer has one must be removed.
 *
 * Like `NoHardcodedTextTest` it reads the source code. It ignores comments and the `@Preview` functions (sample data).
 */
class NoHardcodedColorsTest {

    /**
     * Files (relative to `app/src/main/java/com/example/nightwatchprotocol`) that keep a raw color on purpose.
     * - `ui/social/SocialAvatar.kt`: `#B07A4F` is the fill of the photo bitmap of the person's own avatar; it is data drawn into
     *   a bitmap, it does not depend on the theme (spec 4.3).
     */
    private val PERMANENT_EXCEPTIONS = setOf(
        "ui/social/SocialAvatar.kt",
    )

    private val hardcodedColor = Regex("""\bColor\(\s*0x[0-9A-Fa-f_]+|\bColor\.(Black|White)\b""")

    private fun sourceRoot(): File = listOf(
        File("src/main/java/com/example/nightwatchprotocol"),
        File("app/src/main/java/com/example/nightwatchprotocol"),
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

    private fun outsideTheme(root: File): List<File> =
        kotlinFiles(root).filter { !relative(root, it).startsWith("ui/theme/") }

    @Test
    fun onlyTheThemeDefinesColors_inAllOfMain() {
        val root = sourceRoot()
        val offenders = outsideTheme(root)
            .filter { relative(root, it) !in PERMANENT_EXCEPTIONS }
            .flatMap { file -> offendingLines(file).map { "${relative(root, file)} -> $it" } }
        assertTrue("Hard-coded colors outside ui/theme (use NightWatchTheme.colors):\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun everyException_stillHoldsARawColor() {
        val root = sourceRoot()
        val withColors = outsideTheme(root).filter { offendingLines(it).isNotEmpty() }.map { relative(root, it) }.toSet()
        assertEquals("Listed in PERMANENT_EXCEPTIONS but already clean (remove them)", PERMANENT_EXCEPTIONS, withColors)
    }

    @Test
    fun theThemePackage_isTheOnlyPlaceWithRawColors() {
        // A sanity check of the guard itself: ui/theme does hold raw colors (the palettes), so the scanner finds them.
        val themeFiles = kotlinFiles(File(sourceRoot(), "ui/theme"))
        assertTrue(themeFiles.any { offendingLines(it).isNotEmpty() })
    }
}
