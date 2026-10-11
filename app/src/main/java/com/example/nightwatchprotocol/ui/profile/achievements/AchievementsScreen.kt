package com.example.nightwatchprotocol.ui.profile.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.SettingsScaffold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

data class Badge(val emoji: String, val name: String, val unlocked: Boolean)

/** Fixed data (decision 4): the first three are unlocked, as in the design. */
val FixedBadges: List<Badge> = listOf(
    Badge("🥇", "Primeros pasos", true),
    Badge("🔥", "Racha de 7 días", true),
    Badge("🔑", "Maestro de Contraseñas", true),
    Badge("🔒", "Cazador de Phishing", false),
    Badge("🔒", "Guardián de Redes", false),
    Badge("🔒", "Leyenda NightWatch", false),
    Badge("🔒", "Detective de Ingeniería Social", false),
    Badge("🔒", "Racha de 30 días", false),
    Badge("🔒", "Graduado NightWatch", false),
)

private val LockedCircle = Color(0xFF3E6E71)
private val BadgeTextColor = Color(0xFF162523)

@Composable
fun AchievementsScreen(onClose: () -> Unit) {
    val unlocked = FixedBadges.count { it.unlocked }
    SettingsScaffold(title = "Logros e Insignias", onClose = onClose, topPadding = 90.dp) {
        Text(
            text = "$unlocked de ${FixedBadges.size} insignias desbloqueadas",
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
        )
        FixedBadges.chunked(3).forEach { rowBadges ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                rowBadges.forEach { badge -> BadgeCard(badge, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeCard(badge: Badge, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(130.dp)
            .alpha(if (badge.unlocked) 1f else 0.6f)
            .background(if (badge.unlocked) NightWatchSurfaceButton else NightWatchSurfaceButtonMuted, shape)
            .padding(horizontal = 6.dp, vertical = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(if (badge.unlocked) NightWatchGold else LockedCircle, CircleShape),
        ) {
            Text(text = badge.emoji, color = Color.Black, fontSize = 24.sp)
        }
        Text(
            text = badge.name,
            color = BadgeTextColor,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
