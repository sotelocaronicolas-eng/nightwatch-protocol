package com.example.nightwatchprotocol.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Module 5 rule: **the dark look does not change.** Every dark token is compared with the value the constant (or the
 * hard-coded color) had before the theme was split in two. If this test fails, someone changed how the app looks in dark.
 */
class NightWatchColorsParityTest {

    private val dark = DarkNightWatchColors

    private fun assertSame(name: String, original: Color, actual: Color) =
        assertEquals("$name changed in the dark theme", original, actual)

    @Test
    fun darkTokens_matchTheOriginalConstants() {
        // The old ui/theme/Color.kt constants.
        assertSame("NightWatchBackground", Color(0xFF2C595D), dark.bgBase)
        assertSame("NightWatchSurfaceButton", Color(0xFFD9D9D9), dark.surfaceButton)
        assertSame("NightWatchSurfaceButtonMuted", Color(0xFFA1A1A1), dark.surfaceButtonMuted)
        assertSame("NightWatchBorderAccent", Color(0xFF597F82), dark.borderAccent)
        assertSame("NightWatchLinkAccent", Color(0xFFA2DBFF), dark.link)
        assertSame("NightWatchHeaderPanel", Color(0xFFCAD5D7), dark.headerPanel)
        assertSame("NightWatchOnBackground", Color.White, dark.textPrimary)
        assertSame("NightWatchOnButton", Color.Black, dark.onButton)
        assertSame("NightWatchError", Color(0xFFFF2E16), dark.error)
        assertSame("NightWatchSuccess", Color(0xFF4CAF50), dark.success)
    }

    @Test
    fun darkTokens_matchTheLooseColorsOfTheCode() {
        // The copies that were scattered in ui/components and ui/profile.
        assertSame("NightWatchGold / SwitchOnColor", Color(0xFFE3B54F), dark.selection)
        assertSame("SwitchOnColor", Color(0xFFE3B54F), dark.switchOn)
        assertSame("selection check (the check was gold)", Color(0xFFE3B54F), dark.selectionCheck)
        assertSame("SwitchOffColor", Color(0xFFA1A1A1), dark.switchOff)
        assertSame("switch thumb", Color.White, dark.switchThumb)
        assertSame("ConfirmationPanelColor", Color(0xFF447579), dark.confirmationPanel)
        assertSame("RowTextColor / BadgeTextColor / 0xFF162523", Color(0xFF162523), dark.textOnLight)
        assertSame("TopBarTextColor", Color(0xFFDDDDDD), dark.topBarText)
        assertSame("menu pill", Color(0x99D9D9D9), dark.menuPill)
        assertSame("LockedCircle", Color(0xFF3E6E71), dark.lockedBadge)
        assertSame("elevated card (0xFF3E6E71)", Color(0xFF3E6E71), dark.bgElevated)
        assertSame("DeviceLabelColor / LabelColor", Color(0xFF404040), dark.textMutedOnLight)
        assertSame("soft label (0xFF5E5E5E)", Color(0xFF5E5E5E), dark.textSoftOnLight)
        assertSame("DividerColor", Color(0xFF808080), dark.divider)
        assertSame("white lines and borders", Color.White, dark.headerLine)
        assertSame("code box border", Color.White, dark.codeBorder)
        assertSame("Color.Black on light surfaces", Color.Black, dark.onSurfaceButton)
        assertSame("Color.Black over the link button", Color.Black, dark.onLink)
        assertSame("Google button", Color.White, dark.brandWhite)
    }

    @Test
    fun darkTokens_matchTheStage5Colors() {
        // The loose colors of the screens migrated in Stage 5.
        assertSame("SheetBorderColor (Inicio)", Color(0xFF204143), dark.sheetBorder)
        assertSame("PillColor (Inicio, Ruta)", Color(0xFF94A6A7), dark.noticeSurface)
        assertSame("SocialCardTint", Color(0xFF94A6A7), dark.cardTint)
        assertSame("SocialFail", Color(0xFFC0503E), dark.failAccent)
        assertSame("BackgroundScrim", Color(0x73000000), dark.scrimSoft)
        assertSame("edit-background button", Color(0xCC333333), dark.overlayDark)
        assertSame("background picker", Color(0xFF8C8C8C), dark.pickerSurface)
    }

    @Test
    fun theTokensThatDoNotDependOnTheTheme_areTheSameInLight() {
        assertEquals(dark.scrimSoft, LightNightWatchColors.scrimSoft)
        assertEquals(dark.overlayDark, LightNightWatchColors.overlayDark)
        assertEquals(dark.pickerSurface, LightNightWatchColors.pickerSurface)
        assertEquals(dark.brandWhite, LightNightWatchColors.brandWhite)
    }

    @Test
    fun darkTokens_matchWhatTheFieldsAndButtonsUsed() {
        // NightWatchTextField / SelectField: container A1A1A1, white text, red error. NightWatchButton: D9D9D9 with black text.
        assertSame("field container", Color(0xFFA1A1A1), dark.fieldSurface)
        assertSame("field text", Color.White, dark.fieldText)
        assertSame("error supporting text", Color(0xFFFF2E16), dark.textError)
        assertSame("button container", Color(0xFFD9D9D9), dark.ctaSurface)
        assertSame("secondary text over the background (it was NightWatchHeaderPanel)", Color(0xFFCAD5D7), dark.textSecondary)
        assertSame("text over elevated cards", Color.White, dark.onElevated)
    }

    @Test
    fun darkMaterialScheme_keepsTheOldRoles() {
        val scheme = NightWatchDarkScheme
        assertSame("background", Color(0xFF2C595D), scheme.background)
        assertSame("surface", Color(0xFF2C595D), scheme.surface)
        assertSame("primary", Color(0xFFD9D9D9), scheme.primary)
        assertSame("onPrimary", Color.Black, scheme.onPrimary)
        assertSame("onBackground", Color.White, scheme.onBackground)
        assertSame("onSurface", Color.White, scheme.onSurface)
        assertSame("error", Color(0xFFFF2E16), scheme.error)
    }
}
