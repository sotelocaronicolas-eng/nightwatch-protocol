package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

private val ProfileButtonShape = RoundedCornerShape(20.dp)

/** The wide light button used across the profile screens (299x47, radius 20, "GUARDAR CAMBIOS" style). */
@Composable
fun ProfileButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(299.dp)
            .height(47.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .shadow(4.dp, ProfileButtonShape)
            .background(NightWatchSurfaceButton, ProfileButtonShape)
            .clip(ProfileButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
    }
}
