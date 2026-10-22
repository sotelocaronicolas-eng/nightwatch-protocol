package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchButton

/**
 * Where a battle found by the lobby lands until the arena exists (next stage of the module). It only says the battle is ready and
 * lets the person go back; it never plays it.
 */
@Composable
fun BattlePendingScreen(onClose: () -> Unit) {
    SocialScreen(title = stringResource(R.string.social_battles_title), onClose = onClose) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(24.dp),
        ) {
            EmptyState(stringResource(R.string.social_battle_pending))
            NightWatchButton(text = stringResource(R.string.social_back_to_social), onClick = onClose)
        }
    }
}
