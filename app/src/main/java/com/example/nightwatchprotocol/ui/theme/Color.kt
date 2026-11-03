package com.example.nightwatchprotocol.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// The names the screens have always used. They are no longer fixed colors: each one answers to the current theme
// (`NightWatchTheme.colors`, see NightWatchColors.kt), so they can only be read inside a composition. In the dark theme
// they return exactly the old values.

val NightWatchBackground: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.bgBase
val NightWatchSurfaceButton: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.surfaceButton
val NightWatchSurfaceButtonMuted: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.surfaceButtonMuted
val NightWatchBorderAccent: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.borderAccent
val NightWatchLinkAccent: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.link
val NightWatchHeaderPanel: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.headerPanel
val NightWatchOnBackground: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.textPrimary
val NightWatchOnButton: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.onButton
val NightWatchError: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.error

/** A met requirement (the password rule lists). */
val NightWatchSuccess: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.success

/** The gold of the selection (it used to live in `ui/components`). */
val NightWatchGold: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.selection

/** The gold as text or as a small glyph (the fills and borders use [NightWatchGold]). */
val NightWatchGoldText: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.goldText

/** Secondary text over the screen background (in the dark theme it is the same color as [NightWatchHeaderPanel]). */
val NightWatchSecondaryText: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.textSecondary

/** Text of an error: the red in dark, the main text color in light (the ⚠ mark goes with it, see [errorMark]). */
val NightWatchTextError: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.textError

/** Prefixes an error message with ⚠ in the light theme only (decision A1: dark stays as it is today). */
@Composable
@ReadOnlyComposable
fun errorMark(message: String): String = if (NightWatchTheme.colors.isLight) "⚠ $message" else message
