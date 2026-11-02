package com.example.nightwatchprotocol.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toDrawable

// The scrims of the default `enableEdgeToEdge()` style, so the dark look stays as it was.
private const val DEFAULT_DARK_SCRIM = 0x801B1B1B.toInt()
private const val DEFAULT_LIGHT_SCRIM = 0xE6FFFFFF.toInt()

/** Status bar and navigation bar styles for the app's own look (never the phone's): light icons on dark, dark icons on light. */
fun systemBarStyles(dark: Boolean): Pair<SystemBarStyle, SystemBarStyle> =
    if (dark) {
        SystemBarStyle.dark(AndroidColor.TRANSPARENT) to SystemBarStyle.dark(DEFAULT_DARK_SCRIM)
    } else {
        SystemBarStyle.light(AndroidColor.TRANSPARENT, DEFAULT_DARK_SCRIM) to SystemBarStyle.light(DEFAULT_LIGHT_SCRIM, DEFAULT_DARK_SCRIM)
    }

/** Applies [systemBarStyles] to the activity: edge-to-edge bars and the window background that shows between screens. */
fun ComponentActivity.applyThemeChrome(dark: Boolean, windowBackground: Int) {
    val (statusBar, navigationBar) = systemBarStyles(dark)
    enableEdgeToEdge(statusBarStyle = statusBar, navigationBarStyle = navigationBar)
    window.setBackgroundDrawable(windowBackground.toDrawable())
}

/**
 * Keeps the system bars and the window background in step with the look being shown (the app's, not the phone's).
 * [dark] is whether the screen that is on shows the dark palette.
 */
@Composable
fun SystemChromeEffect(dark: Boolean) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    val background = (if (dark) DarkNightWatchColors else LightNightWatchColors).bgBase.toArgb()
    DisposableEffect(activity, dark, background) {
        activity.applyThemeChrome(dark, background)
        onDispose {}
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
