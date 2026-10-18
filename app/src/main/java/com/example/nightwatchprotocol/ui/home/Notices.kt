package com.example.nightwatchprotocol.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Figma surface/pill (#94A6A7): the chapa of Inicio (108:16), the progress card of the map (2266:76) and the lesson cards. */
internal val PillColor = Color(0xFF94A6A7)

/**
 * Shows [message] in the Snackbar of Home, one at a time: a new notice replaces the one on screen instead of queueing
 * behind it (the spec asks for a single "Próximamente", "Completa la isla anterior", etc. at once).
 */
internal fun CoroutineScope.showNotice(host: SnackbarHostState, message: String) {
    host.currentSnackbarData?.dismiss()
    launch { host.showSnackbar(message) }
}
