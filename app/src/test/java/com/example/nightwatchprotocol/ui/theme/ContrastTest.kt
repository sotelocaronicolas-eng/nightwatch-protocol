package com.example.nightwatchprotocol.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision D6 of Module 5: text >= 4.5:1 (>= 3:1 when it is large: 18 sp, or 14 sp bold), UI components >= 3:1. The pairs
 * are the text/background and component/background combinations the screens really use, for both palettes.
 *
 * The **dark** pairs that were already below the goal before Module 5 are listed in [knownDarkShortfalls]: the rule "dark does
 * not change" means they are documented here and NOT fixed. The test keeps that list honest: a listed pair that starts
 * passing (or whose ratio moves) fails, so the list cannot rot, and a pair not listed that fails is a new problem.
 */
class ContrastTest {

    private class Pair(
        val name: String,
        val fg: Color,
        val bg: Color,
        val min: Double,
    )

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [top] (with its alpha) drawn over an opaque [under]. */
    private fun over(top: Color, under: Color): Color {
        val a = top.alpha
        return Color(
            red = top.red * a + under.red * (1 - a),
            green = top.green * a + under.green * (1 - a),
            blue = top.blue * a + under.blue * (1 - a),
        )
    }

    private val text = 4.5
    private val large = 3.0
    private val component = 3.0

    private fun pairs(c: NightWatchColors): List<Pair> = listOf(
        Pair("textPrimary/bgBase", c.textPrimary, c.bgBase, text),
        Pair("textSecondary/bgBase", c.textSecondary, c.bgBase, text),
        Pair("topBarText/bgBase", c.topBarText, c.bgBase, text),
        Pair("onElevated/bgElevated", c.onElevated, c.bgElevated, text),
        Pair("textOnLight/surfaceButton", c.textOnLight, c.surfaceButton, text),
        Pair("textOnLight/fieldSurface", c.textOnLight, c.fieldSurface, text),
        Pair("textOnLight/ctaSurface", c.textOnLight, c.ctaSurface, text),
        Pair("onButton/ctaSurface", c.onButton, c.ctaSurface, text),
        Pair("onSurfaceButton/surfaceButton", c.onSurfaceButton, c.surfaceButton, text),
        Pair("onSurfaceButton/fieldSurface", c.onSurfaceButton, c.fieldSurface, text),
        Pair("onSurfaceButton/headerPanel", c.onSurfaceButton, c.headerPanel, text),
        Pair("fieldText/fieldSurface", c.fieldText, c.fieldSurface, text),
        Pair("textMutedOnLight/surfaceButton", c.textMutedOnLight, c.surfaceButton, text),
        Pair("textMutedOnLight/fieldSurface", c.textMutedOnLight, c.fieldSurface, text),
        Pair("textSoftOnLight/headerPanel (18 sp bold)", c.textSoftOnLight, c.headerPanel, large),
        Pair("textError/bgBase", c.textError, c.bgBase, text),
        Pair("textError/fieldSurface", c.textError, c.fieldSurface, text),
        Pair("success/bgBase", c.success, c.bgBase, text),
        Pair("link/bgBase", c.link, c.bgBase, text),
        Pair("onLink/link", c.onLink, c.link, text),
        Pair("textPrimary/confirmationPanel", c.textPrimary, c.confirmationPanel, text),
        Pair("textSecondary/confirmationPanel", c.textSecondary, c.confirmationPanel, text),
        Pair("textPrimary/menuPill over bgBase", c.textPrimary, over(c.menuPill, c.bgBase), text),
        Pair("selectionCheck/surfaceButton", c.selectionCheck, c.surfaceButton, text),
        // Tokens of Stage 5.
        Pair("textOnLight/noticeSurface", c.textOnLight, c.noticeSurface, text),
        Pair("textOnLight/cardTint", c.textOnLight, c.cardTint, text),
        Pair("brandWhite/overlayDark (worst case, over white)", c.brandWhite, over(c.overlayDark, Color.White), text),
        // The REFUERZO chips of CyberMind: white text over the orange fill.
        Pair("brandWhite/reinforce", c.brandWhite, c.reinforce, text),
        // The gold as text, over every background it is drawn on (contrast fix).
        Pair("goldText/bgBase", c.goldText, c.bgBase, text),
        Pair("goldText/bgElevated", c.goldText, c.bgElevated, text),
        Pair("goldText/surfaceButton", c.goldText, c.surfaceButton, text),
        Pair("goldText/noticeSurface", c.goldText, c.noticeSurface, text),
        Pair("onGold/selection", c.onGold, c.selection, text),
        // Over the profile pictures (with the 45 % dark layer): the darkest pixel and the lightest pixel measured in the five pictures
        // (the lightest is #8B8B8B, 3.4:1 with white; 1.9 % of profile_bg_5 and 0.2 % of profile_bg_1 are under 4.5:1).
        Pair("onBackdrop/darkest picture pixel", c.onBackdrop, Color.Black, text),
        Pair("onBackdrop/lightest picture pixel (large text or glyph)", c.onBackdrop, Color(0xFF8B8B8B), large),
        Pair("backdropAccent/darkest picture pixel", c.backdropAccent, Color.Black, text),
        // Components (borders, fills and glyphs that carry meaning).
        Pair("selection/surfaceButton", c.selection, c.surfaceButton, component),
        Pair("selection/bgBase", c.selection, c.bgBase, component),
        Pair("borderAccent/bgBase", c.borderAccent, c.bgBase, component),
        Pair("borderAccent/fieldSurface", c.borderAccent, c.fieldSurface, component),
        Pair("error/bgBase", c.error, c.bgBase, component),
        Pair("error/fieldSurface", c.error, c.fieldSurface, component),
        Pair("divider/fieldSurface", c.divider, c.fieldSurface, component),
        Pair("divider/surfaceButton", c.divider, c.surfaceButton, component),
        Pair("headerLine/bgBase", c.headerLine, c.bgBase, component),
        Pair("codeBorder/bgBase", c.codeBorder, c.bgBase, component),
        Pair("switchOn/bgBase", c.switchOn, c.bgBase, component),
        Pair("switchOff or its border/bgBase", if (c.isLight) c.switchBorder else c.switchOff, c.bgBase, component),
        // Tokens of Stage 5.
        Pair("sheetBorder/bgBase", c.sheetBorder, c.bgBase, component),
        Pair("failAccent/bgBase", c.failAccent, c.bgBase, component),
        Pair("failAccent/bgElevated", c.failAccent, c.bgElevated, component),
        Pair("failAccent/cardTint", c.failAccent, c.cardTint, component),
        Pair("selection/bgElevated", c.selection, c.bgElevated, component),
        // The ring of the gold icon circles (lesson, exam intro, finished island) against the gold fill.
        Pair("bgElevated ring/selection", c.bgElevated, c.selection, component),
    )

    /**
     * The dark pairs below the goal. They existed before Module 5 and stay: "the dark look does not change". Each value is the
     * ratio, rounded to two decimals, that the test must still measure. Fixing any of them is a task for the group (spec 12).
     */
    private val knownDarkShortfalls = mapOf(
        // Spec section 2.2-4 / 12 (already known).
        "fieldText/fieldSurface" to 2.58, // white text on the #A1A1A1 field
        "textError/bgBase" to 2.10, // red error text on the background
        "success/bgBase" to 2.80, // green requirement on the background
        "textError/fieldSurface" to 1.44,
        "selectionCheck/surfaceButton" to 1.35, // gold check on the D9D9D9 card
        "borderAccent/bgBase" to 1.78, // decorative border
        "divider/surfaceButton" to 2.80, // decorative divider
        "error/bgBase" to 2.10,
        "error/fieldSurface" to 1.44,
        // Found while writing this test in Stage 4 (not in the spec's list): reported, not fixed.
        "textPrimary/menuPill over bgBase" to 2.54, // white name over the translucent pill of the user menu
        "textMutedOnLight/fieldSurface" to 4.00, // 404040 labels on the #A1A1A1 cards (devices status, profile labels)
        "textSecondary/confirmationPanel" to 3.45, // the CAD5D7 message over the 447579 panel
        "divider/fieldSurface" to 1.53, // 808080 divider on the A1A1A1 card
        "selection/surfaceButton" to 1.35, // the gold border of the selected row on its D9D9D9 card (the spec's gold-over-card case)
        "borderAccent/fieldSurface" to 1.70, // decorative border of the A1A1A1 fields and cards
        // Found while writing the pairs of the Stage 5 tokens: the old dark colors, reported, not fixed.
        "sheetBorder/bgBase" to 1.42, // the 204143 border of the sheet of Inicio (decorative)
        "failAccent/bgBase" to 1.65, // the C0503E red of a lost battle over the background
        "failAccent/bgElevated" to 1.21, // the same red over the 3E6E71 cards
        "failAccent/cardTint" to 1.86, // the same red over the 94A6A7 cards
        "selection/bgElevated" to 2.99, // the gold over the 3E6E71 cards (just under 3:1)
        "bgElevated ring/selection" to 2.99, // the teal ring of the gold icon circles
        // Found while writing the pairs of the gold-text fix: the old dark gold as text, reported, not fixed.
        "goldText/bgBase" to 4.08, // gold labels over the background (Lab "Terminal", lesson module)
        "goldText/bgElevated" to 2.99, // gold values over the 3E6E71 cards (stats, commands)
        "goldText/surfaceButton" to 1.35, // the gold label of the selected tab of the bottom bar over D9D9D9
        "goldText/noticeSurface" to 1.33, // the gold text over the 94A6A7 chapa/cards
    )

    private fun rounded(value: Double): Double = kotlin.math.round(value * 100) / 100

    @Test
    fun lightPalette_meetsTheGoalEverywhere() {
        val failures = pairs(LightNightWatchColors)
            .filter { ratio(it.fg, it.bg) < it.min }
            .map { "${it.name}: ${rounded(ratio(it.fg, it.bg))} < ${it.min}" }
        assertTrue("Light pairs below the goal: $failures", failures.isEmpty())
    }

    @Test
    fun darkPalette_failsOnlyTheDocumentedPairs() {
        val failing = pairs(DarkNightWatchColors).filter { ratio(it.fg, it.bg) < it.min }
        val unexpected = failing.filter { it.name !in knownDarkShortfalls }
            .map { "${it.name}: ${rounded(ratio(it.fg, it.bg))} < ${it.min}" }
        assertTrue("New dark pairs below the goal (document or fix): $unexpected", unexpected.isEmpty())
    }

    @Test
    fun knownDarkShortfalls_areStillShortfalls() {
        val byName = pairs(DarkNightWatchColors).associateBy { it.name }
        val stale = knownDarkShortfalls.keys.filter { name ->
            val pair = byName[name]
            pair == null || ratio(pair.fg, pair.bg) >= pair.min
        }
        assertTrue("Listed as a shortfall but missing or passing now (remove it): $stale", stale.isEmpty())
    }

    @Test
    fun knownDarkShortfalls_keepTheirMeasuredRatio() {
        val byName = pairs(DarkNightWatchColors).associateBy { it.name }
        knownDarkShortfalls.forEach { (name, expected) ->
            val pair = byName.getValue(name)
            val measured = rounded(ratio(pair.fg, pair.bg))
            assertEquals("The ratio of $name moved: the dark colors changed", expected, measured, 0.015)
        }
    }

    @Test
    fun theValuesTheSpecQuotes_areTheOnesMeasured() {
        // Section 4.1 of the spec, a few anchors: if these drift the table of the spec is wrong.
        assertEquals(11.79, ratio(LightNightWatchColors.textPrimary, LightNightWatchColors.bgBase), 0.01)
        assertEquals(3.46, ratio(LightNightWatchColors.selection, LightNightWatchColors.surfaceButton), 0.01)
        assertEquals(10.43, ratio(LightNightWatchColors.selectionCheck, LightNightWatchColors.surfaceButton), 0.01)
        assertEquals(5.28, ratio(LightNightWatchColors.success, LightNightWatchColors.bgBase), 0.01)
        assertEquals(7.79, ratio(DarkNightWatchColors.textPrimary, DarkNightWatchColors.bgBase), 0.01)
        assertEquals(5.72, ratio(DarkNightWatchColors.onElevated, DarkNightWatchColors.bgElevated), 0.01)
    }
}
