package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity

/**
 * While the element (or something inside it) has focus, scrolls the enclosing scrollable so it stays visible: once when
 * it gets focus and again every time the keyboard changes height (the keyboard appears after the focus, and the area
 * above it keeps shrinking while it animates). Without a scrollable parent it does nothing.
 */
fun Modifier.keepVisibleWithKeyboard(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    var hasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(hasFocus) {
        if (hasFocus) {
            requester.bringIntoView()
            snapshotFlow { imeInsets.getBottom(density) }.collect { requester.bringIntoView() }
        }
    }

    onFocusChanged { hasFocus = it.hasFocus }.bringIntoViewRequester(requester)
}
