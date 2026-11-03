package com.example.nightwatchprotocol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.example.nightwatchprotocol.data.preferences.ThemeMode

// Dark: exactly the scheme the app has always had (the other roles keep Material's dark defaults, as before).
internal val NightWatchDarkScheme: ColorScheme = darkColorScheme(
    background = DarkNightWatchColors.bgBase,
    surface = DarkNightWatchColors.bgBase,
    primary = DarkNightWatchColors.surfaceButton,
    onPrimary = DarkNightWatchColors.onButton,
    onBackground = DarkNightWatchColors.textPrimary,
    onSurface = DarkNightWatchColors.textPrimary,
    error = DarkNightWatchColors.error,
)

private val Color1C2B29 = LightNightWatchColors.textPrimary

// Light: every role the Material components of the app use is defined, none keeps Material's default.
internal val NightWatchLightScheme: ColorScheme = lightColorScheme(
    primary = Color1C2B29,
    onPrimary = LightNightWatchColors.ctaSurface,
    primaryContainer = LightNightWatchColors.surfaceButton,
    onPrimaryContainer = Color1C2B29,
    secondary = LightNightWatchColors.borderAccent,
    onSecondary = LightNightWatchColors.ctaSurface,
    secondaryContainer = LightNightWatchColors.surfaceButton,
    onSecondaryContainer = Color1C2B29,
    tertiary = LightNightWatchColors.selection,
    onTertiary = LightNightWatchColors.ctaSurface,
    tertiaryContainer = LightNightWatchColors.surfaceButton,
    onTertiaryContainer = Color1C2B29,
    background = LightNightWatchColors.bgBase,
    onBackground = LightNightWatchColors.textPrimary,
    surface = LightNightWatchColors.bgBase,
    onSurface = LightNightWatchColors.textPrimary,
    surfaceVariant = LightNightWatchColors.fieldSurface,
    onSurfaceVariant = LightNightWatchColors.textSecondary,
    surfaceTint = Color1C2B29,
    inverseSurface = Color1C2B29,
    inverseOnSurface = LightNightWatchColors.fieldSurface,
    inversePrimary = LightNightWatchColors.surfaceButton,
    error = LightNightWatchColors.error,
    onError = LightNightWatchColors.ctaSurface,
    errorContainer = LightNightWatchColors.fieldSurface,
    onErrorContainer = Color1C2B29,
    outline = LightNightWatchColors.borderAccent,
    outlineVariant = LightNightWatchColors.divider,
    scrim = androidx.compose.ui.graphics.Color.Black,
    surfaceBright = LightNightWatchColors.fieldSurface,
    surfaceDim = LightNightWatchColors.surfaceButton,
    surfaceContainerLowest = LightNightWatchColors.ctaSurface,
    surfaceContainerLow = LightNightWatchColors.fieldSurface,
    surfaceContainer = LightNightWatchColors.fieldSurface,
    surfaceContainerHigh = LightNightWatchColors.fieldSurface,
    surfaceContainerHighest = LightNightWatchColors.fieldSurface,
)

/** True when [themeMode] resolves to the dark palette; [systemDark] is what the phone says (only used by SYSTEM). */
fun isDarkTheme(themeMode: ThemeMode, systemDark: Boolean): Boolean = when (themeMode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> systemDark
}

@Composable
fun NightWatchProtocolTheme(themeMode: ThemeMode = ThemeMode.DEFAULT, content: @Composable () -> Unit) {
    val dark = isDarkTheme(themeMode, isSystemInDarkTheme())
    CompositionLocalProvider(LocalNightWatchColors provides if (dark) DarkNightWatchColors else LightNightWatchColors) {
        MaterialTheme(
            colorScheme = if (dark) NightWatchDarkScheme else NightWatchLightScheme,
            typography = Typography,
            content = content,
        )
    }
}

/** True when [themeMode] shows the dark palette right now. */
@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean = isDarkTheme(themeMode, isSystemInDarkTheme())
