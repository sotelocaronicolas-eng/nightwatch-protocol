package com.example.nightwatchprotocol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NightWatchColorScheme = darkColorScheme(
    background = NightWatchBackground,
    surface = NightWatchBackground,
    primary = NightWatchSurfaceButton,
    onPrimary = NightWatchOnButton,
    onBackground = NightWatchOnBackground,
    onSurface = NightWatchOnBackground,
    error = NightWatchError,
)

@Composable
fun NightWatchProtocolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightWatchColorScheme,
        typography = Typography,
        content = content,
    )
}
