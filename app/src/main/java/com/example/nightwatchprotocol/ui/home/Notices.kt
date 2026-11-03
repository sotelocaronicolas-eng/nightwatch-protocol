package com.example.nightwatchprotocol.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.example.nightwatchprotocol.ui.theme.NightWatchTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Figma surface/pill (#94A6A7 in dark): the chapa of Inicio (108:16), the progress card of the map (2266:76) and the lesson
 * cards. It is the `noticeSurface` token, so it answers to the theme.
 */
internal val PillColor: Color @Composable @ReadOnlyComposable get() = NightWatchTheme.colors.noticeSurface

/**
 * Shows [message] in the Snackbar of Home, one at a time: a new notice replaces the one on screen instead of queueing
 * behind it (the spec asks for a single "Próximamente", "Completa la isla anterior", etc. at once).
 */
internal fun CoroutineScope.showNotice(host: SnackbarHostState, message: String) {
    host.currentSnackbarData?.dismiss()
    launch { host.showSnackbar(message) }
}
