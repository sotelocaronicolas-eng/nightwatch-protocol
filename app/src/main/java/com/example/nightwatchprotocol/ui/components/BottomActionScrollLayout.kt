package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The minimum height of the scrollable content: the area the screen has WITHOUT the keyboard. [viewportPx] is the area
 * that is left (the screen already shrank by the keyboard, see `NightWatchScreenBackground`) and [imeBottomPx] is what
 * the keyboard took, so adding both gives back the height the screen has when the keyboard is closed.
 */
internal fun contentMinHeightPx(viewportPx: Int, imeBottomPx: Int): Int = viewportPx + imeBottomPx.coerceAtLeast(0)

/**
 * A screen whose main button sits at the bottom, like the Figma frames, but that is part of the scrollable content.
 *
 * The content is at least as tall as the screen WITHOUT the keyboard ([contentMinHeightPx]), and the weighted spacer
 * pushes [action] to the bottom, [actionBottomPadding] above the navigation bar. So the layout does not change when
 * the keyboard opens: the button stays where it was and the keyboard only makes the content scrollable (the area
 * shrinks, the content does not), so the person can scroll down to the button. A focused field is brought into view
 * by `NightWatchTextField`.
 *
 * The screen must apply `imePadding()` to the area that holds this layout (`NightWatchScreenBackground` does). The
 * minimum height is computed in the layout phase, so it follows the keyboard animation frame by frame. [imeInsets] is
 * the keyboard inset; it is a parameter only so a test can change it.
 */
@Composable
fun BottomActionScrollLayout(
    actionBottomPadding: Dp,
    action: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 41.dp,
    imeInsets: WindowInsets = WindowInsets.ime,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportPx = constraints.maxHeight
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .layout { measurable, incoming ->
                    // Read here, in the layout phase, so a change of the keyboard only re-measures this column.
                    val minHeight = contentMinHeightPx(viewportPx, imeInsets.getBottom(this))
                    val placeable = measurable.measure(incoming.copy(minHeight = minHeight))
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                }
                .padding(horizontal = horizontalPadding),
        ) {
            content()
            // At least this much between the content and the button; the weighted spacer takes all the rest.
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.navigationBarsPadding().padding(bottom = actionBottomPadding),
                content = action,
            )
        }
    }
}
