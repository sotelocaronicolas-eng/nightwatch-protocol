package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Frame shared by the settings-style screens: [TitleTopBar], then one scrolling column. [content] has 20dp side
 * margins and 14dp between items; [footer] comes right after it and spans the full width (the illustrations of
 * Apariencia and Idioma). Everything scrolls together, so nothing is cut on small screens or with large fonts.
 */
@Composable
fun SettingsScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 16.dp,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    NightWatchScreenBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = title, onClose = onClose)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 24.dp),
                    content = content,
                )
                footer()
            }
        }
    }
}
