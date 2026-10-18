package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The container of every screen. The app is edge-to-edge, so the window no longer resizes for the keyboard: the screen
 * does it here with [imePadding], after the background so the colour still fills the area behind the keyboard. A screen
 * with a form must scroll its content (verticalScroll) so a focused field can be brought into view in the space that is
 * left; the system bars are still handled by each screen.
 */
@Composable
fun NightWatchScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        content = content,
    )
}
