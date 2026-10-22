package com.example.nightwatchprotocol.ui.social

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.social.BattleOption
import com.example.nightwatchprotocol.data.social.BattleState
import com.example.nightwatchprotocol.data.social.LastTurn
import com.example.nightwatchprotocol.data.social.TurnOutcome
import com.example.nightwatchprotocol.ui.components.NightWatchButton
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton

/** Figma color of the rival's side of the bar (#C0503E-ish) and of the empty middle. */
private val RivalRed = Color(0xFFC0503E)
private val BarGrey = Color(0xFFA1A1A1)
private val CorrectBorder = NightWatchGold
private val IncorrectBorder = Color(0xFFC0503E)

/** The arena: the question on the person's turn, the wait on the rival's, and the feedback of the turn just played. */
@Composable
fun ArenaScreen(
    onClose: () -> Unit,
    onFinished: (battleId: String) -> Unit,
    viewModel: ArenaViewModel = viewModel(factory = ArenaViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Only while visible: each read is the heartbeat of the battle.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ArenaEvent.Finished -> onFinished(viewModel.battleId)
            }
        }
    }
    // Leaving a battle that is open loses it, so back and "X" ask first.
    BackHandler(enabled = uiState.battle?.isOpen == true) { viewModel.onLeaveClicked() }

    val rival = uiState.battle?.rivalUsername.orEmpty()
    SocialScreen(
        title = if (rival.isEmpty()) stringResource(R.string.social_battles_title) else stringResource(R.string.social_arena_title, rival),
        onClose = if (uiState.battle?.isOpen == true) viewModel::onLeaveClicked else onClose,
    ) {
        ArenaContent(
            uiState = uiState,
            onOption = viewModel::onOptionSelected,
            onNextTurn = viewModel::onNextTurn,
            onRetry = viewModel::onStart,
        )
    }
    if (uiState.confirmingLeave) {
        AlertDialog(
            onDismissRequest = viewModel::onLeaveDismissed,
            title = { Text(stringResource(R.string.social_arena_leave_title)) },
            text = { Text(stringResource(R.string.social_arena_leave_body)) },
            confirmButton = { TextButton(onClick = viewModel::onLeaveConfirmed) { Text(stringResource(R.string.social_arena_leave_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::onLeaveDismissed) { Text(stringResource(R.string.social_arena_keep_playing)) } },
        )
    }
}

@Composable
internal fun ArenaContent(
    uiState: ArenaUiState,
    onOption: (Char) -> Unit,
    onNextTurn: () -> Unit,
    onRetry: () -> Unit,
) {
    val battle = uiState.battle
    if (battle == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            uiState.error?.let { ErrorNotice(it, onRetry, Modifier.padding(horizontal = 20.dp)) } ?: SocialLoading()
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
        ) {
            HpHeader(battle)
            TurnPill(battle)
            uiState.error?.let { InlineError(it, Modifier.padding(top = 8.dp)) }
            when (uiState.phase) {
                ArenaPhase.MyQuestion -> MyQuestion(battle, uiState, onOption)
                ArenaPhase.Feedback -> Feedback(battle, uiState)
                ArenaPhase.RivalTurn -> RivalTurn(battle)
                ArenaPhase.Ulti -> SocialBodyText(stringResource(R.string.social_arena_ulti_pending), modifier = Modifier.padding(top = 24.dp))
                ArenaPhase.Finished, ArenaPhase.Loading -> Unit
            }
        }
        if (uiState.phase == ArenaPhase.Feedback) {
            NightWatchButton(
                text = stringResource(R.string.social_arena_next_turn),
                onClick = onNextTurn,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }
    }
}

/** The two life bars as one tug-of-war bar (Figma 2449:189): gold is mine, red is the rival's. */
@Composable
private fun HpHeader(battle: BattleState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        EmojiCircle(stringResource(R.string.social_search_icon_you), size = 48)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                NameLabel(stringResource(R.string.social_arena_you), Modifier.weight(1f))
                NameLabel(battle.rivalUsername, Modifier, TextAlign.End)
            }
            val mine = battle.myHp.coerceIn(0, 100)
            val rival = battle.rivalHp.coerceIn(0, 100)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(10.dp)
                    .background(BarGrey, RoundedCornerShape(5.dp)),
            ) {
                if (mine > 0) Box(modifier = Modifier.weight(mine.toFloat()).height(10.dp).background(NightWatchGold, RoundedCornerShape(5.dp)))
                // What is left of 200 is the grey middle: the more damage on both sides, the wider it is.
                val middle = (200 - mine - rival).coerceAtLeast(1)
                Box(modifier = Modifier.weight(middle.toFloat()).height(10.dp))
                if (rival > 0) Box(modifier = Modifier.weight(rival.toFloat()).height(10.dp).background(RivalRed, RoundedCornerShape(5.dp)))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                HpLabel(stringResource(R.string.social_arena_hp, battle.myHp), Modifier.weight(1f))
                HpLabel(stringResource(R.string.social_arena_hp, battle.rivalHp), Modifier, TextAlign.End)
            }
        }
        EmojiCircle(stringResource(R.string.social_search_icon_you), size = 48)
    }
}

@Composable
private fun NameLabel(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        textAlign = align,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun HpLabel(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontSize = 11.sp,
        textAlign = align,
        modifier = modifier,
    )
}

/** "Tu turno · 0:57" or "Turno de Mateo · 0:44", from the server's seconds; and the progress of the ulti. */
@Composable
private fun TurnPill(battle: BattleState) {
    val turn = battle.turn
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (turn != null) {
            val seconds by rememberSecondsLeft(turn.timeLeft)
            val time = stringResource(R.string.social_countdown, seconds / 60, seconds % 60)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .widthIn(min = 140.dp)
                    .height(28.dp)
                    .background(if (turn.isMine) NightWatchGold else SocialElevated, RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = if (turn.isMine) {
                        stringResource(R.string.social_arena_my_turn, time)
                    } else {
                        stringResource(R.string.social_arena_rival_turn, battle.rivalUsername, time)
                    },
                    color = if (turn.isMine) SocialOnLight else NightWatchOnBackground,
                    fontFamily = NightWatchBodyFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
            }
        }
        SocialBodyText(
            text = if (battle.myUltiCharged) {
                stringResource(R.string.social_arena_ulti_ready)
            } else {
                stringResource(R.string.social_arena_ulti_progress, battle.myCorrectStreak.coerceIn(0, 3))
            },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MyQuestion(battle: BattleState, uiState: ArenaUiState, onOption: (Char) -> Unit) {
    val question = battle.question ?: return
    uiState.rivalMove?.let { RivalMoveNotice(it, battle.rivalUsername) }
    Text(
        text = question.text,
        color = NightWatchOnBackground,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        question.options.forEach { option ->
            OptionRow(option, selected = uiState.chosen == option.letter, enabled = !uiState.submitting, onClick = { onOption(option.letter) })
        }
    }
}

/** One option: a light card with its letter (Figma 2449:189). [mark] is the "✗" of a wrong answer (2449:255). */
@Composable
private fun OptionRow(
    option: BattleOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = NightWatchGold,
    mark: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (selected) selectedColor else NightWatchSurfaceButton, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = option.letter.toString(), color = SocialOnLight, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            text = option.text,
            color = SocialOnLight,
            fontFamily = NightWatchBodyFontFamily,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 24.dp).weight(1f),
        )
        if (mark != null) Text(text = mark, color = NightWatchError, fontSize = 18.sp)
    }
}

/** The turn of the rival: no question on the phone, only the wait, with the warning about leaving the app. */
@Composable
private fun RivalTurn(battle: BattleState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
        EmojiCircle(stringResource(R.string.social_search_icon_you), size = 72)
        Text(
            text = stringResource(R.string.social_arena_waiting_rival, battle.rivalUsername),
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        if (battle.rivalSecondsSinceSeen > RIVAL_AWAY_SECONDS) {
            SocialBodyText(stringResource(R.string.social_arena_rival_away, battle.rivalUsername), alpha = 1f, modifier = Modifier.padding(top = 12.dp))
        }
        SocialBodyText(stringResource(R.string.social_arena_waiting_hint), modifier = Modifier.padding(top = 20.dp))
    }
}

/** From here the rival has been quiet for a while: the screen says it (the server decides the 60 seconds). */
private const val RIVAL_AWAY_SECONDS = 15.0

@Composable
private fun RivalMoveNotice(move: LastTurn, rival: String) {
    val text = when (move.outcome) {
        TurnOutcome.Correct -> stringResource(R.string.social_arena_rival_correct, rival, move.damage)
        TurnOutcome.Incorrect -> stringResource(R.string.social_arena_rival_incorrect, rival)
        TurnOutcome.Timeout -> stringResource(R.string.social_arena_rival_timeout, rival)
        TurnOutcome.Ulti -> stringResource(R.string.social_arena_rival_ulti, rival, move.damage)
    }
    SocialBodyText(text, alpha = 1f, modifier = Modifier.padding(top = 14.dp))
}

/** The result of the person's own last turn (Figma 2449:255): the tapped option and what the server decided. */
@Composable
private fun Feedback(battle: BattleState, uiState: ArenaUiState) {
    val feedback = uiState.feedback ?: return
    val correct = feedback.outcome == TurnOutcome.Correct || feedback.outcome == TurnOutcome.Ulti
    val title = when (feedback.outcome) {
        TurnOutcome.Correct -> stringResource(R.string.social_arena_correct)
        TurnOutcome.Incorrect -> stringResource(R.string.social_arena_incorrect)
        TurnOutcome.Timeout -> stringResource(R.string.social_arena_timeout)
        TurnOutcome.Ulti -> stringResource(R.string.social_arena_ulti_hit)
    }
    val detail = if (correct && feedback.damage > 0) {
        stringResource(R.string.social_arena_correct_detail, feedback.damage, battle.rivalUsername)
    } else {
        stringResource(R.string.social_arena_no_damage, battle.rivalUsername)
    }
    val border = if (correct) CorrectBorder else IncorrectBorder
    // The question and the tapped option stay on screen, as in Figma; the server never says which option was the right one.
    uiState.answered?.let { question ->
        Text(
            text = question.text,
            color = NightWatchOnBackground,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            question.options.forEach { option ->
                val tapped = feedback.chosen == option.letter
                OptionRow(
                    option = option,
                    selected = tapped,
                    enabled = false,
                    onClick = {},
                    selectedColor = if (correct) NightWatchGold else BarGrey,
                    mark = if (tapped && !correct) stringResource(R.string.social_arena_wrong_mark) else null,
                )
            }
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(top = 28.dp)
            .fillMaxWidth()
            .background(SocialElevated, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.5.dp, border), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(text = title, color = if (correct) NightWatchGold else IncorrectBorder, fontFamily = NightWatchBodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        SocialBodyText(detail, alpha = 1f, modifier = Modifier.padding(top = 4.dp))
    }
}
