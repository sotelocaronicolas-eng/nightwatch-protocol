package com.example.nightwatchprotocol.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.nightwatchprotocol.ui.theme.NightWatchTheme

/** The 46x26 on/off switch from the Figma "ToggleSwitch" (gold when on, grey when off, white thumb). */
@Composable
fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NightWatchTheme.colors
    val track by animateColorAsState(if (checked) colors.switchOn else colors.switchOff, label = "switchTrack")
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .size(width = 46.dp, height = 26.dp)
            .background(track, RoundedCornerShape(13.dp))
            .border(1.dp, colors.switchBorder, RoundedCornerShape(13.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (checked) 23.dp else 3.dp)
                .size(20.dp)
                .shadow(2.dp, CircleShape)
                .background(colors.switchThumb, CircleShape)
                .border(1.dp, colors.switchBorder, CircleShape),
        )
    }
}
