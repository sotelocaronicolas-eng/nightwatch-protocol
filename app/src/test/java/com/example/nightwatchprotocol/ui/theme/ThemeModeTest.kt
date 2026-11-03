package com.example.nightwatchprotocol.ui.theme

import com.example.nightwatchprotocol.data.preferences.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a [ThemeMode] becomes a palette: DARK and LIGHT are fixed, SYSTEM follows the phone. */
class ThemeModeTest {

    @Test
    fun dark_isAlwaysDark_whateverThePhoneSays() {
        assertTrue(isDarkTheme(ThemeMode.DARK, systemDark = true))
        assertTrue(isDarkTheme(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun light_isAlwaysLight_whateverThePhoneSays() {
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun system_followsThePhone() {
        assertTrue(isDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun theDefaultLook_isDark() {
        assertEquals(ThemeMode.DARK, ThemeMode.DEFAULT)
        assertTrue(isDarkTheme(ThemeMode.DEFAULT, systemDark = false))
    }

    @Test
    fun theTwoPalettes_areDifferentAndFlagged() {
        assertFalse(DarkNightWatchColors.isLight)
        assertTrue(LightNightWatchColors.isLight)
        assertNotEquals(DarkNightWatchColors.bgBase, LightNightWatchColors.bgBase)
        assertNotEquals(DarkNightWatchColors.textPrimary, LightNightWatchColors.textPrimary)
    }

    @Test
    fun theMaterialSchemes_followTheirPalette() {
        assertEquals(DarkNightWatchColors.bgBase, NightWatchDarkScheme.background)
        assertEquals(LightNightWatchColors.bgBase, NightWatchLightScheme.background)
        assertEquals(LightNightWatchColors.textPrimary, NightWatchLightScheme.onBackground)
        assertEquals(LightNightWatchColors.error, NightWatchLightScheme.error)
        // The snackbar roles of the light scheme are the app's, not Material's defaults.
        assertEquals(LightNightWatchColors.textPrimary, NightWatchLightScheme.inverseSurface)
        assertEquals(LightNightWatchColors.fieldSurface, NightWatchLightScheme.inverseOnSurface)
    }

    @Test
    fun theLightSelection_usesTheAdjustedGoldAndTheDarkCheck() {
        // Decision A2: #926A1B for the border, #1C2B29 for the check.
        assertEquals(androidx.compose.ui.graphics.Color(0xFF926A1B), LightNightWatchColors.selection)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF1C2B29), LightNightWatchColors.selectionCheck)
    }
}
