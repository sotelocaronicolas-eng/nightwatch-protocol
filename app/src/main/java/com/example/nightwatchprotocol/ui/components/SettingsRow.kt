package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

private val RowShape = RoundedCornerShape(16.dp)
private val RowTextColor = Color(0xFF162523)

/** Light card with emoji, label and chevron, as in the Figma "ListItemRow". */
@Composable
fun SettingsRow(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(4.dp, RowShape)
            .background(NightWatchSurfaceButton, RowShape)
            .border(2.dp, NightWatchBorderAccent, RowShape)
            .clip(RowShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(text = emoji, color = Color.Black, fontSize = 18.sp)
        Text(
            text = label,
            color = RowTextColor,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            color = RowTextColor,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
        )
    }
}

/** Small uppercase heading above a group of rows ("CUENTA", "PROGRESO", ...). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = modifier.alpha(0.7f),
    )
}
