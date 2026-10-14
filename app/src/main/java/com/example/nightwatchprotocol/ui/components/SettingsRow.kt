package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/** Accent used for the selected card and the check mark in Apariencia and Idioma (Figma color/accent/gold). */
val NightWatchGold = Color(0xFFE3B54F)

private val RowShape = RoundedCornerShape(16.dp)
private val RowTextColor = Color(0xFF162523)

/**
 * Light card with emoji, label and a trailing control, as in the Figma "ListItemRow".
 * Without [trailing] a chevron is drawn. With [subtitle] the row is taller (68 instead of 60).
 */
@Composable
fun SettingsRow(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    highlighted: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 68.dp else 60.dp)
            .shadow(4.dp, RowShape)
            .background(NightWatchSurfaceButton, RowShape)
            .border(if (highlighted) 3.dp else 2.dp, if (highlighted) NightWatchGold else NightWatchBorderAccent, RowShape)
            .clip(RowShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(text = emoji, color = Color.Black, fontSize = 18.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = RowTextColor,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = RowTextColor,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = "›",
                color = RowTextColor,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
            )
        }
    }
}

/** Small uppercase heading above a group of rows ("CUENTA", "PROGRESO", ...). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, dimmed: Boolean = true) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = modifier.alpha(if (dimmed) 0.7f else 1f),
    )
}
