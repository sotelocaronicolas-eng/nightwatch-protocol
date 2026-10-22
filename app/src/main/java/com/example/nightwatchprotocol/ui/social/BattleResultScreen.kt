package com.example.nightwatchprotocol.ui.social

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.BattleEndReason
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground

/** "Fin de batalla" (Figma 2451:195 victory and 2451:209 defeat; the draw and the cancelled battle use the same layout). */
@Composable
fun BattleResultScreen(
    onBack: () -> Unit,
    onRematchSent: () -> Unit,
    viewModel: BattleResultViewModel = viewModel(factory = BattleResultViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                BattleResultEvent.RematchSent -> onRematchSent()
            }
        }
    }
    // The battle is over: back goes to Social, never into it.
    BackHandler(onBack = onBack)

    SocialScreen(title = stringResource(R.string.social_end_title), onClose = onBack) {
        BattleResultContent(uiState = uiState, onRetry = viewModel::load, onRematch = viewModel::onRematch, onBack = onBack)
    }
}

@Composable
internal fun BattleResultContent(
    uiState: BattleResultUiState,
    onRetry: () -> Unit,
    onRematch: () -> Unit,
    onBack: () -> Unit,
) {
    val kind = uiState.kind
    Box(modifier = Modifier.fillMaxSize()) {
        if (kind == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                uiState.error?.let { ErrorNotice(it, onRetry, Modifier.padding(horizontal = 20.dp)) } ?: SocialLoading()
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 150.dp),
            ) {
                Headline(uiState, kind)
                if (kind != EndKind.Cancelled) HpCard(uiState.hpLeft)
                Details(uiState, kind)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                uiState.actionError?.let { InlineError(it) }
                if (uiState.rematchFriendCode != null) {
                    SocialPill(
                        text = stringResource(R.string.social_end_rematch),
                        onClick = onRematch,
                        style = PillStyle.Outlined,
                        enabled = !uiState.rematching,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                NightWatchButton(text = stringResource(R.string.social_end_back), onClick = onBack)
            }
        }
    }
}

@Composable
private fun Headline(uiState: BattleResultUiState, kind: EndKind) {
    val icon: Int
    val title: Int
    val body: String
    when (kind) {
        EndKind.Victory -> {
            icon = R.string.social_end_victory_icon
            title = R.string.social_end_victory
            body = if (uiState.reason == BattleEndReason.ForfeitDisconnect || uiState.reason == BattleEndReason.Abandon) {
                stringResource(R.string.social_end_victory_forfeit_body, uiState.rival)
            } else {
                stringResource(R.string.social_end_victory_body, uiState.rival)
            }
        }
        EndKind.Defeat -> {
            icon = R.string.social_end_defeat_icon
            title = R.string.social_end_defeat
            // The two ways of losing without the rival winning are said clearly: leaving, and being away for over a minute.
            body = when (uiState.reason) {
                BattleEndReason.ForfeitDisconnect -> stringResource(R.string.social_end_defeat_disconnect_body)
                BattleEndReason.Abandon -> stringResource(R.string.social_end_defeat_abandon_body)
                else -> stringResource(R.string.social_end_defeat_body, uiState.rival)
            }
        }
        EndKind.Draw -> {
            icon = R.string.social_end_draw_icon
            title = R.string.social_end_draw
            body = stringResource(R.string.social_end_draw_body, uiState.rival)
        }
        EndKind.Cancelled -> {
            icon = R.string.social_end_cancelled_icon
            title = R.string.social_end_cancelled
            body = stringResource(R.string.social_end_cancelled_body)
        }
    }
    Text(text = stringResource(icon), fontSize = 56.sp)
    Text(
        text = stringResource(title),
        color = if (kind == EndKind.Victory) NightWatchGold else NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        modifier = Modifier.padding(top = 12.dp),
    )
    SocialBodyText(body, modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp))
}

@Composable
private fun HpCard(hpLeft: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(top = 28.dp)
            .fillMaxWidth()
            .background(SocialCardTint, RoundedCornerShape(16.dp))
            .padding(vertical = 20.dp),
    ) {
        Text(
            text = stringResource(R.string.social_end_hp_value, hpLeft.coerceIn(0, 100)),
            color = SocialOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        )
        Text(text = stringResource(R.string.social_end_hp_left), color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontSize = 12.sp)
    }
}

/** XP and level, the battle streak or what to review, the training streak and the score: one line each (Figma). */
@Composable
private fun Details(uiState: BattleResultUiState, kind: EndKind) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        if (kind != EndKind.Cancelled) {
            DetailLine(stringResource(if (uiState.xp > 0) R.string.social_end_xp else R.string.social_end_xp_none, uiState.xp))
            val next = uiState.nextLevelXp
            DetailLine(
                if (next != null) {
                    stringResource(R.string.social_end_level_next, uiState.level, uiState.xpTotal, next)
                } else {
                    stringResource(R.string.social_end_level, uiState.level, uiState.xpTotal)
                },
            )
        }
        if (kind == EndKind.Victory && uiState.battleStreak > 0) {
            DetailLine(stringResource(R.string.social_end_battle_streak, uiState.battleStreak))
        }
        if (kind == EndKind.Defeat && uiState.weakTopics.isNotEmpty()) {
            val topics = if (uiState.weakTopics.size >= 2) {
                stringResource(R.string.social_end_review_join, uiState.weakTopics[0], uiState.weakTopics[1])
            } else {
                uiState.weakTopics[0]
            }
            DetailLine(stringResource(R.string.social_end_review, topics))
        }
        if (kind == EndKind.Defeat) DetailLine(stringResource(R.string.social_end_streak_safe))
        val wins = uiState.wins
        val losses = uiState.losses
        if (wins != null && losses != null) {
            DetailLine(
                if (uiState.draws > 0) {
                    stringResource(R.string.social_end_score_draws, wins, losses, uiState.draws)
                } else {
                    stringResource(R.string.social_end_score, wins, losses)
                },
            )
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontSize = 13.sp,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}
