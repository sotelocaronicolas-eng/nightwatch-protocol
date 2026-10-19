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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.learning.BadgeState
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.components.SettingsScaffold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

private val LockedCircle = Color(0xFF3E6E71)
private val BadgeTextColor = Color(0xFF162523)

/** What a locked badge shows in place of its emoji (Figma 2287:119). */
private const val LOCK_EMOJI = "🔒"

/**
 * "Logros e Insignias" (Figma 2287:100): the real state of the nine badges. Read again each time the screen comes back,
 * like the Ruta, Labs and Inicio.
 */
@Composable
fun AchievementsScreen(
    onClose: () -> Unit,
    viewModel: AchievementsViewModel = viewModel(factory = AchievementsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    SettingsScaffold(title = stringResource(R.string.learning_badges_title), onClose = onClose, topPadding = 90.dp) {
        CounterLine(uiState, onRetry = viewModel::refresh)
        uiState.badges.chunked(3).forEach { rowBadges ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                rowBadges.forEach { badge -> BadgeCard(badge, Modifier.weight(1f)) }
            }
        }
    }
}

/** "N de 9 insignias desbloqueadas" (2287:104), or what is happening while the real number is not known. */
@Composable
private fun CounterLine(uiState: AchievementsUiState, onRetry: () -> Unit) {
    val status = uiState.status
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp),
    ) {
        val text = when {
            uiState.loaded -> stringResource(R.string.learning_badges_counter, uiState.unlockedCount, uiState.totalCount)
            status is AchievementsStatus.Failed ->
                stringResource(if (status.offline) R.string.learning_badges_error_offline else R.string.learning_badges_error)
            else -> stringResource(R.string.learning_badges_loading)
        }
        Text(
            text = text,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (status is AchievementsStatus.Failed) {
            // With a last known state the counter stays and this short notice explains why it may be old.
            if (uiState.loaded) {
                Text(
                    text = stringResource(R.string.learning_badges_stale),
                    color = NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 8.dp).alpha(0.85f),
                )
            }
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.learning_retry),
                    color = NightWatchGold,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun BadgeCard(state: BadgeState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val unlocked = state.unlocked
    val name = state.definition.name
    val description = stringResource(
        if (unlocked) R.string.learning_badges_card_unlocked else R.string.learning_badges_card_locked,
        name,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(130.dp)
            .alpha(if (unlocked) 1f else 0.6f)
            .background(if (unlocked) NightWatchSurfaceButton else NightWatchSurfaceButtonMuted, shape)
            .padding(horizontal = 6.dp, vertical = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(if (unlocked) NightWatchGold else LockedCircle, CircleShape),
        ) {
            Text(
                text = if (unlocked) state.definition.emoji else LOCK_EMOJI,
                color = Color.Black,
                fontSize = 24.sp,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Text(
            text = name,
            color = BadgeTextColor,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
