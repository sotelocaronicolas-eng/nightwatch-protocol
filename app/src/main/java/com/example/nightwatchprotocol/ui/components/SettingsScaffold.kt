package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
 * Frame shared by the settings-style screens: [TitleTopBar], then a scrolling column with 20dp side margins and
 * 14dp between items. [decoration] is drawn behind the content (the Apariencia and Idioma illustrations).
 */
@Composable
fun SettingsScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 16.dp,
    decoration: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    NightWatchScreenBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            TitleTopBar(title = title, onClose = onClose)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                decoration()
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 24.dp)
                        .navigationBarsPadding(),
                    content = content,
                )
            }
        }
    }
}
