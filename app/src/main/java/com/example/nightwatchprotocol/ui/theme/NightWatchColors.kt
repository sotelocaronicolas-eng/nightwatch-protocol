package com.example.nightwatchprotocol.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The semantic colours of the app (Module 5, decision D3). Material 3 does not cover all of them, so they travel in their
 * own `CompositionLocal` next to the Material color scheme. Read them with [NightWatchTheme.colors], or with the
 * `NightWatch*` accessors of `Color.kt`, which answer to the current theme.
 *
 * **[DarkNightWatchColors] is the app as it has always looked: every dark value is the one of the old constants**
 * (`NightWatchColorsParityTest` keeps it that way). The light values come from the spec of Module 5 (section 4); the ones
 * marked PROPUESTA there are not in the Figma.
 */
@Immutable
class NightWatchColors(
    /** True for the light palette. Lets a screen add what only the light theme needs (the ⚠ mark of an error). */
    val isLight: Boolean,
    /** Background of every screen. */
    val bgBase: Color,
    /** Main text over [bgBase]. */
    val textPrimary: Color,
    /** Secondary text over [bgBase] (captions, hints, notices). */
    val textSecondary: Color,
    /** Cards over [bgBase] (labs, exam, Social). */
    val bgElevated: Color,
    /** Text over [bgElevated]. */
    val onElevated: Color,
    /** Rows and option cards, secondary buttons. */
    val surfaceButton: Color,
    /** Muted surface (locked cards, the switch when it is off). */
    val surfaceButtonMuted: Color,
    /** Background of the text fields and of the information cards. */
    val fieldSurface: Color,
    /** Text typed in a field, over [fieldSurface]. */
    val fieldText: Color,
    /** Text over [surfaceButton], white or [fieldSurface] surfaces. */
    val textOnLight: Color,
    /** Text of the primary button, over [ctaSurface]. */
    val onButton: Color,
    /** Text and glyphs over [surfaceButton] and [surfaceButtonMuted] (rows, small buttons, badges). */
    val onSurfaceButton: Color,
    /** Primary button. */
    val ctaSurface: Color,
    /** Border of fields and cards. */
    val borderAccent: Color,
    /** Border of the selected option (3 px) and the gold accents. */
    val selection: Color,
    /** The ✓ of the selected option. */
    val selectionCheck: Color,
    val divider: Color,
    /** Error borders and error emphasis. */
    val error: Color,
    /** Text of an error (in the light theme it is the main text color and a ⚠ mark goes with it, decision A1). */
    val textError: Color,
    /** A met requirement. */
    val success: Color,
    /** Panel of the confirmation screens. */
    val confirmationPanel: Color,
    /** Pill of the header of the user menu. */
    val menuPill: Color,
    /** Band of the header of "Mi Perfil". */
    val headerPanel: Color,
    /** Title of the top bars. */
    val topBarText: Color,
    /** The line under a top bar. */
    val headerLine: Color,
    val switchOn: Color,
    val switchOff: Color,
    val switchThumb: Color,
    val switchBorder: Color,
    /** Links and the "resend" style buttons. */
    val link: Color,
    /** Labels over light surfaces. */
    val textMutedOnLight: Color,
    /** Softer labels over light surfaces. */
    val textSoftOnLight: Color,
    /** Circle of a locked badge. */
    val lockedBadge: Color,
    /** White in both themes (brand surfaces such as the Google button). */
    val brandWhite: Color,
    /** Text over [link] when the link color is a button fill. */
    val onLink: Color,
    /** Border of the code boxes of the verification screen (the error state uses [error]). */
    val codeBorder: Color,
    /** Border of the bottom sheet of Inicio (Stage 5). */
    val sheetBorder: Color,
    /** Pills and notices of Inicio, over [bgBase] (Stage 5). */
    val noticeSurface: Color,
    /** Tint of the Social cards that show a state (Stage 5). */
    val cardTint: Color,
    /** The "lost" accent of the battles (Stage 5). */
    val failAccent: Color,
    /** Soft dark layer over the background images of a profile; it does not depend on the theme (Stage 5). */
    val scrimSoft: Color,
    /** Dark layer behind the white text over the background images; it does not depend on the theme (Stage 5). */
    val overlayDark: Color,
    /** Small panel of the background picker (Stage 5). */
    val pickerSurface: Color,
    /** The gold when it is TEXT (or a small glyph) over [bgBase], [bgElevated] or [surfaceButton]; [selection] stays for fills and borders. */
    val goldText: Color,
    /** Text and glyphs drawn over a [selection] fill (gold pills, chosen options, count badges). */
    val onGold: Color,
    /** Text and glyphs over a picture or a scrim (profile backgrounds). It does not depend on the theme. */
    val onBackdrop: Color,
    /** The gold accent over a picture or a scrim (the streak line of a profile). It does not depend on the theme. */
    val backdropAccent: Color,
)

val DarkNightWatchColors = NightWatchColors(
    isLight = false,
    bgBase = Color(0xFF2C595D),
    textPrimary = Color.White,
    textSecondary = Color(0xFFCAD5D7),
    bgElevated = Color(0xFF3E6E71),
    onElevated = Color.White,
    surfaceButton = Color(0xFFD9D9D9),
    surfaceButtonMuted = Color(0xFFA1A1A1),
    fieldSurface = Color(0xFFA1A1A1),
    fieldText = Color.White,
    textOnLight = Color(0xFF162523),
    onButton = Color.Black,
    onSurfaceButton = Color.Black,
    ctaSurface = Color(0xFFD9D9D9),
    borderAccent = Color(0xFF597F82),
    selection = Color(0xFFE3B54F),
    selectionCheck = Color(0xFFE3B54F),
    divider = Color(0xFF808080),
    error = Color(0xFFFF2E16),
    textError = Color(0xFFFF2E16),
    success = Color(0xFF4CAF50),
    confirmationPanel = Color(0xFF447579),
    menuPill = Color(0x99D9D9D9),
    headerPanel = Color(0xFFCAD5D7),
    topBarText = Color(0xFFDDDDDD),
    headerLine = Color.White,
    switchOn = Color(0xFFE3B54F),
    switchOff = Color(0xFFA1A1A1),
    switchThumb = Color.White,
    switchBorder = Color.Transparent,
    link = Color(0xFFA2DBFF),
    textMutedOnLight = Color(0xFF404040),
    textSoftOnLight = Color(0xFF5E5E5E),
    lockedBadge = Color(0xFF3E6E71),
    brandWhite = Color.White,
    onLink = Color.Black,
    codeBorder = Color.White,
    sheetBorder = Color(0xFF204143),
    noticeSurface = Color(0xFF94A6A7),
    cardTint = Color(0xFF94A6A7),
    failAccent = Color(0xFFC0503E),
    scrimSoft = Color(0x73000000),
    overlayDark = Color(0xCC333333),
    pickerSurface = Color(0xFF8C8C8C),
    goldText = Color(0xFFE3B54F),
    onGold = Color(0xFF162523),
    onBackdrop = Color.White,
    backdropAccent = Color(0xFFE3B54F),
)

val LightNightWatchColors = NightWatchColors(
    isLight = true,
    bgBase = Color(0xFFAEF5D5),
    textPrimary = Color(0xFF1C2B29),
    // PROPUESTA: the Figma has no secondary text color; the divider color of the Figma, 7.40:1 over the background.
    textSecondary = Color(0xFF3F4A44),
    // PROPUESTA (decision 4.1 of the spec).
    bgElevated = Color(0xFFF7F4EC),
    onElevated = Color(0xFF1C2B29),
    surfaceButton = Color(0xFFD9D9D9),
    surfaceButtonMuted = Color(0xFFA1A1A1),
    fieldSurface = Color(0xFFF7F4EC),
    fieldText = Color(0xFF1C2B29),
    textOnLight = Color(0xFF1C2B29),
    onButton = Color(0xFF1C2B29),
    onSurfaceButton = Color(0xFF1C2B29),
    ctaSurface = Color.White,
    borderAccent = Color(0xFF597F82),
    // A2: the minimal adjustment of the Figma's #B2842C, which has 2.39:1 over #D9D9D9.
    selection = Color(0xFF926A1B),
    selectionCheck = Color(0xFF1C2B29),
    divider = Color(0xFF3F4A44),
    error = Color(0xFFB85947),
    textError = Color(0xFF1C2B29),
    // PROPUESTA: #4CAF50 has 2.8:1 over the light background.
    success = Color(0xFF1B6B2A),
    confirmationPanel = Color.White,
    menuPill = Color(0x99FFFFFF),
    headerPanel = Color(0xFFF7F4EC),
    topBarText = Color(0xFF1C2B29),
    // PROPUESTA: the Figma has two different SVGs for this line; the main text color is the one that contrasts.
    headerLine = Color(0xFF1C2B29),
    // PROPUESTA (4.2): the Figma only has an SVG for the switch.
    switchOn = Color(0xFF926A1B),
    switchOff = Color(0xFFA1A1A1),
    switchThumb = Color.White,
    switchBorder = Color(0xFF597F82),
    // PROPUESTA (4.3).
    link = Color(0xFF0B5A8A),
    textMutedOnLight = Color(0xFF404040),
    textSoftOnLight = Color(0xFF5E5E5E),
    // PROPUESTA: unchanged from the dark theme (a small circle over a muted card).
    lockedBadge = Color(0xFF3E6E71),
    brandWhite = Color.White,
    onLink = Color.White,
    // PROPUESTA: the Figma does not show the code bar's idle border.
    codeBorder = Color(0xFF597F82),
    // PROPUESTA (4.3): the border of the sheet, over the light background 3.52:1.
    sheetBorder = Color(0xFF597F82),
    // PROPUESTA (4.3): same as bgElevated (13.39 with the main text); the notice also gets a border of borderAccent.
    noticeSurface = Color(0xFFF7F4EC),
    // PROPUESTA (4.3): the "surface/button" of the Figma, 10.43 with the main text.
    cardTint = Color(0xFFD9D9D9),
    // PROPUESTA (4.3): 6.06 over bgElevated.
    failAccent = Color(0xFFA13A2A),
    // Images and their layers do not depend on the theme: same as dark.
    scrimSoft = Color(0x73000000),
    overlayDark = Color(0xCC333333),
    // PROPUESTA (4.3): small panel over the background images, same as dark.
    pickerSurface = Color(0xFF8C8C8C),
    // PROPUESTA: the gold as text needs 4.5:1 on #AEF5D5, #F7F4EC and #D9D9D9 (selection #926A1B only reaches 3.91, 4.44 and 3.46).
    goldText = Color(0xFF7A5612),
    // PROPUESTA: white over the #926A1B fill.
    onGold = Color.White,
    // Over pictures: the same as dark.
    onBackdrop = Color.White,
    backdropAccent = Color(0xFFE3B54F),
)

val LocalNightWatchColors = staticCompositionLocalOf { DarkNightWatchColors }

/** Entry point to the tokens: `NightWatchTheme.colors.bgBase`. */
object NightWatchTheme {
    val colors: NightWatchColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNightWatchColors.current
}
