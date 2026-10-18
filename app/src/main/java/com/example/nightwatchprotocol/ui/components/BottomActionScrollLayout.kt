package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A screen whose main button sits at the bottom, like the Figma frames, but that is part of the scrollable content.
 *
 * Without the keyboard the content is at least as tall as the available area, and the weighted spacer pushes
 * [action] to the bottom, [actionBottomPadding] above the navigation bar: it looks like a button pinned to the bottom.
 * With the keyboard the area shrinks (the screen applies imePadding), the content no longer fits, and everything,
 * button included, scrolls together, so the button never hides a field. A focused field is brought into view by
 * `NightWatchTextField`.
 */
@Composable
fun BottomActionScrollLayout(
    actionBottomPadding: Dp,
    action: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 41.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableHeight = maxHeight
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = availableHeight)
                .verticalScroll(rememberScrollState())
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
