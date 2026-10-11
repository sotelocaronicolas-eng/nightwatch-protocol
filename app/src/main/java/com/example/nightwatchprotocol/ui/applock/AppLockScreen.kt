package com.example.nightwatchprotocol.ui.applock

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchDisplayStyle
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/**
 * Not in Figma: background, logo and an "Desbloquear" button for when the system prompt was cancelled.
 * [onRequestUnlock] shows the system prompt; it is called once when the screen appears and on each tap.
 */
@Composable
fun AppLockScreen(onRequestUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onRequestUnlock() }

    NightWatchScreenBackground(
        // Swallows touches so nothing underneath can be tapped while locked.
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(horizontal = 41.dp).padding(top = 160.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.img_logo_nightwatch),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
            Text(
                text = "Nightwatch Protocol",
                style = NightWatchDisplayStyle,
                color = NightWatchOnBackground,
                modifier = Modifier.padding(top = 16.dp, bottom = 48.dp),
            )
            NightWatchButton(text = "Desbloquear", onClick = onRequestUnlock)
        }
    }
}
